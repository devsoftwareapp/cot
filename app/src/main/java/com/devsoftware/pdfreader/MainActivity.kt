package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // ⚡ WebView Ayarları
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
        }

        // Android Bridge
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url == "settings://all_files") {
                    openAllFilesPermission()
                    return true
                }
                return false
            }
        }

        webView.loadUrl("file:///android_asset/web/index.html")
    }

    /** Tüm Dosya İzni Ekranı - DÜZELTİLMİŞ */
    private fun openAllFilesPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ için doğru intent
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                // Android 10 ve altı
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            Toast.makeText(this, "Ayarlara yönlendiriliyor...", Toast.LENGTH_SHORT).show()
        }
    }

    /** Android → HTML Bridge - GÜNCELLENMİŞ */
    inner class AndroidBridge {

        /** İzin kontrolü - DÜZELTİLMİŞ */
        @JavascriptInterface
        fun checkPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                // Android 10 ve altı için her zaman true
                true
            }
        }

        /** PDF Tarama - OPTİMİZE EDİLMİŞ */
        @JavascriptInterface
        fun listPDFs(): String {
            // Önce izin kontrolü
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    return "PERMISSION_DENIED"
                }
            }

            val pdfList = mutableListOf<String>()
            val roots = mutableListOf<File>()

            // Tüm olası kök dizinler
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ için external storage
                val externalStorage = Environment.getExternalStorageDirectory()
                if (externalStorage.exists()) {
                    roots.add(externalStorage)
                }
            } else {
                // Android 10 ve altı için geleneksel yollar
                roots.apply {
                    add(File("/storage/emulated/0"))
                    add(File("/sdcard"))
                    add(File("/storage/self/primary"))
                    Environment.getExternalStorageDirectory()?.let { add(it) }
                }
            }

            // Downloads, Documents, DCIM gibi önemli klasörleri de tara
            val importantDirs = listOf(
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_DCIM
            )

            importantDirs.forEach { dirType ->
                getExternalFilesDir(dirType)?.let { dir ->
                    if (dir.exists()) roots.add(dir)
                }
            }

            // Tarama işlemi (UI thread'i bloklamamak için basitleştirildi)
            roots.forEach { root ->
                if (root.exists() && root.canRead()) {
                    scanForPDFs(root, pdfList, 0)
                }
            }

            return if (pdfList.isEmpty()) "" else pdfList.joinToString("||")
        }

        /** Rekürsif PDF tarama (derinlik sınırlı) */
        private fun scanForPDFs(dir: File, output: MutableList<String>, depth: Int) {
            // Derinlik sınırı (çok derin klasörlere girme)
            if (depth > 10) return
            
            // Gizli klasörleri atla
            if (dir.name.startsWith(".")) return
            
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        scanForPDFs(file, output, depth + 1)
                    } else if (file.isFile && file.name.lowercase().endsWith(".pdf")) {
                        output.add(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                // Yetki hatası vb. durumları göz ardı et
                e.printStackTrace()
            }
        }

        /** Test için: İzin durumunu döndür */
        @JavascriptInterface
        fun getPermissionStatus(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) "GRANTED" else "DENIED"
            } else {
                "GRANTED_LEGACY"
            }
        }
    }

    /** İzin ekranından dönünce çağrılır */
    override fun onResume() {
        super.onResume()
        webView.post {
            // JavaScript'e izin durumunu gönder
            webView.evaluateJavascript("""
                try {
                    onAndroidResume();
                } catch(e) {
                    console.log('onAndroidResume error: ' + e);
                }
            """.trimIndent(), null)
        }
    }
}
