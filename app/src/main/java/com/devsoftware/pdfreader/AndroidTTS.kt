package com.devsoftware.pdfreader

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.*

class AndroidTTS(private val context: Context, private val callback: TTSCallback) :
    TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var currentRate = 1.0f
    private var isReady = false

    interface TTSCallback {
        fun onTTSStart()
        fun onTTSDone()
        fun onTTSError()
    }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
        if (!isReady) callback.onTTSError()
    }

    fun speak(text: String, langCode: String, rate: Float) {
        if (!isReady || tts == null) {
            callback.onTTSError()
            return
        }

        try {
            val locale = getLocale(langCode)
            tts!!.language = locale
        } catch (_: Exception) {}

        currentRate = rate
        tts!!.setSpeechRate(rate)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
        } else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "TTS_ID"
            @Suppress("DEPRECATION")
            tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, params)
        }

        tts!!.setOnUtteranceProgressListener(object :
            android.speech.tts.UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {
                callback.onTTSStart()
            }

            override fun onDone(utteranceId: String?) {
                callback.onTTSDone()
            }

            override fun onError(utteranceId: String?) {
                callback.onTTSError()
            }
        })
    }

    fun stop() {
        tts?.stop()
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tts?.playSilentUtterance(1, TextToSpeech.QUEUE_ADD, null)
        } else {
            stop()
        }
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    fun shutdown() {
        tts?.shutdown()
    }

    private fun getLocale(code: String): Locale {
        return try {
            val p = code.split("-")
            if (p.size == 2) Locale(p[0], p[1]) else Locale(code)
        } catch (e: Exception) {
            Locale.getDefault()
        }
    }
}
