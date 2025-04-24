package com.example.limbo.Views

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.limbo.R
import com.example.limbo.ViewModel.Functions.graficoUSDTBOB
import com.github.mikephil.charting.charts.LineChart
import android.widget.TextView
import androidx.cardview.widget.CardView

class MainActivity : AppCompatActivity() {
    private lateinit var lineChart: LineChart
    private lateinit var graficoUSDTBOB: graficoUSDTBOB
    private val handler = Handler(Looper.getMainLooper())

    // Adapters for the RecyclerViews
    private lateinit var noticiasAdapter: NoticiasAdapter
    private lateinit var rankingAdapter: RankingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the chart (now smaller)
        lineChart = findViewById(R.id.lineChart)
        graficoUSDTBOB = graficoUSDTBOB(lineChart)
        graficoUSDTBOB.initChart()

        // Initialize the news section
        setupNoticias()

        // Initialize the bank ranking section
        setupRanking()

        // Set up bank cards
        setupBankCards()

        // Start updating the chart
        startUpdatingChart()
    }

    private fun setupNoticias() {
        val recyclerNoticias = findViewById<RecyclerView>(R.id.recyclerNoticias)
        recyclerNoticias.layoutManager = LinearLayoutManager(this)

        // Sample data - this would come from your database
        val noticias = listOf(
            Noticia("BNB", "El Banco BNB realizó cambios en la rúbrica de los límites"),
            Noticia("BISA", "El Banco Bisa reanudó las compras por internet para nuevos usuarios"),
            Noticia("ECO", "El Banco Económico aumentó sus límites y cambió su modalidad")
        )

        noticiasAdapter = NoticiasAdapter(noticias)
        recyclerNoticias.adapter = noticiasAdapter
    }

    private fun setupRanking() {
        val recyclerRanking = findViewById<RecyclerView>(R.id.recyclerRanking)
        recyclerRanking.layoutManager = LinearLayoutManager(this)

        // Sample data - this would come from your database
        val rankings = listOf(
            RankingItem(1, "BISA", R.drawable.logo_bisa, "Límites Altos"),
            RankingItem(2, "BANCO UNIÓN", R.drawable.logo_union, "Permite Cuentas Nuevas"),
            RankingItem(3, "BNB", R.drawable.logo_bnb, "Flexibilidad Alta")
        )

        rankingAdapter = RankingAdapter(rankings)
        recyclerRanking.adapter = rankingAdapter
    }

    private fun setupBankCards() {
        val cardBNB = findViewById<CardView>(R.id.cardBNB)

        cardBNB.setOnClickListener {
            // Navigate to BNB details screen
            val intent = Intent(this, BankDetailsActivity::class.java)
            intent.putExtra("BANK_ID", "BNB")
            startActivity(intent)
        }

        // Add more bank cards as needed
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

// Data classes for the adapters
data class Noticia(val banco: String, val descripcion: String)

data class RankingItem(val position: Int, val bankName: String, val logoResource: Int, val description: String)

// Adapter for news items
class NoticiasAdapter(private val noticias: List<Noticia>) :
    RecyclerView.Adapter<NoticiasAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvBanco: TextView = view.findViewById(R.id.tvBanco)
        val tvDescripcion: TextView = view.findViewById(R.id.tvDescripcion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_noticia, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val noticia = noticias[position]
        holder.tvBanco.text = noticia.banco
        holder.tvDescripcion.text = noticia.descripcion
    }

    override fun getItemCount() = noticias.size
}

// Adapter for ranking items
class RankingAdapter(private val rankings: List<RankingItem>) :
    RecyclerView.Adapter<RankingAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvPosition: TextView = view.findViewById(R.id.tvPosition)
        val ivLogo: ImageView = view.findViewById(R.id.ivBankLogo)
        val tvBankName: TextView = view.findViewById(R.id.tvBankName)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rankingItem = rankings[position]
        holder.tvPosition.text = "TOP ${rankingItem.position}"
        holder.ivLogo.setImageResource(rankingItem.logoResource)
        holder.tvBankName.text = rankingItem.bankName
        holder.tvDescription.text = rankingItem.description
    }

    override fun getItemCount() = rankings.size
}