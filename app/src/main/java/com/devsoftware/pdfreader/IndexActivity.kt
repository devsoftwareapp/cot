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
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(AndroidBridge(this), "Android")

        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

class AndroidBridge(private val activity: Activity) {

    @JavascriptInterface
    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    @JavascriptInterface
    fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun listPDFs(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()) {
                return "PERMISSION_DENIED"
            }

            val root = File("/storage/emulated/0/")
            val pdfList = ArrayList<String>()

            root.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() == "pdf") {
                    pdfList.add(file.absolutePath)
                }
            }

            pdfList.joinToString("||")

        } catch (e: Exception) {
            "ERROR"
        }
    }
}
