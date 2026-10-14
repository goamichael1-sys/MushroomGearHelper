package com.mushroom.gearhelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class GearAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GearAccessibility"
        var instance: GearAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We do not need event-based triggers since we use frame-based screen detection
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility Service Destroyed")
    }

    /**
     * Dispatches a tap gesture at the given screen coordinates (pixels).
     */
    fun tapAt(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Tap successfully executed at ($x, $y)")
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Tap cancelled at ($x, $y)")
                onComplete?.invoke(false)
            }
        }, null)

        if (!dispatched) {
            Log.e(TAG, "Failed to dispatch gesture at ($x, $y)")
            onComplete?.invoke(false)
        }
    }
}
