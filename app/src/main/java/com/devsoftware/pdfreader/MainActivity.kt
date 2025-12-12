package com.devsoftware.pdfreader

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var recentRecyclerView: RecyclerView
    private lateinit var deviceRecyclerView: RecyclerView
    private lateinit var favoritesRecyclerView: RecyclerView
    private lateinit var permissionContainer: LinearLayout
    private lateinit var deviceListContainer: LinearLayout
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fab: FloatingActionButton
    private lateinit var toolsGrid: GridView
    private lateinit var filesList: ListView
    
    // Adapters
    private lateinit var recentAdapter: PdfAdapter
    private lateinit var deviceAdapter: PdfAdapter
    private lateinit var favoritesAdapter: PdfAdapter
    
    // Data
    private val recentFiles = ArrayList<PdfFile>()
    private val deviceFiles = ArrayList<PdfFile>()
    private val favoriteFiles = ArrayList<PdfFile>()
    
    // Permissions
    private val STORAGE_PERMISSION_CODE = 100
    private val REQUEST_MANAGE_ALL_FILES = 101
    private val REQUEST_PICK_PDF = 102
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupToolbar()
        setupTabs()
        setupAdapters()
        setupBottomNavigation()
        setupFab()
        checkPermissions()
        
        // BUTON CLICK EKLENDİ
        val btnPermission = findViewById<Button>(R.id.btnPermission)
        btnPermission.setOnClickListener {
            openAllFilesSettings()
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tabLayout = findViewById(R.id.tabLayout)
        recentRecyclerView = findViewById(R.id.recentRecyclerView)
        deviceRecyclerView = findViewById(R.id.deviceRecyclerView)
        favoritesRecyclerView = findViewById(R.id.favoritesRecyclerView)
        permissionContainer = findViewById(R.id.permissionContainer)
        deviceListContainer = findViewById(R.id.deviceListContainer)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        bottomNav = findViewById(R.id.bottomNav)
        fab = findViewById(R.id.fab)
        toolsGrid = findViewById(R.id.toolsGrid)
        filesList = findViewById(R.id.filesList)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "PDF Reader"
    }
    
    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Son"))
        tabLayout.addTab(tabLayout.newTab().setText("Cihaz"))
        tabLayout.addTab(tabLayout.newTab().setText("Favori"))
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showTab("recent")
                    1 -> showTab("device")
                    2 -> showTab("favorites")
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun setupAdapters() {
        recentAdapter = PdfAdapter(recentFiles) { pdfFile ->
            openPdf(pdfFile)
        }
        deviceAdapter = PdfAdapter(deviceFiles) { pdfFile ->
            openPdf(pdfFile)
        }
        favoritesAdapter = PdfAdapter(favoriteFiles) { pdfFile ->
            openPdf(pdfFile)
        }
        
        recentRecyclerView.layoutManager = LinearLayoutManager(this)
        recentRecyclerView.adapter = recentAdapter
        
        deviceRecyclerView.layoutManager = LinearLayoutManager(this)
        deviceRecyclerView.adapter = deviceAdapter
        
        favoritesRecyclerView.layoutManager = LinearLayoutManager(this)
        favoritesRecyclerView.adapter = favoritesAdapter
    }
    
    private fun setupBottomNavigation() {
        bottomNav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showScreen("home")
                    true
                }
                R.id.nav_tools -> {
                    showScreen("tools")
                    true
                }
                R.id.nav_files -> {
                    showScreen("files")
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupFab() {
        fab.setOnClickListener {
            showFabMenu()
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            if (Environment.isExternalStorageManager()) {
                permissionGranted()
            } else {
                showPermissionDialog()
            }
        } else {
            // Android 10-
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                permissionGranted()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            }
        }
    }
    
    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Dosya Erişim İzni")
            .setMessage("PDF dosyalarınıza erişebilmemiz için \"Tüm Dosyalara Erişim\" iznini vermeniz gerekiyor.")
            .setPositiveButton("Ayarlara Git") { _, _ ->
                openAllFilesSettings()
            }
            .setNegativeButton("İptal", null)
            .show()
    }
    
    private fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES)
            }
        }
    }
    
    private fun permissionGranted() {
        permissionContainer.visibility = View.GONE
        deviceListContainer.visibility = View.VISIBLE
        loadingIndicator.visibility = View.VISIBLE
        
        // Tarama başlat
        scanForPDFs()
    }
    
    private fun scanForPDFs() {
        // Arka planda tarama yap
        Thread {
            try {
                val pdfs = findPDFs(Environment.getExternalStorageDirectory())
                
                runOnUiThread {
                    loadingIndicator.visibility = View.GONE
                    
                    deviceFiles.clear()
                    deviceFiles.addAll(pdfs)
                    deviceAdapter.notifyDataSetChanged()
                    
                    // Son açılanlar için örnek veri
                    if (recentFiles.isEmpty() && pdfs.isNotEmpty()) {
                        recentFiles.addAll(pdfs.take(5))
                        recentAdapter.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingIndicator.visibility = View.GONE
                    Toast.makeText(this, "Tarama hatası", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun findPDFs(directory: File): List<PdfFile> {
        val pdfList = ArrayList<PdfFile>()
        
        if (!directory.exists() || !directory.isDirectory) return pdfList
        
        val files = directory.listFiles() ?: return pdfList
        
        for (file in files) {
            if (file.isDirectory) {
                // Alt dizinleri tara (sınırlı sayıda)
                if (!file.name.startsWith(".") && file.list()?.size ?: 0 < 50) {
                    pdfList.addAll(findPDFs(file))
                }
            } else if (file.isFile && file.name.endsWith(".pdf", true)) {
                val pdfFile = PdfFile(
                    id = file.absolutePath.hashCode(),
                    name = file.name,
                    path = file.absolutePath,
                    size = formatSize(file.length()),
                    date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        .format(Date(file.lastModified())),
                    isFavorite = false
                )
                pdfList.add(pdfFile)
            }
        }
        
        return pdfList
    }
    
    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }
    
    private fun showTab(tabName: String) {
        when (tabName) {
            "recent" -> {
                recentRecyclerView.visibility = View.VISIBLE
                deviceRecyclerView.visibility = View.GONE
                favoritesRecyclerView.visibility = View.GONE
            }
            "device" -> {
                recentRecyclerView.visibility = View.GONE
                deviceRecyclerView.visibility = View.VISIBLE
                favoritesRecyclerView.visibility = View.GONE
            }
            "favorites" -> {
                recentRecyclerView.visibility = View.GONE
                deviceRecyclerView.visibility = View.GONE
                favoritesRecyclerView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun showScreen(screenName: String) {
        when (screenName) {
            "home" -> {
                findViewById<View>(R.id.homeContainer).visibility = View.VISIBLE
                findViewById<View>(R.id.toolsContainer).visibility = View.GONE
                findViewById<View>(R.id.filesContainer).visibility = View.GONE
                fab.visibility = View.VISIBLE
                tabLayout.visibility = View.VISIBLE
            }
            "tools" -> {
                findViewById<View>(R.id.homeContainer).visibility = View.GONE
                findViewById<View>(R.id.toolsContainer).visibility = View.VISIBLE
                findViewById<View>(R.id.filesContainer).visibility = View.GONE
                fab.visibility = View.GONE
                tabLayout.visibility = View.GONE
                showToolsList()
            }
            "files" -> {
                findViewById<View>(R.id.homeContainer).visibility = View.GONE
                findViewById<View>(R.id.toolsContainer).visibility = View.GONE
                findViewById<View>(R.id.filesContainer).visibility = View.VISIBLE
                fab.visibility = View.GONE
                tabLayout.visibility = View.GONE
                showFilesList()
            }
        }
    }
    
    private fun showToolsList() {
        val tools = listOf(
            "PDF Birleştirme" to BirlestirmeActivity::class.java,
            "Sesli Okuma" to SesliOkumaActivity::class.java,
            "OCR Metin Çıkar" to OrcActivity::class.java,
            "PDF İmzalama" to ImzaActivity::class.java,
            "PDF Sıkıştırma" to SikistirmaActivity::class.java,
            "Sayfa Organize" to OrganizeActivity::class.java,
            "Resimden PDF" to ResimdenPdfActivity::class.java,
            "PDF'den Resim" to PdfResmeActivity::class.java
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tools.map { it.first })
        toolsGrid.adapter = adapter
        
        toolsGrid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, tools[position].second)
            startActivity(intent)
        }
    }
    
    private fun showFilesList() {
        val files = listOf(
            "Bu aygıtta",
            "Google Drive", 
            "OneDrive",
            "Dropbox",
            "E-postalardaki PDF'ler",
            "Daha fazla dosyaya göz atın"
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, files)
        filesList.adapter = adapter
        
        filesList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> {
                    showScreen("home")
                    tabLayout.getTabAt(1)?.select()
                }
                5 -> openFileManager()
                else -> Toast.makeText(this, "${files[position]} bağlantısı kuruluyor...", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // PDF'yi viewer.html ile aç
    @SuppressLint("SetJavaScriptEnabled")
    private fun openPdf(pdfFile: PdfFile) {
        // WebView oluştur ve viewer.html'i yükle
        val webView = WebView(this)
        setContentView(webView)
        
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // PDF yüklendi
                supportActionBar?.title = pdfFile.name
            }
        }
        
        // viewer.html'e PDF yolunu gönder
        val encodedPath = Uri.encode(pdfFile.path)
        val url = "file:///android_asset/web/viewer.html?file=$encodedPath&name=${Uri.encode(pdfFile.name)}"
        
        webView.loadUrl(url)
    }
    
    private fun showFabMenu() {
        val options = arrayOf("PDF Yükle", "OCR Metin Çıkar")
        
        AlertDialog.Builder(this)
            .setTitle("Seçenekler")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // PDF yükle
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.type = "application/pdf"
                        startActivityForResult(Intent.createChooser(intent, "PDF Seçin"), REQUEST_PICK_PDF)
                    }
                    1 -> {
                        // OCR aktivitesi
                        startActivity(Intent(this, OrcActivity::class.java))
                    }
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }
    
    private fun openFileManager() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        startActivity(Intent.createChooser(intent, "Dosya Seçin"))
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionGranted()
            } else {
                Toast.makeText(this, "İzin verilmedi", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_MANAGE_ALL_FILES -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        permissionGranted()
                    }
                }
            }
            REQUEST_PICK_PDF -> {
                if (resultCode == RESULT_OK && data != null) {
                    val uri = data.data
                    uri?.let {
                        Toast.makeText(this, "PDF seçildi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ana layout'a geri dön
        setContentView(R.layout.activity_main)
        // View'leri yeniden başlat
        initViews()
        setupToolbar()
        setupTabs()
        setupAdapters()
        setupBottomNavigation()
        setupFab()
        
        // Sayfayı yenile
        if (tabLayout.selectedTabPosition == 1) {
            checkPermissions()
        }
    }
    
    override fun onBackPressed() {
        // Basit back işlemi
        super.onBackPressed()
    }
}

// Data Classes
data class PdfFile(
    val id: Int,
    val name: String,
    val path: String,
    val size: String,
    val date: String,
    var isFavorite: Boolean
)

// RecyclerView Adapter
class PdfAdapter(
    private var pdfFiles: List<PdfFile>,
    private val onItemClick: (PdfFile) -> Unit
) : RecyclerView.Adapter<PdfAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.fileName)
        val fileSize: TextView = view.findViewById(R.id.fileSize)
        val fileDate: TextView = view.findViewById(R.id.fileDate)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pdfFile = pdfFiles[position]
        
        holder.fileName.text = pdfFile.name
        holder.fileSize.text = pdfFile.size
        holder.fileDate.text = pdfFile.date
        
        holder.itemView.setOnClickListener {
            onItemClick(pdfFile)
        }
    }
    
    override fun getItemCount() = pdfFiles.size
    
    fun updateList(newList: List<PdfFile>) {
        pdfFiles = newList
        notifyDataSetChanged()
    }
}
