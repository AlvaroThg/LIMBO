package com.example.limbo.Views

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.ImageButton
import com.example.limbo.R

class BankDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bank_details)

        val bankId = intent.getStringExtra("BANK_ID") ?: "BNB"

        // Set up the back button
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Set up the bank details based on the bank ID
        setupBankDetails(bankId)
    }

    private fun setupBankDetails(bankId: String) {
        val tvBankName = findViewById<TextView>(R.id.tvBankName)
        val tvBankFullName = findViewById<TextView>(R.id.tvBankFullName)

        when (bankId) {
            "BNB" -> {
                tvBankName.text = "BNB"
                tvBankFullName.text = "Banco Nacional de Bolivia"

                // Set up other bank-specific details
                findViewById<TextView>(R.id.tvModalidad).text = "Mensual"
                findViewById<TextView>(R.id.tvLimiteInternet).text = "50$ USD/Internet"
                findViewById<TextView>(R.id.tvLimiteTarjeta).text = "25$ USD/Tarjeta"
                findViewById<TextView>(R.id.tvLimiteATM).text = "25$ USD/ATM"
                findViewById<TextView>(R.id.tvPlataformas).text = "Solo Plataformas de Streaming"
                findViewById<TextView>(R.id.tvCondiciones).text = "Solo para cuentas abiertas antes del 02/10/2024"
            }
            // Add more banks as needed
        }
    }
}