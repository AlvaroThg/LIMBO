package com.example.limbo.Model.Services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
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
            // Aquí iría la lógica para verificar los precios y enviar notificaciones
            // Por ahora solo registramos un mensaje
            Log.d(TAG, "Ejecutando monitoreo de precios")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error al monitorear precios: ${e.message}")
            Result.retry()
        }
    }
}