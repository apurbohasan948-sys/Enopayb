package com.example.core.device

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

data class FlashlightResult(
    val success: Boolean,
    val isTorchOn: Boolean,
    val message: String,
    val error: String? = null
)

class FlashlightController(private val context: Context) {
    private val TAG = "JARVIS_Flashlight"
    private var torchState = false

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    fun isFlashlightSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    fun setTorchMode(enable: Boolean): FlashlightResult {
        if (!isFlashlightSupported()) {
            return FlashlightResult(
                success = false,
                isTorchOn = false,
                message = "Flashlight hardware is not available on this device.",
                error = "NO_FLASH_HARDWARE"
            )
        }

        val cm = cameraManager ?: return FlashlightResult(
            success = false,
            isTorchOn = false,
            message = "CameraManager is unavailable.",
            error = "SERVICE_UNAVAILABLE"
        )

        return try {
            val cameraId = findTorchCameraId(cm)
            if (cameraId == null) {
                return FlashlightResult(
                    success = false,
                    isTorchOn = false,
                    message = "Could not find a camera with an operational flash unit.",
                    error = "NO_TORCH_CAMERA"
                )
            }

            cm.setTorchMode(cameraId, enable)
            torchState = enable
            FlashlightResult(
                success = true,
                isTorchOn = enable,
                message = if (enable) "Turned ON flashlight." else "Turned OFF flashlight."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
            FlashlightResult(
                success = false,
                isTorchOn = torchState,
                message = "Failed to toggle flashlight: ${e.message}",
                error = e.localizedMessage
            )
        }
    }

    fun toggleTorch(): FlashlightResult {
        return setTorchMode(!torchState)
    }

    private fun findTorchCameraId(cm: CameraManager): String? {
        try {
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val flashAvail = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (flashAvail && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
            // Fallback to any camera with flash
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    return id
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding torch camera ID", e)
        }
        return null
    }
}
