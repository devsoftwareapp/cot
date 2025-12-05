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

    @SuppressLint("SetJavaScriptEnabled")
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
            
            // Sayfa yükleme tamamlandığında
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // JavaScript ile iletişim kur
                injectJavaScript()
            }
        }

        webView.loadUrl("file:///android_asset/web/index.html")
    }
    
    // JavaScript'e geri tuşu kontrolü ekle
    private fun injectJavaScript() {
        webView.evaluateJavascript("""
            // Android geri tuşu kontrolü
            window.isAtHomePage = function() {
                return window.location.pathname.includes('index.html') || 
                       window.location.href.includes('index.html');
            };
            
            window.handleAndroidBack = function() {
                // Ana sayfadaysa uyarı göster
                if (isAtHomePage()) {
                    if (typeof currentNav !== 'undefined' && currentNav === 'home') {
                        return true; // Ana sayfada, çıkış yap
                    }
                }
                return false; // Geri git
            };
        """.trimIndent(), null)
    }

    /** Tüm Dosya İzni Ekranı */
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
            Toast.makeText(this, "Ayarlara yönlendiriliyor...", Toast.LENGTH_SHORT).show()
        }
    }

    /** Android → HTML Bridge */
    inner class AndroidBridge {

        /** İzin kontrolü */
        @JavascriptInterface
        fun checkPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        }

        /** PDF Tarama - MediaStore kullanarak */
        @JavascriptInterface
        fun listPDFs(): String {
            // Önce izin kontrolü
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    return "PERMISSION_DENIED"
                }
            }

            return try {
                val pdfList = scanPDFsWithMediaStore()
                if (pdfList.isEmpty()) "" else pdfList.joinToString("||")
            } catch (e: Exception) {
                e.printStackTrace()
                // MediaStore başarısız olursa eski yönteme dön
                legacyListPDFs()
            }
        }
        
        /** MediaStore ile PDF tarama */
        private fun scanPDFsWithMediaStore(): List<String> {
            val pdfList = mutableListOf<String>()
            val contentResolver: android.content.ContentResolver = applicationContext.contentResolver
            
            val projection = arrayOf(
                android.provider.MediaStore.Files.FileColumns._ID,
                android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                android.provider.MediaStore.Files.FileColumns.SIZE,
                android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            
            val selection = "${android.provider.MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            val selectionArgs = arrayOf("application/pdf")
            
            val sortOrder = "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            
            contentResolver.query(
                android.provider.MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val contentUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        id
                    )
                    pdfList.add("$id||$name||${contentUri}")
                }
            }
            
            return pdfList
        }
        
        /** Eski yöntemle PDF tarama (geriye dönük uyumluluk) */
        private fun legacyListPDFs(): String {
            val pdfList = ArrayList<String>()

            val roots = listOf(
                File("/storage/emulated/0"),
                File("/sdcard"),
                File("/storage/self/primary"),
                Environment.getExternalStorageDirectory()
            )

            roots.forEach { root ->
                if (root.exists()) {
                    scanPDFs(root, pdfList)
                }
            }

            return pdfList.joinToString("||")
        }

        /** PDF tarayıcı (recursive) */
        private fun scanPDFs(folder: File, output: MutableList<String>) {
            if (!folder.exists()) return
            if (folder.name.startsWith(".")) return

            folder.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanPDFs(file, output)
                } else if (file.name.lowercase().endsWith(".pdf")) {
                    output.add(file.absolutePath)
                }
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
        
        /** Geri tuşu için: Ana sayfada mı kontrolü */
        @JavascriptInterface
        fun checkIfAtHomePage(): Boolean {
            return webView.url?.contains("index.html") == true
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
    
    // --- GERİ TUŞU İŞLEMİ ---
    private var backPressedTime: Long = 0
    
    override fun onBackPressed() {
        // WebView'de geri gitme özelliğini kontrol et
        if (webView.canGoBack()) {
            // JavaScript ile geri tuşu işlemini kontrol et
            webView.evaluateJavascript("""
                (function() {
                    // Eğer viewer.html'de isek index.html'ye dön
                    if (window.location.href.includes('viewer.html')) {
                        return 'VIEWER';
                    }
                    
                    // Eğer ana sayfadaysak ve home sekmesindeysek çıkış göster
                    if (typeof currentNav !== 'undefined' && currentNav === 'home') {
                        return 'HOME_EXIT';
                    }
                    
                    // Eğer diğer sekmelerdeysek ana sayfaya dön
                    if (typeof currentNav !== 'undefined' && currentNav !== 'home') {
                        switchNav('home');
                        return 'NAV_HOME';
                    }
                    
                    return 'GO_BACK';
                })();
            """.trimIndent()) { result ->
                when (result?.trim('"')) {
                    "VIEWER" -> {
                        // viewer.html'den index.html'ye dön
                        webView.loadUrl("file:///android_asset/web/index.html")
                    }
                    "HOME_EXIT" -> {
                        // Ana sayfada çift tıklama ile çıkış
                        if (backPressedTime + 2000 > System.currentTimeMillis()) {
                            super.onBackPressed()
                            finish()
                        } else {
                            Toast.makeText(this, "Çıkmak için tekrar geri tuşuna basın", Toast.LENGTH_SHORT).show()
                            backPressedTime = System.currentTimeMillis()
                        }
                    }
                    "NAV_HOME" -> {
                        // Zaten switchNav çağrıldı, bir şey yapma
                    }
                    else -> {
                        // WebView'de geri git
                        webView.goBack()
                    }
                }
            }
        } else {
            // WebView'de geri gidilecek sayfa yok
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                super.onBackPressed()
                finish()
            } else {
                Toast.makeText(this, "Çıkmak için tekrar geri tuşuna basın", Toast.LENGTH_SHORT).show()
                backPressedTime = System.currentTimeMillis()
            }
        }
    }
}
