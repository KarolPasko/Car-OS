package com.nextgen.caros.net

import com.nextgen.caros.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    /** Public OSM-based services ask clients to identify themselves with a descriptive agent. */
    const val USER_AGENT = "NextGenCarOS/${BuildConfig.VERSION_NAME} (Android head unit app)"

    val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use { it.bodyOrThrow() }
    }

    suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { it.bodyOrThrow() }
    }

    fun Response.bodyOrThrow(): String {
        val text = body?.string().orEmpty()
        if (!isSuccessful) throw HttpException(code, text.take(300))
        return text
    }
}

class HttpException(val code: Int, val bodySnippet: String) : IOException("HTTP $code")
