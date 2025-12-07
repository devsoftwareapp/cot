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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tts: SesliTTS

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TTS başlat
        tts = SesliTTS(this)

        webView = findViewById(R.id.webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true

        // ✔ TTS fonksiyonları da burada
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback = filePathCallback
                openPDFPicker()
                return true
            }
        }

        webView.loadUrl("file:///android_asset/web/sesli_okuma.html")
    }

    private fun openPDFPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/pdf"
            startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            Toast.makeText(this, "PDF seçici açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                fileChooserCallback?.onReceiveValue(arrayOf(uri))
                fileChooserCallback = null
            }
        }
    }

    // ============================================================
    // ANDROID ↔ JS KÖPRÜSÜ (TTS + Toast + İzin)
    // ============================================================
    inner class AndroidBridge {

        // ---- TOAST ----
        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    msg,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // ---- TTS: speakTextWithRate ----
        @JavascriptInterface
        fun speakTextWithRate(text: String, lang: String, rate: Float) {
            tts.speakTextWithRate(text, lang, rate)
        }

        // ---- TTS: speakText ----
        @JavascriptInterface
        fun speakText(text: String, lang: String) {
            tts.speakText(text, lang)
        }

        // ---- TTS: speak ----
        @JavascriptInterface
        fun speak(text: String, lang: String) {
            tts.speak(text, lang)
        }

        // ---- TTS: stop ----
        @JavascriptInterface
        fun stop() {
            tts.stop()
        }

        // ---- Permissions ----
        @JavascriptInterface
        fun requestPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
