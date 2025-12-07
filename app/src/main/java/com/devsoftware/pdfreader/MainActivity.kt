package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val REQUEST_SELECT_FILE = 1001

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val ws: WebSettings = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        ws.allowFileAccessFromFileURLs = true
        ws.allowUniversalAccessFromFileURLs = true
        ws.mediaPlaybackRequiresUserGesture = false
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // JS <-> Android köprüsü
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        // Basit WebViewClient (harici hatırlatma)
        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    // Harici linkleri disarda aç
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }

        // WebChromeClient: file chooser tetikleyici web tarafından çağrılırsa burada handle
        webView.webChromeClient = object : WebChromeClient() {
            // opsiyonel extend
        }

        // Load our minimal sesli_okuma page
        webView.loadUrl("file:///android_asset/web/sesli_okuma.html")
    }

    // Android -> JS: PDF seçildikten sonra base64'i JS'e gönderiyoruz
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SELECT_FILE && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri == null) {
                Toast.makeText(this, "Dosya seçilmedi", Toast.LENGTH_SHORT).show()
                return
            }

            // Okuma işlemi: dikkat büyük dosyalarda performans!
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes == null || bytes.isEmpty()) {
                    Toast.makeText(this, "Dosya okunamadı", Toast.LENGTH_SHORT).show()
                    return
                }

                // Base64 - NO_WRAP, JS tarafında decode ederiz
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                // Uzun base64'ler evaluateJavascript sınırını zorlayabilir; bu yöntemi kullanıyoruz.
                // JS fonksiyonu: onPdfSelectedFromAndroid(base64)
                val js = "try{ if(window.onPdfSelectedFromAndroid) onPdfSelectedFromAndroid('${escapeForJs(base64)}'); }catch(e){console.error(e)}"
                webView.post { webView.evaluateJavascript(js, null) }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "PDF okunurken hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Basit escape (tek tırnak ve newline için)
    private fun escapeForJs(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    // Android <-> JS köprüsü
    inner class AndroidBridge {

        // HTML'den dosya seçici açılmasını istendiğinde çağrılır
        @JavascriptInterface
        fun openFilePicker() {
            runOnUiThread {
                // Android 11+ cihazlarda ALL FILES izin gerekebilir; burada sadece chooser açıyoruz.
                try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "application/pdf"
                    startActivityForResult(Intent.createChooser(intent, "PDF Dosyası Seç"), REQUEST_SELECT_FILE)
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Dosya seçici açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // JS, Android TTS'yi tetiklemek isterse (senin AndroidTTS sınıfın varsa onunla bağla)
        @JavascriptInterface
        fun speak(text: String, lang: String, rate: Float) {
            // Burada Android TTS'in çağrılması gerekir. Eğer AndroidTTS.kt mevcutsa çağır.
            // Örnek: androidTTS.speak(text, lang, rate)
            // Bu minimal örnekte sadece Toast gösteriyoruz (kendi AndroidTTS ile değiştir).
            runOnUiThread {
                Toast.makeText(this@MainActivity, "TTS (android) çağrıldı: ${text.take(50)}...", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun stop() {
            // androidTTS.stop() gibi implement et
        }

        @JavascriptInterface
        fun isSpeaking(): Boolean {
            // return androidTTS.isSpeaking()
            return false
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        // İzin açma çağrısı (JS'ten tetiklenebilir)
        @JavascriptInterface
        fun openAllFilesPermission() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Lütfen dosya izinlerini kontrol edin", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
