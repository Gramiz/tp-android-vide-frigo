package fr.mathieu.videfrigo.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val date: String,
    val createdAt: Long,
    val photoPath: String? = null,
    val version: Long = 0,
    val deleted: Boolean = false,
)

// La photo du formulaire est copiée dans le payload : une modification ultérieure
// ne change pas une opération déjà en cours d'envoi.
@Entity(tableName = "outbox", indices = [Index("foodId")])
data class PendingOperation(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val operationId: String,
    val foodId: String,
    val payload: String,
    val conflict: String? = null,
)

@Dao
interface FridgeDao {
    @Query("SELECT * FROM foods WHERE deleted = 0 ORDER BY date, createdAt")
    fun observeFoods(): Flow<List<FoodEntity>>
    @Query("SELECT * FROM outbox ORDER BY sequence")
    fun observePending(): Flow<List<PendingOperation>>
    @Query("SELECT * FROM foods WHERE deleted = 0 ORDER BY date, createdAt")
    suspend fun foods(): List<FoodEntity>
    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun food(id: String): FoodEntity?
    @Upsert suspend fun putFood(food: FoodEntity)
    @Insert suspend fun enqueue(operation: PendingOperation)
    @Query("SELECT * FROM outbox ORDER BY sequence LIMIT 1")
    suspend fun firstPending(): PendingOperation?
    @Query("SELECT DISTINCT foodId FROM outbox")
    suspend fun dirtyIds(): List<String>
    @Query("DELETE FROM outbox WHERE sequence = :sequence")
    suspend fun acknowledge(sequence: Long)
    @Query("DELETE FROM outbox WHERE foodId = :id")
    suspend fun discardOperations(id: String)
    @Query("UPDATE foods SET version = :version WHERE id = :id")
    suspend fun updateVersion(id: String, version: Long)
    @Query("UPDATE outbox SET conflict = :remote WHERE sequence = :sequence")
    suspend fun markConflict(sequence: Long, remote: String?)
}

@Database(entities = [FoodEntity::class, PendingOperation::class], version = 2, exportSchema = true)
abstract class FridgeDatabase : RoomDatabase() {
    abstract fun dao(): FridgeDao

    companion object {
        // v1 : le frigo local. v2 : ajout de l'outbox sans effacer les aliments.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS outbox (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, operationId TEXT NOT NULL, foodId TEXT NOT NULL, payload TEXT NOT NULL, conflict TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_foodId ON outbox (foodId)")
            }
        }
    }
}
