package com.mushroom.gearhelper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mushroom.gearhelper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
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

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupListeners() {
        binding.btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        binding.btnAccessibilityPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.switchDryRun.setOnCheckedChangeListener { _, isChecked ->
            config.dryRun = isChecked
            ScreenCaptureService.instance?.config?.dryRun = isChecked
        }

        binding.radioGroupConflict.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioPower -> "power"
                R.id.radioPause -> "pause"
                else -> "stats"
            }
            config.conflictMode = mode
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

            // Launch Screen Capture request
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
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
        // Start Screen Capture Service
        val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(captureIntent)
        } else {
            startService(captureIntent)
        }

        // Start Floating Overlay Service
        startService(Intent(this, FloatingOverlayService::class.java))

        // Minimize app so game is visible with overlay
        moveTaskToBack(true)
    }
}
