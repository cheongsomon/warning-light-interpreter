package ai.pleos.warninglight.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import ai.pleos.warninglight.BuildConfig
import android.content.Context
import android.util.Log
import java.util.Locale
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 음성 안내.
 * 1순위: Pleos TextToSpeech SDK (HYBRID 모드, registerApp으로 서버 합성 사용)
 * 대체: Android 표준 TextToSpeech — Pleos TTS 초기화/재생이 실패하면 자동 전환한다.
 * 어떤 엔진으로 말하고 있는지는 engineLabel로 화면에 표시한다.
 */
class Announcer(
    context: Context,
    private val onEngineChanged: (String) -> Unit,
) {
    private enum class Engine { NONE, PLEOS, ANDROID }

    private val appContext = context.applicationContext
    private var engine = Engine.NONE
    private var pleos: TextToSpeech? = null
    private var android: AndroidTts? = null
    private var androidReady = false
    private var pending: String? = null
    private var lastText: String? = null

    private val pleosEvents = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() {}
        override fun onStop() {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onError(type: ErrorType) {
            Log.w(TAG, "Pleos TTS error: $type → Android TTS로 전환")
            switchToAndroid(replay = lastText)
        }
    }

    fun start() {
        try {
            val tts = TextToSpeech(appContext, Mode.HYBRID)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.addEventListener(pleosEvents)
                    setEngine(Engine.PLEOS)
                    registerApp(tts)
                    pending?.let { pending = null; speak(it) }
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos TTS init failed: $type")
                    switchToAndroid(replay = pending)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Pleos TTS unavailable: ${e.message}")
            switchToAndroid(replay = pending)
        }
    }

    private fun registerApp(tts: TextToSpeech) {
        if (BuildConfig.PLEOS_CLIENT_ID.isBlank()) {
            Log.w(TAG, "Client ID 없음 → 온디바이스 합성만 사용")
            return
        }
        try {
            tts.registerApp(
                BuildConfig.PLEOS_CLIENT_ID,
                BuildConfig.PLEOS_CLIENT_SECRET,
                object : OnServerConnectionListener {
                    override fun onConnected() { Log.i(TAG, "Pleos TTS server connected") }
                    override fun onFailed(type: ErrorType) { Log.w(TAG, "registerApp failed: $type (온디바이스 합성 유지)") }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "registerApp error: ${e.message}")
        }
    }

    /** 이전 안내를 끊고 새 문장을 읽는다. */
    fun speak(text: String) {
        lastText = text
        when (engine) {
            Engine.PLEOS -> try {
                pleos?.stop()
                pleos?.speak(text, true)
            } catch (e: Exception) {
                Log.w(TAG, "Pleos speak failed: ${e.message}")
                switchToAndroid(replay = text)
            }
            Engine.ANDROID ->
                if (androidReady) android?.speak(text, AndroidTts.QUEUE_FLUSH, null, "warning")
                else pending = text
            Engine.NONE -> pending = text
        }
    }

    fun stop() {
        runCatching { pleos?.stop() }
        runCatching { android?.stop() }
    }

    private fun switchToAndroid(replay: String?) {
        if (engine == Engine.ANDROID) return
        setEngine(Engine.ANDROID)
        pending = replay
        android = AndroidTts(appContext) { status ->
            androidReady = status == AndroidTts.SUCCESS
            if (androidReady) {
                android?.language = Locale.KOREAN
                pending?.let { pending = null; speak(it) }
            }
        }
    }

    private fun setEngine(e: Engine) {
        engine = e
        onEngineChanged(
            when (e) {
                Engine.PLEOS -> "Pleos TTS"
                Engine.ANDROID -> "Android TTS (대체)"
                Engine.NONE -> "음성 준비 중"
            }
        )
    }

    fun release() {
        runCatching {
            pleos?.removeEventListener(pleosEvents)
            pleos?.release()
        }
        runCatching { android?.shutdown() }
    }

    companion object {
        private const val TAG = "Announcer"
    }
}
