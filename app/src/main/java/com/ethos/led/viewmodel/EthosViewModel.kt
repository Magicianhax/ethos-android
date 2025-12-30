package com.ethos.led.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ethos.led.api.ApiClient
import com.ethos.led.ble.BleService
import com.ethos.led.model.TierData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EthosViewModel(application: Application) : AndroidViewModel(application) {
    private val ethosApi = ApiClient.ethosApi
    private val bleService = BleService(application)

    private val _currentUser = MutableLiveData<String?>()
    val currentUser: LiveData<String?> = _currentUser

    private val _currentScore = MutableLiveData<Int?>()
    val currentScore: LiveData<Int?> = _currentScore

    private val _currentTier = MutableLiveData<String?>()
    val currentTier: LiveData<String?> = _currentTier

    private val _currentTierColor = MutableLiveData<String>()
    val currentTierColor: LiveData<String> = _currentTierColor

    private val _autoRefresh = MutableLiveData<Boolean>()
    val autoRefresh: LiveData<Boolean> = _autoRefresh

    private val _lastUpdate = MutableLiveData<String?>()
    val lastUpdate: LiveData<String?> = _lastUpdate

    private val _deviceConnected = MutableLiveData<Boolean>()
    val deviceConnected: LiveData<Boolean> = _deviceConnected

    private val _brightness = MutableLiveData<Int>()
    val brightness: LiveData<Int> = _brightness

    private val _customColor = MutableLiveData<String?>()
    val customColor: LiveData<String?> = _customColor

    private val _ledPower = MutableLiveData<Boolean>()
    val ledPower: LiveData<Boolean> = _ledPower

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var autoRefreshJob: Job? = null

    init {
        _brightness.value = 80
        _autoRefresh.value = false
        _deviceConnected.value = false
        _ledPower.value = true
        _currentTierColor.value = "ffffff"
        // Don't auto-connect - let user click connect button after granting permissions
    }

    fun connectToDevice() {
        viewModelScope.launch {
            _deviceConnected.value = bleService.connect(BleService.DEVICE_ADDRESS)
            if (!(_deviceConnected.value ?: false)) {
                _error.value = "Failed to connect. Check device address and Bluetooth."
            }
        }
    }

    fun disconnectDevice() {
        viewModelScope.launch {
            bleService.disconnect()
            _deviceConnected.value = false
        }
    }

    fun setUser(username: String) {
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanUsername.isNotEmpty()) {
            _currentUser.value = cleanUsername
            _customColor.value = null // Reset to tier color
            updateDisplay()
        }
    }

    fun updateDisplay() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = ethosApi.getUserScore(user)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    _currentScore.value = data.score
                    val tier = TierData.getTier(data.score)
                    _currentTier.value = tier.name
                    _currentTierColor.value = tier.color

                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    _lastUpdate.value = sdf.format(Date())

                    // Send to LED
                    val displayColor = _customColor.value ?: tier.color
                    sendToLed(data.score, displayColor)
                } else {
                    _error.value = "Failed to fetch score"
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun sendToLed(score: Int, color: String) {
        if (bleService.isConnected()) {
            bleService.setBrightness(_brightness.value ?: 80)
            bleService.sendText(score.toString(), color)
        }
    }

    fun toggleAutoRefresh() {
        val newValue = !(_autoRefresh.value ?: false)
        _autoRefresh.value = newValue

        if (newValue) {
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()
        autoRefreshJob = viewModelScope.launch {
            while (_autoRefresh.value == true) {
                delay(60000) // 60 seconds
                if (_currentUser.value != null) {
                    updateDisplay()
                }
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun refresh() {
        updateDisplay()
    }

    fun setBrightness(level: Int) {
        val clampedLevel = level.coerceIn(0, 100)
        _brightness.value = clampedLevel
        viewModelScope.launch {
            if (bleService.isConnected() && _currentScore.value != null) {
                bleService.setBrightness(clampedLevel)
                val displayColor = _customColor.value ?: _currentTierColor.value ?: "ffffff"
                sendToLed(_currentScore.value!!, displayColor)
            }
        }
    }

    fun setColor(color: String) {
        val cleanColor = color.removePrefix("#")
        _customColor.value = cleanColor
        viewModelScope.launch {
            if (bleService.isConnected() && _currentScore.value != null) {
                sendToLed(_currentScore.value!!, cleanColor)
            }
        }
    }

    fun resetColor() {
        _customColor.value = null
        viewModelScope.launch {
            if (bleService.isConnected() && _currentScore.value != null) {
                val tierColor = _currentTierColor.value ?: "ffffff"
                sendToLed(_currentScore.value!!, tierColor)
            }
        }
    }

    fun powerOn() {
        viewModelScope.launch {
            if (bleService.isConnected()) {
                val success = bleService.setPower(true)
                if (success) {
                    _ledPower.value = true
                }
            }
        }
    }

    fun powerOff() {
        viewModelScope.launch {
            if (bleService.isConnected()) {
                val success = bleService.setPower(false)
                if (success) {
                    _ledPower.value = false
                }
            }
        }
    }

    fun clearScreen() {
        viewModelScope.launch {
            if (bleService.isConnected()) {
                bleService.clear()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
        viewModelScope.launch {
            bleService.disconnect()
        }
    }
}


