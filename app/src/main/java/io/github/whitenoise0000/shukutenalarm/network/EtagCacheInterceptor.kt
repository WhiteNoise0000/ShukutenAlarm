package io.github.whitenoise0000.shukutenalarm.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * ETag/If-Modified-Sinceによる条件付き取得と304時のキャッシュ復元を行うOkHttpインターセプタ。
 * - 対象: JMAの area.json / forecast/{office}.json のみ。
 * - 304の場合はキャッシュから本文を差し込み200 OKで返却する。
 */
class EtagCacheInterceptor(
    context: Context
) : Interceptor {
    private val cache = CacheStore(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        // GETのみ/対象パスのみ
        val methodOk = req.method.equals("GET", ignoreCase = true)
        val host = req.url.host.lowercase()
        val path = req.url.encodedPath
        val isJma = host == "www.jma.go.jp" && (path == "/bosai/common/const/area.json" || path.startsWith("/bosai/forecast/data/forecast/"))
        val isHolidayRaw = host == "raw.githubusercontent.com" && path.endsWith("/holidays.yml")
        if (!methodOk || (!isJma && !isHolidayRaw)) {
            return chain.proceed(req)
        }

        val url = req.url.toString()
        val entry = cache.read(url)
        val builder = req.newBuilder()
        entry?.etag?.let { builder.header("If-None-Match", it) }
        entry?.lastModified?.let { builder.header("If-Modified-Since", it) }

        val res = chain.proceed(builder.build())
        // 304 -> キャッシュ復元
        if (res.code == 304 && entry != null) {
            res.close()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            return Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK (from cache)")
                .header("X-Cache-Hit", "true")
                .body(ResponseBody.create(mediaType, entry.body))
                .build()
        }

        // 200 -> キャッシュ保存
        if (res.isSuccessful && res.code == 200) {
            val bodyStr = res.body?.string() ?: ""
            val etag = res.header("ETag")
            val lastMod = res.header("Last-Modified")
            cache.write(url, etag, lastMod, bodyStr)
            // 読み取り済のためボディを再構築
            val mediaType = res.body?.contentType() ?: "application/json; charset=utf-8".toMediaTypeOrNull()
            return res.newBuilder()
                .body(ResponseBody.create(mediaType, bodyStr))
                .build()
        }
        return res
    }
}
