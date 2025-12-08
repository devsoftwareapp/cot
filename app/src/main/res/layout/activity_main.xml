package com.devsoftware.pdfreader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.devsoftware.pdfreader.databinding.ActivityMainBinding
import com.devsoftware.pdfreader.fragment.*
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerHelper: DrawerHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupDrawer()
        setupBottomNavigation()
        
        // Varsayılan fragment'ı yükle
        loadFragment(HomeFragment())
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        binding.toolbar.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(binding.navView)
        }
        
        binding.toolbar.btnSearch.setOnClickListener {
            showSearchBar()
        }
        
        binding.toolbar.btnCloseSearch.setOnClickListener {
            hideSearchBar()
        }
    }
    
    private fun setupDrawer() {
        drawerHelper = DrawerHelper(this, binding.drawerLayout, binding.navView)
        drawerHelper.setup()
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_tools -> {
                    loadFragment(ToolsFragment())
                    true
                }
                R.id.nav_files -> {
                    loadFragment(FilesFragment())
                    true
                }
                else -> false
            }
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    private fun showSearchBar() {
        binding.toolbar.headerContent.isVisible = false
        binding.toolbar.searchBar.isVisible = true
        binding.toolbar.searchInput.requestFocus()
    }
    
    private fun hideSearchBar() {
        binding.toolbar.headerContent.isVisible = true
        binding.toolbar.searchBar.isVisible = false
        binding.toolbar.searchInput.text?.clear()
    }
    
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(binding.navView)) {
            binding.drawerLayout.closeDrawer(binding.navView)
        } else if (binding.toolbar.searchBar.isVisible) {
            hideSearchBar()
        } else {
            super.onBackPressed()
        }
    }
}
