package com.karmadrive.incar.share

import android.content.res.AssetManager
import com.karmadrive.incar.data.KarmaStore
import com.karmadrive.incar.engine.KarmaEvent
import com.karmadrive.incar.engine.Kind
import com.karmadrive.incar.game.Damage
import com.karmadrive.incar.game.Ladder
import com.karmadrive.incar.game.Scars
import com.karmadrive.incar.game.Twin
import com.karmadrive.incar.game.TwinMood
import com.karmadrive.incar.run.LiveState
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 라이브 트윈 공유 서버 (프로토타입).
 *
 * 운전자는 주행 중 화면을 볼 수 없지만, 동승자 · 가족은 KARMA 앱(스마트폰) · PC 브라우저로
 * 트윈카가 지금 어떤 상태인지, 왜 망가졌는지 실시간으로 본다.
 * 실서비스에서는 차량 → 카르마 서버 → KARMA 앱 구조이며, 프로토타입은 인카 앱 안의 작은 HTTP 서버로 대신한다.
 *
 *  GET  /             라이브 트윈 웹 (assets/live/index.html)
 *  GET  /img/{name}   트윈카 · 캐릭터 그림
 *  GET  /api/state    트윈카 · 주행 · 상처 · 응원 JSON
 *  POST /api/cheer    응원 스티커 (?emoji=&text=&from=)  — 운전자는 주차 후 언박싱에서 본다
 *  POST /api/repair   부위 수리 (?id=) · 풀 리페어 (?all=1) — 주차 중에만
 */
class LiveShareServer(
    private val assets: AssetManager,
    private val store: KarmaStore,
    private val live: () -> LiveState,
    private val mood: () -> TwinMood,
    private val line: () -> String,
    val port: Int = 8765,
) {
    @Volatile var running = false
        private set
    @Volatile var lastError: String? = null
        private set

    private val pool = Executors.newFixedThreadPool(4)
    private val viewers = ConcurrentHashMap<String, Long>()
    private var server: ServerSocket? = null

    /** 최근 10초 안에 상태를 받아 간 화면 수 */
    fun viewersNow(): Int {
        val cut = System.currentTimeMillis() - 10_000
        viewers.entries.removeIf { it.value < cut }
        return viewers.size
    }

    fun start() {
        if (running) return
        Thread({
            try {
                val ss = ServerSocket(port)
                server = ss
                running = true
                lastError = null
                while (!ss.isClosed) {
                    val s = ss.accept()
                    pool.execute { handle(s) }
                }
            } catch (e: Exception) {
                lastError = e.message
            } finally {
                running = false
            }
        }, "karma-live-share").apply { isDaemon = true }.start()
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 5_000
                val input = BufferedInputStream(s.getInputStream())
                val requestLine = readLine(input) ?: return
                var contentLength = 0
                while (true) {
                    val h = readLine(input) ?: break
                    if (h.isEmpty()) break
                    if (h.lowercase().startsWith("content-length:")) contentLength = h.substringAfter(':').trim().toIntOrNull() ?: 0
                }
                if (contentLength in 1..8_192) input.skip(contentLength.toLong())
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore('?')
                val query = parseQuery(parts[1].substringAfter('?', ""))
                val out = s.getOutputStream()
                viewers[s.inetAddress.hostAddress ?: "?"] = System.currentTimeMillis()
                when {
                    method == "OPTIONS" -> send(out, 204, "text/plain", ByteArray(0))
                    path == "/" || path == "/index.html" -> sendAsset(out, "live/index.html", "text/html; charset=utf-8")
                    path.startsWith("/img/") -> {
                        val name = path.removePrefix("/img/")
                        if (!name.matches(Regex("[a-z0-9_]+\\.webp"))) send(out, 404, "text/plain", "not found".toByteArray())
                        else sendAsset(out, "live/img/$name", "image/webp")
                    }
                    path == "/api/state" -> send(out, 200, "application/json; charset=utf-8", state().toString().toByteArray())
                    path == "/api/cheer" && method == "POST" -> {
                        store.addCheer(query["emoji"] ?: "👍", query["text"] ?: "응원해요", query["from"] ?: "동승자")
                        send(out, 200, "application/json", "{\"ok\":true}".toByteArray())
                    }
                    path == "/api/repair" && method == "POST" -> {
                        val ok = if (live().running) false
                        else if (query["all"] == "1") store.repairAll()
                        else query["id"]?.let { store.repairScar(it) } ?: false
                        val msg = if (live().running) "주행 중에는 수리할 수 없어요" else if (ok) "수리 완료" else "KP가 부족해요"
                        send(out, 200, "application/json; charset=utf-8", JSONObject().put("ok", ok).put("message", msg).toString().toByteArray())
                    }
                    else -> send(out, 404, "text/plain", "not found".toByteArray())
                }
            } catch (_: Exception) {
            }
        }
    }

    /** 공유 화면이 쓰는 상태 JSON */
    fun state(): JSONObject {
        val g = store.game.value
        val l = live()
        val lv = Damage.levelOf(g.damage)
        val skin = store.skin.value
        val look = Twin.look(g.stage, skin, lv.level)
        val m = mood()
        val o = JSONObject()
        o.put("ts", System.currentTimeMillis())
        o.put("car", JSONObject()
            .put("name", g.carName)
            .put("stage", g.stage)
            .put("stageName", Ladder.stages[g.stage.coerceIn(0, Ladder.stages.size - 1)].name)
            .put("model", look.model.name)
            .put("image", look.image)
            .put("damageBaked", look.damageBaked)
            .put("aspect", Twin.aspect(look.image).toDouble())
            .put("body", JSONArray(Twin.bodyRect(look.image).map { it.toDouble() }))
            .put("damage", g.damage)
            .put("level", lv.level)
            .put("levelName", lv.name)
            .put("levelDesc", lv.desc)
            .put("mood", m.label)
            .put("moodEmoji", m.emoji)
            .put("line", line()))
        o.put("kp", store.totalKp.value)
        o.put("tier", store.tier().label)
        o.put("repairAllCost", store.repairAllCost())

        val road = l.road
        o.put("live", JSONObject()
            .put("running", l.running)
            .put("replaying", l.replaying)
            .put("title", l.replayTitle)
            .put("gear", l.gear.name)
            .put("runKp", l.runKp)
            .put("runDamage", l.runDamage)
            .put("combo", l.combo)
            .put("distanceKm", l.distanceKm)
            .put("startMs", l.runStartMs)
            .put("road", if (road == null || !l.running) JSONObject.NULL else JSONObject()
                .put("name", road.roadName)
                .put("limit", road.limitKmh)
                .put("limitSource", road.limitSource)
                .put("school", road.schoolZone?.name ?: JSONObject.NULL)
                .put("incident", road.incident?.title ?: JSONObject.NULL)
                .put("signal", road.signal?.let { "${it.intersectionName} ${it.phase.name}" } ?: JSONObject.NULL)))

        // 이번(또는 마지막) RUN 이벤트 — 지킨 것과 어긴 것 모두
        val events: List<KarmaEvent> = if (l.runEvents.isNotEmpty()) l.runEvents else store.runs.value.firstOrNull()?.events ?: emptyList()
        o.put("events", JSONArray(events.map { e ->
            JSONObject()
                .put("type", e.type.name).put("title", e.type.title).put("emoji", e.type.emoji)
                .put("kind", e.type.kind.name).put("kp", e.kp).put("damage", e.damage)
                .put("detail", e.detail).put("place", e.place).put("t", e.t)
                .put("reason", if (e.type.kind == Kind.BLACK) Scars.reasonOf(e) else e.detail)
                .put("part", Scars.spec(e.type)?.let { "${it.first.title} ${it.second.title}" } ?: JSONObject.NULL)
        }))

        o.put("scars", JSONArray(g.scars.map { s ->
            val (ax, ay) = Twin.anchor(s.part, look)
            JSONObject()
                .put("id", s.id).put("type", s.type.name).put("title", s.type.title).put("emoji", s.type.emoji)
                .put("part", s.part.name).put("partTitle", s.part.title)
                .put("kind", s.kind.name).put("kindTitle", s.kind.title)
                .put("damage", s.damage).put("kp", s.kp).put("reason", s.reason).put("place", s.place)
                .put("source", s.source).put("t", s.t).put("cost", s.repairCost)
                .put("x", ax.toDouble()).put("y", ay.toDouble())
        }))
        o.put("cheers", JSONArray(g.cheers.takeLast(10).map {
            JSONObject().put("emoji", it.emoji).put("text", it.text).put("from", it.from).put("t", it.t)
        }))
        val today = store.todayRuns()
        o.put("today", JSONObject().put("runs", today.size).put("kp", today.sumOf { it.kp }))
        store.runs.value.firstOrNull()?.let { r ->
            o.put("lastRun", JSONObject().put("grade", r.grade.label).put("kp", r.kp).put("damage", r.damage)
                .put("mq", r.mq).put("distanceKm", r.distanceKm).put("startMs", r.startMs).put("endMs", r.endMs))
        }
        return o
    }

    private fun sendAsset(out: OutputStream, path: String, type: String) {
        val bytes = try { assets.open(path).use { it.readBytes() } } catch (e: Exception) { null }
        if (bytes == null) send(out, 404, "text/plain", "not found".toByteArray()) else send(out, 200, type, bytes, cache = type.startsWith("image"))
    }

    private fun send(out: OutputStream, code: Int, type: String, body: ByteArray, cache: Boolean = false) {
        val status = when (code) { 200 -> "OK"; 204 -> "No Content"; else -> "Not Found" }
        val head = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Cache-Control: ${if (cache) "max-age=3600" else "no-store"}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 4_096) return sb.toString()
            sb.append(c.toChar())
        }
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').filter { it.contains('=') }.associate {
            val k = it.substringBefore('=')
            val v = it.substringAfter('=')
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
}
