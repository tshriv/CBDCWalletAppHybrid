package com.ts.cbdcwalletapp.security

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class UsbTokenState {
    object Disconnected : UsbTokenState()
    data class PermissionRequired(val device: UsbDevice) : UsbTokenState()
    data class Initializing(val deviceName: String) : UsbTokenState()
    data class Ready(val deviceName: String) : UsbTokenState()
    object Authenticating : UsbTokenState()
    data class Authenticated(val deviceName: String) : UsbTokenState()
    data class Error(val message: String, val canRetry: Boolean = true) : UsbTokenState()
}

class UsbTokenManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbTokenManager"
        const val ACTION_USB_PERMISSION = "com.ts.cbdcwalletapp.USB_PERMISSION"

        // Watchdata / TrusToken vendor and product IDs
        const val TRUSTOKEN_VENDOR_ID = 10381
        const val TRUSTOKEN_PRODUCT_ID = 64
    }

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _tokenState = MutableStateFlow<UsbTokenState>(UsbTokenState.Disconnected)
    val tokenState: StateFlow<UsbTokenState> = _tokenState.asStateFlow()

    private var activeConnection: UsbDeviceConnection? = null
    private var activeDevice: UsbDevice? = null
    private var isReceiverRegistered = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var monitorJob: Job? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.d(TAG, "USB Permission result: granted=$permissionGranted, device=$device")

                    if (permissionGranted && device != null) {
                        initializeDevice(device)
                    } else {
                        _tokenState.value = UsbTokenState.Error("USB permission was denied. Please grant permission to continue.")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB device attached")
                    checkAndInitializeToken()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    Log.d(TAG, "USB device detached: $device")
                    if (device == null || activeDevice?.deviceId == device.deviceId) {
                        handleDeviceDetached()
                    }
                }
            }
        }
    }

    fun register() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context,
                    usbReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            isReceiverRegistered = true
        }

        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(1000)
                verifyTokenPresence()
            }
        }

        checkAndInitializeToken()
    }

    fun unregister() {
        monitorJob?.cancel()
        monitorJob = null
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
            isReceiverRegistered = false
        }
    }

    /**
     * Periodically checks if the active device has been disconnected.
     */
    fun verifyTokenPresence() {
        val manager = usbManager ?: return
        val deviceList = manager.deviceList
        if (activeDevice != null || _tokenState.value !is UsbTokenState.Disconnected) {
            if (deviceList.isNullOrEmpty()) {
                handleDeviceDetached()
                return
            }
            val currentActive = activeDevice
            if (currentActive != null && !deviceList.containsKey(currentActive.deviceName)) {
                handleDeviceDetached()
                return
            }
        }
    }

    /**
     * Checks connected USB devices, requests permission if needed, and initializes the token.
     */
    fun checkAndInitializeToken() {
        val manager = usbManager
        if (manager == null) {
            _tokenState.value = UsbTokenState.Error("USB Service not available on this device.")
            return
        }

        val deviceList = manager.deviceList
        if (deviceList.isNullOrEmpty()) {
            if (activeDevice != null || _tokenState.value !is UsbTokenState.Disconnected) {
                handleDeviceDetached()
            }
            return
        }

        // Find matching TrusToken or Smart Card Reader device
        val targetDevice = deviceList.values.firstOrNull { isSmartCardReader(it) }
            ?: deviceList.values.firstOrNull() // Fallback to any attached device if single device attached

        if (targetDevice == null) {
            if (activeDevice != null || _tokenState.value !is UsbTokenState.Disconnected) {
                handleDeviceDetached()
            }
            return
        }

        activeDevice = targetDevice

        if (!manager.hasPermission(targetDevice)) {
            _tokenState.value = UsbTokenState.PermissionRequired(targetDevice)
            requestUsbPermission(targetDevice)
        } else {
            initializeDevice(targetDevice)
        }
    }

    fun requestUsbPermission(device: UsbDevice? = activeDevice) {
        val target = device ?: activeDevice ?: return
        val manager = usbManager ?: return

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )

        Log.d(TAG, "Requesting USB permission for device ${target.deviceName}")
        manager.requestPermission(target, permissionIntent)
    }

    private fun initializeDevice(device: UsbDevice) {
        val manager = usbManager ?: return
        _tokenState.value = UsbTokenState.Initializing(device.productName ?: device.deviceName)

        try {
            activeConnection?.close()
            val connection = manager.openDevice(device)
            if (connection == null) {
                _tokenState.value = UsbTokenState.Error("Failed to open USB device connection.")
                return
            }

            activeConnection = connection
            val fd = connection.fileDescriptor
            Log.d(TAG, "USB Device opened, fd=$fd")

            val ret = TrusToken.libint(fd)
            Log.d(TAG, "TrusToken.libint(fd) returned $ret")

            if (ret == 0) {
                val name = device.productName ?: "TrusToken"
                _tokenState.value = UsbTokenState.Ready(name)
            } else {
                _tokenState.value = UsbTokenState.Error("Failed to initialize TrusToken library (code $ret).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing USB token", e)
            _tokenState.value = UsbTokenState.Error("Initialization error: ${e.localizedMessage}")
        }
    }

    fun login(pin: String): Result<String> {
        val currentState = _tokenState.value
        if (currentState !is UsbTokenState.Ready && currentState !is UsbTokenState.Error) {
            return Result.failure(IllegalStateException("Token is not ready for authentication."))
        }

        _tokenState.value = UsbTokenState.Authenticating

        return try {
            val result = TrusToken.login(pin)
            Log.d(TAG, "TrusToken.login result: $result")

            if (result == "Login Success") {
                val devName = activeDevice?.productName ?: "TrusToken"
                _tokenState.value = UsbTokenState.Authenticated(devName)
                Result.success(result)
            } else {
                val devName = activeDevice?.productName ?: "TrusToken"
                _tokenState.value = UsbTokenState.Ready(devName)
                Result.failure(Exception(result))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during token login", e)
            _tokenState.value = UsbTokenState.Error("Token error: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    fun logout(): String {
        return try {
            val res = TrusToken.logout()
            Log.d(TAG, "TrusToken.logout result: $res")
            if (activeDevice != null && activeConnection != null) {
                _tokenState.value = UsbTokenState.Ready(activeDevice?.productName ?: "TrusToken")
            } else {
                _tokenState.value = UsbTokenState.Disconnected
            }
            res
        } catch (e: Exception) {
            Log.e(TAG, "Error logging out token", e)
            "Error: ${e.message}"
        }
    }

    private fun handleDeviceDetached() {
        if (_tokenState.value is UsbTokenState.Disconnected && activeDevice == null && activeConnection == null) {
            return
        }
        Log.d(TAG, "Handling USB device detachment and resetting session")
        try {
            TrusToken.logout()
        } catch (e: Exception) {
            Log.w(TAG, "Error logging out on detach", e)
        }
        try {
            activeConnection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing connection", e)
        }
        activeConnection = null
        activeDevice = null
        _tokenState.value = UsbTokenState.Disconnected
    }

    fun isSmartCardReader(device: UsbDevice): Boolean {
        // Match TrusToken / Watchdata VID:PID
        if (device.vendorId == TRUSTOKEN_VENDOR_ID && device.productId == TRUSTOKEN_PRODUCT_ID) {
            return true
        }
        // Match USB CCID Class (0x0B)
        if (device.deviceClass == UsbConstants.USB_CLASS_CSCID) {
            return true
        }
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CSCID) {
                return true
            }
        }
        return false
    }

    fun isAuthenticated(): Boolean {
        return _tokenState.value is UsbTokenState.Authenticated
    }

    fun resetState() {
        checkAndInitializeToken()
    }
}
