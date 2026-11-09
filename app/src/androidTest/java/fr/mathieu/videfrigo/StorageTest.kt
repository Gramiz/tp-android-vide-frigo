package fr.mathieu.videfrigo

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.mathieu.videfrigo.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migrationPreservesExistingFood() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        val id = UUID.randomUUID().toString()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE foods (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, date TEXT NOT NULL, createdAt INTEGER NOT NULL, photoPath TEXT, version INTEGER NOT NULL, deleted INTEGER NOT NULL)")
            old.execSQL("INSERT INTO foods VALUES (?, 'Pommes', '2026-11-12', 1, NULL, 0, 0)", arrayOf(id))
            old.version = 1
        }
        val db = Room.databaseBuilder(context, FridgeDatabase::class.java, name).addMigrations(FridgeDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Pommes", db.dao().foods().single().name)
            assertNull(db.dao().firstPending())
            assertEquals(2, db.openHelper.writableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun offlineChangesRemainOrderedAndSurviveAnAcknowledgementLostOnTheNetwork() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FridgeDatabase::class.java).build()
        val api = FakeApi()
        val repo = FridgeRepository(db, api, Preferences(context), Photos(context), {}, {})
        try {
            repo.save(null, "Lait", "2026-11-12", null)
            val id = db.dao().foods().single().id
            repo.save(id, "Lait entier", "2026-11-13", null)
            api.loseNextAcknowledgement = true
            try { repo.sync(); fail("Le premier envoi doit perdre sa confirmation.") } catch (_: IOException) { }
            assertNotNull(db.dao().firstPending())
            assertEquals("Lait entier", db.dao().foods().single().name)
            repo.sync()
            assertNull(db.dao().firstPending())
            assertEquals(2L, api.remote.getValue(id).version)
            assertEquals("Lait entier", api.remote.getValue(id).name)
            assertEquals(2L, db.dao().foods().single().version)
            repo.delete(id)
            assertTrue(db.dao().foods().isEmpty())
            repo.sync()
            assertTrue(api.remote.getValue(id).deleted)
            assertEquals(3L, api.remote.getValue(id).version)
        } finally { db.close() }
    }

    @Test fun roomRetainsDataAfterDatabaseIsClosedAndReopened() = runBlocking {
        val name = "persistence-${UUID.randomUUID()}.db"
        val food = FoodEntity(UUID.randomUUID().toString(), "Carottes", "2026-11-14", 1)
        val first = Room.databaseBuilder(context, FridgeDatabase::class.java, name).build()
        first.dao().putFood(food)
        first.close()
        val reopened = Room.databaseBuilder(context, FridgeDatabase::class.java, name).build()
        try { assertEquals(food, reopened.dao().foods().single()) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }

    private class FakeApi : FridgeApi {
        val remote = mutableMapOf<String, FoodDto>()
        private val acknowledgements = mutableMapOf<String, ApiResult>()
        var loseNextAcknowledgement = false
        override suspend fun foods(authorization: String) = remote.values.toList()
        override suspend fun mutate(authorization: String, mutation: Mutation): Response<ApiResult> {
            acknowledgements[mutation.operationId]?.let { return Response.success(it) }
            val version = (remote[mutation.food.id]?.version ?: 0) + 1
            check(mutation.baseVersion == version - 1)
            remote[mutation.food.id] = mutation.food.copy(version = version)
            val acknowledgement = ApiResult(ok = true, version = version)
            acknowledgements[mutation.operationId] = acknowledgement
            if (loseNextAcknowledgement) { loseNextAcknowledgement = false; throw IOException("Confirmation perdue") }
            return Response.success(acknowledgement)
        }
    }
}
