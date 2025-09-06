package io.github.whitenoise0000.shukutenalarm.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.whitenoise0000.shukutenalarm.weather.OpenMeteoApi
import io.github.whitenoise0000.shukutenalarm.weather.WeatherRepository
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * アプリ共通の DI モジュール。
 * - ネットワーク/シリアライザ/リポジトリを提供する。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** JSON シリアライザを提供（未知フィールドは無視）。 */
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    /** OkHttpClient を提供（簡易ロギングを有効化）。 */
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    /** Retrofit（Open‑Meteo 用）を提供。 */
    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient): Retrofit {
        @OptIn(ExperimentalSerializationApi::class)
        val contentType = "application/json".toMediaType()
        // baseUrl は末尾スラッシュ必須
        return Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /** Open‑Meteo API インターフェースを提供。 */
    @Provides
    @Singleton
    fun provideOpenMeteoApi(retrofit: Retrofit): OpenMeteoApi = retrofit.create()

    /** WeatherRepository を提供（アプリケーションコンテキストに基づく）。 */
    @Provides
    @Singleton
    fun provideWeatherRepository(@ApplicationContext context: Context, api: OpenMeteoApi): WeatherRepository =
        WeatherRepository(context, api)
}
