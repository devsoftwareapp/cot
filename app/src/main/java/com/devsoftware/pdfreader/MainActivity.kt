package com.devsoftware.pdfreader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bu Activity sadece ana giriş ekranı olarak durabilir
        // Eğer IndexActivity kullanıyorsan buradan ona yönlendirebilirsin:
        // startActivity(Intent(this, IndexActivity::class.java))
        // finish()
    }
}
