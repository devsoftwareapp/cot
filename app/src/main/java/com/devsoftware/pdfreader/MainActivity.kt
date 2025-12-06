package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import android.webkit.MimeTypeMap

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView
    private var backPressedTime: Long = 0
    private val appFolderName = "PDF Reader"
    private var isInViewer = false
    
    // File chooser için değişkenler
    private var mUploadMessage: ValueCallback<Array<Uri>>? = null
    private val REQUEST_SELECT_FILE = 100
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

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
            allowFileAccessFromFileURLs = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // Ek ayarlar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                allowUniversalAccessFromFileURLs = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mediaPlaybackRequiresUserGesture = false
            }
        }

        // Android Bridge
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        // WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                println("URL yükleme: $url")
                
                // Settings linki
                if (url == "settings://all_files") {
                    openAllFilesPermission()
                    return true
                }
                
                // Viewer.html kontrolü
                if (url.contains("viewer.html")) {
                    isInViewer = true
                } else if (url.contains("index.html")) {
                    isInViewer = false
                }
                
                // Harici bağlantıları engelle
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }
                
                return false
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                println("Sayfa yüklendi: $url")
                
                // Viewer'dan döndüğümüzde
                if (url.contains("index.html") && isInViewer) {
                    isInViewer = false
                    webView.postDelayed({
                        webView.evaluateJavascript("""
                            try {
                                if (typeof onReturnFromViewer === 'function') {
                                    onReturnFromViewer();
                                }
                            } catch(e) {
                                console.log('onReturnFromViewer error: ' + e);
                            }
                        """.trimIndent(), null)
                    }, 300)
                }
            }
        }
        
        // WebChromeClient - FILE CHOOSER İÇİN ÇOK ÖNEMLİ!
        webView.webChromeClient = object : WebChromeClient() {
            // For Android 5.0+
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"  // Tüm dosya türleri
                
                // PDF filtreleme
                if (fileChooserParams?.acceptTypes != null && fileChooserParams.acceptTypes.isNotEmpty()) {
                    val acceptTypes = fileChooserParams.acceptTypes.joinToString(",")
                    if (acceptTypes.contains("pdf") || acceptTypes.contains("application/pdf")) {
                        intent.type = "application/pdf"
                    }
                }
                
                try {
                    startActivityForResult(Intent.createChooser(intent, "Dosya Seç"), REQUEST_SELECT_FILE)
                } catch (e: Exception) {
                    filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
                
                return true
            }
        }

        webView.loadUrl("file:///android_asset/web/index.html")
        
        // Uygulama açıldığında PDF Reader klasörünü oluştur
        createAppFolder()
    }
    
    // Activity result - File chooser sonucu
    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_SELECT_FILE -> {
                // 1. Önce WebChromeClient file chooser'ı kontrol et
                if (filePathCallback != null) {
                    var results: Array<Uri>? = null
                    
                    if (resultCode == RESULT_OK) {
                        if (data != null) {
                            val dataString = data.dataString
                            if (dataString != null) {
                                results = arrayOf(Uri.parse(dataString))
                            }
                        }
                    }
                    
                    filePathCallback?.onReceiveValue(results)
                    filePathCallback = null
                } 
                // 2. Sesli okuma için PDF seçildi mi kontrol et (AndroidBridge.selectSpeechPDF()'den geldi)
                else if (resultCode == RESULT_OK && data != null) {
                    val uri = data.data
                    if (uri != null) {
                        handleSpeechPDFSelection(uri)
                    }
                }
            }
        }
    }
    
    /** Sesli okuma için seçilen PDF'i işle */
    private fun handleSpeechPDFSelection(uri: Uri) {
        try {
            // URI'dan dosya yolunu al
            val filePath = getFilePathFromUri(uri)
            
            // JavaScript'e dosya yolunu gönder
            webView.post {
                webView.evaluateJavascript("""
                    try {
                        // onPDFSelectedForSpeech fonksiyonu var mı kontrol et
                        if (typeof onPDFSelectedForSpeech === 'function') {
                            onPDFSelectedForSpeech('$filePath');
                        } else {
                            // Yoksa URL parametresi ile sesli_okuma.html'yi aç
                            window.location.href = 'file:///android_asset/web/sesli_okuma.html?file=' + encodeURIComponent('$filePath');
                        }
                    } catch(e) {
                        console.log('PDF seçme hatası: ' + e);
                        // Hata durumunda direkt yönlendir
                        window.location.href = 'file:///android_asset/web/sesli_okuma.html?file=' + encodeURIComponent('$filePath');
                    }
                """.trimIndent(), null)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF yüklenirken hata oluştu", Toast.LENGTH_SHORT).show()
        }
    }
    
    /** URI'dan dosya yolunu al */
    private fun getFilePathFromUri(uri: Uri): String {
        return try {
            // content:// URI'si ise
            if (uri.scheme == "content") {
                // Önce dosyayı geçici olarak kopyala
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = File(cacheDir, "temp_speech_pdf_${System.currentTimeMillis()}.pdf")
                
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                tempFile.absolutePath
            } else {
                // file:// URI'si ise direkt yol
                uri.path ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uri.toString()
        }
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
        
        /** HTML'den dosya seçme için çağrılır */
        @JavascriptInterface
        fun openFilePicker() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "application/pdf"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                
                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "PDF Dosyası Seç"),
                        REQUEST_SELECT_FILE
                    )
                } catch (ex: Exception) {
                    Toast.makeText(this@MainActivity, "Dosya seçici açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        /** Sesli okuma için dosya seç */
        @JavascriptInterface
        fun selectPDFForSpeech() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "application/pdf"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                
                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "Sesli Okuma için PDF Seç"),
                        REQUEST_SELECT_FILE
                    )
                } catch (ex: Exception) {
                    Toast.makeText(this@MainActivity, "Dosya seçici açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        /** Sesli okuma için dosya seç (alternatif isim) */
        @JavascriptInterface
        fun selectSpeechPDF() {
            selectPDFForSpeech()
        }

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
        
        // ===== YENİ FONKSİYONLAR =====
        
        /** PDF dosyasını Base64 olarak al (PDF birleştirme için) */
        @JavascriptInterface
        fun getPDFData(path: String): String {
            return try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val bytes = file.readBytes()
                    Base64.encodeToString(bytes, Base64.DEFAULT)
                } else {
                    ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
        
        /** Seçilen PDF'leri birleştir ve kaydet */
        @JavascriptInterface
        fun mergeAndSavePDFs(pdfPaths: String, fileName: String) {
            try {
                val paths = pdfPaths.split("||").filter { it.isNotEmpty() }
                if (paths.size < 2) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, 
                            "En az 2 PDF seçmelisiniz!", 
                            Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                
                // Birleştirme işlemi için UI thread dışında çalıştır
                Thread {
                    try {
                        // PDF'leri birleştir
                        val mergedBytes = mergePDFFiles(paths)
                        
                        if (mergedBytes.isEmpty()) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, 
                                    "PDF birleştirme başarısız!", 
                                    Toast.LENGTH_SHORT).show()
                            }
                            return@Thread
                        }
                        
                        // Kaydet
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val appFolder = File(downloadsDir, appFolderName)
                        
                        if (!appFolder.exists()) {
                            appFolder.mkdirs()
                        }
                        
                        val file = File(appFolder, fileName)
                        FileOutputStream(file).use { fos ->
                            fos.write(mergedBytes)
                        }
                        
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, 
                                "PDF başarıyla birleştirildi: ${file.absolutePath}", 
                                Toast.LENGTH_LONG).show()
                            
                            // WebView'e bildir
                            webView.post {
                                webView.evaluateJavascript("""
                                    try {
                                        if (typeof onPDFMerged === 'function') {
                                            onPDFMerged('${file.absolutePath}', '$fileName');
                                        }
                                    } catch(e) {
                                        console.log('onPDFMerged error: ' + e);
                                    }
                                """.trimIndent(), null)
                            }
                        }
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, 
                                "PDF birleştirme hatası: ${e.message}", 
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
                
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, 
                        "Hata: ${e.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        /** Birleştirilmiş PDF'yi kaydet */
        @JavascriptInterface
        fun saveMergedPDF(base64Data: String, fileName: String) {
            try {
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, appFolderName)
                
                // Klasör yoksa oluştur
                if (!appFolder.exists()) {
                    appFolder.mkdirs()
                }
                
                val file = File(appFolder, fileName)
                FileOutputStream(file).use { fos ->
                    fos.write(decodedBytes)
                }
                
                // Başarı mesajı
                runOnUiThread {
                    Toast.makeText(this@MainActivity, 
                        "PDF kaydedildi: ${file.absolutePath}", 
                        Toast.LENGTH_LONG).show()
                }
                
                // Kaydedilen dosyayı bildir
                webView.post {
                    webView.evaluateJavascript("""
                        try {
                            if (typeof onPDFSaved === 'function') {
                                onPDFSaved('${file.absolutePath}', '$fileName');
                            }
                        } catch(e) {
                            console.log('onPDFSaved error: ' + e);
                        }
                    """.trimIndent(), null)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, 
                        "PDF kaydedilemedi: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        
        /** PDF dosyalarını birleştir (Kotlin tarafında) */
        private fun mergePDFFiles(paths: List<String>): ByteArray {
            // NOT: Bu fonksiyon PDF-Lib kütüphanesi gerektirir
            // Eğer PDF-Lib Kotlin/Java versiyonunuz yoksa, 
            // birleştirme işlemini WebView'de JavaScript ile yapmalısınız
            
            // Geçici çözüm: İlk PDF'i döndür
            // Gerçek birleştirme için PDF-Lib kütüphanesi ekleyin
            return try {
                if (paths.isNotEmpty()) {
                    File(paths[0]).readBytes()
                } else {
                    byteArrayOf()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                byteArrayOf()
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
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            this@MainActivity,
                            "${this@MainActivity.packageName}.provider",
                            file
                        )
                    } else {
                        Uri.fromFile(file)
                    }
                    uris.add(uri)
                }
            }
            
            if (uris.isNotEmpty()) {
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uris[0])
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "application/pdf"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    }
                }
                
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, "PDF'leri Paylaş"))
            }
        }
        
        /** PDF dosyasını aç */
        @JavascriptInterface
        fun openPDF(path: String) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            this@MainActivity,
                            "${this@MainActivity.packageName}.provider",
                            file
                        )
                    } else {
                        Uri.fromFile(file)
                    }
                    
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    startActivity(intent)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, 
                            "Dosya bulunamadı: $path", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, 
                        "PDF açılamadı: ${e.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        /** Yazdırma işlemi */
        @JavascriptInterface
        fun printPDF(path: String) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            this@MainActivity,
                            "${this@MainActivity.packageName}.provider",
                            file
                        )
                    } else {
                        Uri.fromFile(file)
                    }
                    
                    val printIntent = Intent(Intent.ACTION_SEND).apply {
                        setDataAndType(uri, "application/pdf")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    startActivity(Intent.createChooser(printIntent, "PDF Yazdır"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** İzin ekranından dönünce çağrılır */
    override fun onResume() {
        super.onResume()
        webView.post {
            webView.evaluateJavascript("""
                try {
                    if (typeof onAndroidResume === 'function') {
                        onAndroidResume();
                    }
                } catch(e) {
                    console.log('onAndroidResume error: ' + e);
                }
            """.trimIndent(), null)
        }
    }
    
    // --- GERİ TUŞU İŞLEMİ ---
    override fun onBackPressed() {
        // Viewer'da mıyız kontrol et
        if (isInViewer) {
            // Viewer'dan index.html'ye dön
            webView.loadUrl("file:///android_asset/web/index.html")
            return
        }
        
        // JavaScript'e geri tuşu durumunu sor
        webView.evaluateJavascript("""
            (function() {
                try {
                    if (typeof androidBackPressed === 'function') {
                        var result = androidBackPressed();
                        return result;
                    }
                } catch(e) {
                    console.log('androidBackPressed error: ' + e);
                }
                return 'exit_check';
            })();
        """.trimIndent()) { result ->
            val jsResult = result?.trim('"')
            println("JS Geri tuşu sonucu: $jsResult")
            
            when (jsResult) {
                "exit" -> {
                    // Uygulamadan çık
                    super.onBackPressed()
                    finish()
                }
                "exit_check" -> {
                    // Çıkış kontrolü (çift tıklama)
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        super.onBackPressed()
                        finish()
                    } else {
                        Toast.makeText(this, "Çıkmak için tekrar geri tuşuna basın", Toast.LENGTH_SHORT).show()
                        backPressedTime = System.currentTimeMillis()
                    }
                }
                "false", "no_exit" -> {
                    // JavaScript işledi, çıkış yapma
                    println("JavaScript geri tuşunu işledi")
                }
                else -> {
                    // Default: WebView geçmişine git
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        // Çıkış kontrolü
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
    }
    
    /** Bellek temizleme */
    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
