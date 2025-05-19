package com.example.limbo.Views

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.limbo.Model.Services.CryptoChangeMonitorService
import com.example.limbo.R
import com.example.limbo.ViewModel.Functions.graficoUSDTBOB
import com.github.mikephil.charting.charts.LineChart

class MainActivity : AppCompatActivity() {
    private lateinit var lineChart: LineChart
    private lateinit var graficoUSDTBOB: graficoUSDTBOB
    private val handler = Handler(Looper.getMainLooper())

    // Adapters for the RecyclerViews
    private lateinit var noticiasAdapter: NoticiasAdapter
    private lateinit var rankingAdapter: RankingAdapter
    private lateinit var bankAdapter: BankCardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()

        // Set status bar color
        window.statusBarColor = getColor(R.color.black)

        super.onCreate(savedInstanceState)

        // Verificar permisos de notificación de manera correcta
        checkNotificationPermission()

        setContentView(R.layout.activity_main)

        val tvHighestPrice = findViewById<TextView>(R.id.tvHighestPrice)

        // Initialize the chart
        lineChart = findViewById(R.id.lineChart)
        // Pass reference to the highest price TextView
        graficoUSDTBOB = graficoUSDTBOB(lineChart, tvHighestPrice)
        graficoUSDTBOB.initChart()


        // Initialize the news section
        setupNoticias()

        // Initialize the bank ranking section
        setupRanking()

        // Set up bank cards - UPDATED
        setupBanks()

        // Start updating the chart
        startUpdatingChart()

        // Verificar si existe el canal de notificación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel("crypto_price_channel")

            if (channel == null) {
                Log.d("MainActivity", "El canal de notificación no existe, creándolo")
                CryptoChangeMonitorService.createNotificationChannelStatic(this)
            } else {
                Log.d("MainActivity", "Canal de notificación existe con importancia: ${channel.importance}")

                // Si el canal existe pero tiene baja importancia, recrearlo
                if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                    Log.d("MainActivity", "Canal de notificación tiene baja importancia, recreando")
                    notificationManager.deleteNotificationChannel("crypto_price_channel")
                    CryptoChangeMonitorService.createNotificationChannelStatic(this)
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                PackageManager.PERMISSION_GRANTED -> {
                    Log.d("MainActivity", "Permiso de notificación ya concedido")
                    // Iniciar el servicio de monitoreo de precios
                    CryptoChangeMonitorService.schedulePriceMonitoring(this)
                }
                PackageManager.PERMISSION_DENIED -> {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        // El usuario ya rechazó el permiso anteriormente, mostrar diálogo explicativo
                        showNotificationPermissionRationale()
                    } else {
                        // Primera vez que se solicita o el usuario seleccionó "No volver a preguntar"
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            1001
                        )
                    }
                }
            }
        } else {
            // Para versiones anteriores a Android 13, no se necesita solicitar permiso explícitamente
            CryptoChangeMonitorService.schedulePriceMonitoring(this)
        }
    }

    // Método para mostrar un diálogo explicando por qué se necesitan las notificaciones
    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Notificaciones Desactivadas")
            .setMessage("Sin los permisos de notificación, no podrás recibir alertas cuando cambie el precio de USDT/BOB. Por favor, habilita las notificaciones en la configuración de la aplicación.")
            .setPositiveButton("Configuración") { _, _ ->
                // Abrir configuración de la aplicación
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupNoticias() {
        val recyclerNoticias = findViewById<RecyclerView>(R.id.recyclerNoticias)
        recyclerNoticias.layoutManager = LinearLayoutManager(this)

        // Sample data - this would come from your database
        val noticias = listOf(
            Noticia("BNB", "El Banco BNB realizó cambios en la rúbrica de los límites"),
            Noticia("BISA", "El Banco Bisa reanudó las compras por internet para nuevos usuarios"),
            Noticia("ECO", "El Banco Económico aumentó sus límites y cambió su modalidad"),
            Noticia("SOL", "El Banco Sol cambió su modalidad de Mensual a Semanal")
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

    private fun setupBanks() {
        val recyclerBanks = findViewById<RecyclerView>(R.id.recyclerBanks)
        recyclerBanks.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Data para las tarjetas bancarias
        val banks = listOf(
            BankItem(
                "BNB",
                "BNB",
                "Banco Nacional de Bolivia",
                R.drawable.logo_bnb,
                R.drawable.bank_bnb_background
            ),
            BankItem(
                "BISA",
                "BISA",
                "Banco BISA",
                R.drawable.logo_bisa,
                R.drawable.bank_bisa_background
            ),
            BankItem(
                "UNION",
                "BANCO UNIÓN",
                "Banco Unión de Bolivia",
                R.drawable.logo_union,
                R.drawable.bank_union_background
            )
        )

        bankAdapter = BankCardAdapter(this, banks)
        recyclerBanks.adapter = bankAdapter
    }

    private fun startUpdatingChart() {
        graficoUSDTBOB.fetchData()
        handler.postDelayed(object : Runnable {
            override fun run() {
                graficoUSDTBOB.fetchData()
                handler.postDelayed(this, 3000) // Update every 3 seconds
            }
        }, 3000)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Permiso de notificación concedido")
                // Iniciar el servicio ahora que tenemos permiso
                CryptoChangeMonitorService.schedulePriceMonitoring(this)
            } else {
                Log.d("MainActivity", "Permiso de notificación denegado")

                // Mostrar diálogo explicativo solo si el usuario no seleccionó "No volver a preguntar"
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showNotificationPermissionRationale()
                } else {
                    // El usuario seleccionó "No volver a preguntar"
                    Toast.makeText(this, "Para recibir alertas de precios, activa las notificaciones en Configuración", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        graficoUSDTBOB.stopUpdating()
    }

    // Data classes for the adapters
    data class Noticia(val banco: String, val descripcion: String)

    data class RankingItem(val position: Int, val bankName: String, val logoResource: Int, val description: String)

    // Adapter class for Noticias
    inner class NoticiasAdapter(private val noticias: List<Noticia>) :
        RecyclerView.Adapter<NoticiasAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvBanco: TextView = view.findViewById(R.id.tvBanco)
            val tvDescripcion: TextView = view.findViewById(R.id.tvDescripcion)
            val ivNotificationIcon: ImageView? = view.findViewById(R.id.ivNotificationIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_noticia, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val noticia = noticias[position]

            // Set bank name with appropriate color
            holder.tvBanco.text = noticia.banco
            when(noticia.banco) {
                "BNB" -> holder.tvBanco.setTextColor(holder.itemView.context.getColor(R.color.green_accent))
                "BISA" -> holder.tvBanco.setTextColor(holder.itemView.context.getColor(R.color.yellow))
                "ECO" -> holder.tvBanco.setTextColor(holder.itemView.context.getColor(R.color.red))
                "SOL" -> holder.tvBanco.setTextColor(holder.itemView.context.getColor(R.color.purple_200))
                else -> holder.tvBanco.setTextColor(holder.itemView.context.getColor(R.color.white))
            }

            holder.tvDescripcion.text = noticia.descripcion

            // Set notification icon tint to match bank color
            holder.ivNotificationIcon?.let {
                when(noticia.banco) {
                    "BNB" -> it.setColorFilter(holder.itemView.context.getColor(R.color.green_accent))
                    "BISA" -> it.setColorFilter(holder.itemView.context.getColor(R.color.yellow))
                    "ECO" -> it.setColorFilter(holder.itemView.context.getColor(R.color.red))
                    "SOL" -> it.setColorFilter(holder.itemView.context.getColor(R.color.purple_200))
                    else -> it.setColorFilter(holder.itemView.context.getColor(R.color.white))
                }
            }
        }

        override fun getItemCount() = noticias.size
    }

    // Adapter for ranking items
    inner class RankingAdapter(private val rankings: List<RankingItem>) :
        RecyclerView.Adapter<RankingAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
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
}