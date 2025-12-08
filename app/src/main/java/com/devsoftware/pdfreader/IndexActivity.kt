package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class IndexActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // JS Bridge (Android objesi)
        webView.addJavascriptInterface(AndroidBridge(this, webView), "Android")

        // Ana sayfa yükleniyor
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()

        // Ayarlardan geri dönüldü → izni tekrar kontrol et
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Web tarafına "izin verildi" sinyali gönder
                webView.evaluateJavascript("onPermissionGranted()", null)
            } else {
                // Hâlâ izin yok
                webView.evaluateJavascript("onPermissionDenied()", null)
            }
        }
    }
}

class AndroidBridge(private val activity: Activity, private val webView: WebView) {

    // HTML -> Android: İzin durumu sor
    @JavascriptInterface
    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    // HTML -> Android: Ayarlara git
    @JavascriptInterface
    fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }
    }

    // HTML -> Android: PDF listesi al
    @JavascriptInterface
    fun listPDFs(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()) {
                return "PERMISSION_DENIED"
            }

            val root = File("/storage/emulated/0/")
            val list = ArrayList<String>()

            root.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.equals("pdf", true)) {
                    list.add(file.absolutePath)
                }
            }

            if (list.isEmpty()) "EMPTY" else list.joinToString("||")

        } catch (e: Exception) {
            "ERROR"
        }
    }
}
