package com.ethos.led.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BleService(private val context: Context) {
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var deviceAddress: String? = null
    private var logCallback: ((String) -> Unit)? = null

    // BLE UUIDs for iPixel device - try multiple variations
    private val SERVICE_UUID = UUID.fromString("0000fa00-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("0000fa02-0000-1000-8000-00805f9b34fb")
    
    // Alternative UUIDs to try
    private val ALTERNATIVE_SERVICE_UUIDS = listOf(
        UUID.fromString("0000fa00-0000-1000-8000-00805f9b34fb"), // Original
        UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"), // Alternative 1
        UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), // Alternative 2
    )
    
    private var discoveredService: BluetoothGattService? = null
    private var discoveredCharacteristic: BluetoothGattCharacteristic? = null

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        logCallback?.invoke(message)
    }

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("✓ Connected to GATT server (status: $status)")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("✗ Disconnected from GATT server (status: $status)")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    log("→ Connecting to device...")
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    log("→ Disconnecting...")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services
                log("✓ Services discovered: ${services.size} services found")
                
                // Try to find our service
                discoveredService = null
                discoveredCharacteristic = null
                
                services.forEach { service ->
                    val serviceUuid = service.uuid.toString().lowercase()
                    log("  Service: ${service.uuid}")
                    
                    // Check if this matches any of our target UUIDs
                    val matches = ALTERNATIVE_SERVICE_UUIDS.any { 
                        it.toString().lowercase() == serviceUuid 
                    }
                    
                    if (matches || serviceUuid.contains("fa00") || serviceUuid.contains("ff00")) {
                        log("  → Potential match found!")
                        discoveredService = service
                    }
                    
                    service.characteristics.forEach { char ->
                        val charUuid = char.uuid.toString().lowercase()
                        log("    Characteristic: ${char.uuid}")
                        
                        // Check if this is a write characteristic
                        if (charUuid.contains("fa02") || charUuid.contains("ff01") || 
                            (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                            log("    → Write characteristic found!")
                            if (discoveredService == service) {
                                discoveredCharacteristic = char
                            }
                        }
                    }
                }
                
                // If we found a service, use it
                if (discoveredService != null) {
                    log("✓ Using discovered service: ${discoveredService!!.uuid}")
                    if (discoveredCharacteristic != null) {
                        log("✓ Using discovered characteristic: ${discoveredCharacteristic!!.uuid}")
                    } else {
                        log("⚠ Warning: No write characteristic found in discovered service")
                    }
                } else {
                    log("⚠ Service UUID not found. Trying to use first available service...")
                    // Try to use the first non-standard service
                    val customService = services.find { 
                        val uuid = it.uuid.toString().lowercase()
                        !uuid.contains("1800") && !uuid.contains("1801") && !uuid.contains("180a")
                    }
                    if (customService != null) {
                        log("→ Trying service: ${customService.uuid}")
                        discoveredService = customService
                        // Find write characteristic
                        discoveredCharacteristic = customService.characteristics.find {
                            (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                        }
                        if (discoveredCharacteristic != null) {
                            log("✓ Found write characteristic: ${discoveredCharacteristic!!.uuid}")
                        }
                    }
                }
            } else {
                log("✗ Service discovery failed (status: $status)")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("✓ Data written successfully")
            } else {
                log("✗ Write failed (status: $status)")
            }
        }
    }

    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            log("→ Starting connection to $address")
            
            if (bluetoothAdapter == null) {
                log("✗ Bluetooth adapter is null")
                return@withContext false
            }
            
            if (!bluetoothAdapter.isEnabled) {
                log("✗ Bluetooth is not enabled")
                return@withContext false
            }

            log("→ Getting remote device...")
            val device = bluetoothAdapter.getRemoteDevice(address)
            deviceAddress = address
            log("→ Connecting via GATT...")
            
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            
            // Wait for connection
            log("→ Waiting for connection...")
            Thread.sleep(2000)
            
            bluetoothGatt?.let { gatt ->
                val services = gatt.services
                val hasServices = services.isNotEmpty()
                log("→ Connection check: services=${services.size}, hasServices=$hasServices")
                
                if (hasServices) {
                    log("✓ Connection successful - services available")
                    return@withContext true
                } else {
                    // On some devices, services might not be immediately available
                    log("→ Services not yet available, checking connection state...")
                    // Try to discover services again
                    gatt.discoverServices()
                    Thread.sleep(1000)
                    val finalServices = gatt.services
                    val finalConnected = finalServices.isNotEmpty()
                    if (finalConnected) {
                        log("✓ Connection successful after service discovery")
                    } else {
                        log("✗ Connection failed - no services found")
                    }
                    return@withContext finalConnected
                }
            } ?: run {
                log("✗ GATT is null")
                false
            }
        } catch (e: Exception) {
            log("✗ Connection error: ${e.message}")
            Log.e(TAG, "Connection error: ${e.message}", e)
            false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        log("→ Disconnecting...")
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        deviceAddress = null
        log("✓ Disconnected")
    }

    suspend fun sendText(text: String, color: String): Boolean = withContext(Dispatchers.IO) {
        try {
            log("→ Sending text: '$text' with color: #$color")
            val gatt = bluetoothGatt ?: run {
                log("✗ GATT is null")
                return@withContext false
            }
            
            // Use discovered service if available, otherwise try to find it
            val service = discoveredService ?: run {
                // Try to find the service
                val foundService = ALTERNATIVE_SERVICE_UUIDS.firstOrNull { uuid ->
                    gatt.getService(uuid) != null
                }?.let { gatt.getService(it) }
                
                if (foundService == null) {
                    log("✗ Service not found. Available services:")
                    gatt.services.forEach { s ->
                        log("    - ${s.uuid}")
                    }
                    return@withContext false
                }
                foundService
            }
            
            log("→ Using service: ${service.uuid}")
            
            // Use discovered characteristic if available, otherwise try to find it
            val characteristic = discoveredCharacteristic ?: run {
                // Try our target UUID first
                service.getCharacteristic(CHARACTERISTIC_WRITE_UUID) ?: run {
                    // Try to find any write characteristic
                    service.characteristics.find { char ->
                        (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    } ?: run {
                        log("✗ No write characteristic found")
                        log("  Available characteristics:")
                        service.characteristics.forEach { c ->
                            val props = mutableListOf<String>()
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("READ")
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("WRITE")
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.add("WRITE_NO_RESPONSE")
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("NOTIFY")
                            log("    - ${c.uuid} [${props.joinToString(", ")}]")
                        }
                        return@withContext false
                    }
                }
            }
            
            log("→ Using characteristic: ${characteristic.uuid}")

            // Build command packet (simplified - adjust based on your device protocol)
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val colorBytes = hexStringToByteArray(color)
            
            log("→ Building packet: text=${textBytes.size} bytes, color=${colorBytes.size} bytes")
            
            // Combine text and color (protocol specific)
            val packet = buildPacket(textBytes, colorBytes)
            
            log("→ Packet size: ${packet.size} bytes")
            
            characteristic.value = packet
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            log("→ Writing characteristic...")
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                log("→ Write initiated, waiting...")
                Thread.sleep(500) // Wait for write to complete
                log("✓ Write completed")
            } else {
                log("✗ Write failed")
            }
            success
        } catch (e: Exception) {
            log("✗ Send text error: ${e.message}")
            Log.e(TAG, "Send text error: ${e.message}", e)
            false
        }
    }

    suspend fun setBrightness(level: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            log("→ Setting brightness to $level%")
            val gatt = bluetoothGatt ?: return@withContext false
            val service = discoveredService ?: gatt.getService(SERVICE_UUID) ?: return@withContext false
            val characteristic = discoveredCharacteristic ?: service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                ?: return@withContext false

            // Build brightness command (protocol specific)
            val brightnessByte = level.coerceIn(0, 100).toByte()
            val packet = byteArrayOf(0x01, brightnessByte) // Adjust command format
            
            characteristic.value = packet
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                log("✓ Brightness set")
                Thread.sleep(300)
            } else {
                log("✗ Brightness set failed")
            }
            success
        } catch (e: Exception) {
            log("✗ Set brightness error: ${e.message}")
            Log.e(TAG, "Set brightness error: ${e.message}", e)
            false
        }
    }

    suspend fun setPower(powerOn: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            log("→ Setting power: ${if (powerOn) "ON" else "OFF"}")
            val gatt = bluetoothGatt ?: return@withContext false
            val service = discoveredService ?: gatt.getService(SERVICE_UUID) ?: return@withContext false
            val characteristic = discoveredCharacteristic ?: service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                ?: return@withContext false

            val packet = byteArrayOf(0x02, if (powerOn) 0x01 else 0x00) // Adjust command format
            
            characteristic.value = packet
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                Thread.sleep(300)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Set power error: ${e.message}")
            false
        }
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        try {
            log("→ Clearing screen")
            val gatt = bluetoothGatt ?: return@withContext false
            val service = discoveredService ?: gatt.getService(SERVICE_UUID) ?: return@withContext false
            val characteristic = discoveredCharacteristic ?: service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                ?: return@withContext false

            val packet = byteArrayOf(0x03) // Clear command
            
            characteristic.value = packet
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                Thread.sleep(300)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Clear error: ${e.message}")
            false
        }
    }

    fun isConnected(): Boolean {
        val connected = bluetoothGatt != null
        log("→ Connection check: ${if (connected) "connected" else "not connected"}")
        return connected
    }

    private fun buildPacket(textBytes: ByteArray, colorBytes: ByteArray): ByteArray {
        // Simplified packet builder - adjust based on your device protocol
        // This is a placeholder - you'll need to implement the actual protocol
        val packet = ByteArray(textBytes.size + colorBytes.size + 4)
        packet[0] = 0x00 // Command type
        packet[1] = textBytes.size.toByte()
        System.arraycopy(textBytes, 0, packet, 2, textBytes.size)
        System.arraycopy(colorBytes, 0, packet, 2 + textBytes.size, colorBytes.size)
        return packet
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace("#", "").uppercase()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4)
                    + Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    companion object {
        private const val TAG = "BleService"
        const val DEVICE_ADDRESS = "5D:C8:1C:36:B7:AC" // Default device address
    }
}

