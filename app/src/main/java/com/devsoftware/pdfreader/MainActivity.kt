package com.devsoftware.pdfreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
    
    // Tools data
    private val toolsList = ArrayList<ToolItem>()
    
    // Files menu data
    private val filesMenuList = ArrayList<FileSourceItem>()
    
    // Permissions
    private val STORAGE_PERMISSION_CODE = 100
    private val REQUEST_MANAGE_ALL_FILES = 101
    private val REQUEST_PICK_PDF = 102
    
    // State
    private var currentTab = "recent"
    private var currentScreen = "home" // home, tools, files
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupToolbar()
        setupTabs()
        setupAdapters()
        setupBottomNavigation()
        setupFab()
        loadToolsData()
        loadFilesMenuData()
        checkPermissions()
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
        
        // Search button in toolbar
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search -> {
                    showSearchDialog()
                    true
                }
                R.id.action_menu -> {
                    showDrawerMenu()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Son").setIcon(R.drawable.ic_history))
        tabLayout.addTab(tabLayout.newTab().setText("Cihaz").setIcon(R.drawable.ic_phone))
        tabLayout.addTab(tabLayout.newTab().setText("Favori").setIcon(R.drawable.ic_star))
        
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
        recentAdapter = PdfAdapter(this, recentFiles) { pdfFile ->
            openPdf(pdfFile)
        }
        deviceAdapter = PdfAdapter(this, deviceFiles) { pdfFile ->
            openPdf(pdfFile)
        }
        favoritesAdapter = PdfAdapter(this, favoriteFiles) { pdfFile ->
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
    
    private fun loadToolsData() {
        toolsList.apply {
            add(ToolItem("PDF Birleştirme", R.drawable.ic_merge, Color.RED))
            add(ToolItem("Sesli Okuma", R.drawable.ic_volume, Color.GREEN))
            add(ToolItem("OCR Metin Çıkar", R.drawable.ic_text, Color.BLUE))
            add(ToolItem("PDF İmzalama", R.drawable.ic_draw, Color.PURPLE))
            add(ToolItem("PDF Sıkıştırma", R.drawable.ic_compress, Color.ORANGE))
            add(ToolItem("Sayfa Organize", R.drawable.ic_reorder, Color.BROWN))
            add(ToolItem("Resimden PDF", R.drawable.ic_image, Color.CYAN))
            add(ToolItem("PDF'den Resim", R.drawable.ic_picture_pdf, Color.GRAY))
        }
        
        val adapter = object : ArrayAdapter<ToolItem>(this, R.layout.item_tool, toolsList) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_tool, parent, false)
                val tool = getItem(position)
                
                view.findViewById<ImageView>(R.id.toolIcon).setImageResource(tool.icon)
                view.findViewById<TextView>(R.id.toolTitle).text = tool.title
                
                view.setOnClickListener {
                    when (position) {
                        0 -> startActivity(Intent(this@MainActivity, BirlestirmeActivity::class.java))
                        1 -> startActivity(Intent(this@MainActivity, SesliOkumaActivity::class.java))
                        2 -> startActivity(Intent(this@MainActivity, OrcActivity::class.java))
                        3 -> startActivity(Intent(this@MainActivity, ImzaActivity::class.java))
                        4 -> startActivity(Intent(this@MainActivity, SikistirmaActivity::class.java))
                        5 -> startActivity(Intent(this@MainActivity, OrganizeActivity::class.java))
                        6 -> startActivity(Intent(this@MainActivity, ResimdenPdfActivity::class.java))
                        7 -> startActivity(Intent(this@MainActivity, PdfResmeActivity::class.java))
                    }
                }
                
                return view
            }
        }
        
        toolsGrid.adapter = adapter
    }
    
    private fun loadFilesMenuData() {
        filesMenuList.apply {
            add(FileSourceItem("Bu aygıtta", R.drawable.ic_phone, "device"))
            add(FileSourceItem("Google Drive", R.drawable.ic_gdrive, "gdrive"))
            add(FileSourceItem("OneDrive", R.drawable.ic_onedrive, "onedrive"))
            add(FileSourceItem("Dropbox", R.drawable.ic_dropbox, "dropbox"))
            add(FileSourceItem("E-postalardaki PDF'ler", R.drawable.ic_gmail, "gmail"))
            add(FileSourceItem("Daha fazla dosyaya göz atın", R.drawable.ic_folder, "browse_more"))
        }
        
        val adapter = object : ArrayAdapter<FileSourceItem>(this, R.layout.item_file_source, filesMenuList) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_file_source, parent, false)
                val source = getItem(position)
                
                view.findViewById<ImageView>(R.id.sourceIcon).setImageResource(source.icon)
                view.findViewById<TextView>(R.id.sourceTitle).text = source.title
                
                view.setOnClickListener {
                    when (source.id) {
                        "device" -> {
                            showScreen("home")
                            tabLayout.getTabAt(1)?.select()
                        }
                        "browse_more" -> {
                            openFileManager()
                        }
                        else -> {
                            Toast.makeText(
                                this@MainActivity,
                                "${source.title} bağlantısı kuruluyor...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                
                return view
            }
        }
        
        filesList.adapter = adapter
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
                    Toast.makeText(this, "Tarama hatası: ${e.message}", Toast.LENGTH_SHORT).show()
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
                // Alt dizinleri tara (bazı sınırlamalarla)
                if (!file.name.startsWith(".") && file.list()?.size ?: 0 < 100) {
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
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(Locale.getDefault(), size, units[unitIndex])
    }
    
    private fun showTab(tabName: String) {
        currentTab = tabName
        
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
        currentScreen = screenName
        
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
            }
            "files" -> {
                findViewById<View>(R.id.homeContainer).visibility = View.GONE
                findViewById<View>(R.id.toolsContainer).visibility = View.GONE
                findViewById<View>(R.id.filesContainer).visibility = View.VISIBLE
                fab.visibility = View.GONE
                tabLayout.visibility = View.GONE
            }
        }
    }
    
    private fun openPdf(pdfFile: PdfFile) {
        // PDF görüntüleyiciye git
        val intent = Intent(this, PdfViewerActivity::class.java)
        intent.putExtra("pdf_path", pdfFile.path)
        intent.putExtra("pdf_name", pdfFile.name)
        startActivity(intent)
    }
    
    private fun showFabMenu() {
        val view = layoutInflater.inflate(R.layout.menu_fab, null)
        
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        
        view.findViewById<LinearLayout>(R.id.optionImport).setOnClickListener {
            // PDF yükle
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/pdf"
            startActivityForResult(Intent.createChooser(intent, "PDF Seçin"), REQUEST_PICK_PDF)
            dialog.dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.optionOcr).setOnClickListener {
            // OCR aktivitesi
            startActivity(Intent(this, OrcActivity::class.java))
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showSearchDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_search, null)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val searchResults = view.findViewById<RecyclerView>(R.id.searchResults)
        
        val adapter = PdfAdapter(this, ArrayList()) { pdfFile ->
            openPdf(pdfFile)
        }
        
        searchResults.layoutManager = LinearLayoutManager(this)
        searchResults.adapter = adapter
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("PDF Ara")
            .setView(view)
            .setPositiveButton("Kapat", null)
            .create()
        
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().trim().lowercase(Locale.getDefault())
                val allFiles = recentFiles + deviceFiles + favoriteFiles
                
                val results = if (query.isNotEmpty()) {
                    allFiles.filter {
                        it.name.lowercase(Locale.getDefault()).contains(query) ||
                        it.date.contains(query)
                    }
                } else {
                    emptyList()
                }
                
                adapter.updateList(ArrayList(results))
            }
        })
        
        dialog.show()
    }
    
    private fun showDrawerMenu() {
        val view = layoutInflater.inflate(R.layout.drawer_menu, null)
        
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        
        // Drawer menu item'larına tıklama işlemleri
        view.findViewById<LinearLayout>(R.id.menuAbout).setOnClickListener {
            showAboutDialog()
            dialog.dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.menuSettings).setOnClickListener {
            showSettingsDialog()
            dialog.dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.menuPrivacy).setOnClickListener {
            showPrivacyDialog()
            dialog.dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.menuHelp).setOnClickListener {
            showHelpDialog()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun openFileManager() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        startActivity(Intent.createChooser(intent, "Dosya Seçin"))
    }
    
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hakkında")
            .setMessage("PDF Reader by Dev Software\n\nVersion 1.0\n\nTüm hakları saklıdır © 2025")
            .setPositiveButton("Tamam", null)
            .show()
    }
    
    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        AlertDialog.Builder(this)
            .setTitle("Ayarlar")
            .setView(view)
            .setPositiveButton("Kaydet", null)
            .setNegativeButton("İptal", null)
            .show()
    }
    
    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Gizlilik Politikası")
            .setMessage("Verileriniz sadece cihazınızda saklanır. Hiçbir veri sunucularımıza gönderilmez.")
            .setPositiveButton("Tamam", null)
            .show()
    }
    
    private fun showHelpDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_help, null)
        
        AlertDialog.Builder(this)
            .setTitle("Yardım")
            .setView(view)
            .setPositiveButton("Gönder") { _, _ ->
                Toast.makeText(this, "Destek talebiniz alındı", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
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
                        // Seçilen PDF'i işle
                        Toast.makeText(this, "PDF seçildi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (currentTab == "device") {
            checkPermissions()
        }
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

data class ToolItem(
    val title: String,
    val icon: Int,
    val color: Int
)

data class FileSourceItem(
    val title: String,
    val icon: Int,
    val id: String
)

// Color constants
object Color {
    const val RED = 0xFFE53935
    const val GREEN = 0xFF4CAF50
    const val BLUE = 0xFF2196F3
    const val PURPLE = 0xFF9C27B0
    const val ORANGE = 0xFFFF9800
    const val BROWN = 0xFF795548
    const val CYAN = 0xFF00BCD4
    const val GRAY = 0xFF607D8B
}
