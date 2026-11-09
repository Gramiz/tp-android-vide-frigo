package fr.mathieu.videfrigo.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

class SyncConflict : Exception("Un aliment a changé sur le serveur. Choisis la version à garder dans l’application.")

class FridgeRepository(
    private val db: FridgeDatabase,
    private val api: FridgeApi,
    private val preferences: Preferences,
    private val photos: Photos,
    private val enqueueSync: () -> Unit,
    private val updateWidget: () -> Unit,
) {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()
    val foods = dao.observeFoods()
    val pending = dao.observePending()

    suspend fun food(id: String) = dao.food(id)

    suspend fun save(id: String?, name: String, date: String, photoPath: String?) = withContext(Dispatchers.IO) {
        require(FoodRules.validate(name, date) == null) { FoodRules.validate(name, date)!! }
        db.withTransaction {
            val old = id?.let { dao.food(it) }
            val food = FoodEntity(old?.id ?: UUID.randomUUID().toString(), name.trim(), date, old?.createdAt ?: System.currentTimeMillis(), photoPath, old?.version ?: 0)
            val dto = food.dto()
            dao.putFood(food)
            dao.enqueue(PendingOperation(operationId = UUID.randomUUID().toString(), foodId = food.id, payload = json.encodeToString(dto)))
        }
        updateWidget()
        enqueueSync()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val food = dao.food(id) ?: return@withTransaction
            val deleted = food.copy(deleted = true)
            dao.putFood(deleted)
            dao.enqueue(PendingOperation(operationId = UUID.randomUUID().toString(), foodId = id, payload = json.encodeToString(deleted.dto())))
        }
        updateWidget()
        enqueueSync()
    }

    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val authorization = "Bearer ${preferences.deviceToken()}"
            var count = 0
            while (true) {
                val operation = dao.firstPending() ?: break
                if (operation.conflict != null) throw SyncConflict()
                val current = dao.food(operation.foodId) ?: throw IOException("Aliment local absent.")
                val payload = json.decodeFromString<FoodDto>(operation.payload)
                val response = api.mutate(authorization, Mutation(operation.operationId, current.version, payload))
                if (response.code() == 409) {
                    val result = json.decodeFromString<ApiResult>(response.errorBody()?.string() ?: throw IOException("Conflit invalide."))
                    val remote = result.current ?: throw IOException("Version serveur absente.")
                    dao.markConflict(operation.sequence, json.encodeToString(remote))
                    throw SyncConflict()
                }
                if (!response.isSuccessful) throw IOException("Envoi refusé (${response.code()}). Les changements restent sur ce téléphone.")
                val result = response.body() ?: throw IOException("Réponse serveur vide.")
                if (!result.ok || result.version <= 0) throw IOException("Confirmation serveur invalide.")
                db.withTransaction {
                    dao.updateVersion(operation.foodId, result.version)
                    dao.acknowledge(operation.sequence)
                }
                count++
            }
            val remote = api.foods(authorization)
            db.withTransaction {
                val dirty = dao.dirtyIds().toSet()
                remote.filterNot { it.id in dirty }.forEach { dto ->
                    val old = dao.food(dto.id)
                    if (old == null || old.version < dto.version) dao.putFood(dto.entity())
                }
            }
            preferences.syncSucceeded()
            updateWidget()
            count
        }
    }

    suspend fun resolve(operation: PendingOperation, keepLocal: Boolean) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val remote = json.decodeFromString<FoodDto>(requireNotNull(operation.conflict))
            db.withTransaction {
                if (keepLocal) {
                    dao.updateVersion(operation.foodId, remote.version)
                    dao.markConflict(operation.sequence, null)
                } else {
                    dao.discardOperations(operation.foodId)
                    dao.putFood(remote.entity())
                }
            }
        }
        updateWidget()
        enqueueSync()
    }

    private fun FoodEntity.dto() = FoodDto(id, name, date, createdAt, if (deleted) null else photos.encode(photoPath), version, deleted)
    private fun FoodDto.entity() = FoodEntity(id, name, date, createdAt, if (deleted) null else photos.decode(id, photoData), version, deleted)
}
