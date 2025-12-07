package com.devsoftware.pdfreader

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class MainActivity : AppCompatActivity(), AndroidTTS.TTSCallback {

    private lateinit var androidTTS: AndroidTTS

    override fun onStart() {}
    override fun onDone() {
        webView.post { webView.evaluateJavascript("if(window.onTTSDone) onTTSDone();", null) }
    }
    override fun onError() {}

class AndroidTTS(private val context: Context, private val callback: TTSCallback) :
    TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var currentRate = 1.0f
    private var isReady = false

    interface TTSCallback {
        fun onStart()
        fun onDone()
        fun onError()
    }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
        if (!isReady) {
            callback.onError()
        }
    }

    fun speak(text: String, langCode: String, rate: Float) {
        if (!isReady || tts == null) {
            callback.onError()
            return
        }

        try {
            val locale = getLocale(langCode)
            tts!!.language = locale
        } catch (_: Exception) {}

        currentRate = rate
        tts!!.setSpeechRate(rate)

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "TTS_ID"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
        } else {
            @Suppress("DEPRECATION")
            tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, params)
        }

        tts!!.setOnUtteranceProgressListener(object :
            android.speech.tts.UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {
                callback.onStart()
            }

            override fun onDone(utteranceId: String?) {
                callback.onDone()
            }

            override fun onError(utteranceId: String?) {
                callback.onError()
            }
        })
    }

    fun stop() {
        tts?.stop()
    }

    fun pause() {
        // Gerçek pause sadece Android 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tts?.playSilentUtterance(1, TextToSpeech.QUEUE_ADD, null)
        } else {
            stop()
        }
    }

    fun resume(text: String) {
        speak(text, "tr-TR", currentRate)
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    fun shutdown() {
        tts?.shutdown()
    }

    private fun getLocale(code: String): Locale {
        return try {
            val parts = code.split("-")
            if (parts.size == 2) Locale(parts[0], parts[1])
            else Locale(code)
        } catch (e: Exception) {
            Locale.getDefault()
        }
    }
}
