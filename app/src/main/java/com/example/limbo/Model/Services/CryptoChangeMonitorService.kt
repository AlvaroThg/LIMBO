package com.example.limbo.Model.Services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.example.limbo.Model.Apis.RetrofitClient
import com.example.limbo.R
import com.example.limbo.Views.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CryptoChangeMonitorService (
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CryptoChangeMonitor"
        private const val CHANNEL_ID = "crypto_price_channel"
        private const val NOTIFICATION_ID = 1001


        private const val PREFS_NAME = "crypto_prefs"
        private const val KEY_BINANCE_LAST_PRICE = "binance_last_price"
        private const val KEY_BITGET_LAST_PRICE = "bitget_last_price"
        private const val KEY_ELDORADO_LAST_PRICE = "eldorado_last_price"

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

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val notificationsEnabled = prefs.getBoolean("enable_notifications", true)

            if (!notificationsEnabled) {
                Log.d(TAG, "Notificaciones deshabilitadas por el usuario")
                return@withContext
            }

            val exchangesToMonitor = prefs.getStringSet("exchanges_to_monitor",
                setOf("binancep2p", "bitgetp2p", "eldoradop2p")) ?: setOf("binancep2p")


            val smallChangeThreshold = prefs.getInt("small_change_threshold", 1).toFloat()
            val mediumChangeThreshold = prefs.getInt("medium_change_threshold", 3).toFloat()
            val largeChangeThreshold = prefs.getInt("large_change_threshold", 5).toFloat()

            val call = RetrofitClient.apiService.getUsdtBobData()

            try {
                val response = call.execute()
                if (response.isSuccessful && response.body() != null) {
                    val dataMap = response.body()!!
                    val cryptoPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                    if ("binancep2p" in exchangesToMonitor) {
                        val binancePrice = dataMap["binancep2p"]?.ask ?: 0f
                        val lastBinancePrice = cryptoPrefs.getFloat(KEY_BINANCE_LAST_PRICE, 0f)
                        checkSignificantChange(
                            "Binance", binancePrice, lastBinancePrice,
                            smallChangeThreshold, mediumChangeThreshold, largeChangeThreshold
                        )
                        cryptoPrefs.edit().putFloat(KEY_BINANCE_LAST_PRICE, binancePrice).apply()
                    }

                    if ("bitgetp2p" in exchangesToMonitor) {
                        val bitgetPrice = dataMap["bitgetp2p"]?.ask ?: 0f
                        val lastBitgetPrice = cryptoPrefs.getFloat(KEY_BITGET_LAST_PRICE, 0f)
                        checkSignificantChange(
                            "Bitget", bitgetPrice, lastBitgetPrice,
                            smallChangeThreshold, mediumChangeThreshold, largeChangeThreshold
                        )
                        cryptoPrefs.edit().putFloat(KEY_BITGET_LAST_PRICE, bitgetPrice).apply()
                    }

                    if ("eldoradop2p" in exchangesToMonitor) {
                        val eldoradoPrice = dataMap["eldoradop2p"]?.ask ?: 0f
                        val lastEldoradoPrice = cryptoPrefs.getFloat(KEY_ELDORADO_LAST_PRICE, 0f)
                        checkSignificantChange(
                            "Eldorado", eldoradoPrice, lastEldoradoPrice,
                            smallChangeThreshold, mediumChangeThreshold, largeChangeThreshold
                        )
                        cryptoPrefs.edit().putFloat(KEY_ELDORADO_LAST_PRICE, eldoradoPrice).apply()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener datos: ${e.message}")
            }
        }
    }

    private fun checkSignificantChange(
        exchange: String,
        currentPrice: Float,
        lastPrice: Float,
        smallChangeThreshold: Float,
        mediumChangeThreshold: Float,
        largeChangeThreshold: Float
    ) {
        if (lastPrice == 0f) return

        val percentChange = ((currentPrice - lastPrice) / lastPrice) * 100
        val isIncrease = currentPrice > lastPrice

        when {
            Math.abs(percentChange) >= largeChangeThreshold -> {
                val direction = if (isIncrease) "subido" else "bajado"
                val message = "¡ALERTA IMPORTANTE! El precio USDT/BOB en $exchange ha $direction un ${String.format("%.2f", Math.abs(percentChange))}% (de ${String.format("%.2f", lastPrice)} a ${String.format("%.2f", currentPrice)})"
                sendNotification(exchange, message, currentPrice, "large_change", isIncrease)
            }
            Math.abs(percentChange) >= mediumChangeThreshold -> {
                val direction = if (isIncrease) "subido" else "bajado"
                val message = "Cambio notable: El precio USDT/BOB en $exchange ha $direction un ${String.format("%.2f", Math.abs(percentChange))}% (de ${String.format("%.2f", lastPrice)} a ${String.format("%.2f", currentPrice)})"
                sendNotification(exchange, message, currentPrice, "medium_change", isIncrease)
            }
            Math.abs(percentChange) >= smallChangeThreshold -> {
                val direction = if (isIncrease) "subido" else "bajado"
                val message = "Actualización: El precio USDT/BOB en $exchange ha $direction un ${String.format("%.2f", Math.abs(percentChange))}% (de ${String.format("%.2f", lastPrice)} a ${String.format("%.2f", currentPrice)})"
                sendNotification(exchange, message, currentPrice, "small_change", isIncrease)
            }
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun sendNotification(
        exchange: String,
        message: String,
        currentPrice: Float,
        changeType: String,
        isIncrease: Boolean
    ) {

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)


        val specificChangesEnabled = when (changeType) {
            "small_change" -> prefs.getBoolean("notify_small_changes", true)
            "medium_change" -> prefs.getBoolean("notify_medium_changes", true)
            "large_change" -> prefs.getBoolean("notify_large_changes", true)
            else -> true
        }

        if (!specificChangesEnabled) {
            Log.d(TAG, "Notificación de tipo $changeType está deshabilitada")
            return
        }

        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("exchange", exchange)
            putExtra("price", currentPrice)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = when (changeType) {
            "large_change" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            "medium_change" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val icon = if (isIncrease) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("Cambio de precio en $exchange")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(when (changeType) {
                "large_change" -> NotificationCompat.PRIORITY_HIGH
                "medium_change" -> NotificationCompat.PRIORITY_DEFAULT
                else -> NotificationCompat.PRIORITY_LOW
            })
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (changeType == "large_change" || changeType == "medium_change") {
            vibrateDevice(changeType)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = when (exchange) {
            "Binance" -> NOTIFICATION_ID
            "Bitget" -> NOTIFICATION_ID + 1
            "Eldorado" -> NOTIFICATION_ID + 2
            else -> NOTIFICATION_ID + 3
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(TAG, "Notificación enviada para $exchange: $message")
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateDevice(changeType: String) {
        try {
            val vibrationPattern = when (changeType) {
                "large_change" -> longArrayOf(0, 500, 200, 500, 200, 500)
                "medium_change" -> longArrayOf(0, 300, 200, 300)
                else -> longArrayOf(0, 200)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(vibrationPattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al intentar vibrar: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas de Precio Crypto"
            val descriptionText = "Notificaciones de cambios significativos en precios USDT/BOB"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}