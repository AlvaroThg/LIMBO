package com.example.limbo.Views

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.limbo.Model.Services.CryptoChangeMonitorService
import com.example.limbo.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        // Set up back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Set up listeners for preference changes
            val intervalPref = findPreference<EditTextPreference>("notification_interval")
            intervalPref?.setOnPreferenceChangeListener { _, newValue ->
                try {
                    val interval = (newValue as String).toInt()
                    if (interval >= 15) { // At least 15 minutes to prevent overloading
                        CryptoChangeMonitorService.schedulePriceMonitoring(requireContext(), interval)
                        true
                    } else {
                        // Show error message
                        false
                    }
                } catch (e: NumberFormatException) {
                    false
                }
            }

            // Set up changes for notification thresholds
            val enableNotificationsPref = findPreference<SwitchPreferenceCompat>("enable_notifications")
            enableNotificationsPref?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    val interval = preferenceManager.sharedPreferences?.getString("notification_interval", "15")?.toIntOrNull() ?: 15
                    CryptoChangeMonitorService.schedulePriceMonitoring(requireContext(), interval)
                } else {
                    // Cancel scheduled notifications
                    androidx.work.WorkManager.getInstance(requireContext())
                        .cancelUniqueWork("crypto_price_monitoring")
                }
                true
            }
        }
    }
}