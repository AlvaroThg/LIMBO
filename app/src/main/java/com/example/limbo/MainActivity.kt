package com.example.limbo

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.limbo.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.example.limbo.network.CryptoMarketData
import com.github.mikephil.charting.animation.Easing
import com.example.limbo.network.LastValueFormatter
import com.example.limbo.network.CriptoYaApiService
import com.example.limbo.network.RetrofitClient

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private val handler = Handler(Looper.getMainLooper())

    private val binanceEntries = mutableListOf<Entry>()
    private val bitgetEntries = mutableListOf<Entry>()
    private val eldoradoEntries = mutableListOf<Entry>()

    private var timeCounter = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineChart = findViewById(R.id.lineChart)
        initChart()
        startUpdatingChart()
    }

    private fun initChart() {
        lineChart.setBackgroundColor(Color.BLACK)
        lineChart.setDrawGridBackground(false)
        lineChart.setDrawBorders(false)

        lineChart.setViewPortOffsets(135f, 160f, 150f, 180f)
        lineChart.description.text = "Precio USDT/BOB (P2P)"
        lineChart.description.textSize = 14f
        lineChart.description.textColor = Color.DKGRAY

        lineChart.axisRight.isEnabled = false

        lineChart.axisLeft.apply {
            textSize = 14f
            textColor = Color.DKGRAY
            setDrawGridLines(true)
            gridColor = Color.LTGRAY
            gridLineWidth = 1f
            axisLineColor = Color.DKGRAY
            axisLineWidth = 2f
        }

        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textSize = 14f
            textColor = Color.DKGRAY
            setDrawGridLines(false)
            axisLineColor = Color.DKGRAY
            axisLineWidth = 2f
        }

        lineChart.legend.apply {
            textSize = 16f
            textColor = Color.DKGRAY
            form = LegendForm.LINE
            formSize = 20f
        }

        lineChart.setExtraOffsets(16f, 16f, 16f, 16f)

        lineChart.description.textColor = Color.WHITE
        lineChart.axisLeft.apply {
            textColor = Color.WHITE
            gridColor = Color.WHITE
            axisLineColor = Color.WHITE
        }

        lineChart.xAxis.apply {
            textColor = Color.WHITE
            axisLineColor = Color.WHITE
        }

        lineChart.legend.textColor = Color.WHITE
    }

    private fun startUpdatingChart() {
        fetchData()

        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchData()
                handler.postDelayed(this, 1_000)
            }
        }, 1_000)
    }

    private fun fetchData() {
        val call = RetrofitClient.apiService.getUsdtBobData()
        call.enqueue(object : Callback<Map<String, CryptoMarketData>> {
            override fun onResponse(
                call: Call<Map<String, CryptoMarketData>>,
                response: Response<Map<String, CryptoMarketData>>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val dataMap = response.body()!!

                    val binanceData = dataMap["binancep2p"]
                    val bitgetData = dataMap["bitgetp2p"]
                    val eldoradoData = dataMap["eldoradop2p"]

                    if (binanceData != null && bitgetData != null && eldoradoData != null) {
                        val binancePrice = binanceData.ask ?: 0f
                        val bitgetPrice = bitgetData.ask ?: 0f
                        val eldoradoPrice = eldoradoData.ask ?: 0f

                        Log.d("MainActivity", "Binance: $binancePrice - Bitget: $bitgetPrice - Eldorado: $eldoradoPrice")
                        updateChart(binancePrice, bitgetPrice, eldoradoPrice)
                    } else {
                        Toast.makeText(this@MainActivity, "Datos incompletos en la respuesta", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Respuesta no exitosa o vacía", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Map<String, CryptoMarketData>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateChart(binancePrice: Float, bitgetPrice: Float, eldoradoPrice: Float) {
        fun <T> MutableList<T>.limitSize() {
            if (size >= 10) removeAt(0)
        }

        binanceEntries.add(Entry(timeCounter, binancePrice))
        binanceEntries.limitSize()

        bitgetEntries.add(Entry(timeCounter, bitgetPrice))
        bitgetEntries.limitSize()

        eldoradoEntries.add(Entry(timeCounter, eldoradoPrice))
        eldoradoEntries.limitSize()

        timeCounter++

        val binanceDataSet = LineDataSet(binanceEntries, "Binance P2P").apply {
            color = Color.YELLOW
            lineWidth = 4f
            setDrawCircles(true)
            circleRadius = 3f
            setDrawValues(true)
            valueTextSize = 14f
            valueTextColor = Color.WHITE
            valueFormatter = LastValueFormatter(binanceEntries)
        }

        val bitgetDataSet = LineDataSet(bitgetEntries, "Bitget P2P").apply {
            color = Color.CYAN
            lineWidth = 4f
            setDrawCircles(true)
            circleRadius = 3f
            setDrawValues(true)
            valueTextSize = 14f
            valueTextColor = Color.WHITE
            valueFormatter = LastValueFormatter(bitgetEntries)
        }

        val eldoradoDataSet = LineDataSet(eldoradoEntries, "Eldorado P2P").apply {
            color = Color.rgb(255, 165, 0)
            lineWidth = 4f
            setDrawCircles(true)
            circleRadius = 3f
            setDrawValues(true)
            valueTextSize = 14f
            valueTextColor = Color.WHITE
            valueFormatter = LastValueFormatter(eldoradoEntries)
        }

        val lineData = LineData(binanceDataSet, bitgetDataSet, eldoradoDataSet)
        lineChart.data = lineData
        
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}