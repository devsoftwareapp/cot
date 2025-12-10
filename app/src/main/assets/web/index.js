// --- VARIABLES ---
const topBar = document.getElementById('topBar');
const mainContent = document.getElementById('mainContent');
const tabs = document.querySelectorAll('.tab');
const panels = document.querySelectorAll('.content-panel');
const navItems = document.querySelectorAll('.nav-item');
const fabButton = document.getElementById('fabButton');
const fabMenu = document.getElementById('fabMenu');
const drawer = document.getElementById('drawer');
const overlay = document.getElementById('drawerOverlay');
const drawerItems = document.querySelectorAll('.drawer-item[data-accordion]');
const settingsTabs = document.querySelectorAll('.setting-tab');
const themeOptions = document.querySelectorAll('.theme-option');
const pdfFileInput = document.getElementById('pdfFileInput');

// Search Variables
const searchBtn = document.getElementById('searchBtn');
const searchOverlay = document.getElementById('searchOverlay');
const searchInput = document.getElementById('searchInput');
const closeSearchBtn = document.getElementById('closeSearchBtn');
const searchResults = document.getElementById('searchResults');
const searchResultCount = document.getElementById('searchResultCount');

// Context Menu Variables
const contextOverlay = document.getElementById('contextOverlay');
const contextSheet = document.getElementById('contextSheet');
const contextTitle = document.getElementById('contextTitle');
const contextMeta = document.getElementById('contextMeta');
let currentContextFile = null;

// Carousel Variables
const collapsibleWrapper = document.getElementById('collapsibleWrapper');
const carousel = document.getElementById('categoryCarousel');
let isCarouselHidden = true; // Varsayılan olarak gizli
const HIDE_THRESHOLD = 50;
const SHOW_THRESHOLD = 10;

// Dialog Variables
const renameDialog = document.getElementById('renameDialog');
const newFileNameInput = document.getElementById('newFileNameInput');
const renameOldName = document.getElementById('renameOldName');

const addCategoryDialog = document.getElementById('addCategoryDialog');
const categorySelectList = document.getElementById('categorySelectList');

const createCategoryDialog = document.getElementById('createCategoryDialog');
const createCategoryNameInput = document.getElementById('createCategoryNameInput');

const categoryDeleteDialog = document.getElementById('categoryDeleteDialog');
const categoryDeleteList = document.getElementById('categoryDeleteList');

// Print Status Variables
const printStatus = document.getElementById('printStatus');
const printStatusTitle = document.getElementById('printStatusTitle');
const printStatusText = document.getElementById('printStatusText');
const printProgressBar = document.getElementById('printProgressBar');

// Selection Mode Variables
const selectionHeader = document.getElementById('selectionHeader');
const selectionCount = document.getElementById('selectionCount');
let isSelectionMode = false;
let selectedCards = new Set();

// Category Toggle Variables
const toggleCarouselIcon = document.getElementById('toggleCarouselIcon');
const toggleCarouselText = document.getElementById('toggleCarouselText');
const carouselToggle = document.getElementById('carouselToggle');

// Data Storage
let importedFiles = [];
let devicePDFs = [];
let recentFiles = [];
let favoriteFiles = [];

// Kategori yönetimi
let categories = [];
let fileCategories = {}; // Dosya ID -> Kategori ID eşlemesi

// Carousel kategorileri - Alfabetik sıralı
let carouselCategories = [];

// PDF Reader klasör yolu
const PDF_READER_FOLDER = '/storage/emulated/0/Download/PDF Reader';

// Bildirim ID sayacı
let notificationId = 0;

// --- MODERN BİLDİRİM SİSTEMİ ---
function showNotification(title, message, type = 'info', duration = 4000) {
    const notificationId = 'notification-' + Date.now();
    const notification = document.createElement('div');
    notification.id = notificationId;
    notification.className = `notification ${type}`;
    
    // Tip'e göre simge belirle
    let icon = 'info';
    let iconColor = '#2196F3';
    
    switch(type) {
        case 'success':
            icon = 'check_circle';
            iconColor = '#4CAF50';
            break;
        case 'error':
            icon = 'error';
            iconColor = '#f44336';
            break;
        case 'warning':
            icon = 'warning';
            iconColor = '#FF9800';
            break;
        case 'info':
        default:
            icon = 'info';
            iconColor = '#2196F3';
            break;
    }
    
    notification.innerHTML = `
        <div class="notification-icon" style="color: ${iconColor};">
            <span class="material-symbols-rounded">${icon}</span>
        </div>
        <div class="notification-content">
            <div class="notification-title">${title}</div>
            <div class="notification-message">${message}</div>
        </div>
        <button class="notification-close" onclick="closeNotification('${notificationId}')">
            <span class="material-symbols-rounded">close</span>
        </button>
    `;
    
    document.getElementById('notificationContainer').appendChild(notification);
    
    // Animasyon için timeout
    setTimeout(() => {
        notification.classList.add('show');
    }, 10);
    
    // Otomatik kapanma
    if (duration > 0) {
        setTimeout(() => {
            closeNotification(notificationId);
        }, duration);
    }
    
    return notificationId;
}

function closeNotification(id) {
    const notification = document.getElementById(id);
    if (notification) {
        notification.classList.remove('show');
        setTimeout(() => {
            if (notification.parentNode) {
                notification.parentNode.removeChild(notification);
            }
        }, 400);
    }
}

// --- VERİ YÖNETİMİ (CACHE) ---
function saveToCache() {
    try {
        // Kategorileri alfabetik sırala
        categories.sort((a, b) => a.name.localeCompare(b.name, 'tr'));
        
        const data = {
            importedFiles: importedFiles,
            devicePDFs: devicePDFs,
            categories: categories,
            fileCategories: fileCategories,
            isCarouselHidden: isCarouselHidden,
            theme: localStorage.getItem('pdfTheme') || 'light',
            timestamp: Date.now()
        };
        
        localStorage.setItem('pdfReaderData', JSON.stringify(data));
        console.log('Veriler cache\'e kaydedildi');
    } catch (e) {
        console.error('Cache kaydetme hatası:', e);
    }
}

function loadFromCache() {
    try {
        const cachedData = localStorage.getItem('pdfReaderData');
        if (cachedData) {
            const data = JSON.parse(cachedData);
            
            // 30 günden eski verileri temizle
            const THIRTY_DAYS = 30 * 24 * 60 * 60 * 1000;
            if (Date.now() - data.timestamp > THIRTY_DAYS) {
                console.log('Eski cache temizlendi');
                localStorage.removeItem('pdfReaderData');
                return false;
            }
            
            importedFiles = data.importedFiles || [];
            devicePDFs = data.devicePDFs || [];
            categories = data.categories || [];
            fileCategories = data.fileCategories || {};
            isCarouselHidden = data.isCarouselHidden !== undefined ? data.isCarouselHidden : true; // Varsayılan gizli
            
            // Temayı yükle
            if (data.theme) {
                localStorage.setItem('pdfTheme', data.theme);
            }
            
            // Kategorileri alfabetik sırala
            categories.sort((a, b) => a.name.localeCompare(b.name, 'tr'));
            
            console.log('Veriler cache\'den yüklendi:', data);
            return true;
        }
    } catch (e) {
        console.error('Cache yükleme hatası:', e);
        localStorage.removeItem('pdfReaderData');
    }
    return false;
}

// --- KARUSEL OLUŞTURMA ---
function initCarousel() {
    // Carousel kategorilerini oluştur: "Tümü" + alfabetik sıralı kategoriler
    carouselCategories = [
        { id: 'all', name: 'Tümü', icon: 'all_inclusive' }
    ];
    
    // Alfabetik sıralı kategoriler
    categories.sort((a, b) => a.name.localeCompare(b.name, 'tr')).forEach(cat => {
        carouselCategories.push({
            id: cat.id,
            name: cat.name,
            icon: cat.icon || 'folder'
        });
    });
    
    let html = '';
    carouselCategories.forEach((cat, i) => {
        const activeClass = i === 0 ? 'active' : '';
        html += `
            <div class="chip ${activeClass}" data-category="${cat.id}" onclick="selectCarouselCategory('${cat.id}')">
                <span class="material-symbols-rounded">${cat.icon}</span>
                ${cat.name}
            </div>
        `;
    });
    carousel.innerHTML = html;
    
    // Carousel görünürlüğünü ayarla
    updateCarouselVisibility();
}

function selectCarouselCategory(categoryId) {
    document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
    const chip = document.querySelector(`.chip[data-category="${categoryId}"]`);
    if (chip) {
        chip.classList.add('active');
    }
    
    // Kategori seçildiğinde kategori sekmesine geç ve filtrele
    switchTab('categories', true);
    filterByCategory(categoryId);
}

function filterByCategory(categoryId) {
    if (categoryId === 'all') {
        renderCategoriesList();
        return;
    }
    
    const allFiles = [...devicePDFs, ...importedFiles];
    const filteredFiles = allFiles.filter(file => {
        const fileCatId = fileCategories[file.id];
        return fileCatId === categoryId;
    });
    
    renderFilteredCategoriesList(filteredFiles, categoryId);
}

function renderFilteredCategoriesList(files, categoryId) {
    const categoriesList = document.getElementById('categoriesList');
    if (!categoriesList) return;
    
    const category = categories.find(c => c.id === categoryId);
    const categoryName = category ? category.name : categoryId;
    
    if (files.length === 0) {
        categoriesList.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon material-symbols-rounded">category</div>
                <p>"${categoryName}" kategorisinde dosya bulunamadı.</p>
            </div>
        `;
    } else {
        let html = `
            <div class="category-section">
                <h3 style="font-size: 16px; margin: 16px 0 8px 0; color: var(--text-primary);">
                    <span class="material-symbols-rounded" style="font-size: 20px; vertical-align: middle; margin-right: 8px;">${category?.icon || 'folder'}</span>
                    ${categoryName} (${files.length} dosya)
                </h3>
        `;
        
        files.forEach(file => {
            html += createCard(file.name, file.size, file.date, file.isFavorite, file.id, file.category);
        });
        
        html += `</div>`;
        categoriesList.innerHTML = html;
        attachLongPressEvents();
    }
}

// --- CAROUSEL GÖRÜNÜRLÜĞÜ ---
function toggleCarouselVisibility() {
    isCarouselHidden = !isCarouselHidden;
    updateCarouselVisibility();
    saveToCache();
    
    // Bildirim göster
    if (isCarouselHidden) {
        showNotification('Kategoriler Gizlendi', 'Kategori carousel artık görünmüyor.', 'info');
    } else {
        showNotification('Kategoriler Gösteriliyor', 'Kategori carousel şimdi görünür durumda.', 'success');
    }
}

function updateCarouselVisibility() {
    if (isCarouselHidden) {
        document.body.classList.add('carousel-hidden');
        toggleCarouselIcon.textContent = 'visibility_off';
        toggleCarouselText.textContent = 'Varsayılan: Kategori Gizle (kapalı göz)';
        carouselToggle.classList.remove('active');
    } else {
        document.body.classList.remove('carousel-hidden');
        toggleCarouselIcon.textContent = 'visibility';
        toggleCarouselText.textContent = 'Kategorileri Göster (açık göz)';
        carouselToggle.classList.add('active');
    }
}

// --- PDF READER KLASÖRÜ OLUŞTURMA ---
function createPDFReaderFolder() {
    try {
        if (typeof Android !== 'undefined' && Android.createFolder) {
            const result = Android.createFolder(PDF_READER_FOLDER);
            console.log('Klasör oluşturma sonucu:', result);
            if (result === 'SUCCESS') {
                console.log('PDF Reader klasörü oluşturuldu:', PDF_READER_FOLDER);
            } else if (result === 'EXISTS') {
                console.log('PDF Reader klasörü zaten var:', PDF_READER_FOLDER);
            }
        }
    } catch (e) {
        console.error('Klasör oluşturma hatası:', e);
    }
}

// --- ANDROID İZİN FONKSİYONLARI ---
function checkAndroidPermission() {
    try {
        if (typeof Android !== 'undefined' && Android.checkPermission) {
            return Android.checkPermission();
        }
        return false;
    } catch (e) {
        console.error('Permission check error:', e);
        return false;
    }
}

function openAndroidPermissionSettings() {
    try {
        if (typeof Android !== 'undefined' && Android.openAllFilesSettings) {
            Android.openAllFilesSettings();
        } else {
            showNotification('Bağlantı Hatası', 'Android bağlantısı kurulamadı. Lütfen uygulama ayarlarından izin verin.', 'error');
        }
    } catch (e) {
        console.error('Open settings error:', e);
        showNotification('Hata', 'Ayarlar açılamadı: ' + e.message, 'error');
    }
}

function updatePermissionUI(hasPermission) {
    const permissionContainer = document.getElementById('permissionContainer');
    const deviceList = document.getElementById('deviceList');
    const loadingIndicator = document.getElementById('loadingIndicator');
    
    if (!hasPermission) {
        if (permissionContainer) permissionContainer.style.display = 'flex';
        if (deviceList) deviceList.style.display = 'none';
        if (loadingIndicator) loadingIndicator.style.display = 'none';
    } else {
        if (permissionContainer) permissionContainer.style.display = 'none';
        if (deviceList) deviceList.style.display = 'grid';
        if (loadingIndicator) loadingIndicator.style.display = 'block';
        
        // İzin verildiğinde klasörü oluştur
        createPDFReaderFolder();
        scanDeviceForPDFs();
    }
}

// --- GERÇEK DOSYA TARAMA VE BOYUT ALMA ---
function scanDeviceForPDFs() {
    try {
        const loadingIndicator = document.getElementById('loadingIndicator');
        const deviceList = document.getElementById('deviceList');
        
        if (loadingIndicator) loadingIndicator.style.display = 'block';
        if (deviceList) deviceList.style.display = 'none';
        
        let pdfPaths = [];
        if (typeof Android !== 'undefined' && Android.listPDFs) {
            const result = Android.listPDFs();
            console.log("PDF List Result:", result);
            
            if (result === "PERMISSION_DENIED") {
                updatePermissionUI(false);
                return;
            }
            
            if (result && result.trim().length > 0 && result !== "EMPTY" && !result.startsWith("ERROR")) {
                pdfPaths = result.split('||').filter(p => p && p.trim().length > 0);
            }
        }
        
        // Cache'den yüklenen dosyalarla birleştir
        const cachedDevicePDFs = devicePDFs || [];
        const newDevicePDFs = [];
        let nextId = Math.max(1000, ...cachedDevicePDFs.map(f => f.id), 0) + 1;
        
        // Her dosya için gerçek boyutu al
        for (const path of pdfPaths) {
            const name = path.split('/').pop();
            
            // Cache'de var mı kontrol et
            const existingFile = cachedDevicePDFs.find(f => f.path === path);
            if (existingFile) {
                // Gerçek boyutu ve tarihi güncelle
                existingFile.size = getRealFileSize(path);
                existingFile.date = getRealFileDate(path);
                newDevicePDFs.push(existingFile);
            } else {
                // Yeni dosya ekle
                newDevicePDFs.push({
                    id: nextId++,
                    name: name,
                    size: getRealFileSize(path), // GERÇEK BOYUT
                    date: getRealFileDate(path), // GERÇEK TARİH
                    isFavorite: false,
                    path: path,
                    category: null,
                    lastOpened: null
                });
            }
        }
        
        devicePDFs = newDevicePDFs;
        
        if (loadingIndicator) loadingIndicator.style.display = 'none';
        if (deviceList) deviceList.style.display = 'grid';
        
        loadData();
        
    } catch (e) {
        console.error('Device scan error:', e);
        const loadingIndicator = document.getElementById('loadingIndicator');
        if (loadingIndicator) loadingIndicator.style.display = 'none';
        
        devicePDFs = devicePDFs || [];
        loadData();
        showNotification('Tarama Hatası', 'PDF tarama işlemi sırasında hata oluştu: ' + e.message, 'error');
    }
}

// GERÇEK DOSYA BOYUTU ALMA - Android API ile
function getRealFileSize(path) {
    try {
        if (typeof Android !== 'undefined' && Android.getFileSize) {
            const sizeBytes = parseInt(Android.getFileSize(path));
            if (!isNaN(sizeBytes) && sizeBytes > 0) {
                return formatBytes(sizeBytes, 2); // 2 ondalık basamak
            }
        }
        // API'den alınamazsa varsayılan
        return "1.0 MB";
    } catch (e) {
        console.error('File size error:', e);
        return "1.0 MB";
    }
}

// GERÇEK DOSYA TARİHİ ALMA - Android API ile
function getRealFileDate(path) {
    try {
        if (typeof Android !== 'undefined' && Android.getFileDate) {
            const dateStr = Android.getFileDate(path);
            if (dateStr && dateStr !== 'ERROR') {
                const date = new Date(dateStr);
                const day = String(date.getDate()).padStart(2, '0');
                const month = String(date.getMonth() + 1).padStart(2, '0');
                const year = date.getFullYear();
                return `${day}.${month}.${year}`;
            }
        }
        // API'den alınamazsa bugünün tarihi
        const now = new Date();
        const day = String(now.getDate()).padStart(2, '0');
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const year = now.getFullYear();
        return `${day}.${month}.${year}`;
    } catch (e) {
        console.error('File date error:', e);
        const now = new Date();
        const day = String(now.getDate()).padStart(2, '0');
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const year = now.getFullYear();
        return `${day}.${month}.${year}`;
    }
}

function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';
    
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

// Android Callbacks
function onPermissionGranted() {
    console.log("İzin verildi - onPermissionGranted çağrıldı");
    updatePermissionUI(true);
    showNotification('İzin Verildi', 'Dosya erişim izni başarıyla verildi.', 'success');
}

function onPermissionDenied() {
    console.log("İzin verilmedi - onPermissionDenied çağrıldı");
    updatePermissionUI(false);
    showNotification('İzin Verilmedi', 'Dosya erişim izni verilmedi. Ayarlardan izin verebilirsiniz.', 'warning');
}

function checkPermissionOnResume() {
    const hasPermission = checkAndroidPermission();
    console.log("Resume permission check:", hasPermission);
    updatePermissionUI(hasPermission);
}

function checkInitialPermission() {
    const hasPermission = checkAndroidPermission();
    console.log("Initial permission check:", hasPermission);
    updatePermissionUI(hasPermission);
}

// --- ARAÇLAR YÜKLEME ---
function loadTools() {
  const toolsGrid = document.getElementById('toolsGrid');
  if (!toolsGrid) return;
  
  let html = '';
  toolsData.forEach(tool => {
    html += `
      <div class="tool-card" onclick="openToolPage('${tool.page}')">
        <div class="tool-icon-wrapper" style="background: ${tool.color}20; color: ${tool.color};">
          <span class="material-symbols-rounded">${tool.iconCode}</span>
        </div>
        <div class="tool-title">${tool.title}</div>
      </div>
    `;
  });
  
  toolsGrid.innerHTML = html;
}

function openToolPage(page) {
  window.location.href = page;
}

function initTheme() {
  const saved = localStorage.getItem('pdfTheme');
  const systemPrefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  let themeToSet = 'light';
  if (saved === 'dark' || (saved === 'device' && systemPrefersDark)) {
    themeToSet = 'dark';
  } else if (saved === 'light') {
    themeToSet = 'light';
  } else if (saved === 'device' && !systemPrefersDark) {
    themeToSet = 'light';
  }
  document.documentElement.setAttribute('data-theme', themeToSet);
}

function setThemePreference(preference) {
    localStorage.setItem('pdfTheme', preference);
    themeOptions.forEach(opt => opt.classList.remove('active'));
    document.querySelector(`.theme-option[data-theme-val="${preference}"]`).classList.add('active');
    initTheme();
    saveToCache();
    loadData();
    
    // Bildirim göster
    let message = '';
    switch(preference) {
        case 'light': message = 'Açık tema uygulandı.'; break;
        case 'dark': message = 'Koyu tema uygulandı.'; break;
        case 'device': message = 'Cihaz teması uygulandı.'; break;
    }
    showNotification('Tema Değiştirildi', message, 'success');
}

function loadData() {
  // Tüm dosyaları birleştir
  const allFiles = [...devicePDFs, ...importedFiles];
  
  // Son kullanılanları sırala
  recentFiles = allFiles
    .filter(file => file.lastOpened)
    .sort((a, b) => new Date(b.lastOpened) - new Date(a.lastOpened))
    .slice(0, 20);
  
  // Favorileri filtrele
  favoriteFiles = allFiles.filter(file => file.isFavorite);
  
  // Listeleri render et
  renderList('recentList', recentFiles);
  renderList('deviceList', allFiles);
  renderList('favoritesList', favoriteFiles);
  renderCategoriesList();
  
  // Cache'e kaydet
  saveToCache();
}

function renderCategoriesList() {
    const categoriesList = document.getElementById('categoriesList');
    if (!categoriesList) return;
    
    // Kategoriye göre gruplandır
    const filesByCategory = {};
    const allFiles = [...devicePDFs, ...importedFiles];
    
    // Alfabetik sıralı kategoriler
    const sortedCategories = [...categories].sort((a, b) => a.name.localeCompare(b.name, 'tr'));
    
    // Her kategori için dosyaları filtrele
    sortedCategories.forEach(category => {
        filesByCategory[category.id] = allFiles.filter(file => 
            fileCategories[file.id] === category.id
        );
    });
    
    // Kategori olmayanlar için
    filesByCategory.uncategorized = allFiles.filter(file => 
        !fileCategories[file.id] || fileCategories[file.id] === 'uncategorized'
    );
    
    let html = '';
    let hasCategories = false;
    
    // Her kategori için bölüm oluştur
    sortedCategories.forEach(category => {
        const files = filesByCategory[category.id];
        if (files.length > 0) {
            hasCategories = true;
            html += `
                <div class="category-section">
                    <h3 style="font-size: 16px; margin: 16px 0 8px 0; color: var(--text-primary);">
                        <span class="material-symbols-rounded" style="font-size: 20px; vertical-align: middle; margin-right: 8px;">${category.icon}</span>
                        ${category.name} <span class="sort-badge">A-Z</span> (${files.length} dosya)
                    </h3>
            `;
            
            files.forEach(file => {
                html += createCard(file.name, file.size, file.date, file.isFavorite, file.id, file.category);
            });
            
            html += `</div>`;
        }
    });
    
    // Kategorize edilmemiş dosyalar
    const uncategorizedFiles = filesByCategory.uncategorized;
    if (uncategorizedFiles.length > 0) {
        hasCategories = true;
        html += `
            <div class="category-section">
                <h3 style="font-size: 16px; margin: 16px 0 8px 0; color: var(--text-primary);">
                    <span class="material-symbols-rounded" style="font-size: 20px; vertical-align: middle; margin-right: 8px;">folder</span>
                    Kategorize Edilmemiş (${uncategorizedFiles.length} dosya)
                </h3>
        `;
        
        uncategorizedFiles.forEach(file => {
            html += createCard(file.name, file.size, file.date, file.isFavorite, file.id, file.category);
        });
        
        html += `</div>`;
    }
    
    if (!hasCategories) {
        html = `
            <div class="empty-state">
                <div class="empty-icon material-symbols-rounded">category</div>
                <p>Henüz kategorilendirilmiş dosya yok.</p>
                <p class="text-sm mt-2">Dosyaları kategoriye eklemek için dosyaya uzun basın veya menüyü açın.</p>
            </div>
        `;
    }
    
    categoriesList.innerHTML = html;
    attachLongPressEvents();
}

function renderList(listId, files) {
    const list = document.getElementById(listId);
    if (files.length === 0) {
        let emptyText = 'Henüz dosya yok.';
        if (listId === 'recentList') emptyText = 'Henüz açılmış dosya yok.';
        if (listId === 'favoritesList') emptyText = 'Henüz favori dosya yok.';
        
        list.innerHTML = `
        <div class="empty-state">
            <div class="empty-icon material-symbols-rounded">folder_open</div>
            <p>${emptyText}</p>
        </div>`;
    } else {
        let html = '';
        files.forEach(file => {
             html += createCard(file.name, file.size, file.date, file.isFavorite, file.id, file.category);
        });
        list.innerHTML = html;
        attachLongPressEvents();
    }
}

function createCard(name, size, date, isFavorite, id, categoryId) {
  const MAX_LENGTH = 30;
  const truncatedName = name.length > MAX_LENGTH 
      ? name.substring(0, MAX_LENGTH) + '...' 
      : name;

  const theme = document.documentElement.getAttribute('data-theme');
  const bgColor = theme === 'dark' ? 'var(--pdf-color-bg)' : '#E53935';
  const textColor = theme === 'dark' ? 'var(--pdf-color-text)' : '#E53935';
  const fillStar = isFavorite ? 1 : 0;
  
  // Kategori bilgisini al
  const category = categories.find(c => c.id === categoryId);
  const categoryBadge = category ? `<div class="category-badge">${category.name}</div>` : '';

  const pdfIconSvg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 64 64">
      <rect x="0" y="0" width="64" height="64" rx="10" fill="${bgColor}"/>
      <path d="M18 12h22l10 10v30a4 4 0 0 1-4 4H18a4 4 0 0 1-4-4V16a4 4 0 0 1 4-4z"
            fill="#FFFFFF"/>
      <polygon points="40,12 50,22 40,22" fill="#F0F0F0"/>
      <rect x="22" y="24" width="18" height="3" rx="1.5" fill="${textColor}"/>
      <rect x="22" y="30" width="20" height="3" rx="1.5" fill="${textColor}"/>
      <rect x="22" y="36" width="20" height="3" rx="1.5" fill="${textColor}"/>
      <text x="50%" y="52" text-anchor="middle"
            font-family="Segoe UI, Roboto, Arial, sans-serif"
            font-weight="700" font-size="14" fill="${textColor}">
        PDF
      </text>
    </svg>
  `;

  return `
  <div class="card" 
       data-name="${name}" 
       data-size="${size}" 
       data-date="${date}"
       data-favorite="${isFavorite ? 'true' : 'false'}"
       data-id="${id}"
       onclick="handleCardClick(this, '${name}', ${id})">
    
    ${categoryBadge}
    
    <div class="selection-checkbox" onclick="event.stopPropagation(); toggleCardSelection(${id})"></div>
    
    <div class="card-icon-svg-wrapper">
        ${pdfIconSvg}
    </div>
    <div class="card-details">
        <h3 class="card-title">${truncatedName}</h3>
        <div class="card-meta">
            <span class="font-semibold">${size}</span> 
            <span>•</span> 
            <span>${date}</span>
        </div>
    </div>
    <div class="card-actions">
        <button class="icon-button favorite-btn" 
                data-fill="${fillStar}"
                onclick="event.stopPropagation(); toggleFavorite(this, ${id})">
            <span class="material-symbols-rounded" style="font-variation-settings: 'FILL' ${fillStar}, 'wght' 500, 'GRAD' 0, 'opsz' 24;">star</span>
        </button>
        <button class="icon-button more-btn" 
                onclick="event.stopPropagation(); openContextMenu('${name}', '${size}', '${date}', ${id})">
            <span class="material-symbols-rounded">more_vert</span>
        </button>
    </div>
  </div>`;
}

// --- DOSYA YÜKLEME İŞLEMLERİ ---
function handleFabAction(action) {
  if (action === 'scan') {
      showNotification('Tarama Başlatılıyor', 'Belge tarama modu açılıyor...', 'info');
  } else if (action === 'import') {
      pdfFileInput.click();
  }
  fabMenu.classList.remove('show');
}

function handleFileSelect(event) {
    const files = event.target.files;
    if (files && files.length > 0) {
        const file = files[0];
        
        const now = new Date();
        const formattedDate = `${String(now.getDate()).padStart(2, '0')}.${String(now.getMonth() + 1).padStart(2, '0')}.${now.getFullYear()}`;
        
        const reader = new FileReader();
        
        reader.onload = () => {
            const base64Data = reader.result;

            const newFile = {
                name: file.name,
                size: formatBytes(file.size, 2), // GERÇEK BOYUT
                date: formattedDate,
                isFavorite: false,
                id: Date.now(),
                base64: base64Data,
                category: null,
                lastOpened: null
            };

            importedFiles.unshift(newFile);

            showNotification('Dosya Yüklendi', `${newFile.name} başarıyla içe aktarıldı.`, 'success');
            
            if (currentNav !== 'home') {
                switchNav('home');
            }
            
            switchTab('device', true);
            
            loadData();
            saveToCache();
            
            pdfFileInput.value = '';
        };

        reader.readAsDataURL(file);
    }
}

// --- CONTEXT MENU FONKSİYONLARI ---
function openContextMenu(name, size, date, cardId) {
    if (isSelectionMode) {
        toggleCardSelection(cardId);
        return;
    }
    
    currentContextFile = { name, size, date, cardId };
    contextTitle.textContent = name;
    contextMeta.textContent = `${size} • ${date}`;
    
    contextOverlay.classList.add('show');
    contextSheet.classList.add('show');
}

function closeContextMenu() {
    contextOverlay.classList.remove('show');
    contextSheet.classList.remove('show');
}

function handleContextAction(action) {
    closeContextMenu();
    setTimeout(() => {
        switch(action) {
            case 'rename':
                openRenameDialog(currentContextFile.cardId, currentContextFile.name);
                break;
            case 'share':
                shareSingleFile(currentContextFile.cardId, currentContextFile.name);
                break;
            case 'print':
                printSingleFile(currentContextFile.cardId, currentContextFile.name);
                break;
            case 'category_add':
                openAddCategoryDialog();
                break;
            case 'category_remove':
                removeFromCategory(currentContextFile.cardId, currentContextFile.name);
                break;
            case 'delete':
                deleteSingleFile(currentContextFile.cardId, currentContextFile.name);
                break;
        }
    }, 300);
}

// --- KATEGORİ EKLEME DİALOG ---
function openAddCategoryDialog() {
    // Kategori seçim listesini oluştur - Alfabetik sıralı
    let html = '';
    
    // Alfabetik sıralı kategoriler
    const sortedCategories = [...categories].sort((a, b) => a.name.localeCompare(b.name, 'tr'));
    
    sortedCategories.forEach(category => {
        // Bu kategorideki dosya sayısını bul
        const fileCount = Object.values(fileCategories).filter(id => id === category.id).length;
        // Bu dosya bu kategoride mi kontrol et
        const isSelected = fileCategories[currentContextFile.cardId] === category.id;
        
        html += `
            <div class="category-select-item ${isSelected ? 'selected' : ''}" 
                 onclick="addFileToCategory('${category.id}', '${category.name}')">
                <div class="category-select-icon">
                    <span class="material-symbols-rounded">${category.icon}</span>
                </div>
                <div class="category-select-info">
                    <div class="category-select-name">${category.name}</div>
                    <div class="category-select-count">${fileCount} dosya</div>
                </div>
                ${isSelected ? '<span class="selected-badge">SEÇİLİ</span>' : ''}
            </div>
        `;
    });
    
    categorySelectList.innerHTML = html;
    addCategoryDialog.classList.add('show');
}

function closeAddCategoryDialog() {
    addCategoryDialog.classList.remove('show');
}

function createNewCategory() {
    closeAddCategoryDialog();
    setTimeout(() => {
        createCategoryNameInput.value = '';
        createCategoryNameInput.focus();
        createCategoryDialog.classList.add('show');
    }, 300);
}

function closeCreateCategoryDialog() {
    createCategoryDialog.classList.remove('show');
}

function createCategory() {
    const categoryName = createCategoryNameInput.value.trim();
    if (!categoryName) {
        showNotification('Hata', 'Lütfen bir kategori adı girin.', 'error');
        return;
    }
    
    // Aynı isimde kategori var mı kontrol et
    const existingCategory = categories.find(c => c.name.toLowerCase() === categoryName.toLowerCase());
    if (existingCategory) {
        showNotification('Hata', 'Bu isimde bir kategori zaten var.', 'warning');
        return;
    }
    
    const newId = 'cat_' + Date.now();
    const newCategory = {
        id: newId,
        name: categoryName,
        icon: 'folder'
    };
    
    categories.push(newCategory);
    
    // Kategorileri alfabetik sırala
    categories.sort((a, b) => a.name.localeCompare(b.name, 'tr'));
    
    // Carousel'ı yenile
    initCarousel();
    
    closeCreateCategoryDialog();
    loadData();
    saveToCache();
    
    showNotification('Kategori Eklendi', `"${categoryName}" kategorisi başarıyla oluşturuldu!`, 'success');
    
    // Otomatik olarak bu kategoriyi seç
    setTimeout(() => {
        addFileToCategory(newId, categoryName);
    }, 500);
}

function addFileToCategory(categoryId, categoryName) {
    fileCategories[currentContextFile.cardId] = categoryId;
    
    // Dosya nesnesini de güncelle
    let file = importedFiles.find(f => f.id === currentContextFile.cardId);
    if (!file) {
        file = devicePDFs.find(f => f.id === currentContextFile.cardId);
    }
    if (file) {
        file.category = categoryId;
    }
    
    loadData();
    saveToCache();
    showNotification('Kategoriye Eklendi', `"${currentContextFile.name}" dosyası "${categoryName}" kategorisine eklendi.`, 'success');
    closeAddCategoryDialog();
}

function removeFromCategory(cardId, fileName) {
    if (!fileCategories[cardId]) {
        showNotification('Bilgi', 'Bu dosya zaten bir kategoride değil.', 'info');
        return;
    }
    
    const categoryId = fileCategories[cardId];
    const category = categories.find(c => c.id === categoryId);
    const categoryName = category ? category.name : 'Kategori';
    
    fileCategories[cardId] = null;
    
    // Dosya nesnesini de güncelle
    let file = importedFiles.find(f => f.id === cardId);
    if (!file) {
        file = devicePDFs.find(f => f.id === cardId);
    }
    if (file) {
        file.category = null;
    }
    
    loadData();
    saveToCache();
    showNotification('Kategoriden Çıkarıldı', `"${fileName}" dosyası "${categoryName}" kategorisinden çıkarıldı.`, 'info');
}

// --- KATEGORİ SİLME DİALOG ---
function openCategoryDeleteDialog() {
    closeContextMenu();
    
    // Alfabetik sıralı kategoriler
    const sortedCategories = [...categories].sort((a, b) => a.name.localeCompare(b.name, 'tr'));
    
    let html = '';
    if (sortedCategories.length === 0) {
        html = `
            <div class="empty-state" style="padding: 20px;">
                <div class="empty-icon material-symbols-rounded">category</div>
                <p>Silinecek kategori yok.</p>
            </div>
        `;
    } else {
        sortedCategories.forEach(category => {
            // Bu kategorideki dosya sayısını bul
            const fileCount = Object.values(fileCategories).filter(id => id === category.id).length;
            
            html += `
                <div class="category-delete-item">
                    <div class="category-delete-info">
                        <div class="category-delete-icon">
                            <span class="material-symbols-rounded">${category.icon}</span>
                        </div>
                        <div class="category-delete-text">
                            <div class="category-delete-name">${category.name}</div>
                            <div class="category-delete-count">${fileCount} dosya</div>
                        </div>
                    </div>
                    <button class="delete-button" onclick="deleteCategory('${category.id}', '${category.name}')" ${fileCount > 0 ? '' : ''}>
                        Sil
                    </button>
                </div>
            `;
        });
    }
    
    categoryDeleteList.innerHTML = html;
    categoryDeleteDialog.classList.add('show');
}

function closeCategoryDeleteDialog() {
    categoryDeleteDialog.classList.remove('show');
}

function deleteCategory(categoryId, categoryName) {
    // Bu kategorideki dosya sayısını bul
    const fileCount = Object.values(fileCategories).filter(id => id === categoryId).length;
    
    if (fileCount > 0) {
        if (!confirm(`"${categoryName}" kategorisinde ${fileCount} dosya bulunuyor. Yine de silmek istiyor musunuz?\n\nBu dosyalar kategorisiz olarak işaretlenecektir.`)) {
            return;
        }
    } else {
        if (!confirm(`"${categoryName}" kategorisini silmek istediğinize emin misiniz?`)) {
            return;
        }
    }
    
    // Kategoriyi sil
    categories = categories.filter(c => c.id !== categoryId);
    
    // Carousel'dan da sil
    carouselCategories = carouselCategories.filter(c => c.id !== categoryId);
    
    // Carousel'ı yenile
    initCarousel();
    
    // Bu kategorideki dosyaları kategorisiz yap
    Object.keys(fileCategories).forEach(fileId => {
        if (fileCategories[fileId] === categoryId) {
            fileCategories[fileId] = null;
            
            // Dosya nesnelerini de güncelle
            let file = importedFiles.find(f => f.id === parseInt(fileId));
            if (!file) {
                file = devicePDFs.find(f => f.id === parseInt(fileId));
            }
            if (file) {
                file.category = null;
            }
        }
    });
    
    loadData();
    saveToCache();
    
    showNotification('Kategori Silindi', `"${categoryName}" kategorisi başarıyla silindi!`, 'success');
    
    // Dialog'u kapat
    setTimeout(() => {
        closeCategoryDeleteDialog();
    }, 300);
}

// --- YENİDEN ADLANDIRMA DİALOG ---
function openRenameDialog(cardId, oldName) {
    currentContextFile = { cardId, oldName };
    renameOldName.textContent = `Eski ad: ${oldName}`;
    
    const nameWithoutExt = oldName.replace(/\.pdf$/i, '');
    newFileNameInput.value = nameWithoutExt;
    newFileNameInput.focus();
    
    renameDialog.classList.add('show');
}

function closeRenameDialog() {
    renameDialog.classList.remove('show');
}

function confirmRenameFile() {
    if (!currentContextFile) return;
    
    const newName = newFileNameInput.value.trim();
    if (!newName) {
        showNotification('Hata', 'Lütfen bir dosya adı girin.', 'error');
        return;
    }
    
    const newNameWithExt = newName.endsWith('.pdf') ? newName : newName + '.pdf';
    
    if (newNameWithExt === currentContextFile.oldName) {
        showNotification('Bilgi', 'Yeni ad eski adla aynı.', 'info');
        return;
    }
    
    renameFile(currentContextFile.cardId, currentContextFile.oldName, newNameWithExt);
    closeRenameDialog();
}

function renameFile(cardId, oldName, newNameWithExt) {
    let fileIndex = importedFiles.findIndex(f => f.id === cardId);
    if(fileIndex > -1) {
        importedFiles[fileIndex].name = newNameWithExt;
    }
    
    fileIndex = devicePDFs.findIndex(f => f.id === cardId);
    if(fileIndex > -1) {
        const file = devicePDFs[fileIndex];
        try {
            if (typeof Android !== 'undefined' && Android.renameFile && file.path) {
                const newPath = Android.renameFile(file.path, newNameWithExt);
                if (newPath && newPath !== 'ERROR') {
                    devicePDFs[fileIndex].path = newPath;
                    devicePDFs[fileIndex].name = newNameWithExt;
                } else {
                    devicePDFs[fileIndex].name = newNameWithExt;
                }
            } else {
                devicePDFs[fileIndex].name = newNameWithExt;
            }
        } catch (e) {
            console.error('Rename error:', e);
            devicePDFs[fileIndex].name = newNameWithExt;
        }
    }
    
    loadData();
    saveToCache();
    showNotification('Dosya Adı Güncellendi', `"${oldName}" dosyasının adı "${newNameWithExt}" olarak değiştirildi.`, 'success');
}

// --- SELECTION MODE FUNCTIONS ---
function enterSelectionMode() {
    isSelectionMode = true;
    document.body.classList.add('selection-mode');
    updateSelectionCount();
}

function exitSelectionMode() {
    isSelectionMode = false;
    document.body.classList.remove('selection-mode');
    selectedCards.clear();
    document.querySelectorAll('.selection-checkbox').forEach(cb => {
        cb.classList.remove('selected');
    });
    document.querySelectorAll('.card').forEach(card => {
        card.classList.remove('selected');
    });
}

function toggleCardSelection(cardId) {
    if (!isSelectionMode) {
        enterSelectionMode();
    }
    
    const card = document.querySelector(`.card[data-id="${cardId}"]`);
    const checkbox = card.querySelector('.selection-checkbox');
    
    if (selectedCards.has(cardId)) {
        selectedCards.delete(cardId);
        checkbox.classList.remove('selected');
        card.classList.remove('selected');
    } else {
        selectedCards.add(cardId);
        checkbox.classList.add('selected');
        card.classList.add('selected');
    }
    
    updateSelectionCount();
    
    if (selectedCards.size === 0) {
        exitSelectionMode();
    }
}

function selectAllCards() {
    const allCards = document.querySelectorAll('.card:not(.hidden)');
    allCards.forEach(card => {
        const cardId = parseInt(card.dataset.id);
        selectedCards.add(cardId);
        const checkbox = card.querySelector('.selection-checkbox');
        checkbox.classList.add('selected');
        card.classList.add('selected');
    });
    updateSelectionCount();
}

function deselectAllCards() {
    selectedCards.clear();
    document.querySelectorAll('.selection-checkbox').forEach(cb => {
        cb.classList.remove('selected');
    });
    document.querySelectorAll('.card').forEach(card => {
        card.classList.remove('selected');
    });
    updateSelectionCount();
}

function shareSelectedCards() {
    if (selectedCards.size === 0) {
        showNotification('Uyarı', 'Lütfen paylaşmak için öğe seçin.', 'warning');
        return;
    }
    
    const selectedFiles = [];
    selectedCards.forEach(cardId => {
        let file = importedFiles.find(f => f.id === cardId);
        if (!file) {
            file = devicePDFs.find(f => f.id === cardId);
        }
        if (file) selectedFiles.push(file);
    });
    
    if (selectedFiles.length === 0) {
        showNotification('Hata', 'Paylaşılacak dosya bulunamadı.', 'error');
        return;
    }
    
    if (navigator.share) {
        const sharePromises = selectedFiles.map(file => {
            if (file.base64) {
                return fetch(file.base64)
                    .then(res => res.blob())
                    .then(blob => new File([blob], file.name, { type: 'application/pdf' }));
            } else if (typeof Android !== 'undefined' && Android.getFileForSharing) {
                return new Promise((resolve) => {
                    const fileObj = { name: file.name, path: file.path };
                    resolve(fileObj);
                });
            }
            return Promise.resolve(null);
        });
        
        Promise.all(sharePromises).then(files => {
            const validFiles = files.filter(f => f);
            if (validFiles.length > 0) {
                navigator.share({
                    title: validFiles.length === 1 ? validFiles[0].name : `${validFiles.length} PDF Dosyası`,
                    files: validFiles
                }).catch(err => {
                    console.error('Paylaşım hatası:', err);
                    showNotification('Bilgi', `${selectedFiles.length} adet dosya paylaşım için hazırlanıyor...`, 'info');
                });
            }
        });
    } else {
        showNotification('Bilgi', `${selectedFiles.length} adet dosya paylaşım için hazırlanıyor...`, 'info');
        
        try {
            if (typeof Android !== 'undefined' && Android.shareFiles) {
                const filePaths = selectedFiles.map(f => f.path || f.name).join('||');
                Android.shareFiles(filePaths);
            }
        } catch (e) {
            console.error('Android share error:', e);
        }
    }
}

function printSelectedCards() {
    if (selectedCards.size === 0) {
        showNotification('Uyarı', 'Lütfen yazdırmak için öğe seçin.', 'warning');
        return;
    }
    
    const selectedFiles = [];
    selectedCards.forEach(cardId => {
        let file = importedFiles.find(f => f.id === cardId);
        if (!file) {
            file = devicePDFs.find(f => f.id === cardId);
        }
        if (file) selectedFiles.push(file);
    });
    
    if (selectedFiles.length === 0) {
        showNotification('Hata', 'Yazdırılacak dosya bulunamadı.', 'error');
        return;
    }
    
    showPrintStatus(selectedFiles.length);
    
    selectedFiles.forEach((file, index) => {
        setTimeout(() => {
            printSingleFileDirectly(file, index + 1, selectedFiles.length);
        }, index * 1000);
    });
}

function printSingleFile(cardId, fileName) {
    let file = importedFiles.find(f => f.id === cardId);
    if (!file) {
        file = devicePDFs.find(f => f.id === cardId);
    }
    
    if (!file) {
        showNotification('Hata', 'Dosya bulunamadı.', 'error');
        return;
    }
    
    showPrintStatus(1);
    setTimeout(() => {
        printSingleFileDirectly(file, 1, 1);
    }, 500);
}

function showPrintStatus(totalFiles) {
    printStatusTitle.textContent = totalFiles > 1 ? `${totalFiles} Dosya Yazdırılıyor` : 'Dosya Yazdırılıyor';
    printStatusText.textContent = totalFiles > 1 ? 'Dosyalar hazırlanıyor...' : 'Dosya hazırlanıyor...';
    printProgressBar.style.width = '0%';
    printStatus.classList.add('show');
}

function updatePrintProgress(current, total) {
    const percent = (current / total) * 100;
    printProgressBar.style.width = `${percent}%`;
    printStatusText.textContent = `${current}/${total} dosya yazdırılıyor...`;
    
    if (current === total) {
        setTimeout(() => {
            printStatus.classList.remove('show');
            showNotification('Yazdırma Tamamlandı', `${total} dosya yazdırma işlemi tamamlandı.`, 'success');
        }, 1000);
    }
}

function printSingleFileDirectly(file, currentIndex, totalCount) {
    const fileName = file.name;
    console.log(`Yazdırılıyor: ${fileName} (${currentIndex}/${totalCount})`);
    
    updatePrintProgress(currentIndex, totalCount);
    
    try {
        if (typeof Android !== 'undefined' && Android.printPDF) {
            if (file.path) {
                Android.printPDF(file.path);
                console.log(`Android Print API ile yazdırma başlatıldı: ${fileName}`);
                return;
            }
        }
        
        if (file.base64) {
            printBase64PDF(file.base64, fileName);
        } else if (file.path) {
            printFileViaIframe(file.path, fileName);
        } else {
            showNotification('Uyarı', `"${fileName}" için yazdırma işlemi başlatılamadı. Lütfen dosyayı açıp manuel yazdırın.`, 'warning');
        }
        
    } catch (e) {
        console.error('Yazdırma hatası:', e);
        showNotification('Hata', `"${fileName}" yazdırılırken hata oluştu: ${e.message}`, 'error');
    }
}

function printBase64PDF(base64Data, fileName) {
    try {
        const base64WithoutPrefix = base64Data.split(',')[1];
        const binaryData = atob(base64WithoutPrefix);
        const bytes = new Uint8Array(binaryData.length);
        
        for (let i = 0; i < binaryData.length; i++) {
            bytes[i] = binaryData.charCodeAt(i);
        }
        
        const blob = new Blob([bytes], { type: 'application/pdf' });
        const blobUrl = URL.createObjectURL(blob);
        
        const printIframe = document.createElement('iframe');
        printIframe.style.display = 'none';
        printIframe.src = blobUrl;
        document.body.appendChild(printIframe);
        
        printIframe.onload = function() {
            setTimeout(() => {
                try {
                    printIframe.contentWindow.print();
                    setTimeout(() => {
                        document.body.removeChild(printIframe);
                        URL.revokeObjectURL(blobUrl);
                    }, 1000);
                } catch (e) {
                    console.error('Yazdırma hatası:', e);
                    document.body.removeChild(printIframe);
                    URL.revokeObjectURL(blobUrl);
                }
            }, 500);
        };
    } catch (e) {
        console.error('Base64 yazdırma hatası:', e);
        showNotification('Hata', 'Yazdırma işlemi başlatılamadı.', 'error');
    }
}

function printFileViaIframe(filePath, fileName) {
    const printIframe = document.createElement('iframe');
    printIframe.style.display = 'none';
    printIframe.src = filePath;
    document.body.appendChild(printIframe);
    
    printIframe.onload = function() {
        setTimeout(() => {
            try {
                printIframe.contentWindow.print();
                setTimeout(() => {
                    document.body.removeChild(printIframe);
                }, 1000);
            } catch (e) {
                console.error('Yazdırma hatası:', e);
                document.body.removeChild(printIframe);
            }
        }, 500);
    };
}

function deleteSelectedCards() {
    if (selectedCards.size === 0) {
        showNotification('Uyarı', 'Lütfen silmek için öğe seçin.', 'warning');
        return;
    }
    
    if (confirm(`${selectedCards.size} öğe silinecek. Emin misiniz?`)) {
        selectedCards.forEach(cardId => {
            importedFiles = importedFiles.filter(file => file.id !== cardId);
            
            const fileIndex = devicePDFs.findIndex(f => f.id === cardId);
            if (fileIndex > -1) {
                const file = devicePDFs[fileIndex];
                try {
                    if (typeof Android !== 'undefined' && Android.deleteFile && file.path) {
                        Android.deleteFile(file.path);
                    }
                } catch (e) {
                    console.error('Delete error:', e);
                }
                devicePDFs.splice(fileIndex, 1);
            }
            
            delete fileCategories[cardId];
        });
        
        showNotification('Silme Tamamlandı', `${selectedCards.size} öğe silindi.`, 'success');
        exitSelectionMode();
        loadData();
        saveToCache();
    }
}

function updateSelectionCount() {
    selectionCount.textContent = `${selectedCards.size} öğe seçildi`;
}

// --- FAVORİ İŞLEVİ ---
function toggleFavorite(button, cardId) {
    if (isSelectionMode) {
        toggleCardSelection(cardId);
        return;
    }
    
    const span = button.querySelector('.material-symbols-rounded');
    const card = button.closest('.card');
    let isFavorite = card.dataset.favorite === 'true';

    isFavorite = !isFavorite;
    card.dataset.favorite = isFavorite;
    
    const fillValue = isFavorite ? 1 : 0;
    span.style.fontVariationSettings = `'FILL' ${fillValue}, 'wght' 500, 'GRAD' 0, 'opsz' 24`;

    let fileIndex = importedFiles.findIndex(f => f.id === cardId);
    if(fileIndex > -1) {
        importedFiles[fileIndex].isFavorite = isFavorite;
    }
    
    fileIndex = devicePDFs.findIndex(f => f.id === cardId);
    if(fileIndex > -1) {
        devicePDFs[fileIndex].isFavorite = isFavorite;
    }
    
    loadData();
    saveToCache();
    
    // Bildirim göster
    if (isFavorite) {
        showNotification('Favorilere Eklendi', 'Dosya favorilere eklendi.', 'success');
    } else {
        showNotification('Favorilerden Çıkarıldı', 'Dosya favorilerden çıkarıldı.', 'info');
    }
}

// --- LONG PRESS VE CLICK YÖNETİMİ ---
let longPressTimer;
let isLongPress = false;

function attachLongPressEvents() {
    const cards = document.querySelectorAll('.card');
    cards.forEach(card => {
        card.addEventListener('touchstart', (e) => {
            if (e.target.closest('.icon-button')) {
                clearTimeout(longPressTimer);
                return;
            }
            
            isLongPress = false;
            longPressTimer = setTimeout(() => {
                isLongPress = true;
                if (navigator.vibrate) navigator.vibrate(50);
                const cardId = parseInt(card.dataset.id);
                toggleCardSelection(cardId);
            }, 500);
        }, {passive: true});

        card.addEventListener('touchend', () => {
            clearTimeout(longPressTimer);
        });

        card.addEventListener('touchmove', () => {
            clearTimeout(longPressTimer);
        });
    });
}

function handleCardClick(element, name, cardId) {
    if (isSelectionMode) {
        toggleCardSelection(cardId);
        return;
    }
    
    if (!isLongPress) {
        let fileData = importedFiles.find(f => f.id === cardId);
        if (!fileData) {
            fileData = devicePDFs.find(f => f.id === cardId);
        }

        if (fileData) {
            fileData.lastOpened = new Date().toISOString();
            
            loadData();
            saveToCache();
            
            openPDF(fileData, name);
        }
    }
}

function openPDF(fileData, name) {
    if (fileData.base64) {
        const url = "viewer.html?base64=" + 
                    encodeURIComponent(fileData.base64) + 
                    "&name=" + 
                    encodeURIComponent(name) +
                    "&id=" + 
                    fileData.id;
        window.location.href = url;
    } else if (fileData.path) {
        const url = "viewer.html?file=" + 
                    encodeURIComponent(fileData.path) + 
                    "&name=" + 
                    encodeURIComponent(name) +
                    "&id=" + 
                    fileData.id;
        window.location.href = url;
    } else {
        showNotification('Hata', 'Dosya verisi bulunamadı!', 'error');
    }
}

function shareSingleFile(cardId, fileName) {
    let file = importedFiles.find(f => f.id === cardId);
    if (!file) {
        file = devicePDFs.find(f => f.id === cardId);
    }
    
    if (!file) {
        showNotification('Hata', 'Dosya bulunamadı.', 'error');
        return;
    }
    
    if (navigator.share) {
        if (file.base64) {
            fetch(file.base64)
                .then(res => res.blob())
                .then(blob => {
                    const fileToShare = new File([blob], fileName, { type: 'application/pdf' });
                    navigator.share({
                        title: fileName,
                        files: [fileToShare]
                    }).catch(err => {
                        console.error('Paylaşım hatası:', err);
                        showNotification('Bilgi', `"${fileName}" paylaşılıyor...`, 'info');
                    });
                });
        } else if (typeof Android !== 'undefined' && Android.shareFile) {
            Android.shareFile(file.path || fileName);
        } else {
            showNotification('Bilgi', `"${fileName}" paylaşılıyor...`, 'info');
        }
    } else {
        showNotification('Bilgi', `"${fileName}" paylaşılıyor...`, 'info');
        
        try {
            if (typeof Android !== 'undefined' && Android.shareFile) {
                Android.shareFile(file.path || fileName);
            }
        } catch (e) {
            console.error('Android share error:', e);
        }
    }
}

function deleteSingleFile(cardId, fileName) {
    if(confirm(`"${fileName}" dosyasını silmek istediğinize emin misiniz?`)) {
        importedFiles = importedFiles.filter(f => f.id !== cardId);
        
        const fileIndex = devicePDFs.findIndex(f => f.id === cardId);
        if(fileIndex > -1) {
            const file = devicePDFs[fileIndex];
            try {
                if (typeof Android !== 'undefined' && Android.deleteFile && file.path) {
                    Android.deleteFile(file.path);
                }
            } catch (e) {
                console.error('Delete error:', e);
            }
            devicePDFs.splice(fileIndex, 1);
        }
        
        delete fileCategories[cardId];
        
        loadData();
        saveToCache();
        showNotification('Silme Tamamlandı', 'Dosya silindi.', 'success');
    }
}

// --- ARAMA FONKSİYONLARI ---
function openSearch() {
    searchOverlay.classList.add('active');
    searchInput.focus();
    performSearch();
}

function closeSearch() {
    searchOverlay.classList.remove('active');
    searchInput.value = '';
    clearSearch();
}

function clearSearch() {
    searchInput.value = '';
    performSearch();
}

function performSearch() {
    const query = searchInput.value.toLowerCase().trim();
    const allFiles = [...devicePDFs, ...importedFiles];
    
    if (query === '') {
        searchResultCount.textContent = 'Arama yapmak için bir şeyler yazın...';
        searchResults.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon material-symbols-rounded">search</div>
                <p>Arama yapmak için yukarıya bir şeyler yazın</p>
            </div>
        `;
        return;
    }
    
    const results = allFiles.filter(file => {
        const name = file.name.toLowerCase();
        const date = file.date.toLowerCase();
        
        if (name.includes(query)) return true;
        
        if (date.includes(query)) return true;
        
        if (file.size.toLowerCase().includes(query)) return true;
        
        if (file.category) {
            const category = categories.find(c => c.id === file.category);
            if (category && category.name.toLowerCase().includes(query)) return true;
        }
        
        return false;
    });
    
    searchResultCount.textContent = `${results.length} sonuç bulundu`;
    
    if (results.length === 0) {
        searchResults.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon material-symbols-rounded">search_off</div>
                <p>"${query}" için sonuç bulunamadı</p>
            </div>
        `;
    } else {
        let html = '';
        results.forEach(file => {
            const category = file.category ? categories.find(c => c.id === file.category) : null;
            const categoryBadge = category ? `<div class="category-badge" style="position: relative; top: 0; right: 0; margin-left: auto;">${category.name}</div>` : '';
            
            html += `
            <div class="card" onclick="openPDF(${JSON.stringify(file).replace(/"/g, '&quot;')}, '${file.name.replace(/'/g, "\\'")}')">
                <div class="card-icon-svg-wrapper">
                    <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 64 64">
                        <rect x="0" y="0" width="64" height="64" rx="10" fill="#E53935"/>
                        <path d="M18 12h22l10 10v30a4 4 0 0 1-4 4H18a4 4 0 0 1-4-4V16a4 4 0 0 1 4-4z"
                              fill="#FFFFFF"/>
                        <polygon points="40,12 50,22 40,22" fill="#F0F0F0"/>
                        <rect x="22" y="24" width="18" height="3" rx="1.5" fill="#E53935"/>
                        <rect x="22" y="30" width="20" height="3" rx="1.5" fill="#E53935"/>
                        <rect x="22" y="36" width="20" height="3" rx="1.5" fill="#E53935"/>
                        <text x="50%" y="52" text-anchor="middle"
                              font-family="Segoe UI, Roboto, Arial, sans-serif"
                              font-weight="700" font-size="14" fill="#E53935">
                            PDF
                        </text>
                    </svg>
                </div>
                <div class="card-details">
                    <h3 class="card-title">${file.name}</h3>
                    <div class="card-meta">
                        <span class="font-semibold">${file.size}</span> 
                        <span>•</span> 
                        <span>${file.date}</span>
                    </div>
                </div>
                ${categoryBadge}
            </div>
            `;
        });
        searchResults.innerHTML = html;
    }
}

// --- EVENTS ---
function attachEvents() {
    
  contextOverlay.addEventListener('click', closeContextMenu);

  searchBtn.addEventListener('click', openSearch);
  closeSearchBtn.addEventListener('click', closeSearch);
  searchInput.addEventListener('input', performSearch);

  panels.forEach(panel => {
    panel.addEventListener('scroll', (e) => {
      if (searchOverlay.classList.contains('active') || 
          document.body.classList.contains('selection-mode') ||
          !document.body.classList.contains('is-home')) return;

      const currentScroll = panel.scrollTop;
      const scrollDelta = currentScroll - lastScrollY;

      if (scrollDelta > 0 && currentScroll > 50) {
        topBar.classList.add('hidden');
      }
      else if (scrollDelta < -10) {
        topBar.classList.remove('hidden');
      }
      else if (currentScroll <= 10) {
        topBar.classList.remove('hidden');
      }

      lastScrollY = currentScroll;
    });
  });

  panels.forEach(panel => {
    panel.addEventListener('touchstart', e => {
        if (searchOverlay.classList.contains('active') || 
            !document.body.classList.contains('is-home')) return;

        if(!panel.classList.contains('active')) return;
        startX = e.touches[0].clientX;
        startY = e.touches[0].clientY;
        isSwiping = false;
        isVerticalScroll = false;
    }, {passive: false});

    panel.addEventListener('touchmove', e => {
        if (searchOverlay.classList.contains('active') || 
            !document.body.classList.contains('is-home')) return;

        if(!panel.classList.contains('active')) return;
        const currentX = e.touches[0].clientX;
        const currentY = e.touches[0].clientY;
        const diffX = currentX - startX;
        const diffY = currentY - startY;
        const moveThreshold = 10;
        
        if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > moveThreshold) {
            e.preventDefault();
            isSwiping = true;
            isVerticalScroll = false;
        } else if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(diffY) > moveThreshold) {
            isSwiping = false;
            isVerticalScroll = true;
        }
    }, {passive: false});

    panel.addEventListener('touchend', e => {
        if (searchOverlay.classList.contains('active') || 
            !document.body.classList.contains('is-home')) return;

        if(!panel.classList.contains('active')) return;
        const endX = e.changedTouches[0].clientX;
        const diffX = endX - startX;
        const swipeThreshold = 50;

        if (isSwiping && Math.abs(diffX) > swipeThreshold) {
            changeTab(diffX < 0 ? 1 : -1);
        }
        isSwiping = false;
        isVerticalScroll = false;
    });
  });
  
  tabs.forEach(t => t.addEventListener('click', () => switchTab(t.dataset.tab, true)));
  navItems.forEach(n => n.addEventListener('click', () => switchNav(n.dataset.nav)));
  
  fabButton.addEventListener('click', (e) => {
    e.stopPropagation();
    fabMenu.classList.toggle('show');
  });
  document.addEventListener('click', () => fabMenu.classList.remove('show'));
  
  document.getElementById('menuBtn').addEventListener('click', () => {
    drawer.classList.add('open');
    overlay.classList.add('show');
  });
  overlay.addEventListener('click', () => {
    drawer.classList.remove('open');
    overlay.classList.remove('show');
  });
  
  drawerItems.forEach(item => {
      item.addEventListener('click', () => {
          const targetId = `accordion-${item.dataset.accordion}`;
          const targetContent = document.getElementById(targetId);
          document.querySelectorAll('.drawer-accordion-content.open').forEach(content => {
              if (content !== targetContent) {
                  content.classList.remove('open');
              }
          });
          targetContent.classList.toggle('open');
      });
  });

  settingsTabs.forEach(tab => {
      tab.addEventListener('click', () => {
          const targetTab = tab.dataset.tab;
          settingsTabs.forEach(t => t.classList.remove('active'));
          document.querySelectorAll('.setting-panel').forEach(p => p.classList.remove('active'));
          tab.classList.add('active');
          document.querySelector(`.setting-panel[data-panel="${targetTab}"]`).classList.add('active');
      });
  });
}

// --- NAVIGATION ---
function switchTab(id, isClickOrNav = false) {
  tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === id));
  
  const panelIds = ['recentPanel', 'devicePanel', 'favoritesPanel', 'categoriesPanel'];
  panelIds.forEach(panelId => {
      const panel = document.getElementById(panelId);
      if (panel) {
          panel.classList.toggle('active', panelId === `${id}Panel`);
          if(panelId === `${id}Panel` && isClickOrNav) {
              panel.scrollTop = 0;
              lastScrollY = 0;
          }
      }
  });
  
  if (id === 'device') {
      setTimeout(() => {
          checkPermissionOnResume();
      }, 300);
  }
}

function changeTab(dir) {
  const active = document.querySelector('.tab.active').dataset.tab;
  const idx = tabOrder.indexOf(active);
  const nextIdx = idx + dir;
  if(nextIdx >= 0 && nextIdx < tabOrder.length) {
      switchTab(tabOrder[nextIdx], false);
  }
}

function switchNav(id) {
  if (isSelectionMode) {
      exitSelectionMode();
  }
  
  navItems.forEach(n => n.classList.toggle('active', n.dataset.nav === id));
  currentNav = id;
  
  if (id === 'home') {
      document.body.classList.add('is-home');
      document.body.classList.remove('is-tools', 'is-files');
      
      panels.forEach(p => p.classList.remove('active'));
      fabButton.style.display = 'flex';
      
      mainContent.style.paddingTop = isCarouselHidden ? 'var(--top-bar-total-no-carousel)' : 'var(--top-bar-total)';
      switchTab('recent', true);
  } else {
      document.body.classList.remove('is-home');
      if (id === 'tools') {
          document.body.classList.add('is-tools');
          document.body.classList.remove('is-files');
      } else if (id === 'files') {
          document.body.classList.add('is-files');
          document.body.classList.remove('is-tools');
      }
      
      panels.forEach(p => p.classList.remove('active'));
      fabButton.style.display = 'none';
      
      mainContent.style.paddingTop = `calc(var(--h-header) + 16px)`;
      const targetPanel = document.getElementById(`${id}Panel`);
      if (targetPanel) {
        targetPanel.classList.add('active');
        targetPanel.scrollTop = 0;
      }
  }
}

function handleFileSource(source) {
    if (source === 'device') {
        switchNav('home');
        switchTab('device', true);
    } else if (source === 'browse_more') {
        showNotification('Dosya Yöneticisi', 'Cihazınızın dosya yöneticisi açılıyor...', 'info');
    } else {
        showNotification('Bağlantı', `${source.toUpperCase()} kaynağına bağlanılıyor...`, 'info');
    }
}

function sendHelpEmail(e) {
    e.preventDefault();
    const email = document.getElementById('helpEmail').value;
    const subject = document.getElementById('helpSubject').value;
    const issue = document.getElementById('helpIssue').value;
    const recipient = "devsoftawaremail@gmail.com";
    const mailtoLink = `mailto:${recipient}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent("Gönderen: " + email + "\n\nSorun:\n" + issue)}`;
    window.location.href = mailtoLink;
    showNotification('Destek Talebi', 'E-posta uygulamanız açılıyor. Lütfen Gönder butonuna basın.', 'info');
    document.querySelector('.help-form').reset();
}

// --- INITIALIZATION ---
document.addEventListener('DOMContentLoaded', () => {
  const cacheLoaded = loadFromCache();
  
  initTheme();
  
  if (!cacheLoaded || categories.length === 0) {
    categories = [
        { id: 'is', name: 'İş', icon: 'work' },
        { id: 'okul', name: 'Okul', icon: 'school' },
        { id: 'finans', name: 'Finans', icon: 'attach_money' },
        { id: 'saglik', name: 'Sağlık', icon: 'medical_services' },
        { id: 'seyahat', name: 'Seyahat', icon: 'flight' }
    ];
    
    // Alfabetik sırala
    categories.sort((a, b) => a.name.localeCompare(b.name, 'tr'));
  }
  
  initCarousel();
  updateCarouselVisibility();
  
  loadData();
  loadTools();
  attachEvents();
  
  document.querySelector('.setting-tab').classList.add('active');
  document.querySelector('.setting-panel').classList.add('active');
  
  const currentTheme = localStorage.getItem('pdfTheme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  document.querySelector(`.theme-option[data-theme-val="${currentTheme === 'dark' ? 'dark' : (currentTheme === 'light' ? 'light' : 'device')}"]`).classList.add('active');
  
  document.body.classList.add('is-home');
  
  setTimeout(() => {
    checkInitialPermission();
  }, 1000);
  
  const deviceTab = document.querySelector('.tab[data-tab="device"]');
  if (deviceTab) {
    deviceTab.addEventListener('click', () => {
      setTimeout(() => {
        checkPermissionOnResume();
      }, 300);
    });
  }
  
  setInterval(saveToCache, 30000);
});

// Global fonksiyonlar
window.setThemePreference = setThemePreference;
window.sendHelpEmail = sendHelpEmail;
window.handleFileSource = handleFileSource;
window.handleFabAction = handleFabAction;
window.openSearch = openSearch;
window.closeSearch = closeSearch;
window.clearSearch = clearSearch;
window.performSearch = performSearch;
window.openContextMenu = openContextMenu;
window.handleContextAction = handleContextAction;
window.handleCardClick = handleCardClick;
window.openPDF = openPDF;
window.toggleFavorite = toggleFavorite;
window.enterSelectionMode = enterSelectionMode;
window.exitSelectionMode = exitSelectionMode;
window.toggleCardSelection = toggleCardSelection;
window.selectAllCards = selectAllCards;
window.deselectAllCards = deselectAllCards;
window.deleteSelectedCards = deleteSelectedCards;
window.shareSelectedCards = shareSelectedCards;
window.printSelectedCards = printSelectedCards;
window.printSingleFile = printSingleFile;
window.handleFileSelect = handleFileSelect;
window.openToolPage = openToolPage;
window.openAndroidPermissionSettings = openAndroidPermissionSettings;
window.onPermissionGranted = onPermissionGranted;
window.onPermissionDenied = onPermissionDenied;
window.scanDeviceForPDFs = scanDeviceForPDFs;
window.checkPermissionOnResume = checkPermissionOnResume;
window.selectCarouselCategory = selectCarouselCategory;
window.toggleCarouselVisibility = toggleCarouselVisibility;
window.openRenameDialog = openRenameDialog;
window.closeRenameDialog = closeRenameDialog;
window.confirmRenameFile = confirmRenameFile;
window.openAddCategoryDialog = openAddCategoryDialog;
window.closeAddCategoryDialog = closeAddCategoryDialog;
window.closeCreateCategoryDialog = closeCreateCategoryDialog;
window.createCategory = createCategory;
window.openCategoryDeleteDialog = openCategoryDeleteDialog;
window.closeCategoryDeleteDialog = closeCategoryDeleteDialog;
window.deleteCategory = deleteCategory;
window.showNotification = showNotification;
window.closeNotification = closeNotification;

// --- ARAÇLAR VERİLERİ ---
const toolsData = [
  {
    id: 1,
    title: "PDF Birleştirme",
    icon: "merge",
    iconCode: "merge",
    color: "#E53935",
    page: "birlestirme.html"
  },
  {
    id: 2,
    title: "Sesli Okuma",
    icon: "volume_up",
    iconCode: "volume_up",
    color: "#4CAF50",
    page: "sesli_okuma.html"
  },
  {
    id: 3,
    title: "OCR Metin Çıkarma",
    icon: "text_fields",
    iconCode: "text_fields",
    color: "#2196F3",
    page: "orc.html"
  },
  {
    id: 4,
    title: "PDF İmzalama",
    icon: "draw",
    iconCode: "draw",
    color: "#9C27B0",
    page: "imza.html"
  },
  {
    id: 5,
    title: "PDF Sıkıştırma",
    icon: "compress",
    iconCode: "compress",
    color: "#FF9800",
    page: "sikistirma.html"
  },
  {
    id: 6,
    title: "PDF Sayfalarını Organize Etme",
    icon: "reorder",
    iconCode: "reorder",
    color: "#795548",
    page: "organize.html"
  },
  {
    id: 7,
    title: "Resimden PDF",
    icon: "image",
    iconCode: "image",
    color: "#00BCD4",
    page: "resimden_pdf.html"
  },
  {
    id: 8,
    title: "PDF'den Resim",
    icon: "picture_as_pdf",
    iconCode: "picture_as_pdf",
    color: "#607D8B",
    page: "pdf_resme.html"
  }
];