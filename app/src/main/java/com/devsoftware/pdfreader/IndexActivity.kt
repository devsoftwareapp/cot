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

    lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.allowUniversalAccessFromFileURLs = true
        s.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(AndroidBridge(this), "Android")

        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()

        // Ayarlardan dönünce izin kontrol et ve JavaScript'i tetikle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // İzin verildi
                webView.evaluateJavascript("onPermissionGranted()", null)
            } else {
                // İzin verilmedi
                webView.evaluateJavascript("onPermissionDenied()", null)
            }
        } else {
            // Android 10 ve altı için otomatik izin verilmiş kabul et
            webView.evaluateJavascript("onPermissionGranted()", null)
        }
    }
}

class AndroidBridge(private val activity: Activity) {

    @JavascriptInterface
    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else true
    }

    @JavascriptInterface
    fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivity(intent)
            } catch (e: Exception) {
                // Bazı cihazlarda farklı intent gerekebilir
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivity(intent)
            }
        } else {
            // Android 10 ve altı için normal depolama izni iste
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun listPDFs(): String {
        return try {
            if (Build.VERSION.SDK_INT >= 30 &&
                !Environment.isExternalStorageManager()) {
                return "PERMISSION_DENIED"
            }

            val root = File("/storage/emulated/0/")
            val list = ArrayList<String>()

            root.walkTopDown().forEach { f ->
                if (f.isFile && f.extension.equals("pdf", true)) {
                    list.add(f.absolutePath)
                }
            }

            if (list.isEmpty()) "EMPTY" else list.joinToString("||")

        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
