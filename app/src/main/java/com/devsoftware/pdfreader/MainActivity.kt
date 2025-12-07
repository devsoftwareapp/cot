package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
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
    private val REQUEST_SELECT_PDF = 3001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val ws = webView.settings
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

        // JS Köprüsü
        webView.addJavascriptInterface(JSBridge(), "Android")

        // URL yakalama
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Sesli okuma ekranını yükle
        webView.loadUrl("file:///android_asset/web/sesli_okuma.html")
    }

    // ------------------------------------------------------------------
    // PDF SEÇİM SONUCU
    // ------------------------------------------------------------------
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SELECT_PDF && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri == null) {
                Toast.makeText(this, "PDF bulunamadı", Toast.LENGTH_SHORT).show()
                return
            }

            try {
                val input: InputStream? = contentResolver.openInputStream(uri)
                val bytes = input?.readBytes()
                input?.close()

                if (bytes == null) {
                    Toast.makeText(this, "PDF okunamadı", Toast.LENGTH_SHORT).show()
                    return
                }

                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val safe = base64
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "")
                    .replace("\r", "")

                val js = "onPdfSelectedFromAndroid('$safe')"

                webView.post {
                    webView.evaluateJavascript(js, null)
                }

            } catch (e: Exception) {
                Toast.makeText(this, "PDF okunamadı: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------------
    // JS ↔ ANDROID KÖPRÜSÜ
    // ------------------------------------------------------------------
    inner class JSBridge {

        @JavascriptInterface
        fun openFilePicker() {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_GET_CONTENT)
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    i.type = "application/pdf"
                    startActivityForResult(Intent.createChooser(i, "PDF Seç"), REQUEST_SELECT_PDF)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Seçici açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun openAllFilesPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))
                }
            }
        }
    }
}
