package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import android.webkit.MimeTypeMap
import android.webkit.WebSettings

class MainActivity : AppCompatActivity(), AndroidTTS.TTSCallback {

    private lateinit var androidTTS: AndroidTTS

    override fun onTTSStart() {}

    override fun onTTSDone() {
        webView.post {
            webView.evaluateJavascript("if(window.onTTSDone) onTTSDone();", null)
        }
    }

    override fun onTTSError() {}

    lateinit var webView: WebView
    private var backPressedTime: Long = 0
    private val appFolderName = "PDF Reader"
    private var isInViewer = false

    private var mUploadMessage: ValueCallback<Array<Uri>>? = null
    private val REQUEST_SELECT_FILE = 100
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidTTS = AndroidTTS(this, this)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            allowFileAccessFromFileURLs = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                allowUniversalAccessFromFileURLs = true
                allowFileAccessFromFileURLs = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mediaPlaybackRequiresUserGesture = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(true)
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false
        }

        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.webViewClient = object : WebViewClient() {}

        webView.webChromeClient = object : WebChromeClient() {}

        webView.loadUrl("file:///android_asset/web/index.html")
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun speakText(text: String, lang: String, rate: Float) {
            androidTTS.speak(text, lang, rate)
        }

        @JavascriptInterface
        fun stopSpeaking() {
            androidTTS.stop()
        }

        @JavascriptInterface
        fun pauseSpeaking() {
            androidTTS.pause()
        }

        @JavascriptInterface
        fun isSpeaking(): Boolean {
            return androidTTS.isSpeaking()
        }
    }

    override fun onDestroy() {
        androidTTS.shutdown()
        super.onDestroy()
    }
}
