package com.karmadrive.incar.signal

import android.content.Context
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * Pleos Vehicle SDK(ai.pleos.playground:Vehicle) 신호 소스.
 *
 * 에뮬레이터의 AAOS 표준 Car API로는 방향지시등 · 조향각 · 안전벨트 · 와이퍼 · 전조등이
 * "차량이 지원하지 않음"으로 막혀 있어(서드파티 권한), Pleos SDK의 도메인 API로 받는다.
 *
 * 공식 샘플(github.com/Pleos-Playground/pleos-sample-vehicle-app)과 같은 방식:
 *  Vehicle(context) → initialize() → getTurnSignal() / getSteeringWheel() / getSeat() / getWiper() / getBrake() / getLight()
 *  → register…(area?, listener) → 리스너 콜백(on…Updated / onFailed) → unregister… → release()
 *
 * SDK의 메서드 이름 · 리스너 타입은 버전마다 조금씩 다를 수 있어 리플렉션으로 찾는다.
 * (SDK가 클래스패스에 없으면 isSdkPresent() = false → 이 소스는 붙지 않는다)
 */
class PleosVehicleSource(private val context: Context) : VehicleSignalSource {

    private class Sub(val name: String, val domain: String, val keywords: List<String>, val area: String? = null, val toEvent: (Long, Any?) -> SignalEvent?)

    private data class Registration(val target: Any, val unregister: Method?, val args: Array<Any?>)

    private var vehicle: Any? = null
    private val registrations = mutableListOf<Registration>()
    private val status = ConcurrentHashMap<String, SignalStatus>()
    @Volatile private var lastError: String? = null

    private val subs = listOf(
        Sub("방향지시등", "getTurnSignal", listOf("TurnSignal", "")) { t, v -> turnOf(v)?.let { SignalEvent.TurnSignal(t, it) } },
        Sub("조향각", "getSteeringWheel", listOf("Angle")) { t, v -> numberOf(v)?.let { SignalEvent.Steering(t, it) } },
        Sub("안전벨트", "getSeat", listOf("Belt", "Buckle"), area = "SeatArea") { t, v -> onOffOf(v, onWords = listOf("BUCKLED", "LATCHED", "FASTENED", "ON", "TRUE"))?.let { SignalEvent.SeatBelt(t, it) } },
        Sub("와이퍼", "getWiper", listOf("Wiper", ""), area = "WindowArea") { t, v -> onOffOf(v)?.let { SignalEvent.Wiper(t, it) } },
        Sub("브레이크 페달", "getBrake", listOf("Pedal", "Brake", "")) { t, v -> onOffOf(v, onWords = listOf("PRESS", "APPLIED", "ON", "TRUE"))?.let { SignalEvent.BrakePedal(t, it) } },
        Sub("전조등", "getLight", listOf("Exterior", "HeadLamp", "Headlight", "LowBeam")) { t, v -> onOffOf(v)?.let { SignalEvent.Headlights(t, it) } },
    )

    override fun start(listener: (SignalEvent) -> Unit) {
        try {
            val cls = Class.forName(VEHICLE_CLASS)
            val v = cls.getConstructor(Context::class.java).newInstance(context.applicationContext)
            cls.getMethod("initialize").invoke(v)
            vehicle = v
        } catch (e: Throwable) {
            lastError = rootMessage(e)
            subs.forEach { status[it.name] = SignalStatus("${it.name} (Pleos SDK)", false, "초기화 실패: $lastError") }
            return
        }
        subs.forEach { sub -> subscribe(sub, listener) }
    }

    private fun subscribe(sub: Sub, listener: (SignalEvent) -> Unit) {
        val v = vehicle ?: return
        try {
            val domain = v.javaClass.getMethod(sub.domain).invoke(v)
                ?: throw IllegalStateException("${sub.domain} 없음")
            val candidates = domain.javaClass.methods.filter { m ->
                m.name.startsWith("register") && m.parameterTypes.any { isListener(it) }
            }
            // 키워드 우선순위대로 고른다 (빈 문자열 = 아무거나)
            val method = sub.keywords.firstNotNullOfOrNull { kw ->
                candidates.firstOrNull { kw.isEmpty() || it.name.contains(kw, ignoreCase = true) }
            } ?: throw IllegalStateException("register 메서드 없음 (${candidates.joinToString { it.name }})")

            val listenerType = method.parameterTypes.first { isListener(it) }
            val handler = InvocationHandler { proxy, m, args ->
                when (m.name) {
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "KarmaListener(${sub.name})"
                    else -> {
                        if (m.name.contains("fail", ignoreCase = true)) {
                            val err = (args?.firstOrNull() as? Throwable)?.let { rootMessage(it) } ?: "실패"
                            status[sub.name] = SignalStatus("${sub.name} (Pleos SDK)", false, "${method.name}: $err")
                        } else {
                            val raw = args?.lastOrNull { it !is Throwable && !isArea(it) } ?: args?.firstOrNull()
                            val ev = try { sub.toEvent(System.currentTimeMillis(), raw) } catch (_: Exception) { null }
                            status[sub.name] = SignalStatus("${sub.name} (Pleos SDK)", true, "${method.name} → ${describe(raw)}")
                            if (ev != null) listener(ev)
                        }
                        if (m.returnType == java.lang.Boolean.TYPE) false else null
                    }
                }
            }
            val proxy = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType), handler)
            val args = method.parameterTypes.map { p ->
                when {
                    isListener(p) -> proxy
                    isAreaClass(p) -> makeArea(p, sub.area)
                    else -> null
                }
            }.toTypedArray()
            if (args.indices.any { args[it] == null && isAreaClass(method.parameterTypes[it]) }) {
                throw IllegalStateException("구역 생성 실패: " + method.parameterTypes.joinToString { it.simpleName } + " / " + areaDiag)
            }
            method.invoke(domain, *args)
            val unregister = domain.javaClass.methods.firstOrNull {
                it.name == "un" + method.name && it.parameterTypes.contentEquals(method.parameterTypes)
            }
            registrations += Registration(domain, unregister, args)
            status.putIfAbsent(sub.name, SignalStatus("${sub.name} (Pleos SDK)", true, "${method.name} 등록 · 값 대기 중"))
        } catch (e: Throwable) {
            status[sub.name] = SignalStatus("${sub.name} (Pleos SDK)", false, rootMessage(e))
        }
    }

    override fun stop() {
        registrations.forEach { r -> runCatching { r.unregister?.invoke(r.target, *r.args) } }
        registrations.clear()
        vehicle?.let { v -> runCatching { v.javaClass.getMethod("release").invoke(v) } }
        vehicle = null
    }

    override fun status(): List<SignalStatus> = subs.mapNotNull { status[it.name] }

    // ---------- 변환 ----------

    private fun turnOf(v: Any?): Turn? {
        val s = describe(v).uppercase()
        return when {
            v is Number -> when (v.toInt()) { 1 -> Turn.LEFT; 2 -> Turn.RIGHT; else -> Turn.OFF }
            "HAZARD" in s || "EMERGENCY" in s || "BOTH" in s -> Turn.OFF
            "LEFT" in s -> Turn.LEFT
            "RIGHT" in s -> Turn.RIGHT
            "OFF" in s || "NONE" in s -> Turn.OFF
            else -> null
        }
    }

    private fun numberOf(v: Any?): Float? = when (v) {
        is Number -> v.toFloat()
        else -> describe(v).let { Regex("-?\\d+(\\.\\d+)?").find(it)?.value?.toFloatOrNull() }
    }

    private fun onOffOf(v: Any?, onWords: List<String> = listOf("ON", "TRUE", "LOW", "HIGH", "AUTO", "INTERMITTENT")): Boolean? = when (v) {
        is Boolean -> v
        is Number -> v.toFloat() > 0f
        null -> null
        else -> {
            val s = describe(v).uppercase()
            when {
                s.startsWith("UN") || "OFF" in s || "FALSE" in s || "RELEASE" in s || "NONE" in s -> false
                onWords.any { it in s } -> true
                else -> null
            }
        }
    }

    private fun describe(v: Any?): String = when (v) {
        null -> "null"
        is Enum<*> -> v.name
        else -> v.toString()
    }

    private fun isListener(c: Class<*>) = c.isInterface && c.name.startsWith(LISTENER_PACKAGE)

    private fun isAreaClass(c: Class<*>) = c.name.startsWith(AREA_PACKAGE)

    private fun isArea(o: Any?) = o != null && isAreaClass(o.javaClass)

    /** SeatArea(SeatValue.ROW_1_LEFT) 처럼 운전석 쪽 구역을 만든다 */
    private var areaDiag = ""

    private fun makeArea(areaType: Class<*>, hint: String?): Any? {
        // 파라미터 타입이 추상 Area면 도메인에 맞는 실제 구역 클래스(SeatArea · WindowArea …)를 찾아 만든다
        val names = listOfNotNull(hint) + AREA_CLASSES
        val candidates = (listOf(areaType) + names.mapNotNull { n ->
            try { Class.forName(AREA_PACKAGE + n) } catch (_: Throwable) { null }
        }.filter { areaType.isAssignableFrom(it) }).distinct()
        val diag = StringBuilder()
        for (cls in candidates) {
            makeAreaOf(cls)?.let { return it }
            diag.append(cls.simpleName).append(" ctors=")
                .append(cls.declaredConstructors.joinToString("|") { c -> c.parameterTypes.joinToString(",") { it.simpleName } })
                .append(" statics=")
                .append(cls.declaredFields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }.take(4).joinToString(",") { it.name })
                .append("; ")
        }
        areaDiag = diag.toString()
        return null
    }

    private fun pickEnum(type: Class<*>): Any? {
        val values = type.enumConstants ?: return null
        return values.firstOrNull { (it as Enum<*>).name in PREFERRED } ?: values.firstOrNull()
    }

    private fun makeAreaOf(cls: Class<*>): Any? {
        if (cls.isInterface) return null
        // ① SeatArea.ROW_1_LEFT 같은 정적 상수
        val statics = cls.declaredFields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && cls.isAssignableFrom(it.type) }
        (statics.firstOrNull { it.name in PREFERRED } ?: statics.firstOrNull { it.name != "Companion" && it.name != "INSTANCE" })?.let { f ->
            try { f.isAccessible = true; f.get(null)?.let { return it } } catch (_: Throwable) { }
        }
        if (java.lang.reflect.Modifier.isAbstract(cls.modifiers)) return null
        // ② 생성자(공개 · 비공개 모두): 열거형 1개, 문자열 1개, 열거형 + 기본값 마커
        for (c in cls.declaredConstructors.sortedBy { it.parameterTypes.size }) {
            val p = c.parameterTypes
            val args: Array<Any?>? = when {
                p.size == 1 && p[0].isEnum -> arrayOf(pickEnum(p[0]))
                // SeatArea(vararg SeatValue) — 열거형 배열 1칸
                p.size == 1 && p[0].isArray && p[0].componentType.isEnum -> {
                    val arr = java.lang.reflect.Array.newInstance(p[0].componentType, 1)
                    java.lang.reflect.Array.set(arr, 0, pickEnum(p[0].componentType))
                    arrayOf<Any?>(arr)
                }
                p.size == 1 && p[0] == String::class.java -> arrayOf("ROW_1_LEFT")
                p.isNotEmpty() && p[0].isEnum -> Array(p.size) { i ->
                    when {
                        i == 0 -> pickEnum(p[0])
                        p[i] == Int::class.javaPrimitiveType -> 0
                        else -> null
                    }
                }
                else -> null
            }
            if (args != null) {
                try { c.isAccessible = true; return c.newInstance(*args) } catch (_: Throwable) { }
            }
        }
        // ③ 정적 팩토리 메서드 (of / from / create …)
        for (m in cls.declaredMethods) {
            if (!java.lang.reflect.Modifier.isStatic(m.modifiers) || !cls.isAssignableFrom(m.returnType)) continue
            val p = m.parameterTypes
            if (p.size == 1 && p[0].isEnum) {
                try { m.isAccessible = true; m.invoke(null, pickEnum(p[0]))?.let { return it } } catch (_: Throwable) { }
            }
        }
        return null
    }

    private fun rootMessage(e: Throwable): String {
        var c: Throwable = e
        while (c.cause != null && c.cause !== c) c = c.cause!!
        return c.message ?: c.javaClass.simpleName
    }

    companion object {
        private const val VEHICLE_CLASS = "ai.pleos.playground.vehicle.Vehicle"
        private const val LISTENER_PACKAGE = "ai.pleos.playground.vehicle.listener."
        private const val AREA_PACKAGE = "ai.pleos.playground.vehicle.api.model.area."
        private val PREFERRED = setOf("ROW_1_LEFT", "FRONT_LEFT", "FRONT_WINDSHIELD", "DRIVER")
        private val AREA_CLASSES = listOf("SeatArea", "WindowArea", "DoorArea", "MirrorArea", "WheelArea")

        /** 앱에 Pleos Vehicle SDK가 포함돼 있는지 */
        fun isSdkPresent(): Boolean = try {
            Class.forName(VEHICLE_CLASS)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
