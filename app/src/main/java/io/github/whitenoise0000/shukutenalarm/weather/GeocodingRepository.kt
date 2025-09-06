package io.github.whitenoise0000.shukutenalarm.weather

import android.content.Context
import android.icu.text.Transliterator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * 地名検索のための簡易リポジトリ。
 * - Open‑Meteo Geocoding API を利用（APIキー不要）。
 */
class GeocodingRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val api: GeocodingApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        @OptIn(ExperimentalSerializationApi::class)
        val contentType = "application/json".toMediaType()
        // Retrofit の baseUrl は末尾にスラッシュが必要
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GeocodingApi::class.java)
    }

    /**
     * 都市名を日本向けに検索する。
     * - JP の国コードの結果に限定
     * - サフィックスの試行（市/府/県/都）
     * - ICU のローマ字化での再試行
     */
    suspend fun searchCity(name: String): List<GeoPlace> {
        val q = name.trim()
        if (q.isBlank()) return emptyList()

        val candidates = mutableListOf<String>()
        candidates += q
        val suffixes = listOf("市", "府", "県", "都")
        candidates += suffixes.map { q + it }

        // ローマ字への変換を試す（ICU）
        runCatching {
            val tr = Transliterator.getInstance("Any-Latin; Latin-ASCII")
            val romaji = tr.transliterate(q)
                .replace(Regex("[^A-Za-z ]"), "")
                .trim()
            if (romaji.isNotBlank()) candidates += romaji
        }

        for (term in candidates.distinct()) {
            val res = api.search(name = term, language = "ja", count = 8).results.filter { it.countryCode == "JP" }
            if (res.isNotEmpty()) return res
        }
        return emptyList()
    }
}
