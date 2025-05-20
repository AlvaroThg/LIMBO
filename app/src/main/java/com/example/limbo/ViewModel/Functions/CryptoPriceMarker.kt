package com.example.limbo.ViewModel.Functions

import android.content.Context
import android.widget.TextView
import com.example.limbo.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

/**
 * Marcador personalizado para mostrar información detallada
 * cuando el usuario toca un punto en el gráfico.
 */
class CryptoPriceMarker(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let {
            val price = String.format("%.2f", e.y)
            val time = format.format(Date())
            tvContent.text = "$price BOB\n$time"
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}