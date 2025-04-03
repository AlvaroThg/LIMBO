package com.example.limbo.ViewModel.Functions

import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter

class LastValueFormatter(
    private val dataSetEntries: List<Entry>
) : ValueFormatter() {

    override fun getPointLabel(entry: Entry): String {
        val lastEntry = dataSetEntries.lastOrNull()
        return if (entry == lastEntry) {
            String.format("%.3f", entry.y)
        } else {
            ""
        }
    }
}