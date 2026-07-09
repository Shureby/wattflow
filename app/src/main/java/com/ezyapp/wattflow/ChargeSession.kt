package com.ezyapp.wattflow

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val DIRECTION_CHARGE = 0
const val DIRECTION_DISCHARGE = 1

@Entity(tableName = "charge_sessions")
data class ChargeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long,
    val endTs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val plugged: Int,          // BatteryManager.BATTERY_PLUGGED_* at session start
    val avgWatts: Double,      // positive magnitude in both directions
    val peakWatts: Double,
    val energyWh: Double,
    val direction: Int = DIRECTION_CHARGE,
)

/** Downsampled watts curve for one session (~1 point / 10 s). */
@Entity(tableName = "session_samples", indices = [Index("sessionId")])
data class SessionSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ts: Long,
    val watts: Double,
)

/** Logged when a charge session reaches 100% — baseline for battery health trend. */
@Entity(tableName = "full_charge_log")
data class FullChargeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val fromLevel: Int,
    val energyWh: Double,
    val chargeCounterUah: Long,   // -1 when the device doesn't report it
)

@Dao
interface ChargeSessionDao {
    @Insert
    suspend fun insert(session: ChargeSession): Long

    @Insert
    suspend fun insertSamples(samples: List<SessionSample>)

    @Insert
    suspend fun insertFullCharge(event: FullChargeEvent)

    @Query("SELECT * FROM charge_sessions ORDER BY startTs DESC LIMIT 200")
    fun recent(): Flow<List<ChargeSession>>

    @Query("SELECT * FROM session_samples WHERE sessionId = :sessionId ORDER BY ts")
    suspend fun samplesFor(sessionId: Long): List<SessionSample>

    @Query("SELECT * FROM charge_sessions ORDER BY startTs")
    suspend fun allSessions(): List<ChargeSession>

    @Query("SELECT * FROM full_charge_log ORDER BY ts")
    suspend fun fullChargeHistory(): List<FullChargeEvent>
}

@Database(
    entities = [ChargeSession::class, SessionSample::class, FullChargeEvent::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChargeSessionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE charge_sessions ADD COLUMN direction INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS session_samples (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionId INTEGER NOT NULL, ts INTEGER NOT NULL, watts REAL NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_session_samples_sessionId " +
                        "ON session_samples (sessionId)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS full_charge_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "ts INTEGER NOT NULL, fromLevel INTEGER NOT NULL, " +
                        "energyWh REAL NOT NULL, chargeCounterUah INTEGER NOT NULL)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "wattflow.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
