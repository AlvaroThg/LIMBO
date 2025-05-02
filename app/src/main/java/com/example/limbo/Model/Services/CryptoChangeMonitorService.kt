package com.example.limbo.Model.Services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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

        // Debug flag - set to true to force notifications regardless of threshold
        private const val DEBUG_FORCE_NOTIFICATIONS = true



        fun schedulePriceMonitoring(context: Context, intervalMinutes: Int = 15) {
            // Request notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    if (context is androidx.appcompat.app.AppCompatActivity) {
                        context.requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            1001
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request notification permission: ${e.message}")
                }
            }

            // Create notification channel early
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
                val descriptionText = "Notifications for significant changes in USDT/BOB prices"
                val importance = NotificationManager.IMPORTANCE_HIGH  // Increased importance
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
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
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

    override suspend fun doWork(): Result {
        return try {
            checkPriceChanges()

            // Reschedule the next check after this one completes
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val intervalMinutes = prefs.getString("notification_interval", "15")?.toIntOrNull() ?: 15

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
            Log.e(TAG, "Error monitoring prices: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun checkPriceChanges() {
        withContext(Dispatchers.IO) {
            // Check if notifications are enabled in preferences
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val notificationsEnabled = prefs.getBoolean("enable_notifications", true)

            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications disabled by user")
                return@withContext
            }

            // Get which exchanges to monitor from preferences
            val exchangesToMonitor = prefs.getStringSet("exchanges_to_monitor",
                setOf("binancep2p", "bitgetp2p", "eldoradop2p")) ?: setOf("binancep2p")

            // Get threshold settings from preferences
            val smallChangeThreshold = 0.001f  // 0.1% de cambio
            val mediumChangeThreshold = 0.003f // 0.3% de cambio
            val largeChangeThreshold = 0.005f  // 0.5% de cambio

            Log.d(TAG, "Thresholds: small=$smallChangeThreshold, medium=$mediumChangeThreshold, large=$largeChangeThreshold")

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
                            Log.d(TAG, "Binance percent change: ${percentChange * 100}%")

                            checkSignificantChange(
                                "Binance", binancePrice, lastBinancePrice,
                                smallChangeThreshold, mediumChangeThreshold, largeChangeThreshold
                            )
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
                            Log.d(TAG, "Bitget percent change: ${percentChange * 100}%")

                            checkSignificantChange(
                                "Bitget", bitgetPrice, lastBitgetPrice,
                                smallChangeThreshold, mediumChangeThreshold, largeChangeThreshold
                            )
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
                            Log.d(TAG, "Eldorado percent change: ${percentChange * 100}%")

                            checkSignificantChange(
                                "Eldorado", eldoradoPrice, lastEldoradoPrice,
                                smallChangeThreshold, mediumChangeThreshold, largeChangeThreshold
                            )
                        } else {
                            Log.d(TAG, "Skipping Eldorado notification check (first run or no previous data)")
                        }
                        cryptoPrefs.edit().putFloat(KEY_ELDORADO_LAST_PRICE, eldoradoPrice).apply()
                    }
                } else {
                    Log.e(TAG, "Error fetching data: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching data: ${e.message}")
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
        if (lastPrice == 0f) {
            Log.d(TAG, "Skipping $exchange - no previous price available")
            return
        }  // Skip first execution when we don't have a previous value

        val percentChange = ((currentPrice - lastPrice) / lastPrice)
        val absPercentChange = Math.abs(percentChange)
        val isIncrease = currentPrice > lastPrice

        Log.d(TAG, "$exchange percent change: ${percentChange * 100}% (absolute: ${absPercentChange * 100}%)")
        Log.d(TAG, "Thresholds: small=$smallChangeThreshold, medium=$mediumChangeThreshold, large=$largeChangeThreshold")

        // Debugging flag to force notifications for testing
        if (DEBUG_FORCE_NOTIFICATIONS) {
            Log.d(TAG, "DEBUG MODE: Forcing test notification for $exchange")
            val message = "DEBUG: Test notification for $exchange. Current price: ${String.format("%.2f", currentPrice)}"
            sendNotification(exchange, message, currentPrice, "large_change", true)
            return
        }

        when {
            absPercentChange >= largeChangeThreshold -> {
                val direction = if (isIncrease) "increased" else "decreased"
                val message = "¡ALERTA IMPORTANTE! El precio USDT/BOB en $exchange ha $direction en ${String.format("%.2f", Math.abs(percentChange * 100))}% (de ${String.format("%.2f", lastPrice)} a ${String.format("%.2f", currentPrice)})"
                Log.d(TAG, "Large change detected for $exchange: $message")
                sendNotification(exchange, message, currentPrice, "large_change", isIncrease)
            }
            absPercentChange >= mediumChangeThreshold -> {
                val direction = if (isIncrease) "subido" else "bajado"
                val message = "Cambio notable: El precio USDT/BOB en $exchange ha $direction ${String.format("%.2f", Math.abs(percentChange * 100))}% (de ${String.format("%.2f", lastPrice)} a ${String.format("%.2f", currentPrice)})"
                Log.d(TAG, "Medium change detected for $exchange: $message")
                sendNotification(exchange, message, currentPrice, "medium_change", isIncrease)
            }
            absPercentChange >= smallChangeThreshold -> {
                val direction = if (isIncrease) "subido" else "bajado"
                val message = "Actualización: El precio USDT/BOB en $exchange ha $direction ${String.format("%.2f", Math.abs(percentChange * 100))}% (de ${String.format("%.2f", lastPrice)} a ${String.format("%.2f", currentPrice)})"
                Log.d(TAG, "Small change detected for $exchange: $message")
                sendNotification(exchange, message, currentPrice, "small_change", isIncrease)
            }
            else -> {
                Log.d(TAG, "No significant change for $exchange: ${absPercentChange * 100}% (below threshold of ${smallChangeThreshold * 100}%)")
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
        // Check if this type of change notification is enabled
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val specificChangesEnabled = when (changeType) {
            "small_change" -> prefs.getBoolean("notify_small_changes", true)
            "medium_change" -> prefs.getBoolean("notify_medium_changes", true)
            "large_change" -> prefs.getBoolean("notify_large_changes", true)
            else -> true
        }

        if (!specificChangesEnabled && !DEBUG_FORCE_NOTIFICATIONS) {
            Log.d(TAG, "Notification type $changeType is disabled")
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

        // Spanish titles for notifications
        val title = when (changeType) {
            "large_change" -> "¡ALERTA! Cambio de precio en $exchange"
            "medium_change" -> "Cambio notable en $exchange"
            else -> "Actualización de precio en $exchange"
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Always use high priority
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        // Add vibration pattern
        if (changeType == "large_change" || changeType == "medium_change" || DEBUG_FORCE_NOTIFICATIONS) {
            try {
                val vibrationPattern = when (changeType) {
                    "large_change" -> longArrayOf(0, 500, 200, 500, 200, 500)
                    "medium_change" -> longArrayOf(0, 300, 200, 300)
                    else -> longArrayOf(0, 200)
                }
                notificationBuilder.setVibrate(vibrationPattern)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set vibration pattern: ${e.message}")
            }
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
            if (changeType == "large_change" || changeType == "medium_change" || DEBUG_FORCE_NOTIFICATIONS) {
                try {
                    vibrateDevice(changeType)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to vibrate: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification: ${e.message}")
            e.printStackTrace()
        }
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
            Log.e(TAG, "Error trying to vibrate: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Crypto Price Alerts"
            val descriptionText = "Notifications for significant changes in USDT/BOB prices"
            val importance = NotificationManager.IMPORTANCE_HIGH  // Using HIGH importance
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)  // Show badge on app icon
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC  // Show on lock screen
                vibrationPattern = longArrayOf(0, 500, 200, 500)  // Set default vibration pattern

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