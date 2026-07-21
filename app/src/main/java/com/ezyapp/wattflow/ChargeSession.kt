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
    // Recovered from a checkpoint after the process died mid-session (killed
    // by the OS/OEM, or crashed) instead of ending normally — the tail past
    // the last checkpoint is lost, so totals are a floor, not exact.
    val interrupted: Boolean = false,
    // First sample where level read 100 during this (charge-direction)
    // session, if any -- lets a "held at full" duration be computed
    // exactly instead of guessed. Null for discharge sessions, sessions
    // that never reached 100%, and anything recorded before this field
    // existed (see estimateReachedFullTs's trickle-detection fallback).
    val reachedFullTs: Long? = null,
)

/** Downsampled watts curve for one session (~1 point / 10 s). */
@Entity(tableName = "session_samples", indices = [Index("sessionId")])
data class SessionSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ts: Long,
    val watts: Double,
)

/** One 60-second charger benchmark run. */
@Entity(tableName = "benchmark_results")
data class BenchmarkResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val label: String,         // user-given charger name, may be blank
    val plugged: Int,          // BatteryManager.BATTERY_PLUGGED_* during the run
    val avgWatts: Double,
    val peakWatts: Double,
    val stabilityPct: Double,  // 100 = perfectly steady
    val startLevel: Int,
    val endLevel: Int,
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

    @Query("SELECT * FROM charge_sessions WHERE endTs >= :since ORDER BY startTs")
    suspend fun sessionsSince(since: Long): List<ChargeSession>

    @Query("SELECT * FROM full_charge_log ORDER BY ts")
    suspend fun fullChargeHistory(): List<FullChargeEvent>

    @Insert
    suspend fun insertBenchmark(result: BenchmarkResult): Long

    @Query("SELECT * FROM benchmark_results ORDER BY avgWatts DESC")
    fun benchmarks(): Flow<List<BenchmarkResult>>

    @Query("DELETE FROM benchmark_results WHERE id = :id")
    suspend fun deleteBenchmark(id: Long)
}

@Database(
    entities = [
        ChargeSession::class, SessionSample::class, FullChargeEvent::class,
        BenchmarkResult::class,
    ],
    version = 6,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS benchmark_results (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "ts INTEGER NOT NULL, label TEXT NOT NULL, " +
                        "plugged INTEGER NOT NULL, avgWatts REAL NOT NULL, " +
                        "peakWatts REAL NOT NULL, stabilityPct REAL NOT NULL, " +
                        "startLevel INTEGER NOT NULL, endLevel INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE charge_sessions ADD COLUMN interrupted " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Drops sessions where the level moved the wrong way for their
        // direction — a stale/corrupted checkpoint recovery artifact, not a
        // real session (see SessionRecorder.recoverCheckpoint).
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val badSessions = "(SELECT id FROM charge_sessions WHERE " +
                    "(direction = 0 AND endLevel < startLevel - 1) OR " +
                    "(direction = 1 AND endLevel > startLevel + 1))"
                db.execSQL("DELETE FROM session_samples WHERE sessionId IN $badSessions")
                db.execSQL(
                    "DELETE FROM charge_sessions WHERE " +
                        "(direction = 0 AND endLevel < startLevel - 1) OR " +
                        "(direction = 1 AND endLevel > startLevel + 1)"
                )
            }
        }

        // Exact "reached 100%" timestamp for the Sleep Drain held-at-full
        // card (v1.5.1) -- null for every pre-existing session, which is
        // fine: those fall back to trickle-detection off the stored watts
        // curve (see estimateReachedFullTs).
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE charge_sessions ADD COLUMN reachedFullTs INTEGER"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "wattflow.db"
            ).addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
            )
                .build().also { instance = it }
        }
    }
}
