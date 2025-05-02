package com.example.limbo.Model.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.limbo.Model.Apis.RetrofitClient
import com.example.limbo.Model.Objects.CryptoMarketData
import com.example.limbo.R
import com.example.limbo.Views.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

/**
 * Servicio para monitorear cambios en los tipos de cambio y enviar notificaciones
 */
class CryptoChangeMonitorService(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CryptoChangeMonitor"
        private const val CHANNEL_ID = "crypto_price_channel"
        private const val NOTIFICATION_ID = 1001

        // Umbrales para detección de cambios significativos (en porcentaje)
        private const val SMALL_CHANGE_THRESHOLD = 1.0f // 1%
        private const val MEDIUM_CHANGE_THRESHOLD = 3.0f // 3%
        private const val LARGE_CHANGE_THRESHOLD = 5.0f // 5%

        // Clave para almacenar los últimos precios registrados
        private const val PREFS_NAME = "crypto_prefs"
        private const val KEY_BINANCE_LAST_PRICE = "binance_last_price"
        private const val KEY_BITGET_LAST_PRICE = "bitget_last_price"
        private const val KEY_ELDORADO_LAST_PRICE = "eldorado_last_price"

        /**
         * Método para programar el monitoreo periódico
         */
        fun schedulePriceMonitoring(context: Context, intervalMinutes: Int = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val monitoringRequest = PeriodicWorkRequestBuilder<CryptoChangeMonitorService>(
                intervalMinutes.toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("price_monitoring")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "crypto_price_monitoring",
                ExistingPeriodicWorkPolicy.REPLACE,
                monitoringRequest
            )

            Log.d(TAG, "Monitoreo de precios programado cada $intervalMinutes minutos")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            checkPriceChanges()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error al monitorear precios: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun checkPriceChanges() {
        withContext(Dispatchers.IO) {
            val call = RetrofitClient.apiService.getUsdtBobData()

            // Obtener datos de manera sincrónica en coroutine context
            try {
                val response = call.execute()
                if (response.isSuccessful && response.body() != null) {
                    val dataMap = response.body()!!

                    val binancePrice = dataMap["binancep2p"]?.ask ?: 0f
                    val bitgetPrice = dataMap["bitgetp2p"]?.ask ?: 0f
                    val eldoradoPrice = dataMap["eldoradop2p"]?.ask ?: 0f

                    // Comparar con los precios anteriores
                    val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastBinancePrice = sharedPrefs.getFloat(KEY_BINANCE_LAST_PRICE, 0f)
                    val lastBitgetPrice = sharedPrefs.getFloat(KEY_BITGET_LAST_PRICE, 0f)
                    val lastEldoradoPrice = sharedPrefs.getFloat(KEY_ELDORADO_LAST_PRICE, 0f)

                    // Verificar si hay cambios significativos
                    checkSignificantChange("Binance", binancePrice, lastBinancePrice)
                    checkSignificantChange("Bitget", bitgetPrice, lastBitgetPrice)
                    checkSignificantChange("Eldorado", eldoradoPrice, lastEldoradoPrice)

                    // Guardar los nuevos precios
                    sharedPrefs.edit().apply {
                        putFloat(KEY_BINANCE_LAST_PRICE, binancePrice)
                        putFloat(KEY_BITGET_LAST_PRICE, bitgetPrice)
                        putFloat(KEY_ELDORADO_LAST_PRICE, eldoradoPrice)
                        apply()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener datos: ${e.message}")
            }
        }
    }

    private fun checkSignificantChange(exchange: String, currentPrice: Float, lastPrice: Float) {
        if (lastPrice == 0f) return // Primera ejecución, no hay comparación

        val percentChange = ((currentPrice - lastPrice) / lastPrice) * 100
        val isIncrease = currentPrice > lastPrice

        when {
            Math.abs(percentChange) >= LARGE_CHANGE_THRESHOLD -> {
                val direction = if (isIncrease) "subido" else "bajado"
                val message = "El precio USDT/BOB en $exchange ha $direction un ${String.format("%.2f", Math.abs(percentChange))}%"
                sendNotification(exchange, message, currentPrice, "large_change")
            }
            Math.abs(percentChange) >= MEDIUM_CHANGE_THRESHOLD -> {
                val direction = if (isIncrease) "subido" else "bajado"
                val message = "El precio USDT/BOB en $exchange ha $direction un ${String.format("%.2f", Math.abs(percentChange))}%"
                sendNotification(exchange, message, currentPrice, "medium_change")
            }
            Math.abs(percentChange) >= SMALL_CHANGE_THRESHOLD -> {
                val direction = if (isIncrease) "subido" else "bajado"
                val message = "Pequeño cambio en $exchange: ${String.format("%.2f", Math.abs(percentChange))}% ($direction)"
                sendNotification(exchange, message, currentPrice, "small_change")
            }
        }
    }

    private fun sendNotification(exchange: String, message: String, currentPrice: Float, changeType: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal de notificación (requerido para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Cambios de Precio Crypto"
            val description = "Notificaciones sobre cambios significativos en el tipo de cambio USDT/BOB"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para abrir la app al tocar la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Determinar el icono basado en si subió o bajó
        val iconRes = if (message.contains("subido")) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down

        // Crear notificación
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("Cambio en tipo de cambio USDT/BOB")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Generar un ID único para cada notificación
        val notificationId = NOTIFICATION_ID + exchange.hashCode() + changeType.hashCode()

        // Mostrar la notificación
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Notificación enviada: $message")
    }
}