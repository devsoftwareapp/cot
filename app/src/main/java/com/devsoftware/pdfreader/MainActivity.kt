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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView
    private var backPressedTime: Long = 0
    private val appFolderName = "PDF Reader"

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // WebView Ayarları
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
                
                // Viewer.html'den index.html'ye dönüş
                if (url.contains("file:///android_asset/web/index.html")) {
                    // JavaScript'e geri dönüldü bilgisini gönder
                    webView.postDelayed({
                        webView.evaluateJavascript("""
                            if (typeof onReturnFromViewer === 'function') {
                                onReturnFromViewer();
                            }
                        """.trimIndent(), null)
                    }, 300)
                }
                
                return false
            }
        }

        webView.loadUrl("file:///android_asset/web/index.html")
        
        // Uygulama açıldığında PDF Reader klasörünü oluştur
        createAppFolder()
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

    /** PDF Reader Klasörü Oluştur */
    private fun createAppFolder() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appFolder = File(downloadsDir, appFolderName)
            
            if (!appFolder.exists()) {
                if (appFolder.mkdirs()) {
                    println("PDF Reader klasörü oluşturuldu: ${appFolder.absolutePath}")
                } else {
                    println("PDF Reader klasörü oluşturulamadı!")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

        /** PDF Tarama - PDF Reader klasörünü hariç tut */
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
                val externalStorage = Environment.getExternalStorageDirectory()
                if (externalStorage.exists()) {
                    roots.add(externalStorage)
                }
            } else {
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

            // Tarama işlemi - PDF Reader klasörünü hariç tut
            roots.forEach { root ->
                if (root.exists() && root.canRead()) {
                    scanForPDFs(root, pdfList, 0)
                }
            }

            return if (pdfList.isEmpty()) "" else pdfList.joinToString("||")
        }

        /** Rekürsif PDF tarama - PDF Reader klasörünü atla */
        private fun scanForPDFs(dir: File, output: MutableList<String>, depth: Int) {
            if (depth > 10) return
            if (dir.name.startsWith(".")) return
            
            // PDF Reader klasörünü atla
            if (dir.name == appFolderName) return
            
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        scanForPDFs(file, output, depth + 1)
                    } else if (file.isFile && file.name.lowercase().endsWith(".pdf")) {
                        output.add(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        /** Dosya boyutunu al */
        @JavascriptInterface
        fun getFileSize(path: String): Long {
            return try {
                File(path).length()
            } catch (e: Exception) {
                0L
            }
        }

        /** Dosya tarihini al */
        @JavascriptInterface
        fun getFileDate(path: String): String {
            return try {
                val file = File(path)
                val date = Date(file.lastModified())
                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                sdf.format(date)
            } catch (e: Exception) {
                ""
            }
        }

        /** İzin durumunu döndür */
        @JavascriptInterface
        fun getPermissionStatus(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) "GRANTED" else "DENIED"
            } else {
                "GRANTED_LEGACY"
            }
        }
        
        /** PDF Reader klasörünü oluştur */
        @JavascriptInterface
        fun createAppFolder(): String {
            return try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, appFolderName)
                
                if (!appFolder.exists()) {
                    if (appFolder.mkdirs()) {
                        "SUCCESS:${appFolder.absolutePath}"
                    } else {
                        "ERROR:Could not create folder"
                    }
                } else {
                    "EXISTS:${appFolder.absolutePath}"
                }
            } catch (e: Exception) {
                "ERROR:${e.message}"
            }
        }
        
        /** Dosyayı PDF Reader klasörüne kaydet */
        @JavascriptInterface
        fun saveToAppFolder(sourcePath: String, fileName: String): String {
            return try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, appFolderName)
                
                // Klasör yoksa oluştur
                if (!appFolder.exists()) {
                    appFolder.mkdirs()
                }
                
                val sourceFile = File(sourcePath)
                val destFile = File(appFolder, fileName)
                
                // Dosyayı kopyala
                sourceFile.copyTo(destFile, overwrite = true)
                
                "SUCCESS:${destFile.absolutePath}"
            } catch (e: Exception) {
                "ERROR:${e.message}"
            }
        }
        
        /** Dosyaları paylaş */
        @JavascriptInterface
        fun shareFiles(filePaths: String) {
            val paths = filePaths.split("||").filter { it.isNotEmpty() }
            if (paths.isEmpty()) return
            
            val uris = ArrayList<Uri>()
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val uri = Uri.fromFile(file)
                    uris.add(uri)
                }
            }
            
            if (uris.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
                intent.type = "application/pdf"
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                startActivity(Intent.createChooser(intent, "PDF'leri Paylaş"))
            }
        }
    }

    /** İzin ekranından dönünce çağrılır */
    override fun onResume() {
        super.onResume()
        webView.post {
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
    override fun onBackPressed() {
        webView.evaluateJavascript("""
            if (typeof androidBackPressed === 'function') {
                var result = androidBackPressed();
                result;
            } else {
                'exit_check';
            }
        """.trimIndent()) { result ->
            val jsResult = result?.trim('"')
            
            if (jsResult == "exit" || jsResult == "exit_check") {
                // Ana sayfadaysa çift tıklama ile çıkış
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
}
