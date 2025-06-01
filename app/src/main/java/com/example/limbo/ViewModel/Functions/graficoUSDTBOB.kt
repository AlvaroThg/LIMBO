package com.example.limbo.ViewModel.Functions

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.limbo.R
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.Utils
import com.example.limbo.Model.Objects.CryptoMarketData
import com.example.limbo.Model.Apis.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class graficoUSDTBOB(
    private val lineChart: LineChart,
    private val tvHighestPrice: TextView? = null,
    private val tvBinancePrice: TextView? = null,
    private val tvBitgetPrice: TextView? = null,
    private val tvEldoradoPrice: TextView? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private val binanceEntries = mutableListOf<Entry>()
    private val bitgetEntries = mutableListOf<Entry>()
    private val eldoradoEntries = mutableListOf<Entry>()
    private var timeCounter = 0f
    private var isUpdating = false
    private var lastUpdateTime = 0L

    // Variables para almacenar los precios actuales
    private var binancePrice = 0f
    private var lastBinancePrice = 0f
    private var bitgetPrice = 0f
    private var lastBitgetPrice = 0f
    private var eldoradoPrice = 0f
    private var lastEldoradoPrice = 0f

    // Métodos getter para acceder a los precios desde fuera de la clase
    fun getBinancePrice(): Float = binancePrice
    fun getBitgetPrice(): Float = bitgetPrice
    fun getEldoradoPrice(): Float = eldoradoPrice

    // Colores mejorados para cada exchange
    private val binanceColor = Color.rgb(240, 185, 11) // Amarillo Binance
    private val bitgetColor = Color.rgb(0, 171, 169)   // Verde/turquesa Bitget
    private val eldoradoColor = Color.rgb(255, 128, 0) // Naranja para Eldorado

    init {
        initChart()
        startUpdatingChart()
    }

    fun initChart() {
        lineChart.apply {
            setBackgroundColor(Color.BLACK)
            setDrawGridBackground(false)
            setDrawBorders(false)

            // Configuraciones adicionales
            description.isEnabled = true
            description.text = "Precio USDT/BOB (P2P)"
            description.textSize = 12f
            description.textColor = Color.WHITE

            // Animación al inicio
            animateX(1000)

            // Enable touch gestures
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            // Si no hay datos, mostrar mensaje
            setNoDataText("Cargando datos...")
            setNoDataTextColor(Color.WHITE)

            // Establecer el mínimo visible de puntos
            setVisibleXRangeMinimum(6f)
            setVisibleXRangeMaximum(20f)

            // Listener para selección
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val price = String.format("%.2f", it.y)
                        Toast.makeText(context, "Precio: $price BOB", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onNothingSelected() {
                    // No hacer nada
                }
            })

            // Establecer margen adecuado
            extraBottomOffset = 10f
            extraTopOffset = 10f
            extraLeftOffset = 10f
            extraRightOffset = 10f
        }

        // Configurar el eje X
        val xAxis = lineChart.xAxis
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textSize = 10f
            textColor = Color.WHITE
            setDrawGridLines(false)
            axisLineColor = Color.GRAY
            axisLineWidth = 0.5f

            // Formateo de valores X (tiempo)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val minutes = (value % 60).toInt()
                    val hours = (value / 60).toInt()
                    return String.format("%02d:%02d", hours, minutes)
                }
            }
        }

        // Configurar el eje Y izquierdo
        val yAxis = lineChart.axisLeft
        yAxis.apply {
            textSize = 10f
            textColor = Color.WHITE
            setDrawGridLines(true)
            gridColor = Color.DKGRAY
            gridLineWidth = 0.3f
            axisLineColor = Color.GRAY
            axisLineWidth = 0.5f

            // Establecer líneas límite para valores clave
            removeAllLimitLines()
            val highestPrice = maxOf(binancePrice, bitgetPrice, eldoradoPrice)
            if (highestPrice > 0) {
                val limitLine = LimitLine(highestPrice, "Máximo: ${String.format("%.2f", highestPrice)}")
                limitLine.lineColor = Color.GREEN
                limitLine.lineWidth = 1f
                limitLine.textColor = Color.WHITE
                limitLine.textSize = 10f
                addLimitLine(limitLine)
            }

            // Formateo de valores Y
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.2f", value)
                }
            }
        }

        // Desactivar el eje derecho
        lineChart.axisRight.isEnabled = false

        // Configurar la leyenda
        lineChart.legend.apply {
            textSize = 10f
            textColor = Color.WHITE
            form = Legend.LegendForm.LINE
            formSize = 12f
            formLineWidth = 2f
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            yOffset = 10f
        }
    }

    fun startUpdatingChart() {
        if (!isUpdating) {
            isUpdating = true
            fetchData()
            scheduleNextUpdate()
        }
    }

    private fun scheduleNextUpdate() {
        handler.postDelayed({
            fetchData()
            scheduleNextUpdate()
        }, 5000) // Actualizar cada 5 segundos
    }

    fun fetchData() {
        // Registrar el tiempo de la última actualización
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < 4000) {
            // Si pasó menos de 4 segundos desde la última actualización, ignorar
            return
        }
        lastUpdateTime = currentTime

        val call = RetrofitClient.apiService.getUsdtBobData()
        call.enqueue(object : Callback<Map<String, CryptoMarketData>> {
            override fun onResponse(call: Call<Map<String, CryptoMarketData>>, response: Response<Map<String, CryptoMarketData>>) {
                if (response.isSuccessful && response.body() != null) {
                    response.body()?.let { dataMap ->
                        // Guardar precios anteriores
                        lastBinancePrice = binancePrice
                        lastBitgetPrice = bitgetPrice
                        lastEldoradoPrice = eldoradoPrice

                        // Actualizar precios actuales
                        binancePrice = dataMap["binancep2p"]?.ask ?: 0f
                        bitgetPrice = dataMap["bitgetp2p"]?.ask ?: 0f
                        eldoradoPrice = dataMap["eldoradop2p"]?.ask ?: 0f

                        Log.d("graficoUSDTBOB", "Binance: $binancePrice - Bitget: $bitgetPrice - Eldorado: $eldoradoPrice")

                        // Animar cambio de precios en los TextView
                        updatePriceLabels()

                        // Actualizar el gráfico con animación
                        updateChartWithAnimation(binancePrice, bitgetPrice, eldoradoPrice)

                        // Actualizar etiqueta de precio más alto
                        updateHighestPriceLabel()
                    }
                } else {
                    Log.e("graficoUSDTBOB", "Error en la respuesta: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Map<String, CryptoMarketData>>, t: Throwable) {
                Log.e("graficoUSDTBOB", "Error en la petición: ${t.message}")
            }
        })
    }

    private fun updatePriceLabels() {
        // Actualizar TextView de Binance con animación
        tvBinancePrice?.let {
            animatePriceChange(it, lastBinancePrice, binancePrice)
        }

        // Actualizar TextView de Bitget con animación
        tvBitgetPrice?.let {
            animatePriceChange(it, lastBitgetPrice, bitgetPrice)
        }

        // Actualizar TextView de Eldorado con animación
        tvEldoradoPrice?.let {
            animatePriceChange(it, lastEldoradoPrice, eldoradoPrice)
        }
    }

    private fun animatePriceChange(textView: TextView, oldValue: Float, newValue: Float) {
        if (oldValue == 0f || oldValue == newValue) {
            textView.text = "${String.format("%.2f", newValue)} BOB"
            return
        }

        val animator = ValueAnimator.ofFloat(oldValue, newValue)
        animator.duration = 1500  // Duración más larga para animación más suave
        animator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()  // Interpolador más suave
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            textView.text = "${String.format("%.2f", animatedValue)} BOB"

            // Cambiar color según aumente o disminuya
            if (newValue > oldValue) {
                textView.setTextColor(Color.GREEN)
            } else if (newValue < oldValue) {
                textView.setTextColor(Color.RED)
            }
        }

        // Añadir listener para el final de la animación
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Asegurar que el valor final es exactamente el nuevo valor
                textView.text = "${String.format("%.2f", newValue)} BOB"

                // Programar el retorno al color normal
                handler.postDelayed({
                    textView.setTextColor(Color.WHITE)
                }, 500)
            }
        })

        animator.start()
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

    fun updateChartWithAnimation(binancePrice: Float, bitgetPrice: Float, eldoradoPrice: Float) {
        // Función para limitar el tamaño de las listas
        fun <T> MutableList<T>.limitSize(maxSize: Int = 10) {
            while (size > maxSize) removeAt(0)
        }

        // Guardar la configuración de zoom y posición actual
        val visibleXRange = lineChart.visibleXRange
        val centerX = lineChart.lowestVisibleX + (lineChart.visibleXRange / 2f)

        // Añadir nuevos puntos al final
        binanceEntries.add(Entry(timeCounter, binancePrice)).also { binanceEntries.limitSize() }
        bitgetEntries.add(Entry(timeCounter, bitgetPrice)).also { bitgetEntries.limitSize() }
        eldoradoEntries.add(Entry(timeCounter, eldoradoPrice)).also { eldoradoEntries.limitSize() }
        timeCounter++

        // Crear data sets mejorados
        val dataSets = ArrayList<ILineDataSet>()

        // Crear y añadir DataSet para Binance
        if (binanceEntries.isNotEmpty()) {
            val binanceDataSet = createDataSet(binanceEntries, "Binance P2P", binanceColor)
            // Mejorar suavizado
            binanceDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            binanceDataSet.cubicIntensity = 0.1f // Menor valor para curvas más suaves
            dataSets.add(binanceDataSet)
        }

        // Crear y añadir DataSet para Bitget
        if (bitgetEntries.isNotEmpty()) {
            val bitgetDataSet = createDataSet(bitgetEntries, "Bitget P2P", bitgetColor)
            // Mejorar suavizado
            bitgetDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            bitgetDataSet.cubicIntensity = 0.1f
            dataSets.add(bitgetDataSet)
        }

        // Crear y añadir DataSet para Eldorado
        if (eldoradoEntries.isNotEmpty()) {
            val eldoradoDataSet = createDataSet(eldoradoEntries, "Eldorado P2P", eldoradoColor)
            // Mejorar suavizado
            eldoradoDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            eldoradoDataSet.cubicIntensity = 0.1f
            dataSets.add(eldoradoDataSet)
        }

        // Crear LineData con todos los conjuntos
        val lineData = LineData(dataSets)

        // Usar caché de dibujo para evitar parpadeo
        lineChart.setDrawingCacheEnabled(true)

        // Si el gráfico ya tiene datos, animar la transición
        if (lineChart.data != null && lineChart.data.dataSetCount > 0) {
            // Actualizar datos existentes con animación suave
            lineChart.data = lineData
            lineChart.notifyDataSetChanged()

            // Mantener el zoom y posición anteriores
            if (visibleXRange > 0) {
                lineChart.setVisibleXRange(visibleXRange, visibleXRange)
                lineChart.moveViewToX(centerX)
            }



        } else {
            // Primera vez que se establecen datos
            lineChart.data = lineData
            lineChart.invalidate()
            lineChart.animateXY(1000, 1000)
        }

        // Asegurar suficientes puntos visibles
        if (binanceEntries.size > 1) {
            lineChart.setVisibleXRangeMaximum(max(10f, binanceEntries.size.toFloat()))
            // Solo mover vista a X si es la primera carga
            if (lineChart.data == null || lineChart.data.dataSetCount == 0) {
                lineChart.moveViewToX(timeCounter - 1)
            }
        }
    }

    private fun createDataSet(entries: List<Entry>, label: String, color: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            this.color = color
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            circleHoleRadius = 2f
            circleColors = listOf(color)
            setDrawValues(true)
            valueTextSize = 9f
            valueTextColor = color

            // Modo cubico para suavizar líneas
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f

            // Sombra bajo la línea
            setDrawFilled(true)
            fillAlpha = 50

            if (Utils.getSDKInt() >= 18) {
                // Degradado para el fondo
                val drawable = getGradientDrawable(color)
                fillDrawable = drawable
            } else {
                fillColor = color
            }

            // Última entrada con valor visible
            if (entries.isNotEmpty()) {
                valueFormatter = LastValueFormatter(entries)
            }

            // Destacar última entrada
            highlightLineWidth = 1.5f
            highLightColor = Color.WHITE
            setDrawHighlightIndicators(true)

            // Efecto de línea punteada para algunos datasets
            if (label.contains("Bitget")) {
                enableDashedLine(10f, 5f, 0f)
            }
        }
    }

    private fun getGradientDrawable(color: Int): Drawable {
        val startColor = Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))
        val endColor = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))

        return ContextCompat.getDrawable(lineChart.context, R.drawable.bg_chart_gradient) ?:
        ContextCompat.getDrawable(lineChart.context, R.drawable.background_gradient)!!
    }

    fun stopUpdating() {
        isUpdating = false
        handler.removeCallbacksAndMessages(null)
    }
}