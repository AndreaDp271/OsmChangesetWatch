package org.osmwatch.changesetwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmwatch.changesetwatch.data.AppDatabase
import org.osmwatch.changesetwatch.data.ChangesetRepository
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.data.Prefs
import org.osmwatch.changesetwatch.data.TokenStore
import org.osmwatch.changesetwatch.network.OsmAuth
import org.osmwatch.changesetwatch.network.OsmHttpException
import android.content.Intent
import android.net.Uri
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.data.toRow
import org.osmwatch.changesetwatch.domain.ChangeAction
import org.osmwatch.changesetwatch.domain.ChangeGeometry
import org.osmwatch.changesetwatch.domain.ElementData
import org.osmwatch.changesetwatch.domain.ElementDiff
import org.osmwatch.changesetwatch.domain.ElementDiffer
import org.osmwatch.changesetwatch.domain.ElementRef
import org.osmwatch.changesetwatch.domain.ElementType
import org.osmwatch.changesetwatch.domain.Flags
import org.osmwatch.changesetwatch.domain.GeometryResolver
import org.osmwatch.changesetwatch.domain.LatLon
import org.osmwatch.changesetwatch.domain.OsmChangeSummary
import org.osmwatch.changesetwatch.domain.Verdict
import org.osmwatch.changesetwatch.domain.VersionedId
import org.osmwatch.changesetwatch.network.OsmClient
import org.osmwatch.changesetwatch.network.OsmElementSource

@OptIn(ExperimentalCoroutinesApi::class)
class ChangesetsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val client = OsmClient()
    private val prefs = Prefs(app)
    private val tokens = TokenStore(app)
    private val repo = ChangesetRepository(db, client, maxChanges = { prefs.maxChanges })

    val areaFilter = MutableStateFlow<Long?>(null)
    val includeSeen = MutableStateFlow(true)
    /** false (default): solo i changeset VERIFICATI dentro l'area; true: anche in attesa/incerti/troppo grandi/fuori. */
    val showAll = MutableStateFlow(false)
    val onlyFlagged = MutableStateFlow(false)
    val hideTrusted = MutableStateFlow(false)
    /** Valori scelti toccando il chip in ciclo: 0 = nessun filtro. */
    val minChanges = MutableStateFlow(0)
    val maxAgeHours = MutableStateFlow(0)
    val suspectUsers = MutableStateFlow(prefs.suspectUsers)
    val trustedUsers = MutableStateFlow(prefs.trustedUsers)
    val refreshing = MutableStateFlow(false)
    /** "Verifica 3/25 …" durante il refresh, null altrimenti. */
    val progress = MutableStateFlow<String?>(null)
    /** Messaggio d'errore dell'ultimo refresh, null se ok. */
    val error = MutableStateFlow<String?>(null)

    val areas: StateFlow<List<WatchArea>> =
        db.areaDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Testo di ricerca: utente, commento, editor o id (filtro locale sulla lista). */
    val query = MutableStateFlow("")

    val changesets: StateFlow<List<ChangesetRow>> =
        combine(areaFilter, includeSeen, showAll) { a, s, all -> Triple(a, s, all) }
            .flatMapLatest { (a, s, all) ->
                val verdicts = if (all) Verdict.entries.map { it.name } else listOf(Verdict.CONFIRMED.name)
                db.changesetDao().observe(a, s, verdicts)
            }
            .combine(query) { rows, q ->
                val t = q.trim()
                if (t.isEmpty()) rows else rows.filter {
                    it.id.toString().contains(t) || listOf(it.user, it.comment, it.createdBy).any { f -> f?.contains(t, ignoreCase = true) == true }
                }
            }
            .let { base -> combine(base, onlyFlagged, hideTrusted, suspectUsers, trustedUsers) { rows, f, h, s, t ->
                rows.filter { r ->
                    (!f || Flags.header(r.changesCount, r.comment, r.user, s).isNotEmpty()) && (!h || r.user !in t)
                }
            } }
            .combine(minChanges) { rows, n -> if (n == 0) rows else rows.filter { (it.changesCount ?: 0) >= n } }
            .combine(maxAgeHours) { rows, h ->
                val from = System.currentTimeMillis() - h * 3_600_000L
                if (h == 0) rows else rows.filter { it.createdAt >= from }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cycleMinChanges() { minChanges.update { MIN_CHANGES_STEPS[(MIN_CHANGES_STEPS.indexOf(it) + 1) % MIN_CHANGES_STEPS.size] } }
    fun cycleMaxAge() { maxAgeHours.update { MAX_AGE_STEPS[(MAX_AGE_STEPS.indexOf(it) + 1) % MAX_AGE_STEPS.size] } }

    fun toggleSuspect(user: String) {
        suspectUsers.update { if (user in it) it - user else it + user }
        prefs.suspectUsers = suspectUsers.value
    }

    fun toggleTrusted(user: String) {
        trustedUsers.update { if (user in it) it - user else it + user }
        prefs.trustedUsers = trustedUsers.value
    }

    /** Changeset aperto da link esterno e non presente in lista: non salvato nel DB (areaId = EXTERNAL). */
    private val external = MutableStateFlow<Map<Long, ChangesetRow>>(emptyMap())

    fun observeOne(id: Long, areaId: Long): Flow<ChangesetRow?> =
        if (areaId == EXTERNAL) external.map { it[id] } else db.changesetDao().observeOne(id, areaId)

    /** true (default): cerca solo tra i changeset delle aree; false: ricerca globale su OSM (utente esatto o id). */
    val areasOnly = MutableStateFlow(true)
    val globalResults = MutableStateFlow<List<ChangesetRow>>(emptyList())
    val searching = MutableStateFlow(false)

    /** Ricerca globale: numero = id changeset, altrimenti nome utente OSM esatto (l'API non cerca nei commenti). Max 50. */
    fun searchGlobal() {
        val q = query.value.trim()
        if (q.isEmpty() || searching.value) return
        viewModelScope.launch {
            searching.value = true
            error.value = null
            try {
                val found = withContext(Dispatchers.IO) {
                    val id = q.removePrefix("#").toLongOrNull()
                    if (id != null) listOfNotNull(client.fetchChangeset(id)) else client.fetchChangesetsByUser(q, 50)
                }
                val now = System.currentTimeMillis()
                val rows = found.map { it.toRow(EXTERNAL, Verdict.PENDING, now).copy(seen = true) }
                external.update { it + rows.associateBy { r -> r.id } }
                globalResults.value = rows
                if (rows.isEmpty()) error.value = "“$q”: not found"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "“$q”: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                searching.value = false
            }
        }
    }

    /** Da richiesta di apertura (link) a [id, areaId] da mostrare; la UI lo consuma e lo azzera. */
    val openRequest = MutableStateFlow<List<Long>?>(null)

    /** Link `.../changeset/ID`: usa la riga in DB se c'è, altrimenti scarica solo l'intestazione dall'API. */
    fun openLink(id: Long) {
        viewModelScope.launch {
            error.value = null
            try {
                val known = db.changesetDao().findAny(id)
                if (known != null) { openRequest.value = listOf(id, known.areaId); return@launch }
                val cs = withContext(Dispatchers.IO) { client.fetchChangeset(id) }
                if (cs == null) { error.value = "#$id: not found"; return@launch }
                external.update { it + (id to cs.toRow(EXTERNAL, Verdict.PENDING, System.currentTimeMillis()).copy(seen = true)) }
                openRequest.value = listOf(id, EXTERNAL)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "#$id: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // --- minimappa del changeset ---
    private val geoCache = HashMap<Long, GeoState>()
    val geoState = MutableStateFlow<GeoState>(GeoState.Loading)

    /** Scarica osmChange + poche richieste (una alla volta) e ricostruisce la geometria. Oltre `maxChanges`: non scarica. */
    fun loadGeometry(row: ChangesetRow) {
        diffState.value = null
        geoCache[row.id]?.let { geoState.value = it; return }
        if ((row.changesCount ?: 0) > prefs.maxChanges) { geoState.value = GeoState.TooLarge; return }
        geoState.value = GeoState.Loading
        viewModelScope.launch {
            val s = try {
                withContext(Dispatchers.IO) {
                    val change = client.fetchOsmChange(row.id)
                    GeoState.Ready(GeometryResolver.resolve(change, OsmElementSource(client), includeBefore = true), change)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                GeoState.Failed(e.message ?: e.javaClass.simpleName)
            }
            if (s is GeoState.Ready) geoCache[row.id] = s
            geoState.value = s
        }
    }

    // --- cosa è cambiato in un elemento toccato sulla mappa ---
    val diffState = MutableStateFlow<DiffState?>(null)

    fun clearDiff() { diffState.value = null }

    /** Stato attuale dal osmChange + versione precedente (v-1, una richiesta) per il confronto. */
    fun loadDiff(ref: ElementRef) {
        val change = (geoState.value as? GeoState.Ready)?.change ?: return
        val (action, current) = currentOf(change, ref) ?: return
        diffState.value = DiffState.Loading(ref)
        viewModelScope.launch {
            diffState.value = try {
                val previous = if (action != ChangeAction.CREATE && (ref.version ?: 0) > 1) {
                    withContext(Dispatchers.IO) {
                        OsmElementSource(client).details(ref.type, listOf(VersionedId(ref.id, ref.version!! - 1)))[ref.id]
                    }
                } else null
                DiffState.Ready(ElementDiffer.diff(ref, action, current, previous))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DiffState.Failed(ref, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun currentOf(change: OsmChangeSummary, ref: ElementRef): Pair<ChangeAction, ElementData?>? = when (ref.type) {
        ElementType.NODE -> change.nodes.firstOrNull { it.id == ref.id }?.let {
            it.action to if (it.action == ChangeAction.DELETE) null else ElementData(
                if (it.lat != null && it.lon != null) LatLon(it.lat, it.lon) else null, emptyList(), it.tags,
            )
        }
        ElementType.WAY -> change.ways.firstOrNull { it.id == ref.id }?.let {
            it.action to if (it.action == ChangeAction.DELETE) null else ElementData(null, it.nodeRefs, it.tags)
        }
        ElementType.RELATION -> change.relations.firstOrNull { it.id == ref.id }?.let {
            it.action to if (it.action == ChangeAction.DELETE) null else ElementData(null, emptyList(), it.tags, it.members)
        }
    }

    // --- login OSM e commenti ---
    val loggedIn = MutableStateFlow(tokens.token != null)
    val commentState = MutableStateFlow<CommentState>(CommentState.Idle)
    private val authHttp = OsmClient.defaultHttp()
    private fun str(id: Int) = getApplication<Application>().getString(id)

    /** Intent per il browser: apre la pagina di autorizzazione OSM; il ritorno arriva a [handleOAuth]. */
    fun loginIntent(): Intent {
        val verifier = OsmAuth.randomToken()
        val state = OsmAuth.randomToken()
        prefs.pendingVerifier = verifier
        prefs.pendingState = state
        return Intent(Intent.ACTION_VIEW, Uri.parse(OsmAuth.authorizeUrl(OsmAuth.challenge(verifier), state)))
    }

    fun handleOAuth(uri: Uri) {
        val verifier = prefs.pendingVerifier
        val ok = verifier != null && uri.getQueryParameter("state") == prefs.pendingState
        prefs.pendingVerifier = null; prefs.pendingState = null
        val code = uri.getQueryParameter("code")
        if (!ok || code == null) { error.value = str(R.string.login_failed); return }
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) { OsmAuth.exchange(authHttp, code, verifier!!) }
                tokens.token = token
                loggedIn.value = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "${str(R.string.login_failed)}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun logout() { tokens.token = null; loggedIn.value = false }

    fun sendComment(changesetId: Long, text: String) {
        val token = tokens.token ?: run { loggedIn.value = false; return }
        if (commentState.value is CommentState.Sending) return
        commentState.value = CommentState.Sending
        viewModelScope.launch {
            commentState.value = try {
                withContext(Dispatchers.IO) { OsmAuth.postComment(authHttp, token, changesetId, text.trim()) }
                CommentState.Done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OsmHttpException) {
                if (e.code == 401) logout()
                CommentState.Error(when (e.code) {
                    401 -> str(R.string.comment_err_auth)
                    409 -> str(R.string.comment_err_closed)
                    429 -> str(R.string.comment_err_rate)
                    else -> e.message ?: "HTTP ${e.code}"
                })
            } catch (e: Exception) {
                CommentState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun resetComment() { commentState.value = CommentState.Idle }

    fun markSeen(id: Long, areaId: Long) = viewModelScope.launch { db.changesetDao().markSeen(id, areaId) }

    /**
     * Scarica gli ultimi 100 changeset per ogni area abilitata, anche precedenti alla creazione dell'area, e li verifica
     * (dentro l'area davvero?). Aree una alla volta con pausa di 1 s (rate limit). I duplicati sono ignorati dal DB.
     */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            val failures = mutableListOf<String>()
            try {
                val enabled = db.areaDao().observeAll().first().filter { it.enabled }
                enabled.forEachIndexed { i, area ->
                    if (i > 0) delay(1_000)
                    try {
                        repo.refreshLatest(area) { done, total -> progress.value = if (total > 0) "${area.name}: $done/$total" else null }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures += "${area.name}: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            } finally {
                progress.value = null
                error.update { failures.takeIf { it.isNotEmpty() }?.joinToString("\n") }
                refreshing.value = false
            }
        }
    }
}

/** areaId fittizio dei changeset aperti da link e non appartenenti a nessuna area. */
const val EXTERNAL = -1L

private val MIN_CHANGES_STEPS = listOf(0, 10, 100, 500)
private val MAX_AGE_STEPS = listOf(0, 24, 168) // ore: tutto, 24 h, 7 giorni

sealed interface GeoState {
    data object Loading : GeoState
    data object TooLarge : GeoState
    data class Failed(val message: String) : GeoState
    data class Ready(val geometry: ChangeGeometry, val change: OsmChangeSummary) : GeoState
}

sealed interface DiffState {
    data class Loading(val ref: ElementRef) : DiffState
    data class Failed(val ref: ElementRef, val message: String) : DiffState
    data class Ready(val diff: ElementDiff) : DiffState
}

sealed interface CommentState {
    data object Idle : CommentState
    data object Sending : CommentState
    data object Done : CommentState
    data class Error(val message: String) : CommentState
}
