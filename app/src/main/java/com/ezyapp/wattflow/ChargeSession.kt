package com.ezyapp.wattflow

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "charge_sessions")
data class ChargeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long,
    val endTs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val plugged: Int,          // BatteryManager.BATTERY_PLUGGED_* at session start
    val avgWatts: Double,
    val peakWatts: Double,
    val energyWh: Double,
)

@Dao
interface ChargeSessionDao {
    @Insert
    suspend fun insert(session: ChargeSession)

    @Query("SELECT * FROM charge_sessions ORDER BY startTs DESC LIMIT 200")
    fun recent(): Flow<List<ChargeSession>>
}

@Database(entities = [ChargeSession::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChargeSessionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "wattflow.db"
            ).build().also { instance = it }
        }
    }
}
