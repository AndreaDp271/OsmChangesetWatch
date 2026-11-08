package org.osmwatch.changesetwatch.ui

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.AppDatabase
import org.osmwatch.changesetwatch.data.ChangesetRepository
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.data.Prefs
import org.osmwatch.changesetwatch.data.TokenStore
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.data.toRow
import org.osmwatch.changesetwatch.domain.ChangeAction
import org.osmwatch.changesetwatch.domain.ChangeGeometry
import org.osmwatch.changesetwatch.domain.ChangeKind
import org.osmwatch.changesetwatch.domain.ChangesetComment
import org.osmwatch.changesetwatch.domain.ElementData
import org.osmwatch.changesetwatch.domain.ElementDiff
import org.osmwatch.changesetwatch.domain.ElementDiffer
import org.osmwatch.changesetwatch.domain.ElementHistory
import org.osmwatch.changesetwatch.domain.ElementRef
import org.osmwatch.changesetwatch.domain.ElementType
import org.osmwatch.changesetwatch.domain.ElementVersion
import org.osmwatch.changesetwatch.domain.Flags
import org.osmwatch.changesetwatch.domain.GeometryResolver
import org.osmwatch.changesetwatch.domain.LatLon
import org.osmwatch.changesetwatch.domain.OsmChangeSummary
import org.osmwatch.changesetwatch.domain.RowTags
import org.osmwatch.changesetwatch.domain.UserActivity
import org.osmwatch.changesetwatch.domain.UserActivityCalc
import org.osmwatch.changesetwatch.domain.UserBlock
import org.osmwatch.changesetwatch.domain.UserInfo
import org.osmwatch.changesetwatch.domain.Verdict
import org.osmwatch.changesetwatch.domain.VersionedId
import org.osmwatch.changesetwatch.domain.attempt
import org.osmwatch.changesetwatch.domain.reason
import org.osmwatch.changesetwatch.network.OsmAuth
import org.osmwatch.changesetwatch.network.OsmClient
import org.osmwatch.changesetwatch.network.OsmElementSource
import org.osmwatch.changesetwatch.network.OsmHttpException

@OptIn(ExperimentalCoroutinesApi::class)
class ChangesetsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val client = OsmClient()
    private val prefs = Prefs(app)
    private val tokens = TokenStore(app)
    private val repo = ChangesetRepository(db, client, maxChanges = { prefs.maxChanges })

    val areaFilter = MutableStateFlow<Long?>(null)
    val includeSeen = MutableStateFlow(true)
    /**
     * false (default): solo i changeset VERIFICATI dentro l'area; true: anche in
     * attesa/incerti/troppo grandi/fuori.
     */
    val showAll = MutableStateFlow(false)
    val onlyFlagged = MutableStateFlow(false)
    val hideTrusted = MutableStateFlow(false)
    /** 0 = nessun filtro; valori in [MIN_CHANGES_STEPS] e [MAX_AGE_STEPS]. */
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
        db.areaDao()
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Testo di ricerca: utente, commento, editor o id (filtro locale sulla lista). */
    val query = MutableStateFlow("")

    private val candidates: Flow<List<ChangesetRow>> =
        combine(areaFilter, includeSeen, showAll) { a, s, all -> Triple(a, s, all) }
            .flatMapLatest { (a, s, all) ->
                val verdicts =
                    if (all) Verdict.entries.map { it.name } else listOf(Verdict.CONFIRMED.name)
                db.changesetDao().observe(a, s, verdicts)
            }
            .combine(query) { rows, q ->
                val t = q.trim()
                if (t.isEmpty()) rows
                else
                    rows.filter {
                        it.id.toString().contains(t) ||
                            listOf(it.user, it.comment, it.createdBy).any { f ->
                                f?.contains(t, ignoreCase = true) == true
                            }
                    }
            }
            .let { base ->
                combine(base, onlyFlagged, hideTrusted, suspectUsers, trustedUsers) {
                    rows,
                    f,
                    h,
                    s,
                    t ->
                    rows.filter { r ->
                        (!f ||
                            (Flags.header(r.changesCount, r.comment, r.user, s) +
                                    Flags.fromCounts(
                                        r.createdCount,
                                        r.modifiedCount,
                                        r.deletedCount,
                                    ))
                                .isNotEmpty()) && (!h || r.user !in t)
                    }
                }
            }
            .combine(minChanges) { rows, n ->
                if (n == 0) rows else rows.filter { (it.changesCount ?: 0) >= n }
            }
            .combine(maxAgeHours) { rows, h ->
                val from = System.currentTimeMillis() - h * 3_600_000L
                if (h == 0) rows else rows.filter { it.createdAt >= from }
            }

    val editorFilter = MutableStateFlow<String?>(null)
    val hashtagFilter = MutableStateFlow<String?>(null)
    val kindFilter = MutableStateFlow(ChangeKind.ANY)

    /**
     * Voci dei menu: calcolate prima di questi due filtri, così scegliere un editor non nasconde
     * gli altri.
     */
    val editors: StateFlow<List<String>> =
        candidates
            .map { rows -> rows.mapNotNull { RowTags.editor(it.createdBy) }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hashtags: StateFlow<List<String>> =
        candidates
            .map { rows -> rows.flatMap { RowTags.hashtags(it.comment) }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val changesets: StateFlow<List<ChangesetRow>> =
        combine(candidates, editorFilter, hashtagFilter, kindFilter) { rows, e, h, k ->
                rows.filter {
                    (e == null || RowTags.editor(it.createdBy) == e) &&
                        (h == null || h in RowTags.hashtags(it.comment)) &&
                        k.matches(it.createdCount, it.modifiedCount, it.deletedCount)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Torna alla lista di default: solo changeset verificati nell'area, nessun altro filtro (area e
     * ricerca restano).
     */
    fun resetFilters() {
        includeSeen.value = true
        showAll.value = false
        onlyFlagged.value = false
        hideTrusted.value = false
        minChanges.value = 0
        maxAgeHours.value = 0
        editorFilter.value = null
        hashtagFilter.value = null
        kindFilter.value = ChangeKind.ANY
    }

    /** Segui un utente da qualsiasi changeset; ignora i doppioni. */
    fun watchUser(name: String) {
        viewModelScope.launch {
            // dal DB, non da [areas]: se nessuna schermata la osserva quella lista è vecchia
            if (db.areaDao().isWatched(name)) return@launch
            db.areaDao()
                .upsert(
                    WatchArea(
                        name = "@$name",
                        minLat = 0.0,
                        minLon = 0.0,
                        maxLat = 0.0,
                        maxLon = 0.0,
                        watchUser = name,
                    )
                )
        }
    }

    /** Sospetto e fidato si escludono: segnarne uno toglie l'altro. */
    fun toggleSuspect(user: String) {
        val add = user !in suspectUsers.value
        suspectUsers.update { if (add) it + user else it - user }
        if (add) trustedUsers.update { it - user }
        saveUserLists()
    }

    fun toggleTrusted(user: String) {
        val add = user !in trustedUsers.value
        trustedUsers.update { if (add) it + user else it - user }
        if (add) suspectUsers.update { it - user }
        saveUserLists()
    }

    private fun saveUserLists() {
        prefs.suspectUsers = suspectUsers.value
        prefs.trustedUsers = trustedUsers.value
    }

    /** Dalla schermata utenti: i changeset recenti di [user], ovunque nel mondo. */
    fun showUserChangesets(user: String) {
        areasOnly.value = false
        query.value = user
        searchGlobal()
    }

    /** Esce dalla ricerca globale: di nuovo la lista delle aree, senza il testo cercato. */
    fun backToAreas() {
        areasOnly.value = true
        query.value = ""
    }

    /**
     * Changeset aperto da link esterno e non presente in lista: non salvato nel DB (areaId =
     * EXTERNAL).
     */
    private val external = MutableStateFlow<Map<Long, ChangesetRow>>(emptyMap())

    /**
     * I changeset aperti da link stanno solo in memoria: se Android ha chiuso l'app e la riapre sul
     * dettaglio, vanno riscaricati.
     */
    fun ensureExternal(id: Long) {
        if (id !in external.value) openLink(id)
    }

    fun observeOne(id: Long, areaId: Long): Flow<ChangesetRow?> =
        if (areaId == EXTERNAL) external.map { it[id] }
        else db.changesetDao().observeOne(id, areaId)

    /**
     * true (default): cerca solo tra i changeset delle aree; false: ricerca globale su OSM (utente
     * esatto o id).
     */
    val areasOnly = MutableStateFlow(true)
    val globalResults = MutableStateFlow<List<ChangesetRow>>(emptyList())
    val searching = MutableStateFlow(false)

    /**
     * Ricerca globale: numero = id changeset, altrimenti nome utente OSM esatto (l'API non cerca
     * nei commenti). Max 50.
     */
    fun searchGlobal() {
        val q = query.value.trim()
        if (q.isEmpty() || searching.value) return
        viewModelScope.launch {
            searching.value = true
            error.value = null
            attempt {
                    val found =
                        withContext(Dispatchers.IO) {
                            val id = q.removePrefix("#").toLongOrNull()
                            if (id != null) listOfNotNull(client.fetchChangeset(id))
                            else client.fetchChangesetsByUser(q, 50)
                        }
                    val now = System.currentTimeMillis()
                    val rows =
                        found.map { it.toRow(EXTERNAL, Verdict.PENDING, now).copy(seen = true) }
                    external.update { it + rows.associateBy { r -> r.id } }
                    globalResults.value = rows
                    if (rows.isEmpty()) error.value = "“$q”: not found"
                }
                .onFailure { error.value = "“$q”: ${it.reason}" }
            searching.value = false
        }
    }

    /** Da richiesta di apertura (link) a [id, areaId] da mostrare; la UI lo consuma e lo azzera. */
    val openRequest = MutableStateFlow<List<Long>?>(null)

    /**
     * Link `.../changeset/ID`: usa la riga in DB se c'è, altrimenti scarica solo l'intestazione
     * dall'API.
     */
    fun openLink(id: Long) {
        viewModelScope.launch {
            error.value = null
            attempt {
                    val known = db.changesetDao().findAny(id)
                    if (known != null) {
                        openRequest.value = listOf(id, known.areaId)
                        return@launch
                    }
                    val cs = withContext(Dispatchers.IO) { client.fetchChangeset(id) }
                    if (cs == null) {
                        error.value = "#$id: not found"
                        return@launch
                    }
                    external.update {
                        it +
                            (id to
                                cs.toRow(EXTERNAL, Verdict.PENDING, System.currentTimeMillis())
                                    .copy(seen = true))
                    }
                    openRequest.value = listOf(id, EXTERNAL)
                }
                .onFailure { error.value = "#$id: ${it.reason}" }
        }
    }

    // --- minimappa del changeset ---
    private val geoCache = HashMap<Long, GeoState>()
    val geoState = MutableStateFlow<GeoState>(GeoState.Loading)

    /**
     * Scarica osmChange + poche richieste (una alla volta) e ricostruisce la geometria. Oltre
     * `maxChanges`: non scarica.
     */
    fun loadGeometry(row: ChangesetRow) {
        diffState.value = null
        geoCache[row.id]?.let {
            geoState.value = it
            return
        }
        if ((row.changesCount ?: 0) > prefs.maxChanges) {
            geoState.value = GeoState.TooLarge
            return
        }
        geoState.value = GeoState.Loading
        viewModelScope.launch {
            val s =
                attempt {
                        withContext(Dispatchers.IO) {
                            val change = client.fetchOsmChange(row.id)
                            GeoState.Ready(
                                GeometryResolver.resolve(
                                    change,
                                    OsmElementSource(client),
                                    includeBefore = true,
                                ),
                                change,
                            )
                        }
                    }
                    .getOrElse { GeoState.Failed(it.reason) }
            if (s is GeoState.Ready) geoCache[row.id] = s
            geoState.value = s
        }
    }

    // --- cronologia completa di un elemento ---
    val historyState = MutableStateFlow<HistoryState?>(null)

    fun loadHistory(ref: ElementRef) {
        historyState.value = HistoryState.Loading(ref)
        viewModelScope.launch {
            historyState.value =
                attempt {
                        val versions =
                            withContext(Dispatchers.IO) { client.fetchHistory(ref.type, ref.id) }
                        HistoryState.Ready(ref, ElementHistory.diffs(ref, versions))
                    }
                    .getOrElse { HistoryState.Failed(ref, it.reason) }
        }
    }

    fun clearHistory() {
        historyState.value = null
    }

    // --- blocchi ricevuti dall'autore, con data e motivo ---
    val blocksState = MutableStateFlow<BlocksState?>(null)

    fun loadBlocks(user: String) {
        val loading = BlocksState.Loading(user)
        blocksState.value = loading
        viewModelScope.launch {
            val result =
                attempt {
                        BlocksState.Ready(
                            user,
                            withContext(Dispatchers.IO) { client.fetchBlocks(user) },
                        )
                    }
                    .getOrElse { BlocksState.Failed(user, it.reason) }
            // chiuso o riaperto per un altro utente nel frattempo: il risultato non serve più
            if (blocksState.value === loading) blocksState.value = result
        }
    }

    fun clearBlocks() {
        blocksState.value = null
    }

    // --- cosa è cambiato in un elemento toccato sulla mappa ---
    val diffState = MutableStateFlow<DiffState?>(null)

    fun clearDiff() {
        diffState.value = null
    }

    /** Stato attuale dal osmChange + versione precedente (v-1, una richiesta) per il confronto. */
    fun loadDiff(ref: ElementRef) {
        val change = (geoState.value as? GeoState.Ready)?.change ?: return
        val (action, current) = currentOf(change, ref) ?: return
        diffState.value = DiffState.Loading(ref)
        viewModelScope.launch {
            diffState.value =
                attempt {
                        val previous =
                            if (action != ChangeAction.CREATE && (ref.version ?: 0) > 1) {
                                withContext(Dispatchers.IO) {
                                    OsmElementSource(client)
                                        .details(
                                            ref.type,
                                            listOf(VersionedId(ref.id, ref.version!! - 1)),
                                        )[ref.id]
                                }
                            } else null
                        DiffState.Ready(ElementDiffer.diff(ref, action, current, previous))
                    }
                    .getOrElse { DiffState.Failed(ref, it.reason) }
        }
    }

    private fun currentOf(
        change: OsmChangeSummary,
        ref: ElementRef,
    ): Pair<ChangeAction, ElementData?>? =
        when (ref.type) {
            ElementType.NODE ->
                change.nodes
                    .firstOrNull { it.id == ref.id }
                    ?.let {
                        it.action to
                            if (it.action == ChangeAction.DELETE) null
                            else
                                ElementData(
                                    if (it.lat != null && it.lon != null) LatLon(it.lat, it.lon)
                                    else null,
                                    emptyList(),
                                    it.tags,
                                )
                    }
            ElementType.WAY ->
                change.ways
                    .firstOrNull { it.id == ref.id }
                    ?.let {
                        it.action to
                            if (it.action == ChangeAction.DELETE) null
                            else ElementData(null, it.nodeRefs, it.tags)
                    }
            ElementType.RELATION ->
                change.relations
                    .firstOrNull { it.id == ref.id }
                    ?.let {
                        it.action to
                            if (it.action == ChangeAction.DELETE) null
                            else ElementData(null, emptyList(), it.tags, it.members)
                    }
        }

    // --- profilo dell'autore (pubblico) ---
    val userInfo = MutableStateFlow<UserInfo?>(null)
    private val userCache = HashMap<Long, UserInfo?>()
    private var userFor = 0L
    val userAvatar = MutableStateFlow<ImageBitmap?>(null)
    private val avatarCache = HashMap<Long, ImageBitmap?>()

    fun loadUser(uid: Long?) {
        userFor = uid ?: 0L
        if (uid == null) {
            userInfo.value = null
            userAvatar.value = null
            return
        }
        if (userCache.containsKey(uid)) {
            userInfo.value = userCache[uid]
            userAvatar.value = avatarCache[uid]
            return
        }
        userInfo.value = null
        userAvatar.value = null
        viewModelScope.launch {
            attempt { // se fallisce resta null: la scheda autore non mostra il profilo
                val u = withContext(Dispatchers.IO) { client.fetchUser(uid) }
                userCache[uid] = u
                if (userFor == uid) userInfo.value = u
                // avatar facoltativo: se manca o fallisce resta l'icona generica
                val img =
                    u?.imageUrl?.let { url ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                    client.fetchImage(url)?.let {
                                        BitmapFactory.decodeByteArray(it, 0, it.size)
                                            ?.asImageBitmap()
                                    }
                                }
                                .getOrNull()
                        }
                    }
                avatarCache[uid] = img
                if (userFor == uid) userAvatar.value = img
            }
        }
    }

    // --- attività recente dell'autore (stile HDYC, solo su richiesta) ---
    val activity = MutableStateFlow<UserActivity?>(null)
    val activityLoading = MutableStateFlow(false)
    private val activityCache = HashMap<Long, UserActivity?>()
    private var activityFor = 0L

    fun loadActivity(uid: Long) {
        activityFor = uid
        if (activityCache.containsKey(uid)) {
            activity.value = activityCache[uid]
            activityLoading.value = false
            return
        }
        activity.value = null
        activityLoading.value = true
        viewModelScope.launch {
            attempt { // se fallisce resta null: la sezione mostra "non disponibile"
                val list = withContext(Dispatchers.IO) { client.fetchChangesetsByUid(uid, 100) }
                val areaBoxes = areas.value.filter { it.watchUser == null }.map { it.bbox }
                val a = UserActivityCalc.of(list, areaBoxes)
                activityCache[uid] = a
                if (activityFor == uid) activity.value = a
            }
            if (activityFor == uid) activityLoading.value = false
        }
    }

    // --- discussione del changeset (pubblica, senza login) ---
    /** null = in caricamento o non disponibile. */
    val discussion = MutableStateFlow<List<ChangesetComment>?>(null)
    private var discussionFor = 0L

    fun loadDiscussion(id: Long) {
        if (discussionFor != id) discussion.value = null
        discussionFor = id
        viewModelScope.launch {
            attempt { // se fallisce resta null: la sezione commenti non compare
                val list = withContext(Dispatchers.IO) { client.fetchDiscussion(id) }
                if (discussionFor == id) discussion.value = list
            }
        }
    }

    // --- login OSM e commenti ---
    val loggedIn = MutableStateFlow(tokens.token != null)
    val commentState = MutableStateFlow<CommentState>(CommentState.Idle)
    private val authHttp = OsmClient.defaultHttp()

    private fun str(id: Int) = getApplication<Application>().getString(id)

    /**
     * Intent per il browser: apre la pagina di autorizzazione OSM; il ritorno arriva a
     * [handleOAuth].
     */
    fun loginIntent(): Intent {
        val verifier = OsmAuth.randomToken()
        val state = OsmAuth.randomToken()
        prefs.pendingVerifier = verifier
        prefs.pendingState = state
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse(OsmAuth.authorizeUrl(OsmAuth.challenge(verifier), state)),
        )
    }

    private fun loginFailed(detail: String) {
        Log.w("OsmLogin", detail)
        val msg = "${str(R.string.login_failed)}: $detail"
        error.value = msg
        Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
    }

    fun handleOAuth(uri: Uri) {
        val verifier = prefs.pendingVerifier
        val ok = verifier != null && uri.getQueryParameter("state") == prefs.pendingState
        prefs.pendingVerifier = null
        prefs.pendingState = null
        val code = uri.getQueryParameter("code")
        if (!ok || code == null) {
            loginFailed(
                uri.getQueryParameter("error_description")
                    ?: uri.getQueryParameter("error")
                    ?: if (verifier == null) "no pending login" else "state mismatch"
            )
            return
        }
        viewModelScope.launch {
            attempt {
                    tokens.token =
                        withContext(Dispatchers.IO) { OsmAuth.exchange(authHttp, code, verifier!!) }
                    loggedIn.value = true
                }
                .onFailure { loginFailed(it.reason) }
        }
    }

    fun logout() {
        tokens.token = null
        loggedIn.value = false
    }

    fun sendComment(changesetId: Long, text: String) {
        val token =
            tokens.token
                ?: run {
                    loggedIn.value = false
                    return
                }
        if (commentState.value is CommentState.Sending) return
        commentState.value = CommentState.Sending
        viewModelScope.launch {
            commentState.value =
                attempt {
                        withContext(Dispatchers.IO) {
                            OsmAuth.postComment(authHttp, token, changesetId, text.trim())
                        }
                        loadDiscussion(changesetId)
                        CommentState.Done
                    }
                    .getOrElse { e ->
                        if (e is OsmHttpException && e.code == 401) logout()
                        CommentState.Error(
                            when ((e as? OsmHttpException)?.code) {
                                401 -> str(R.string.comment_err_auth)
                                409 -> str(R.string.comment_err_closed)
                                429 -> str(R.string.comment_err_rate)
                                else -> e.reason
                            }
                        )
                    }
        }
    }

    fun resetComment() {
        commentState.value = CommentState.Idle
    }

    fun markSeen(id: Long, areaId: Long) =
        viewModelScope.launch { db.changesetDao().markSeen(id, areaId) }

    val loadingMore = MutableStateFlow(false)
    /** Esito dell'ultimo "Carica altri" (testo già pronto), null finché non lo premi. */
    val loadMoreResult = MutableStateFlow<String?>(null)

    /**
     * Pagina più vecchia per l'area scelta, o per tutte le aree abilitate; una alla volta, 1 s di
     * pausa.
     */
    fun loadMore() {
        if (loadingMore.value || refreshing.value) return
        viewModelScope.launch {
            loadingMore.value = true
            loadMoreResult.value = null
            error.value = null
            var fetched = 0
            var confirmed = 0
            val failures = mutableListOf<String>()
            try {
                val all = db.areaDao().observeAll().first()
                val targets =
                    areaFilter.value?.let { id -> all.filter { it.id == id } }
                        ?: all.filter { it.enabled }
                targets.forEachIndexed { i, area ->
                    if (i > 0) delay(1_000)
                    attempt { repo.loadOlder(area, progressFor(area)) }
                        .onSuccess {
                            fetched += it.fetched
                            confirmed += it.confirmed
                        }
                        .onFailure { failures += "${area.name}: ${it.reason}" }
                }
                val res = getApplication<Application>().resources
                loadMoreResult.value =
                    if (fetched == 0) res.getString(R.string.load_more_none)
                    else
                        res.getQuantityString(R.plurals.load_more_done, fetched, fetched, confirmed)
            } finally {
                progress.value = null
                error.update { failures.takeIf { it.isNotEmpty() }?.joinToString("\n") }
                loadingMore.value = false
            }
        }
    }

    private fun progressFor(area: WatchArea): (Int, Int) -> Unit = { done, total ->
        progress.value = if (total > 0) "${area.name}: $done/$total" else null
    }

    /**
     * Scarica gli ultimi changeset di ogni area abilitata e li verifica (dentro l'area davvero?).
     * Aree una alla volta con pausa di 1 s (rate limit); i duplicati sono ignorati dal DB.
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
                    attempt { repo.refreshLatest(area, progressFor(area)) }
                        .onFailure { failures += "${area.name}: ${it.reason}" }
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

val MIN_CHANGES_STEPS = listOf(0, 10, 100, 500)
val MAX_AGE_STEPS = listOf(0, 24, 168) // ore: tutto, 24 h, 7 giorni

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

sealed interface BlocksState {
    val user: String

    data class Loading(override val user: String) : BlocksState

    data class Failed(override val user: String, val message: String) : BlocksState

    data class Ready(override val user: String, val blocks: List<UserBlock>) : BlocksState
}

sealed interface HistoryState {
    val ref: ElementRef

    data class Loading(override val ref: ElementRef) : HistoryState

    data class Failed(override val ref: ElementRef, val message: String) : HistoryState

    data class Ready(
        override val ref: ElementRef,
        val versions: List<Pair<ElementVersion, ElementDiff>>,
    ) : HistoryState
}
