package com.example.limbo.ViewModel.Functions

import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Formateador personalizado que solo muestra el valor para la última entrada
 * en un conjunto de datos, dejando las demás entradas sin etiqueta.
 * Además, mejora el formato visual de los valores.
 */
class LastValueFormatter(
    private val dataSetEntries: List<Entry>
) : ValueFormatter() {

    override fun getPointLabel(entry: Entry): String {
        val lastEntry = dataSetEntries.lastOrNull()
        return if (entry == lastEntry) {
            // Formato con 2 decimales para mejor visualización
            String.format("%.2f", entry.y)
        } else {
            ""
        }
    }
}