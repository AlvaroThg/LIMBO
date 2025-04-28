package com.example.limbo.Views

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.ImageView
import android.widget.RelativeLayout
import com.example.limbo.R

class BankDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                findViewById<TextView>(R.id.tvPlataformas).text = "Solo Plataformas de Streaming"
                findViewById<TextView>(R.id.tvCondiciones).text = "Solo para cuentas abiertas antes del 02/10/2024"
            }
            "BISA" -> {
                tvBankName.text = "BISA"
                tvBankFullName.text = "Banco BISA"
                ivBankLogo.setImageResource(R.drawable.logo_bisa)

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
            // Puedes agregar más bancos según sea necesario
        }
    }
}