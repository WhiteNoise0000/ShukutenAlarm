package io.github.whitenoise0000.shukutenalarm.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.whitenoise0000.shukutenalarm.network.EtagCacheInterceptor
import io.github.whitenoise0000.shukutenalarm.weather.WeatherRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.AreaRepository
import io.github.whitenoise0000.shukutenalarm.weather.jma.GsiApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaConstApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.JmaForecastApi
import io.github.whitenoise0000.shukutenalarm.weather.jma.TelopsRepository
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * アプリ全体のDIモジュール。
 * - ネットワーク/シリアライザ/リポジトリを提供する。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** JSON シリアライザ提供（未知フィールドは無視）。 */
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    /** OkHttpClient 提供（基本ログ + ETagキャッシュ）。 */
    @Provides
    @Singleton
    fun provideOkHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .addInterceptor(EtagCacheInterceptor(context))
        .build()

    /** Retrofit(JMA, 共通ベース) */
    @Provides
    @Singleton
    fun provideJmaRetrofit(json: Json, client: OkHttpClient): Retrofit {
        @OptIn(ExperimentalSerializationApi::class)
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://www.jma.go.jp/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /** Retrofit(GSI) */
    @Provides
    @Singleton
    fun provideGsiRetrofit(json: Json, client: OkHttpClient): Retrofit {
        @OptIn(ExperimentalSerializationApi::class)
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://mreversegeocoder.gsi.go.jp/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /** APIs */
    @Provides @Singleton fun provideJmaConstApi(jmaRetrofit: Retrofit): JmaConstApi = jmaRetrofit.create()
    @Provides @Singleton fun provideJmaForecastApi(jmaRetrofit: Retrofit): JmaForecastApi = jmaRetrofit.create()
    @Provides @Singleton fun provideGsiApi(gsiRetrofit: Retrofit): GsiApi = gsiRetrofit.create()
    
    /** Repositories */
    @Provides
    @Singleton
    fun provideAreaRepository(@ApplicationContext context: Context, constApi: JmaConstApi): AreaRepository =
        AreaRepository(context, constApi)

    @Provides
    @Singleton
    fun provideWeatherRepository(
        @ApplicationContext context: Context,
        forecastApi: JmaForecastApi,
        gsiApi: GsiApi,
        areaRepository: AreaRepository
    ): WeatherRepository = WeatherRepository(
        context,
        forecastApi,
        gsiApi,
        areaRepository,
        TelopsRepository(context)
    )
}

