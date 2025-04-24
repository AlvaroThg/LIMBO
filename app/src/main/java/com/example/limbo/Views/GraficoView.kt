package com.example.limbo.Views


import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.limbo.R
import com.github.mikephil.charting.charts.LineChart
import com.example.limbo.ViewModel.Functions.graficoUSDTBOB


class GraficoView : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var graficoUSDTBOB: graficoUSDTBOB
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grafico)

        lineChart = findViewById(R.id.lineChart)
        graficoUSDTBOB = graficoUSDTBOB(lineChart)

    }

    private fun startUpdatingChart() {
        graficoUSDTBOB.fetchData()
        handler.postDelayed(object : Runnable {
            override fun run() {
                graficoUSDTBOB.fetchData()
                handler.postDelayed(this, 1_000)
            }
        }, 1_000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
