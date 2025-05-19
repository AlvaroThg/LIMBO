package com.example.limbo.Model.Services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import android.content.pm.PackageManager

class CryptoChangeMonitorService(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CryptoChangeMonitor"
        private const val CHANNEL_ID = "crypto_price_channel"
        private const val NOTIFICATION_ID = 1001

        // Preferences keys for storing last prices
        private const val PREFS_NAME = "crypto_prefs"
        private const val KEY_BINANCE_LAST_PRICE = "binance_last_price"
        private const val KEY_BITGET_LAST_PRICE = "bitget_last_price"
        private const val KEY_ELDORADO_LAST_PRICE = "eldorado_last_price"

        // Flag to detect first run
        private const val KEY_FIRST_RUN = "first_run_completed"

        // Debug flag - set to false in production
        private const val DEBUG_FORCE_NOTIFICATIONS = false

        fun schedulePriceMonitoring(context: Context, intervalMinutes: Int = 5) {
            // Ya no solicitar permisos aquí, eso se maneja en MainActivity
            // Solo crear el canal de notificación
            createNotificationChannelStatic(context)

            // Configure work constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Use a non-periodic work request with automatic rescheduling
            // This prevents the work manager from canceling tasks before they complete
            val monitoringRequest = OneTimeWorkRequestBuilder<CryptoChangeMonitorService>()
                .setConstraints(constraints)
                .addTag("price_monitoring")
                .build()

            // Use REPLACE policy to ensure we don't have multiple workers
            WorkManager.getInstance(context).enqueueUniqueWork(
                "crypto_price_monitoring",
                ExistingWorkPolicy.REPLACE,
                monitoringRequest
            )

            Log.d(TAG, "Price monitoring scheduled every $intervalMinutes minutes")

            // For debugging - try an immediate notification to verify permissions
            if (DEBUG_FORCE_NOTIFICATIONS) {
                sendDebugNotification(context)
            }
        }

        // Static method to create notification channel
        fun createNotificationChannelStatic(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Crypto Price Alerts"
                val descriptionText = "Notificaciones de cambios en precios USDT/BOB"
                val importance = NotificationManager.IMPORTANCE_DEFAULT  // Reduced importance
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created with importance: $importance")
            }
        }

        // Debug method to verify notifications are working
        private fun sendDebugNotification(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_arrow_up)
                    .setContentTitle("Debug Notification")
                    .setContentText("This is a test to verify notification permissions")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                try {
                    notificationManager.notify(9999, builder.build())
                    Log.d(TAG, "Debug notification sent")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send debug notification: ${e.message}")
                }
            }
        }
    }

    // Variables para evitar spam en los logs
    private var lastErrorCode = 0
    private var lastErrorMessage = ""

    override suspend fun doWork(): Result {
        return try {
            checkPriceChanges()

            // Reschedule the next check after this one completes
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val intervalMinutes = prefs.getString("notification_interval", "5")?.toIntOrNull() ?: 5

            // Delay next check by the specified interval
            val nextCheckDelay = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())

            // Schedule the next check
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val nextMonitoringRequest = OneTimeWorkRequestBuilder<CryptoChangeMonitorService>()
                .setConstraints(constraints)
                .addTag("price_monitoring")
                .setInitialDelay(nextCheckDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "crypto_price_monitoring",
                ExistingWorkPolicy.REPLACE,
                nextMonitoringRequest
            )

            Result.success()
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            if (errorMessage != lastErrorMessage) {
                Log.e(TAG, "Error monitoring prices: $errorMessage")
                lastErrorMessage = errorMessage
            }
            Result.retry()
        }
    }

    private suspend fun checkPriceChanges() {
        withContext(Dispatchers.IO) {
            // Verificar si hay conexión a Internet
            if (!isNetworkAvailable()) {
                Log.e(TAG, "No Internet connection available. Skipping price check.")
                return@withContext
            }

            // Check if notifications are enabled in preferences
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val notificationsEnabled = prefs.getBoolean("enable_notifications", true)

            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications disabled by user")
                return@withContext
            }

            // Verificar si tenemos permisos para notificaciones en Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionStatus = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "No permission for notifications. Skipping price check.")
                    return@withContext
                }
            }

            // Get which exchanges to monitor from preferences
            val exchangesToMonitor = prefs.getStringSet("exchanges_to_monitor",
                setOf("binancep2p", "bitgetp2p", "eldoradop2p")) ?: setOf("binancep2p")

            // Definir un umbral mínimo para evitar notificaciones por micro-fluctuaciones
            val minChangeThreshold = 0.0001f  // 0.01% de cambio mínimo

            Log.d(TAG, "Minimum threshold for notifications: ${minChangeThreshold * 100}%")

            try {
                val call = RetrofitClient.apiService.getUsdtBobData()
                try {
                    val response = call.execute()
                    if (response.isSuccessful && response.body() != null) {
                        val dataMap = response.body()!!
                        val cryptoPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val isFirstRun = !cryptoPrefs.getBoolean(KEY_FIRST_RUN, false)

                        if (isFirstRun) {
                            Log.d(TAG, "First run detected - storing initial prices")
                            cryptoPrefs.edit().putBoolean(KEY_FIRST_RUN, true).apply()
                        }

                        // Check Binance price if it's in the monitored exchanges
                        if ("binancep2p" in exchangesToMonitor) {
                            val binancePrice = dataMap["binancep2p"]?.ask ?: 0f
                            val lastBinancePrice = cryptoPrefs.getFloat(KEY_BINANCE_LAST_PRICE, 0f)
                            Log.d(TAG, "Binance: current=$binancePrice, last=$lastBinancePrice")

                            if (!isFirstRun && lastBinancePrice > 0f) {
                                val percentChange = ((binancePrice - lastBinancePrice) / lastBinancePrice)
                                val absPercentChange = Math.abs(percentChange)
                                Log.d(TAG, "Binance percent change: ${percentChange * 100}%")

                                // Notificar para cualquier cambio por encima del mínimo
                                if (absPercentChange >= minChangeThreshold || DEBUG_FORCE_NOTIFICATIONS) {
                                    val message = "LIM.BO: Binance nuevo precio ${String.format("%.2f", binancePrice)}"
                                    Log.d(TAG, "Price change detected for Binance: $message")
                                    sendNotification("Binance", message, binancePrice, "price_update", binancePrice > lastBinancePrice)
                                }
                            } else {
                                Log.d(TAG, "Skipping Binance notification check (first run or no previous data)")
                            }
                            cryptoPrefs.edit().putFloat(KEY_BINANCE_LAST_PRICE, binancePrice).apply()
                        }

                        // Check Bitget price if it's in the monitored exchanges
                        if ("bitgetp2p" in exchangesToMonitor) {
                            val bitgetPrice = dataMap["bitgetp2p"]?.ask ?: 0f
                            val lastBitgetPrice = cryptoPrefs.getFloat(KEY_BITGET_LAST_PRICE, 0f)
                            Log.d(TAG, "Bitget: current=$bitgetPrice, last=$lastBitgetPrice")

                            if (!isFirstRun && lastBitgetPrice > 0f) {
                                val percentChange = ((bitgetPrice - lastBitgetPrice) / lastBitgetPrice)
                                val absPercentChange = Math.abs(percentChange)
                                Log.d(TAG, "Bitget percent change: ${percentChange * 100}%")

                                // Notificar para cualquier cambio por encima del mínimo
                                if (absPercentChange >= minChangeThreshold || DEBUG_FORCE_NOTIFICATIONS) {
                                    val message = "LIM.BO: Bitget nuevo precio ${String.format("%.2f", bitgetPrice)}"
                                    Log.d(TAG, "Price change detected for Bitget: $message")
                                    sendNotification("Bitget", message, bitgetPrice, "price_update", bitgetPrice > lastBitgetPrice)
                                }
                            } else {
                                Log.d(TAG, "Skipping Bitget notification check (first run or no previous data)")
                            }
                            cryptoPrefs.edit().putFloat(KEY_BITGET_LAST_PRICE, bitgetPrice).apply()
                        }

                        // Check Eldorado price if it's in the monitored exchanges
                        if ("eldoradop2p" in exchangesToMonitor) {
                            val eldoradoPrice = dataMap["eldoradop2p"]?.ask ?: 0f
                            val lastEldoradoPrice = cryptoPrefs.getFloat(KEY_ELDORADO_LAST_PRICE, 0f)
                            Log.d(TAG, "Eldorado: current=$eldoradoPrice, last=$lastEldoradoPrice")

                            if (!isFirstRun && lastEldoradoPrice > 0f) {
                                val percentChange = ((eldoradoPrice - lastEldoradoPrice) / lastEldoradoPrice)
                                val absPercentChange = Math.abs(percentChange)
                                Log.d(TAG, "Eldorado percent change: ${percentChange * 100}%")

                                // Notificar para cualquier cambio por encima del mínimo
                                if (absPercentChange >= minChangeThreshold || DEBUG_FORCE_NOTIFICATIONS) {
                                    val message = "LIM.BO: Eldorado nuevo precio ${String.format("%.2f", eldoradoPrice)}"
                                    Log.d(TAG, "Price change detected for Eldorado: $message")
                                    sendNotification("Eldorado", message, eldoradoPrice, "price_update", eldoradoPrice > lastEldoradoPrice)
                                }
                            } else {
                                Log.d(TAG, "Skipping Eldorado notification check (first run or no previous data)")
                            }
                            cryptoPrefs.edit().putFloat(KEY_ELDORADO_LAST_PRICE, eldoradoPrice).apply()
                        }
                    } else {
                        // Registrar el error pero sin spam
                        if (response.code() != lastErrorCode) {
                            Log.e(TAG, "Error fetching data: ${response.code()} - ${response.message()}")
                            lastErrorCode = response.code()
                        }
                    }
                } catch (e: Exception) {
                    // Registrar el error pero evitar spam
                    val errorMessage = e.message ?: "Unknown error"
                    if (errorMessage != lastErrorMessage) {
                        Log.e(TAG, "Error fetching data: $errorMessage")
                        lastErrorMessage = errorMessage
                    }
                }
            } catch (e: Exception) {
                // Error al crear el cliente Retrofit o al acceder a la API
                val errorMessage = e.message ?: "Unknown error"
                if (errorMessage != lastErrorMessage) {
                    Log.e(TAG, "Error with Retrofit client: $errorMessage")
                    lastErrorMessage = errorMessage
                }
            }
        }
    }

    // Función para verificar la conexión a Internet
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
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
        // Check if notifications are enabled
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val notificationsEnabled = prefs.getBoolean("enable_notifications", true)

        if (!notificationsEnabled && !DEBUG_FORCE_NOTIFICATIONS) {
            Log.d(TAG, "Notifications are disabled")
            return
        }

        // Verificar si tenemos permisos para notificaciones en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "No permission for notifications. Skipping notification.")
                return
            }
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

        // Usar un sonido estándar para notificaciones
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Icono basado en si el precio subió o bajó
        val icon = if (isIncrease) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("Actualización de Precio")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        // Patrón de vibración corto para no molestar tanto
        try {
            val vibrationPattern = longArrayOf(0, 100)
            notificationBuilder.setVibrate(vibrationPattern)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set vibration pattern: ${e.message}")
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Use different notification IDs for different exchanges to show multiple notifications
        val notificationId = when (exchange) {
            "Binance" -> NOTIFICATION_ID
            "Bitget" -> NOTIFICATION_ID + 1
            "Eldorado" -> NOTIFICATION_ID + 2
            else -> NOTIFICATION_ID + 3
        }

        try {
            notificationManager.notify(notificationId, notificationBuilder.build())
            Log.d(TAG, "Notification sent for $exchange: $message")

            // Also try to vibrate the device
            try {
                vibrateDevice("price_update")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to vibrate: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification: ${e.message}")
            e.printStackTrace()
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateDevice(changeType: String) {
        try {
            // Usar un patrón de vibración corto para no molestar tanto
            val vibrationPattern = longArrayOf(0, 100)

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
            Log.e(TAG, "Error trying to vibrate: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Crypto Price Alerts"
            val descriptionText = "Notificaciones de cambios en precios USDT/BOB"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                vibrationPattern = longArrayOf(0, 100)  // Patrón de vibración más corto

                // Set sound
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created with importance: $importance")
        }
    }
}