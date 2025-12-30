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

    // BLE UUIDs for iPixel device
    private val SERVICE_UUID = UUID.fromString("0000fa00-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_WRITE_UUID = UUID.fromString("0000fa02-0000-1000-8000-00805f9b34fb")

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
            }
        }
    }

    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.e(TAG, "Bluetooth not enabled")
                return@withContext false
            }

            val device = bluetoothAdapter.getRemoteDevice(address)
            deviceAddress = address
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            
            // Wait a bit for connection
            Thread.sleep(1000)
            bluetoothGatt?.let { gatt ->
                val connected = gatt.services.isNotEmpty() || 
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                connected
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
            false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        deviceAddress = null
    }

    suspend fun sendText(text: String, color: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val gatt = bluetoothGatt ?: return@withContext false
            val service = gatt.getService(SERVICE_UUID) ?: return@withContext false
            val characteristic = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                ?: return@withContext false

            // Build command packet (simplified - adjust based on your device protocol)
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val colorBytes = hexStringToByteArray(color)
            
            // Combine text and color (protocol specific)
            val packet = buildPacket(textBytes, colorBytes)
            
            characteristic.value = packet
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                Thread.sleep(500) // Wait for write to complete
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Send text error: ${e.message}")
            false
        }
    }

    suspend fun setBrightness(level: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val gatt = bluetoothGatt ?: return@withContext false
            val service = gatt.getService(SERVICE_UUID) ?: return@withContext false
            val characteristic = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                ?: return@withContext false

            // Build brightness command (protocol specific)
            val brightnessByte = level.coerceIn(0, 100).toByte()
            val packet = byteArrayOf(0x01, brightnessByte) // Adjust command format
            
            characteristic.value = packet
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            val success = gatt.writeCharacteristic(characteristic)
            if (success) {
                Thread.sleep(300)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Set brightness error: ${e.message}")
            false
        }
    }

    suspend fun setPower(powerOn: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val gatt = bluetoothGatt ?: return@withContext false
            val service = gatt.getService(SERVICE_UUID) ?: return@withContext false
            val characteristic = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
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
            val gatt = bluetoothGatt ?: return@withContext false
            val service = gatt.getService(SERVICE_UUID) ?: return@withContext false
            val characteristic = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
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
        return bluetoothGatt != null
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

