package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // === WebView Ayarları ===
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true

        webView.webChromeClient = WebChromeClient()

        // HTML → Android köprüsü
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        // Özel link yakalama
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {

                if (url == "settings://all_files") {
                    openAllFilesPermission()
                    return true
                }

                return false
            }
        }

        // index.html yükle
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    /** Tüm Dosya Erişimi Ayarı */
    private fun openAllFilesPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** JavaScript Interface (HTML → Android) */
    inner class AndroidBridge {

        @JavascriptInterface
        fun checkPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        }

        @JavascriptInterface
        fun listPDFs(): String {

            val pdfList = ArrayList<String>()

            // === Tarama yapılacak potansiyel root dizinler ===
            val roots = listOf(
                File("/storage/emulated/0"),
                File("/sdcard"),
                File("/storage/self/primary"),
                Environment.getExternalStorageDirectory()
            )

            for (root in roots) {
                if (root.exists() && root.canRead()) {
                    scanSafe(root, pdfList)
                }
            }

            return pdfList.joinToString("||")
        }

        /** Güvenli PDF tarama — Android/data ve .klasörleri skip */
        private fun scanSafe(dir: File, out: MutableList<String>) {

            if (!dir.exists() || !dir.canRead()) return

            // Android/data ve obb -> yasak → atla
            if (dir.absolutePath.contains("/Android/data") ||
                dir.absolutePath.contains("/Android/obb")) return

            // gizli klasörleri atla
            if (dir.name.startsWith(".")) return

            val files = dir.listFiles() ?: return

            for (file in files) {

                if (file.isDirectory) {
                    scanSafe(file, out)
                } else if (file.name.lowercase().endsWith(".pdf")) {
                    out.add(file.absolutePath)
                }
            }
        }
    }

    /** Ayarlardan dönünce HTML'e bilgi gönder */
    override fun onResume() {
        super.onResume()
        webView.post {
            webView.evaluateJavascript("onAndroidResume()", null)
        }
    }
}
