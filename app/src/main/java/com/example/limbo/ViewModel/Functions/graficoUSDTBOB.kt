package com.example.limbo.ViewModel.Functions

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
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

class graficoUSDTBOB(private val lineChart: LineChart, private val tvHighestPrice: TextView? = null) {
    private val handler = Handler(Looper.getMainLooper())
    private val binanceEntries = mutableListOf<Entry>()
    private val bitgetEntries = mutableListOf<Entry>()
    private val eldoradoEntries = mutableListOf<Entry>()
    private var timeCounter = 0f

    // Variables para almacenar los precios actuales
    private var binancePrice = 0f
    private var bitgetPrice = 0f
    private var eldoradoPrice = 0f

    // Métodos getter para acceder a los precios desde fuera de la clase
    fun getBinancePrice(): Float = binancePrice
    fun getBitgetPrice(): Float = bitgetPrice
    fun getEldoradoPrice(): Float = eldoradoPrice

    init {
        initChart()
        startUpdatingChart()
    }

    public fun initChart() {
        lineChart.apply {
            setBackgroundColor(Color.BLACK)
            setDrawGridBackground(false)
            setDrawBorders(false)

            // Ajusta los márgenes para que sean proporcionales al tamaño del gráfico
            // en lugar de valores fijos
            val width = lineChart.width
            val height = lineChart.height
            if (width > 0 && height > 0) {
                setViewPortOffsets(
                    width * 0.1f,  // 10% del ancho por la izquierda
                    height * 0.1f, // 10% del alto por arriba
                    width * 0.1f,  // 10% del ancho por la derecha
                    height * 0.15f // 15% del alto por abajo (para etiquetas X)
                )
            } else {
                // Si aún no tenemos dimensiones, usar postDelayed para aplicar después
                post {
                    val w = lineChart.width
                    val h = lineChart.height
                    if (w > 0 && h > 0) {
                        setViewPortOffsets(
                            w * 0.1f,
                            h * 0.1f,
                            w * 0.1f,
                            h * 0.15f
                        )
                        invalidate()
                    }
                }
            }

            description.text = "Precio USDT/BOB (P2P)"
            description.textSize = 14f // Aumentar ligeramente el tamaño de texto
            description.textColor = Color.WHITE
        }

        // Ajustar tamaño de texto en los ejes
        lineChart.axisLeft.apply {
            textSize = 12f // Texto ligeramente más grande
            textColor = Color.WHITE
            setDrawGridLines(true)
            gridColor = Color.DKGRAY // Color más oscuro para las líneas de cuadrícula
            gridLineWidth = 0.5f // Líneas más finas
            axisLineColor = Color.WHITE
            axisLineWidth = 1f // Línea más fina
        }

        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textSize = 12f // Texto ligeramente más grande
            textColor = Color.WHITE
            setDrawGridLines(false)
            axisLineColor = Color.WHITE
            axisLineWidth = 1f // Línea más fina
        }

        lineChart.legend.apply {
            textSize = 14f // Texto más grande para mejor legibilidad
            textColor = Color.WHITE
            form = Legend.LegendForm.LINE
            formSize = 12f // Símbolos ligeramente más grandes
            // Colocar la leyenda en la parte superior para ahorrar espacio
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        }

        // Desactivar el eje derecho ya que no lo usamos
        lineChart.axisRight.isEnabled = false

        // Permitir pellizco para zoom
        lineChart.setPinchZoom(true)

        // Establecer el mínimo visible de puntos
        lineChart.setVisibleXRangeMinimum(6f)

        // Animación más suave
        lineChart.animateX(1000)
    }

    public fun startUpdatingChart() {
        fetchData()
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchData()
                // Cambiamos el intervalo a 3 segundos (3000 milisegundos)
                handler.postDelayed(this, 5_000)
            }
        }, 5_000) // Primer delay también a 3 segundos
    }

    public fun fetchData() {
        val call = RetrofitClient.apiService.getUsdtBobData()
        call.enqueue(object : Callback<Map<String, CryptoMarketData>> {
            override fun onResponse(call: Call<Map<String, CryptoMarketData>>, response: Response<Map<String, CryptoMarketData>>) {
                if (response.isSuccessful && response.body() != null) {
                    response.body()?.let { dataMap ->
                        binancePrice = dataMap["binancep2p"]?.ask ?: 0f
                        bitgetPrice = dataMap["bitgetp2p"]?.ask ?: 0f
                        eldoradoPrice = dataMap["eldoradop2p"]?.ask ?: 0f

                        Log.d("graficoUSDTBOB", "Binance: $binancePrice - Bitget: $bitgetPrice - Eldorado: $eldoradoPrice")
                        updateChart(binancePrice, bitgetPrice, eldoradoPrice)
                        updateHighestPriceLabel()
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

    private fun updateHighestPriceLabel() {
        // Actualizar el TextView con el precio más alto
        tvHighestPrice?.let {
            val highestPrice: Float
            val marketName: String

            when {
                binancePrice >= bitgetPrice && binancePrice >= eldoradoPrice -> {
                    highestPrice = binancePrice
                    marketName = "BinanceP2P"
                }
                bitgetPrice >= binancePrice && bitgetPrice >= eldoradoPrice -> {
                    highestPrice = bitgetPrice
                    marketName = "BitgetP2P"
                }
                else -> {
                    highestPrice = eldoradoPrice
                    marketName = "ElDoradoP2P"
                }
            }

            it.text = "Precio más alto en: $marketName. Precio: ${String.format("%.2f", highestPrice)}"
        }
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
        lineWidth = 2.5f // Línea ligeramente más gruesa para mejor visibilidad
        setDrawCircles(true)
        circleRadius = 3f // Círculos ligeramente más grandes
        circleColors = listOf(color)
        setDrawValues(true) // Mostrar valores para el último punto
        valueTextSize = 10f // Tamaño de texto para los valores
        valueTextColor = color

        // Última entrada con valor visible
        if (entries.isNotEmpty()) {
            val lastEntry = entries.last()
            valueFormatter = LastValueFormatter(entries)
        }
    }

    fun stopUpdating() {
        handler.removeCallbacksAndMessages(null)
    }

    fun setInfoTextViews(tvBinanceInfo: TextView?, tvBitgetInfo: TextView?, tvEldoradoInfo: TextView?) {

    }
}