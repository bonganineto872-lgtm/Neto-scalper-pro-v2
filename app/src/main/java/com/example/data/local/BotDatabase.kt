package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.model.FxPair
import com.example.domain.model.SignalHistoryRecord
import com.example.domain.model.SignalStatus
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "signal_history")
data class SignalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signalId: String,
    val pairName: String,
    val timeframeName: String,
    val sideName: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val tp1Pips: Double,
    val tp2Pips: Double,
    val tp3Pips: Double,
    val capturedPips: Double,
    val maxDrawdownPips: Double,
    val confidencePercent: Int,
    val strategyName: String,
    val outcomeName: String,
    val createdAt: Long,
    val closedAt: Long
) {
    fun toDomain(): SignalHistoryRecord {
        return SignalHistoryRecord(
            id = id,
            signalId = signalId,
            pair = FxPair.entries.find { it.name == pairName } ?: FxPair.XAU_USD,
            timeframe = Timeframe.entries.find { it.name == timeframeName } ?: Timeframe.M5,
            side = TradeSide.entries.find { it.name == sideName } ?: TradeSide.BUY,
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            stopLoss = stopLoss,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            tp1Pips = tp1Pips,
            tp2Pips = tp2Pips,
            tp3Pips = tp3Pips,
            capturedPips = capturedPips,
            maxDrawdownPips = maxDrawdownPips,
            confidencePercent = confidencePercent,
            strategyName = strategyName,
            outcome = SignalStatus.entries.find { it.name == outcomeName } ?: SignalStatus.CLOSED,
            createdAt = createdAt,
            closedAt = closedAt
        )
    }

    companion object {
        fun fromDomain(record: SignalHistoryRecord): SignalHistoryEntity {
            return SignalHistoryEntity(
                id = record.id,
                signalId = record.signalId,
                pairName = record.pair.name,
                timeframeName = record.timeframe.name,
                sideName = record.side.name,
                entryPrice = record.entryPrice,
                exitPrice = record.exitPrice,
                stopLoss = record.stopLoss,
                tp1 = record.tp1,
                tp2 = record.tp2,
                tp3 = record.tp3,
                tp1Pips = record.tp1Pips,
                tp2Pips = record.tp2Pips,
                tp3Pips = record.tp3Pips,
                capturedPips = record.capturedPips,
                maxDrawdownPips = record.maxDrawdownPips,
                confidencePercent = record.confidencePercent,
                strategyName = record.strategyName,
                outcomeName = record.outcome.name,
                createdAt = record.createdAt,
                closedAt = record.closedAt
            )
        }
    }
}

@Dao
interface BotDao {
    @Query("SELECT * FROM signal_history ORDER BY closedAt DESC LIMIT 150")
    fun observeSignalHistory(): Flow<List<SignalHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignalHistory(record: SignalHistoryEntity)

    @Query("DELETE FROM signal_history")
    suspend fun clearSignalHistory()
}

@Database(
    entities = [SignalHistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class BotDatabase : RoomDatabase() {
    abstract fun botDao(): BotDao

    companion object {
        @Volatile
        private var INSTANCE: BotDatabase? = null

        fun getInstance(context: Context): BotDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BotDatabase::class.java,
                    "pippulse_bot.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class SignalHistoryLocalSource(private val dao: BotDao) {
    val signalHistory: Flow<List<SignalHistoryRecord>> =
        dao.observeSignalHistory().map { list -> list.map { it.toDomain() } }

    suspend fun recordSignalOutcome(record: SignalHistoryRecord) {
        dao.insertSignalHistory(SignalHistoryEntity.fromDomain(record))
    }

    suspend fun clearAll() {
        dao.clearSignalHistory()
    }
}
