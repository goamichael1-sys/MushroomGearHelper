package com.mushroom.gearhelper

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat

class FloatingOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingOverlayService"
        var instance: FloatingOverlayService? = null
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var tvStatus: TextView? = null
    private var btnPlayPause: TextView? = null
    private var isPlaying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            initFloatingWidget()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing floating widget: ${e.message}", e)
        }
    }

    private fun initFloatingWidget() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_MushroomGearHelper)
        val inflater = LayoutInflater.from(contextThemeWrapper)
        floatView = inflater.inflate(R.layout.layout_floating_widget, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        windowManager?.addView(floatView, layoutParams)

        tvStatus = floatView?.findViewById(R.id.tvStatus)
        btnPlayPause = floatView?.findViewById(R.id.btnPlayPause)
        val btnClose = floatView?.findViewById<TextView>(R.id.btnCloseWidget)
        val dragHandle = floatView?.findViewById<View>(R.id.floatingRoot)

        btnPlayPause?.setOnClickListener {
            togglePlayPause()
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }

        dragHandle?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                            try {
                                windowManager?.updateViewLayout(floatView, layoutParams)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating overlay layout: ${e.message}")
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun togglePlayPause() {
        isPlaying = !isPlaying
        if (isPlaying) {
            btnPlayPause?.text = "⏸"
            btnPlayPause?.setTextColor(ContextCompat.getColor(this, R.color.accent_orange))
            tvStatus?.text = "Scanning..."
            ScreenCaptureService.instance?.startScanning()
        } else {
            btnPlayPause?.text = "▶"
            btnPlayPause?.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            tvStatus?.text = "Paused"
            ScreenCaptureService.instance?.stopScanning()
        }
    }

    fun updateStatus(status: String) {
        tvStatus?.post {
            tvStatus?.text = status
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatView != null) {
            try {
                windowManager?.removeView(floatView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing float view: ${e.message}")
            }
            floatView = null
        }
        ScreenCaptureService.instance?.stopScanning()
        instance = null
    }
}
