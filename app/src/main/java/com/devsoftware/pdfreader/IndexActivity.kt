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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class IndexActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // WebView ayarları
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // WebView client'ları
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkAndUpdatePermission()
            }
        }

        // JavaScript Interface
        webView.addJavascriptInterface(AndroidInterface(this), "Android")

        // Local HTML yükle
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdatePermission()
    }

    private fun checkAndUpdatePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Android 10 ve altı için true
        }

        val jsCode = if (hasPermission) {
            "onPermissionGranted()"
        } else {
            "onPermissionDenied()"
        }
        
        webView.post {
            webView.evaluateJavascript(jsCode, null)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // JavaScript Interface
    inner class AndroidInterface(private val activity: Activity) {

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
            activity.runOnUiThread {
                try {
                    // DOĞRUDAN "TÜM DOSYALARA ERİŞİM" EKRANINA GİT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Ana intent: Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        val uri = Uri.parse("package:${activity.packageName}")
                        
                        // İlk deneme
                        try {
                            val intent1 = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent1.data = uri
                            activity.startActivity(intent1)
                        } catch (e1: Exception) {
                            // İkinci deneme
                            try {
                                val intent2 = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                intent2.data = uri
                                activity.startActivity(intent2)
                            } catch (e2: Exception) {
                                // Üçüncü deneme: Uygulama detayları
                                val intent3 = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent3.data = uri
                                activity.startActivity(intent3)
                            }
                        }
                    } else {
                        // Android 10 ve altı için
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${activity.packageName}")
                        activity.startActivity(intent)
                    }
                } catch (e: Exception) {
                    // Basit hata mesajı
                    Toast.makeText(activity, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun listPDFs(): String {
            try {
                // İzin kontrolü
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        return "PERMISSION_DENIED"
                    }
                }

                val root = Environment.getExternalStorageDirectory()
                val list = mutableListOf<String>()

                // PDF tarama
                scanForPDFs(root, list, 0, 3)

                return if (list.isEmpty()) {
                    "EMPTY"
                } else {
                    list.joinToString("||")
                }
            } catch (e: Exception) {
                return "ERROR"
            }
        }

        private fun scanForPDFs(dir: android.os.storage.StorageVolume?, list: MutableList<String>, depth: Int, maxDepth: Int) {
            if (depth > maxDepth) return
            
            dir?.directory?.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanForPDFs(file, list, depth + 1, maxDepth)
                } else if (file.isFile && file.name.endsWith(".pdf", true)) {
                    list.add(file.absolutePath)
                }
            }
        }

        private fun scanForPDFs(dir: java.io.File, list: MutableList<String>, depth: Int, maxDepth: Int) {
            if (depth > maxDepth) return
            
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanForPDFs(file, list, depth + 1, maxDepth)
                } else if (file.isFile && file.name.endsWith(".pdf", true)) {
                    list.add(file.absolutePath)
                }
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
