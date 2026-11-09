package com.karmadrive.incar.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 트윈카 음성. Pleos TextToSpeech SDK 출시 전까지 Android 표준 TextToSpeech로 대체(어댑터 구조).
 * 차량(AAOS)에서는 미디어가 아닌 '내비 안내' 오디오로 내보내야 잘 들리므로 USAGE_ASSISTANCE_NAVIGATION_GUIDANCE 사용.
 */
class TwinVoice(context: Context) : TextToSpeech.OnInitListener {
    @Volatile private var ready = false
    @Volatile private var pending: String? = null
    private val _status = MutableStateFlow("음성 엔진 준비 중…")
    /** 신호 점검 화면용 상태 */
    val status: StateFlow<String> = _status
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _status.value = "✗ 음성 엔진 초기화 실패 (코드 $status) — 기기에 TTS 엔진이 없을 수 있음"
            return
        }
        val engine = tts.defaultEngine ?: "알 수 없음"
        val lang = tts.setLanguage(Locale.KOREAN)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            _status.value = "✗ 한국어 음성 없음 (엔진 $engine) — 설정 › 시스템 › 언어 › TTS에서 한국어 음성 설치 필요"
            return
        }
        try {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        } catch (_: Exception) {
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _status.value = "▶ 말하는 중… (엔진 $engine)"
            }

            override fun onDone(utteranceId: String?) {
                _status.value = "✓ 음성 재생 완료 (엔진 $engine, 한국어)"
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _status.value = "✗ 음성 재생 오류 (엔진 $engine)"
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _status.value = "✗ 음성 재생 오류 $errorCode (엔진 $engine)"
            }
        })
        ready = true
        _status.value = "✓ 음성 준비됨 (엔진 $engine, 한국어)"
        pending?.let { speak(it) }
        pending = null
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "karma-${System.currentTimeMillis()}")
    }

    fun stop() {
        if (ready) tts.stop()
    }
}
