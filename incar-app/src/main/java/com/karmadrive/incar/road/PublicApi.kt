package com.karmadrive.incar.road

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** HTTP GET + 응답 JSON을 '필드 이름이 조금 달라도' 읽어 내는 도우미 */
object PublicApi {

    fun get(url: String, timeoutMs: Int = 10_000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) throw ApiException("HTTP $code: ${body.take(200)}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /** 공공데이터포털 키: 일반 인증키(Decoding)를 넣으면 인코딩, 이미 인코딩된 키(%포함)는 그대로 */
    fun encodeKey(key: String): String = if (key.contains('%')) key else URLEncoder.encode(key, "UTF-8")

    /** JSON 어디에 있든 "items"(또는 "item") 배열을 찾아 객체 목록으로 */
    fun findItems(root: Any?): List<JSONObject> {
        when (root) {
            is JSONArray -> {
                val objs = (0 until root.length()).mapNotNull { root.opt(it) as? JSONObject }
                if (objs.isNotEmpty() && objs.none { it.has("items") || it.has("item") || it.has("body") }) return objs
                objs.forEach { o -> findItems(o).let { if (it.isNotEmpty()) return it } }
            }
            is JSONObject -> {
                for (k in listOf("items", "item")) {
                    when (val v = root.opt(k)) {
                        is JSONArray -> return (0 until v.length()).mapNotNull { v.opt(it) as? JSONObject }
                        is JSONObject -> {
                            val inner = findItems(v)
                            return if (inner.isNotEmpty()) inner else listOf(v)
                        }
                    }
                }
                val keys = root.keys()
                while (keys.hasNext()) {
                    val r = findItems(root.opt(keys.next()))
                    if (r.isNotEmpty()) return r
                }
            }
        }
        return emptyList()
    }

    /** "totalCount"를 JSON 어디서든 찾기 */
    fun findTotalCount(root: Any?): Int? {
        when (root) {
            is JSONObject -> {
                if (root.has("totalCount")) return root.optString("totalCount").toIntOrNull()
                val keys = root.keys()
                while (keys.hasNext()) findTotalCount(root.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until root.length()) findTotalCount(root.opt(i))?.let { return it }
        }
        return null
    }

    /** 후보 이름 중 하나, 없으면 정규식에 맞는 첫 필드 */
    fun pick(o: JSONObject, names: List<String>, pattern: Regex? = null): String? {
        for (n in names) {
            val v = o.optString(n, "")
            if (v.isNotBlank() && v != "null") return v
        }
        if (pattern != null) {
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (pattern.containsMatchIn(k)) {
                    val v = o.optString(k, "")
                    if (v.isNotBlank() && v != "null") return v
                }
            }
        }
        return null
    }

    fun parse(body: String): Any = body.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it) }
}

class ApiException(message: String) : Exception(message)
