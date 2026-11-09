package fr.mathieu.videfrigo.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class FoodDto(
    val id: String,
    val name: String,
    val date: String,
    val createdAt: Long,
    val photoData: String? = null,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class Mutation(val operationId: String, val baseVersion: Long, val food: FoodDto)
@Serializable
data class ApiResult(val ok: Boolean = false, val version: Long = 0, val current: FoodDto? = null, val error: String? = null)

interface FridgeApi {
    @GET("api/foods")
    suspend fun foods(@Header("Authorization") authorization: String): List<FoodDto>
    @POST("api/foods")
    suspend fun mutate(@Header("Authorization") authorization: String, @Body mutation: Mutation): Response<ApiResult>
}
