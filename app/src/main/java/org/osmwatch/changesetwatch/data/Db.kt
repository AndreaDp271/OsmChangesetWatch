package org.osmwatch.changesetwatch.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.osmwatch.changesetwatch.domain.AreaShape
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.Changeset
import org.osmwatch.changesetwatch.domain.LatLon
import org.osmwatch.changesetwatch.domain.Verdict

@Entity(tableName = "watch_area")
data class WatchArea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val enabled: Boolean = true,
    val lastCheckedAt: Long? = null, // epoch ms
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Non null: monitoraggio di un utente OSM (tutto il mondo) invece di un'area; la bbox allora
     * non è usata.
     */
    val watchUser: String? = null,
    /** Confine (es. comune) codificato con [AreaShape.encode]; null = vale il rettangolo. */
    val polygon: String? = null,
) {
    val bbox: BBox
        get() = BBox(minLat, minLon, maxLat, maxLon)
}

/**
 * Un changeset può comparire per più aree: chiave (id, areaId). Date in epoch ms. Usata dalla
 * fase 3.
 */
@Entity(
    tableName = "changeset",
    primaryKeys = ["id", "areaId"],
    foreignKeys = [ForeignKey(WatchArea::class, ["id"], ["areaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("areaId")],
)
data class ChangesetRow(
    val id: Long,
    val areaId: Long,
    val user: String?,
    val uid: Long?,
    val createdAt: Long,
    val closedAt: Long?,
    val comment: String?,
    val createdBy: String?,
    val source: String?,
    val changesCount: Int?,
    val bboxMinLat: Double?,
    val bboxMinLon: Double?,
    val bboxMaxLat: Double?,
    val bboxMaxLon: Double?,
    val verdict: Verdict,
    val seen: Boolean = false,
    val fetchedAt: Long,
    val createdCount: Int? = null,
    val modifiedCount: Int? = null,
    val deletedCount: Int? = null,
)

@Dao
interface AreaDao {
    @Query("SELECT * FROM watch_area ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WatchArea>>

    @Upsert suspend fun upsert(area: WatchArea)

    @Query("SELECT COUNT(*) > 0 FROM watch_area WHERE watchUser = :user")
    suspend fun isWatched(user: String): Boolean

    @Query("UPDATE watch_area SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE watch_area SET lastCheckedAt = :at WHERE id = :id")
    suspend fun setLastChecked(id: Long, at: Long)

    @Query("DELETE FROM watch_area WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ChangesetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // dedup su (id, areaId): non sovrascrive "seen"
    suspend fun insertAll(rows: List<ChangesetRow>): List<Long> // -1 = già presente

    @Query(
        "SELECT * FROM changeset WHERE (:areaId IS NULL OR areaId = :areaId) AND (:includeSeen OR seen = 0) AND verdict IN (:verdicts) ORDER BY createdAt DESC"
    )
    fun observe(
        areaId: Long?,
        includeSeen: Boolean,
        verdicts: List<String>,
    ): Flow<List<ChangesetRow>>

    @Query("SELECT MIN(createdAt) FROM changeset WHERE areaId = :areaId")
    suspend fun oldestCreatedAt(areaId: Long): Long?

    @Query(
        "SELECT * FROM changeset WHERE areaId = :areaId AND verdict = 'PENDING' ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun pending(areaId: Long, limit: Int): List<ChangesetRow>

    @Query("UPDATE changeset SET verdict = :verdict WHERE id = :id AND areaId = :areaId")
    suspend fun setVerdict(id: Long, areaId: Long, verdict: String)

    @Query("SELECT * FROM changeset WHERE id = :id AND areaId = :areaId")
    fun observeOne(id: Long, areaId: Long): Flow<ChangesetRow?>

    @Query("SELECT * FROM changeset WHERE id = :id LIMIT 1")
    suspend fun findAny(id: Long): ChangesetRow?

    /** Changeset nell'area (CONFIRMED) non ancora visti, per area. */
    @Query(
        "SELECT areaId, COUNT(*) AS n FROM changeset WHERE seen = 0 AND verdict = 'CONFIRMED' GROUP BY areaId"
    )
    fun observeUnseenCounts(): Flow<List<AreaCount>>

    /**
     * Completa i conteggi delle righe salvate prima che esistessero (l'insert IGNORE non le
     * aggiorna).
     */
    @Query(
        "UPDATE changeset SET createdCount = :c, modifiedCount = :m, deletedCount = :d WHERE id = :id AND createdCount IS NULL"
    )
    suspend fun fillCounts(id: Long, c: Int, m: Int, d: Int)

    @Query("UPDATE changeset SET seen = 1 WHERE id = :id AND areaId = :areaId")
    suspend fun markSeen(id: Long, areaId: Long)
}

data class AreaCount(val areaId: Long, val n: Int)

fun Changeset.toRow(areaId: Long, verdict: Verdict, now: Long) =
    ChangesetRow(
        id = id,
        areaId = areaId,
        user = user,
        uid = uid,
        createdAt = createdAt.toEpochMilli(),
        closedAt = closedAt?.toEpochMilli(),
        comment = comment,
        createdBy = createdBy,
        source = source,
        changesCount = changesCount,
        bboxMinLat = bbox?.minLat,
        bboxMinLon = bbox?.minLon,
        bboxMaxLat = bbox?.maxLat,
        bboxMaxLon = bbox?.maxLon,
        verdict = verdict,
        fetchedAt = now,
        createdCount = createdCount,
        modifiedCount = modifiedCount,
        deletedCount = deletedCount,
    )

// shortcut: le versioni senza Migration esplicita (< 2) si azzerano; da 2 in poi le aree
// dell'utente si conservano
@Database(entities = [WatchArea::class, ChangesetRow::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun areaDao(): AreaDao

    abstract fun changesetDao(): ChangesetDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "osmwatch.db",
                            )
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .fallbackToDestructiveMigration()
                            .build()
                            .also { instance = it }
                }
    }
}

private val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_area ADD COLUMN watchUser TEXT")
        }
    }

private val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE changeset ADD COLUMN createdCount INTEGER")
            db.execSQL("ALTER TABLE changeset ADD COLUMN modifiedCount INTEGER")
            db.execSQL("ALTER TABLE changeset ADD COLUMN deletedCount INTEGER")
        }
    }

private val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_area ADD COLUMN polygon TEXT")
        }
    }

/**
 * Forme decodificate una volta per area (il confine può avere migliaia di punti): la chiave include
 * il testo del confine.
 */
private object AreaShapes {
    private val cache = HashMap<Long, Triple<String?, AreaShape, List<List<LatLon>>>>()

    @Synchronized
    fun of(a: WatchArea): Triple<String?, AreaShape, List<List<LatLon>>> {
        cache[a.id]
            ?.takeIf { it.first == a.polygon && it.second.bbox == a.bbox }
            ?.let {
                return it
            }
        val shape = AreaShape(a.bbox, AreaShape.decode(a.polygon))
        return Triple(a.polygon, shape, shape.simplified(160)).also { cache[a.id] = it }
    }
}

val WatchArea.shape: AreaShape
    get() = AreaShapes.of(this).second

/** Confine ridotto per il glifo in lista (vuoto se l'area è un rettangolo). */
val WatchArea.glyphRings: List<List<LatLon>>
    get() = AreaShapes.of(this).third
