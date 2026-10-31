package com.appistan.installer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class Phase { QUEUED, RESOLVING, DOWNLOADING, VERIFYING, READY, INSTALLING, AWAITING_USER, SUCCESS, SKIPPED, FAILED, CANCELLED;
    val terminal get() = this in setOf(SUCCESS, SKIPPED, FAILED, CANCELLED)
}
class Converters {
    @TypeConverter fun phase(value: String) = Phase.valueOf(value)
    @TypeConverter fun phase(value: Phase) = value.name
}
@Entity(tableName = "queue")
data class QueueItem(
    @PrimaryKey val id: String, val name: String, val position: Int,
    val phase: Phase = Phase.QUEUED, val message: String = "En espera",
    val release: String = "", val downloaded: Long = 0, val total: Long = 0,
    val sessionId: Int = -1
)
@Entity(tableName = "artifacts", indices = [Index("appId")])
data class ArtifactRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val appId: String,
    val name: String, val url: String, val size: Long, val md5: String,
    val downloadId: Long = -1, val privatePath: String = ""
)
@Dao interface QueueDao {
    @Query("SELECT * FROM queue ORDER BY position") fun observe(): Flow<List<QueueItem>>
    @Query("SELECT * FROM queue ORDER BY position") suspend fun items(): List<QueueItem>
    @Query("SELECT * FROM queue WHERE id=:id") suspend fun item(id: String): QueueItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(item: QueueItem)
    @Insert suspend fun putArtifact(file: ArtifactRow): Long
    @Update suspend fun updateArtifact(file: ArtifactRow)
    @Query("SELECT * FROM artifacts WHERE appId=:id ORDER BY id") suspend fun artifacts(id: String): List<ArtifactRow>
    @Query("DELETE FROM artifacts WHERE appId=:id") suspend fun deleteArtifacts(id: String)
    @Query("DELETE FROM queue") suspend fun clearQueue()
    @Query("DELETE FROM artifacts") suspend fun clearArtifacts()
    @Transaction suspend fun newBatch(rows: List<QueueItem>) { clearArtifacts(); clearQueue(); rows.forEach { put(it) } }
}
@Database(entities = [QueueItem::class, ArtifactRow::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class QueueDatabase : RoomDatabase() { abstract fun queue(): QueueDao }
