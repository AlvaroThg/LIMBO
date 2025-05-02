package com.example.limbo.Views

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.example.limbo.R

class BankDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide action bar
        supportActionBar?.hide()

        // Set status bar color
        window.statusBarColor = Color.BLACK

        setContentView(R.layout.activity_bank_details)

        val bankId = intent.getStringExtra("BANK_ID") ?: "BNB"

        // Set up the back button
        val headerLayout = findViewById<RelativeLayout>(R.id.headerLayout)
        headerLayout.setOnClickListener {
            finish()
        }

        // Set up the bank details based on the bank ID
        setupBankDetails(bankId)
    }

    private fun setupBankDetails(bankId: String) {
        val tvBankName = findViewById<TextView>(R.id.tvBankName)
        val tvBankFullName = findViewById<TextView>(R.id.tvBankFullName)
        val ivBankLogo = findViewById<ImageView>(R.id.ivBankLogo)

        // Apply custom bank-specific theme if needed
        val bankThemeColor = when (bankId) {
            "BNB" -> R.color.green_accent
            "BISA" -> R.color.yellow
            "UNION" -> R.drawable.bank_union_background // Or use a specific color for Union bank
            else -> R.color.green_accent // Default to BNB theme
        }

        // Apply theme color to certain UI elements if needed
        // For example:
        // findViewById<View>(R.id.someElement).backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bankThemeColor))

        when (bankId) {
            "BNB" -> {
                tvBankName.text = "BNB"
                tvBankFullName.text = "Banco Nacional de Bolivia"
                ivBankLogo.setImageResource(R.drawable.logo_bnb)

                // Set up other bank-specific details
                findViewById<TextView>(R.id.tvModalidad).text = "Mensual"
                findViewById<TextView>(R.id.tvLimiteInternet).text = "50$ USD/Internet"
                findViewById<TextView>(R.id.tvLimiteTarjeta).text = "25$ USD/Tarjeta"
                findViewById<TextView>(R.id.tvLimiteATM).text = "25$ USD/ATM"
                findViewById<TextView>(R.id.tvPlataformas).text = "Solo Plataformas\nde Streaming"
                findViewById<TextView>(R.id.tvCondiciones).text = "Solo para cuentas\nabiertas antes del\n02/10/2024"
            }
            "BISA" -> {
                tvBankName.text = "BISA"
                tvBankFullName.text = "Banco BISA"
                ivBankLogo.setImageResource(R.drawable.logo_bisa)

                // Create yellow highlight theme
                // window.statusBarColor = ContextCompat.getColor(this, R.color.bisa_dark_yellow)

                // Set up other bank-specific details
                findViewById<TextView>(R.id.tvModalidad).text = "Semanal"
                findViewById<TextView>(R.id.tvLimiteInternet).text = "100$ USD/Internet"
                findViewById<TextView>(R.id.tvLimiteTarjeta).text = "50$ USD/Tarjeta"
                findViewById<TextView>(R.id.tvLimiteATM).text = "50$ USD/ATM"
                findViewById<TextView>(R.id.tvPlataformas).text = "Todas las plataformas"
                findViewById<TextView>(R.id.tvCondiciones).text = "Para todas las cuentas"
            }
            "UNION" -> {
                tvBankName.text = "BANCO UNIÓN"
                tvBankFullName.text = "Banco Unión de Bolivia"
                ivBankLogo.setImageResource(R.drawable.logo_union)

                // Set up other bank-specific details
                findViewById<TextView>(R.id.tvModalidad).text = "Mensual"
                findViewById<TextView>(R.id.tvLimiteInternet).text = "75$ USD/Internet"
                findViewById<TextView>(R.id.tvLimiteTarjeta).text = "30$ USD/Tarjeta"
                findViewById<TextView>(R.id.tvLimiteATM).text = "40$ USD/ATM"
                findViewById<TextView>(R.id.tvPlataformas).text = "Plataformas seleccionadas"
                findViewById<TextView>(R.id.tvCondiciones).text = "Para nuevas cuentas también"
            }
            // You can add more banks as needed
        }
    }
}