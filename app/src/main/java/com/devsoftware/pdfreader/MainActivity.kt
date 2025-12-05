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
    private var backPressedTime: Long = 0

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
                
                // Viewer.html'den geri dönüş kontrolü
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
            
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // JavaScript'e geri tuşu kontrolü için fonksiyon ekle
                injectBackButtonHandler()
            }
        }

        webView.loadUrl("file:///android_asset/web/index.html")
    }
    
    // JavaScript'e geri tuşu kontrolü ekle
    private fun injectBackButtonHandler() {
        webView.evaluateJavascript("""
            // Android geri tuşu kontrolü için global değişken
            window.androidBackPressed = function() {
                // Mevcut sayfanın viewer.html olup olmadığını kontrol et
                if (window.location.href.includes('viewer.html')) {
                    // Viewer'dan index.html'ye dön
                    window.location.href = 'file:///android_asset/web/index.html';
                    return true;
                }
                
                // Eğer selection modundaysa çık
                if (typeof isSelectionMode !== 'undefined' && isSelectionMode) {
                    if (typeof exitSelectionMode === 'function') {
                        exitSelectionMode();
                    }
                    return false;
                }
                
                // Eğer arama modundaysa çık
                if (document.body.classList.contains('is-searching')) {
                    const closeBtn = document.getElementById('closeSearchBtn');
                    if (closeBtn) closeBtn.click();
                    return false;
                }
                
                // Eğer drawer açıksa kapat
                const drawer = document.getElementById('drawer');
                if (drawer && drawer.classList.contains('open')) {
                    const overlay = document.getElementById('drawerOverlay');
                    if (overlay) overlay.click();
                    return false;
                }
                
                // Eğer context menu açıksa kapat
                const contextSheet = document.getElementById('contextSheet');
                if (contextSheet && contextSheet.classList.contains('show')) {
                    const contextOverlay = document.getElementById('contextOverlay');
                    if (contextOverlay) contextOverlay.click();
                    return false;
                }
                
                // Eğer FAB menu açıksa kapat
                const fabMenu = document.getElementById('fabMenu');
                if (fabMenu && fabMenu.classList.contains('show')) {
                    fabMenu.classList.remove('show');
                    return false;
                }
                
                // Ana sayfada değilsek ana sayfaya dön
                if (typeof currentNav !== 'undefined' && currentNav !== 'home') {
                    if (typeof switchNav === 'function') {
                        switchNav('home');
                    }
                    return false;
                }
                
                // Ana sayfadaysak ve viewer.html'de değilsek çıkış kontrolü
                return 'exit_check';
            };
            
            // Viewer'dan dönüldüğünde çağrılacak fonksiyon
            window.onReturnFromViewer = function() {
                // Viewer'dan döndük, ana sayfaya geç
                if (typeof currentNav !== 'undefined') {
                    currentNav = 'home';
                }
                
                // Nav'ı güncelle
                const navItems = document.querySelectorAll('.nav-item');
                navItems.forEach(nav => nav.classList.remove('active'));
                const homeNav = document.querySelector('.nav-item[data-nav="home"]');
                if (homeNav) homeNav.classList.add('active');
                
                // Paneli güncelle
                const panels = document.querySelectorAll('.content-panel');
                panels.forEach(panel => panel.classList.remove('active'));
                const homePanel = document.getElementById('recentPanel');
                if (homePanel) homePanel.classList.add('active');
                
                // Tabs'ı güncelle
                const tabs = document.querySelectorAll('.tab');
                tabs.forEach(tab => tab.classList.remove('active'));
                const recentTab = document.querySelector('.tab[data-tab="recent"]');
                if (recentTab) recentTab.classList.add('active');
                
                // Top bar'ı sıfırla
                const topBar = document.getElementById('topBar');
                if (topBar) {
                    topBar.style.transform = 'translateY(0)';
                    topBar.classList.remove('tabs-hidden');
                }
                
                // Main content padding'i ayarla
                const mainContent = document.getElementById('mainContent');
                if (mainContent) {
                    mainContent.style.paddingTop = 'var(--top-bar-total)';
                }
                
                // FAB'ı göster
                const fabButton = document.getElementById('fabButton');
                if (fabButton) {
                    fabButton.style.display = 'flex';
                }
                
                console.log('Returned from viewer');
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

        /** PDF Tarama */
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

            // Tarama işlemi
            roots.forEach { root ->
                if (root.exists() && root.canRead()) {
                    scanForPDFs(root, pdfList, 0)
                }
            }

            return if (pdfList.isEmpty()) "" else pdfList.joinToString("||")
        }

        /** Rekürsif PDF tarama */
        private fun scanForPDFs(dir: File, output: MutableList<String>, depth: Int) {
            if (depth > 10) return
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
                e.printStackTrace()
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
        
        /** Geri tuşu için: Viewer'da mı kontrolü */
        @JavascriptInterface
        fun isInViewer(): Boolean {
            return webView.url?.contains("viewer.html") == true
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
        // JavaScript ile geri tuşu işlemini kontrol et
        webView.evaluateJavascript("""
            if (typeof androidBackPressed === 'function') {
                androidBackPressed();
            } else {
                'exit_check';
            }
        """.trimIndent()) { result ->
            val jsResult = result?.trim('"')
            
            if (jsResult == "exit_check") {
                // Ana sayfadaysa çift tıklama ile çıkış
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    super.onBackPressed()
                    finish()
                } else {
                    Toast.makeText(this, "Çıkmak için tekrar geri tuşuna basın", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
            // Diğer durumlarda JavaScript zaten işlemi yaptı, bir şey yapma
        }
    }
}
