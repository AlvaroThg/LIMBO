package com.example.limbo.ViewModel.Functions

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.example.limbo.Model.Objects.CryptoMarketData
import com.example.limbo.Model.Apis.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class graficoUSDTBOB(private val lineChart: LineChart) {
    private val handler = Handler(Looper.getMainLooper())
    private val binanceEntries = mutableListOf<Entry>()
    private val bitgetEntries = mutableListOf<Entry>()
    private val eldoradoEntries = mutableListOf<Entry>()
    private var timeCounter = 0f

    init {
        initChart()
        startUpdatingChart()
    }

    public fun initChart() {
        lineChart.apply {
            setBackgroundColor(Color.BLACK)
            setDrawGridBackground(false)
            setDrawBorders(false)
            setViewPortOffsets(135f, 160f, 150f, 180f)
            description.text = "Precio USDT/BOB (P2P)"
            description.textSize = 14f
            description.textColor = Color.WHITE
        }

        lineChart.axisLeft.apply {
            textSize = 14f
            textColor = Color.WHITE
            setDrawGridLines(true)
            gridColor = Color.WHITE
            gridLineWidth = 1f
            axisLineColor = Color.WHITE
            axisLineWidth = 2f
        }

        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textSize = 14f
            textColor = Color.WHITE
            setDrawGridLines(false)
            axisLineColor = Color.WHITE
            axisLineWidth = 2f
        }

        lineChart.legend.apply {
            textSize = 16f
            textColor = Color.WHITE
            form = Legend.LegendForm.LINE
            formSize = 20f
        }
    }

    public fun startUpdatingChart() {
        fetchData()
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchData()
                handler.postDelayed(this, 1_000)
            }
        }, 1_000)
    }

    public fun fetchData() {
        val call = RetrofitClient.apiService.getUsdtBobData()
        call.enqueue(object : Callback<Map<String, CryptoMarketData>> {
            override fun onResponse(call: Call<Map<String, CryptoMarketData>>, response: Response<Map<String, CryptoMarketData>>) {
                if (response.isSuccessful && response.body() != null) {
                    response.body()?.let { dataMap ->
                        val binancePrice = dataMap["binancep2p"]?.ask ?: 0f
                        val bitgetPrice = dataMap["bitgetp2p"]?.ask ?: 0f
                        val eldoradoPrice = dataMap["eldoradop2p"]?.ask ?: 0f
                        Log.d("graficoUSDTBOB", "Binance: $binancePrice - Bitget: $bitgetPrice - Eldorado: $eldoradoPrice")
                        updateChart(binancePrice, bitgetPrice, eldoradoPrice)
                    }
                } else {
                    Toast.makeText(lineChart.context, "Respuesta no exitosa", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Map<String, CryptoMarketData>>, t: Throwable) {
                Toast.makeText(lineChart.context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    public fun updateChart(binancePrice: Float, bitgetPrice: Float, eldoradoPrice: Float) {
        fun <T> MutableList<T>.limitSize() { if (size >= 10) removeAt(0) }
        binanceEntries.add(Entry(timeCounter, binancePrice)).also { binanceEntries.limitSize() }
        bitgetEntries.add(Entry(timeCounter, bitgetPrice)).also { bitgetEntries.limitSize() }
        eldoradoEntries.add(Entry(timeCounter, eldoradoPrice)).also { eldoradoEntries.limitSize() }
        timeCounter++
        val binanceDataSet = createDataSet(binanceEntries, "Binance P2P", Color.YELLOW)
        val bitgetDataSet = createDataSet(bitgetEntries, "Bitget P2P", Color.CYAN)
        val eldoradoDataSet = createDataSet(eldoradoEntries, "Eldorado P2P", Color.rgb(255, 165, 0))
        lineChart.data = LineData(binanceDataSet, bitgetDataSet, eldoradoDataSet)
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    public fun createDataSet(entries: List<Entry>, label: String, color: Int) = LineDataSet(entries, label).apply {
        this.color = color
        lineWidth = 4f
        setDrawCircles(true)
        circleRadius = 3f
        setDrawValues(true)
        valueTextSize = 14f
        valueTextColor = Color.WHITE
    }

    fun stopUpdating() {
        handler.removeCallbacksAndMessages(null)
    }
}
