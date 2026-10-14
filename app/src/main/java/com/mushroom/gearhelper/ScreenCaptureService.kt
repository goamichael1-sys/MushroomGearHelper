package com.mushroom.gearhelper

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "MushroomCaptureChannel"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        var instance: ScreenCaptureService? = null
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var scanJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var config = HelperConfig()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    private fun startForegroundNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Mushroom Gear Helper Active")
                .setContentText("Auto-clicking Sell/Equip/Gear in Legend of Mushroom...")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground notification: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            try {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        Log.i(TAG, "MediaProjection stopped")
                        stopScanning()
                    }
                }, Handler(Looper.getMainLooper()))

                setupVirtualDisplay()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MediaProjection: ${e.message}", e)
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplay() {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            Log.i(TAG, "Virtual display setup for ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupVirtualDisplay: ${e.message}", e)
        }
    }

    fun startScanning() {
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            Log.i(TAG, "Starting gear scan loop (LIVE MODE)...")
            var lastAction = ""
            var retries = 0
            var smokeSince = 0L
            var lastGearTap = 0L
            var lastCloseTap = System.currentTimeMillis()
            var confirmAllowed = false

            while (isActive) {
                var sleepDelay = config.scanIdleMs
                val now = System.currentTimeMillis()

                try {
                    val bitmap = captureFrame()
                    if (bitmap != null) {
                        val metricsMap = mutableMapOf<String, ColorFractions>()
                        val w = bitmap.width.toFloat()
                        val h = bitmap.height.toFloat()

                        for ((name, reg) in config.regions) {
                            val r = Rect(
                                (reg.left * w).toInt(),
                                (reg.top * h).toInt(),
                                (reg.right * w).toInt(),
                                (reg.bottom * h).toInt()
                            )
                            metricsMap[name] = DecisionLogic.analyzeRegion(bitmap, r)
                        }

                        // 1. Check Dialog ("All attributes greater... continue selling?")
                        if (DecisionLogic.isDialog(metricsMap, config)) {
                            sleepDelay = config.postActionDelayMs
                            if (confirmAllowed) {
                                FloatingOverlayService.instance?.updateStatus("Confirming Sale")
                                val pt = config.points["confirm"]
                                if (pt != null) {
                                    GearAccessibilityService.instance?.tapAt(pt.x * w, pt.y * h)
                                }
                                confirmAllowed = false
                            } else {
                                FloatingOverlayService.instance?.updateStatus("Dialog: Cancelling to re-check")
                                val pt = config.points["cancel"]
                                if (pt != null) {
                                    GearAccessibilityService.instance?.tapAt(pt.x * w, pt.y * h)
                                    confirmAllowed = true
                                }
                            }
                        } else {
                            val popupReading = DecisionLogic.readPopup(metricsMap, config)

                            // 2. Gear Comparison Popup Visible
                            if (popupReading.popup) {
                                smokeSince = 0L
                                sleepDelay = config.scanPopupMs
                                val decision = DecisionLogic.decide(popupReading, config)
                                val statusText = "${decision.action.uppercase()}: ${decision.reason}"
                                FloatingOverlayService.instance?.updateStatus(statusText)

                                if (decision.action == "sell" || decision.action == "equip") {
                                    if (decision.action == lastAction) {
                                        retries++
                                    } else {
                                        retries = 0
                                    }

                                    if (retries <= 3) {
                                        val pt = config.points[decision.action]
                                        if (pt != null) {
                                            Log.i(TAG, "Tapping ${decision.action} at (${pt.x * w}, ${pt.y * h})")
                                            GearAccessibilityService.instance?.tapAt(pt.x * w, pt.y * h)
                                            lastAction = decision.action
                                            sleepDelay = config.postActionDelayMs
                                        }
                                    } else {
                                        FloatingOverlayService.instance?.updateStatus("Paused: No effect after 4 tries")
                                    }
                                } else if (decision.action == "pause") {
                                    FloatingOverlayService.instance?.updateStatus("Paused: Unreadable gear")
                                }
                            } else {
                                // 3. No Popup Open -> Idle / Lamp Smoke / Periodic Close
                                lastAction = ""
                                retries = 0

                                // Periodic close spot (X) tap every 60s
                                if (now - lastCloseTap >= 60000L) {
                                    lastCloseTap = now
                                    val closePt = config.points["close"]
                                    if (closePt != null) {
                                        Log.i(TAG, "Periodic close (X) tap at (${closePt.x * w}, ${closePt.y * h})")
                                        GearAccessibilityService.instance?.tapAt(closePt.x * w, closePt.y * h)
                                        FloatingOverlayService.instance?.updateStatus("Tapped close (X)")
                                        sleepDelay = 1000L
                                    }
                                } else {
                                    // Lamp Smoke / Gear detection above magic lamp
                                    val smokeFrac = metricsMap["smoke"]?.smoke ?: 0f
                                    val smokePresent = smokeFrac >= config.thresholds.smoke

                                    if (smokePresent) {
                                        if (smokeSince == 0L) smokeSince = now
                                        val smokeDurationSec = (now - smokeSince) / 1000
                                        FloatingOverlayService.instance?.updateStatus("Smoke ${smokeDurationSec}s (Tap gear...)")

                                        // Smoke has lingered for >= 3s, tap the floating gear!
                                        if (smokeDurationSec >= 3 && (now - lastGearTap >= 5000L)) {
                                            lastGearTap = now
                                            smokeSince = 0L
                                            val gearPt = config.points["gear"]
                                            if (gearPt != null) {
                                                Log.i(TAG, "Tapping gear above lamp at (${gearPt.x * w}, ${gearPt.y * h})")
                                                GearAccessibilityService.instance?.tapAt(gearPt.x * w, gearPt.y * h)
                                                FloatingOverlayService.instance?.updateStatus("Tapped gear above lamp!")
                                                sleepDelay = 1200L
                                            }
                                        }
                                    } else {
                                        smokeSince = 0L
                                        FloatingOverlayService.instance?.updateStatus("Watching for gear / lamp…")
                                    }
                                }
                            }
                        }
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scan loop: ${e.message}", e)
                }

                delay(sleepDelay)
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        FloatingOverlayService.instance?.updateStatus("Helper Paused")
    }

    private fun captureFrame(): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring frame: ${e.message}")
            return null
        } finally {
            image?.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        serviceScope.cancel()
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources: ${e.message}")
        }
        instance = null
    }
}
