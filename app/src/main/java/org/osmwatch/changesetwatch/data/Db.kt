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
import kotlinx.coroutines.flow.Flow
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.Changeset

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
) {
    val bbox: BBox get() = BBox(minLat, minLon, maxLat, maxLon)
}

enum class Verdict { CONFIRMED, LIKELY, UNCERTAIN, OUTSIDE, SKIPPED_TOO_LARGE }

/** Un changeset può comparire per più aree: chiave (id, areaId). Date in epoch ms. Usata dalla fase 3. */
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
)

@Dao
interface AreaDao {
    @Query("SELECT * FROM watch_area ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WatchArea>>

    @Upsert suspend fun upsert(area: WatchArea)

    @Query("UPDATE watch_area SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE watch_area SET lastCheckedAt = :at WHERE id = :id")
    suspend fun setLastChecked(id: Long, at: Long)

    @Query("DELETE FROM watch_area WHERE id = :id")
    suspend fun delete(id: Long)
}


@Dao
interface ChangesetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // dedup su (id, areaId): non sovrascrive "seen"
    suspend fun insertAll(rows: List<ChangesetRow>)

    @Query("SELECT * FROM changeset WHERE (:areaId IS NULL OR areaId = :areaId) AND (:includeSeen OR seen = 0) ORDER BY createdAt DESC")
    fun observe(areaId: Long?, includeSeen: Boolean): Flow<List<ChangesetRow>>

    @Query("SELECT * FROM changeset WHERE id = :id AND areaId = :areaId")
    fun observeOne(id: Long, areaId: Long): Flow<ChangesetRow?>

    @Query("UPDATE changeset SET seen = 1 WHERE id = :id AND areaId = :areaId")
    suspend fun markSeen(id: Long, areaId: Long)
}

fun Changeset.toRow(areaId: Long, verdict: Verdict, now: Long) = ChangesetRow(
    id = id, areaId = areaId, user = user, uid = uid,
    createdAt = createdAt.toEpochMilli(), closedAt = closedAt?.toEpochMilli(),
    comment = comment, createdBy = createdBy, source = source, changesCount = changesCount,
    bboxMinLat = bbox?.minLat, bboxMinLon = bbox?.minLon, bboxMaxLat = bbox?.maxLat, bboxMaxLon = bbox?.maxLon,
    verdict = verdict, fetchedAt = now,
)

// shortcut: exportSchema=false e nessuna migrazione, finché l'app non è rilasciata
@Database(entities = [WatchArea::class, ChangesetRow::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun areaDao(): AreaDao
    abstract fun changesetDao(): ChangesetDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "osmwatch.db")
                .build().also { instance = it }
        }
    }
}
