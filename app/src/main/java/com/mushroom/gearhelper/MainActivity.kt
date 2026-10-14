package com.mushroom.gearhelper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mushroom.gearhelper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "MushroomPrefs"
        private const val KEY_SMOKE_LINGER = "smoke_linger_sec"
        private const val KEY_CLOSE_INTERVAL = "close_interval_sec"
        private const val KEY_CONFLICT_MODE = "conflict_mode"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private val config = HelperConfig()

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startServices(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission is required to read gear stats.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedPreferences()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun loadSavedPreferences() {
        val savedSmokeLinger = prefs.getInt(KEY_SMOKE_LINGER, 5)
        val savedCloseInterval = prefs.getInt(KEY_CLOSE_INTERVAL, 30)
        val savedConflictMode = prefs.getString(KEY_CONFLICT_MODE, "stats") ?: "stats"

        config.smokeLingerSec = savedSmokeLinger
        config.closeIntervalSec = savedCloseInterval
        config.conflictMode = savedConflictMode

        binding.sliderSmokeLinger.value = savedSmokeLinger.toFloat().coerceIn(2f, 15f)
        binding.tvSmokeLingerLabel.text = "Smoke Hover Duration: ${savedSmokeLinger}s"

        binding.sliderCloseInterval.value = savedCloseInterval.toFloat().coerceIn(10f, 120f)
        binding.tvCloseIntervalLabel.text = "Close (X) Click Interval: ${savedCloseInterval}s"

        when (savedConflictMode) {
            "power" -> binding.radioPower.isChecked = true
            "pause" -> binding.radioPause.isChecked = true
            else -> binding.radioStats.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivity(intent)
                }
            }
        }

        binding.btnAccessibilityPermission.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Open Android Settings -> Accessibility", Toast.LENGTH_LONG).show()
            }
        }

        // Smoke Linger Slider
        binding.sliderSmokeLinger.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val sec = value.toInt()
                config.smokeLingerSec = sec
                binding.tvSmokeLingerLabel.text = "Smoke Hover Duration: ${sec}s"
                prefs.edit().putInt(KEY_SMOKE_LINGER, sec).apply()
                ScreenCaptureService.instance?.config?.smokeLingerSec = sec
            }
        }

        // Close (X) Interval Slider
        binding.sliderCloseInterval.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val sec = value.toInt()
                config.closeIntervalSec = sec
                binding.tvCloseIntervalLabel.text = "Close (X) Click Interval: ${sec}s"
                prefs.edit().putInt(KEY_CLOSE_INTERVAL, sec).apply()
                ScreenCaptureService.instance?.config?.closeIntervalSec = sec
            }
        }

        // Conflict Radio Group
        binding.radioGroupConflict.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioPower -> "power"
                R.id.radioPause -> "pause"
                else -> "stats"
            }
            config.conflictMode = mode
            prefs.edit().putString(KEY_CONFLICT_MODE, mode).apply()
            ScreenCaptureService.instance?.config?.conflictMode = mode
        }

        binding.btnStartHelper.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Floating Overlay permission first (Step 1).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!GearAccessibilityService.isRunning) {
                Toast.makeText(this, "Please enable Accessibility Service first (Step 2).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting screen capture: ${e.message}", e)
                Toast.makeText(this, "Failed to request screen capture: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updatePermissionStatus() {
        val overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val accessibilityGranted = GearAccessibilityService.isRunning

        if (overlayGranted) {
            binding.btnOverlayPermission.text = "✓ Overlay Permission Granted"
            binding.btnOverlayPermission.isEnabled = false
            binding.btnOverlayPermission.setBackgroundColor(ContextCompat.getColor(this, R.color.teal_700))
        } else {
            binding.btnOverlayPermission.text = "Grant Overlay Permission"
            binding.btnOverlayPermission.isEnabled = true
        }

        if (accessibilityGranted) {
            binding.btnAccessibilityPermission.text = "✓ Accessibility Service Enabled"
            binding.btnAccessibilityPermission.isEnabled = false
            binding.btnAccessibilityPermission.setBackgroundColor(ContextCompat.getColor(this, R.color.teal_700))
        } else {
            binding.btnAccessibilityPermission.text = "Enable Accessibility Service"
            binding.btnAccessibilityPermission.isEnabled = true
        }
    }

    private fun startServices(resultCode: Int, data: Intent) {
        try {
            val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(captureIntent)
            } else {
                startService(captureIntent)
            }

            // Sync current config into service
            ScreenCaptureService.instance?.config?.let {
                it.smokeLingerSec = config.smokeLingerSec
                it.closeIntervalSec = config.closeIntervalSec
                it.conflictMode = config.conflictMode
            }

            startService(Intent(this, FloatingOverlayService::class.java))
            moveTaskToBack(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services: ${e.message}", e)
            Toast.makeText(this, "Error starting services: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
