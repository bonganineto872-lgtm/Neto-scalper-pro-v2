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
import com.example.domain.model.ClosedTradeRecord
import com.example.domain.model.EquityPoint
import com.example.domain.model.FxPair
import com.example.domain.model.TradeSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "trade_history")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pairName: String,
    val sideName: String,
    val lotSize: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val pipsPnl: Double,
    val realizedPnlZar: Double,
    val strategyName: String,
    val closeReason: String,
    val openedAt: Long,
    val closedAt: Long,
    val environmentLabel: String
) {
    fun toDomain(): ClosedTradeRecord {
        return ClosedTradeRecord(
            id = id,
            pair = FxPair.entries.find { it.name == pairName } ?: FxPair.EUR_USD,
            side = TradeSide.entries.find { it.name == sideName } ?: TradeSide.BUY,
            lotSize = lotSize,
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            pipsPnl = pipsPnl,
            realizedPnlZar = realizedPnlZar,
            strategyName = strategyName,
            closeReason = closeReason,
            openedAt = openedAt,
            closedAt = closedAt,
            environmentLabel = environmentLabel
        )
    }

    companion object {
        fun fromDomain(record: ClosedTradeRecord): TradeEntity {
            return TradeEntity(
                id = record.id,
                pairName = record.pair.name,
                sideName = record.side.name,
                lotSize = record.lotSize,
                entryPrice = record.entryPrice,
                exitPrice = record.exitPrice,
                pipsPnl = record.pipsPnl,
                realizedPnlZar = record.realizedPnlZar,
                strategyName = record.strategyName,
                closeReason = record.closeReason,
                openedAt = record.openedAt,
                closedAt = record.closedAt,
                environmentLabel = record.environmentLabel
            )
        }
    }
}

@Entity(tableName = "equity_snapshots")
data class EquitySnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val equity: Double
) {
    fun toDomain(): EquityPoint = EquityPoint(timestamp = timestamp, equity = equity)
}

@Dao
interface BotDao {
    @Query("SELECT * FROM trade_history ORDER BY closedAt DESC LIMIT 150")
    fun observeTradeHistory(): Flow<List<TradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeEntity)

    @Query("DELETE FROM trade_history")
    suspend fun clearTradeHistory()

    @Query("SELECT * FROM equity_snapshots ORDER BY timestamp ASC LIMIT 200")
    fun observeEquityCurve(): Flow<List<EquitySnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquitySnapshot(snapshot: EquitySnapshotEntity)

    @Query("DELETE FROM equity_snapshots")
    suspend fun clearEquitySnapshots()
}

@Database(
    entities = [TradeEntity::class, EquitySnapshotEntity::class],
    version = 2,
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

class TradeHistoryLocalSource(private val dao: BotDao) {
    val tradeHistory: Flow<List<ClosedTradeRecord>> =
        dao.observeTradeHistory().map { list -> list.map { it.toDomain() } }

    val equityCurve: Flow<List<EquityPoint>> =
        dao.observeEquityCurve().map { list -> list.map { it.toDomain() } }

    suspend fun recordClosedTrade(record: ClosedTradeRecord, newEquity: Double) {
        dao.insertTrade(TradeEntity.fromDomain(record))
        dao.insertEquitySnapshot(EquitySnapshotEntity(timestamp = record.closedAt, equity = newEquity))
    }

    suspend fun seedInitialEquityIfEmpty(startingEquity: Double) {
        dao.insertEquitySnapshot(
            EquitySnapshotEntity(timestamp = System.currentTimeMillis(), equity = startingEquity)
        )
    }

    suspend fun resetAll(startingEquity: Double) {
        dao.clearTradeHistory()
        dao.clearEquitySnapshots()
        dao.insertEquitySnapshot(
            EquitySnapshotEntity(timestamp = System.currentTimeMillis(), equity = startingEquity)
        )
    }
}
