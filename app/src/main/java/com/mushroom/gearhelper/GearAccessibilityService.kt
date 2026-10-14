package com.mushroom.gearhelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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
        Log.i(TAG, "Accessibility Service Connected and Ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

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
     * Uses a 100ms stroke to guarantee games register the touch down/up sequence.
     */
    fun tapAt(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        val clickPath = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 150))
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i(TAG, "Tap executed successfully at ($x, $y)")
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Tap was cancelled by system at ($x, $y)")
                onComplete?.invoke(false)
            }
        }, null)

        if (!dispatched) {
            Log.e(TAG, "dispatchGesture returned false for ($x, $y) - check accessibility permissions")
            onComplete?.invoke(false)
        }
    }
}
