package fr.volaracing.training.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val activity: String,
    val aLat: Double?, val aLon: Double?,
    val bLat: Double?, val bLon: Double?,
    val gateWidth: Double,
)

@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val startUtcMs: Long,
)

/** Le trace detaille reste dans les CSV du dossier [path] (gps.csv, imu.csv, marks.csv). */
@Entity
data class Passage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val courseId: Long,
    val timeNs: Long,
    val lowQuality: Boolean,
    val vMax: Double,
    val gMax: Double,
    val startNs: Long,
    val endNs: Long,
    val path: String,
)

@Dao
interface AppDao {
    @Insert suspend fun insertCourse(c: Course): Long
    @Update suspend fun updateCourse(c: Course)
    @Query("SELECT * FROM Course ORDER BY id DESC") fun courses(): Flow<List<Course>>
    @Query("SELECT * FROM Course WHERE id = :id") suspend fun course(id: Long): Course?
    @Query("DELETE FROM Course WHERE id = :id") suspend fun deleteCourse(id: Long)

    @Insert suspend fun insertSession(s: Session): Long

    @Insert suspend fun insertPassage(p: Passage): Long
    @Query("SELECT * FROM Passage WHERE courseId = :cid ORDER BY id DESC") fun passages(cid: Long): Flow<List<Passage>>
    @Query("SELECT * FROM Passage WHERE id = :id") suspend fun passage(id: Long): Passage?
}

@Database(entities = [Course::class, Session::class, Passage::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "vola.db")
                .build().also { inst = it }
        }
    }
}
