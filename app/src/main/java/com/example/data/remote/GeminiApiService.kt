package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class GeminiGenerateRequest(
    val system_instruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    val inline_data: GeminiInlineData? = null
)

data class GeminiInlineData(
    val mime_type: String,
    val data: String
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 4096
)

data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiErrorDetail? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

data class GeminiModelsResponse(
    val models: List<GeminiModelInfo>? = null,
    val error: GeminiErrorDetail? = null
)

data class GeminiModelInfo(
    val name: String,
    val displayName: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

data class GeminiErrorEnvelope(
    val error: GeminiErrorDetail? = null
)

data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

interface GeminiApiService {
    @GET("v1beta/models")
    suspend fun listModels(
        @Header("x-goog-api-key") apiKey: String
    ): Response<GeminiModelsResponse>

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: GeminiGenerateRequest
    ): Response<GeminiGenerateResponse>

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"

        fun create(): GeminiApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("x-goog-api-key")
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GeminiApiService::class.java)
        }
    }
}
