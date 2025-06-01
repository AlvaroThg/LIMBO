package com.example.limbo.Views

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.limbo.R
import com.github.mikephil.charting.charts.LineChart
import com.example.limbo.ViewModel.Functions.graficoUSDTBOB

class GraficoView : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var graficoUSDTBOB: graficoUSDTBOB
    private val handler = Handler(Looper.getMainLooper())

    // TextViews from price info card
    private lateinit var tvCurrentPrice: TextView
    private lateinit var tvPriceChange: TextView
    private lateinit var tvHighestPrice: TextView
    private lateinit var tvLowestPrice: TextView
    private lateinit var ivPriceChange: ImageView

    // TextViews from exchange info card
    private lateinit var tvBinancePrice: TextView
    private lateinit var tvBitgetPrice: TextView
    private lateinit var tvEldoradoPrice: TextView

    // Time frame buttons
    private lateinit var btn1h: TextView
    private lateinit var btn1d: TextView
    private lateinit var btn1w: TextView
    private lateinit var btn1m: TextView
    private lateinit var btnAll: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grafico)

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Initialize chart
        lineChart = findViewById(R.id.lineChart)

        // Initialize TextViews from price info card
        tvCurrentPrice = findViewById(R.id.tvCurrentPrice)
        tvPriceChange = findViewById(R.id.tvPriceChange)
        tvHighestPrice = findViewById(R.id.tvHighestPrice)
        tvLowestPrice = findViewById(R.id.tvLowestPrice)
        ivPriceChange = findViewById(R.id.ivPriceChange)

        // Initialize TextViews from exchange info card
        tvBinancePrice = findViewById(R.id.tvBinancePrice)
        tvBitgetPrice = findViewById(R.id.tvBitgetPrice)
        tvEldoradoPrice = findViewById(R.id.tvEldoradoPrice)

        // Initialize time frame buttons
        btn1h = findViewById(R.id.btn1h)
        btn1d = findViewById(R.id.btn1d)
        btn1w = findViewById(R.id.btn1w)
        btn1m = findViewById(R.id.btn1m)
        btnAll = findViewById(R.id.btnAll)

        // Set up time frame button listeners
        setupTimeFrameButtons()

        // Initialize and configure grafico object with all TextViews
        graficoUSDTBOB = graficoUSDTBOB(
            lineChart,
            tvHighestPrice,
            tvBinancePrice,
            tvBitgetPrice,
            tvEldoradoPrice
        )

        // Set initial values
        updateUIWithPriceInfo()
    }

    private fun setupTimeFrameButtons() {
        // Default selection is 1D
        highlightSelectedButton(btn1d)

        btn1h.setOnClickListener {
            highlightSelectedButton(it as TextView)
            // Lógica para timeframe de 1 hora
        }

        btn1d.setOnClickListener {
            highlightSelectedButton(it as TextView)
            // Lógica para timeframe de 1 día
        }

        btn1w.setOnClickListener {
            highlightSelectedButton(it as TextView)
            // Lógica para timeframe de 1 semana
        }

        btn1m.setOnClickListener {
            highlightSelectedButton(it as TextView)
            // Lógica para timeframe de 1 mes
        }

        btnAll.setOnClickListener {
            highlightSelectedButton(it as TextView)
            // Lógica para todo el historial
        }
    }

    private fun highlightSelectedButton(selected: TextView) {
        // Reset all buttons to default style
        val buttons = listOf(btn1h, btn1d, btn1w, btn1m, btnAll)
        buttons.forEach {
            it.setBackgroundResource(0) // Transparent background
            it.setTextColor(Color.WHITE)
        }

        // Highlight selected button
        selected.setBackgroundResource(R.drawable.rounded_button)
        selected.setTextColor(Color.WHITE)
    }

    private fun updateUIWithPriceInfo() {
        // Actualizar precio actual
        val currentPrice = graficoUSDTBOB.getBinancePrice() // O promedio de precios
        tvCurrentPrice.text = String.format("%.2f BOB", currentPrice)

        // Para demo, calcular cambio de precio
        val lastBinancePrice = currentPrice - 0.05f // Ejemplo para demo
        val changePercent = if (lastBinancePrice > 0)
            ((currentPrice - lastBinancePrice) / lastBinancePrice) * 100f else 0.23f

        val changeText = if (changePercent >= 0)
            "+${String.format("%.2f", changePercent)}%"
        else
            "${String.format("%.2f", changePercent)}%"

        tvPriceChange.text = changeText

        // Establecer color basado en la dirección del cambio
        val priceChangeColor = if (changePercent >= 0) {
            ivPriceChange.setImageResource(R.drawable.ic_arrow_up)
            Color.parseColor("#2BB461") // Verde
        } else {
            ivPriceChange.setImageResource(R.drawable.ic_arrow_down)
            Color.parseColor("#FF4436") // Rojo
        }

        tvPriceChange.setTextColor(priceChangeColor)
        ivPriceChange.setColorFilter(priceChangeColor)

        // Actualizar los precios más altos y más bajos
        val highPrice = maxOf(
            graficoUSDTBOB.getBinancePrice(),
            graficoUSDTBOB.getBitgetPrice(),
            graficoUSDTBOB.getEldoradoPrice()
        ) + 0.05f
        val lowPrice = minOf(
            graficoUSDTBOB.getBinancePrice(),
            graficoUSDTBOB.getBitgetPrice(),
            graficoUSDTBOB.getEldoradoPrice()
        ) - 0.05f

        tvHighestPrice.text = "Precio más alto: ${String.format("%.2f", highPrice)} BOB"
        tvLowestPrice.text = "Precio más bajo: ${String.format("%.2f", lowPrice)} BOB"
    }

    override fun onResume() {
        super.onResume()
        // Iniciar actualización de gráfico cuando la actividad es visible
        startUpdatingChart()
    }

    private fun startUpdatingChart() {
        graficoUSDTBOB.fetchData()
        handler.postDelayed(object : Runnable {
            override fun run() {
                graficoUSDTBOB.fetchData()
                updateUIWithPriceInfo()
                handler.postDelayed(this, 5000) // Actualizar cada 5 segundos
            }
        }, 5000)
    }

    override fun onPause() {
        super.onPause()
        // Detener actualizaciones cuando la actividad no es visible
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        graficoUSDTBOB.stopUpdating()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}