package com.ethos.led

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ethos.led.databinding.ActivityMainBinding
import com.ethos.led.viewmodel.EthosViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: EthosViewModel by viewModels()
    private var brightnessDebounceJob: kotlinx.coroutines.Job? = null

    // Permission launcher for Bluetooth
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            checkBluetoothAndConnect()
        } else {
            Toast.makeText(
                this,
                "Bluetooth permissions are required to connect",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
        requestBluetoothPermissions()
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                requestPermissionLauncher.launch(permissions)
            } else {
                checkBluetoothAndConnect()
            }
        } else {
            // Older Android versions
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                requestPermissionLauncher.launch(permissions)
            } else {
                checkBluetoothAndConnect()
            }
        }
    }

    private fun checkBluetoothAndConnect() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(
                this,
                getString(R.string.bluetooth_not_enabled),
                Toast.LENGTH_LONG
            ).show()
            binding.connectButton.text = getString(R.string.connect_to_led)
        } else {
            // Bluetooth is enabled, but don't auto-connect - let user click button
            binding.connectButton.text = getString(R.string.connect_to_led)
        }
    }

    private fun setupObservers() {
        // Username
        viewModel.currentUser.observe(this) { username ->
            if (username != null) {
                binding.usernameText.text = "@$username"
                binding.usernameText.visibility = View.VISIBLE
            } else {
                binding.usernameText.visibility = View.GONE
            }
        }

        // Score
        viewModel.currentScore.observe(this) { score ->
            if (score != null) {
                binding.scoreText.text = score.toString()
            } else {
                binding.scoreText.text = "----"
            }
        }

        // Tier
        viewModel.currentTier.observe(this) { tier ->
            binding.tierText.text = tier ?: getString(R.string.enter_username_below)
        }

        // Tier Color
        viewModel.currentTierColor.observe(this) { color ->
            updateScoreColor()
        }

        // Custom Color
        viewModel.customColor.observe(this) { color ->
            updateScoreColor()
            updateColorPreview()
        }

        // Brightness
        viewModel.brightness.observe(this) { brightness ->
            binding.brightnessSeekBar.progress = brightness
            binding.brightnessValue.text = "$brightness%"
        }

        // Auto Refresh
        viewModel.autoRefresh.observe(this) { enabled ->
            binding.autoRefreshSwitch.isChecked = enabled
        }

        // Last Update
        viewModel.lastUpdate.observe(this) { updateTime ->
            updateStatus()
        }

        // Device Connected
        viewModel.deviceConnected.observe(this) { connected ->
            updateStatus()
            updateConnectButton(connected)
        }

        // LED Power
        viewModel.ledPower.observe(this) { power ->
            updateStatus()
        }

        // Loading
        viewModel.isLoading.observe(this) { loading ->
            binding.showScoreButton.isEnabled = !loading
            if (loading) {
                binding.showScoreButton.text = "Loading..."
            } else {
                binding.showScoreButton.text = getString(R.string.show_score)
            }
        }

        // Error
        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        // Show Score Button
        binding.showScoreButton.setOnClickListener {
            val username = binding.usernameInput.text.toString().trim()
            if (username.isNotEmpty()) {
                viewModel.setUser(username)
            } else {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
            }
        }

        // Brightness SeekBar
        binding.brightnessSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        binding.brightnessValue.text = "$progress%"
                        // Debounce brightness updates
                        brightnessDebounceJob?.cancel()
                        brightnessDebounceJob = lifecycleScope.launch {
                            kotlinx.coroutines.delay(500)
                            viewModel.setBrightness(progress)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )

        // Color Picker Button
        binding.colorPickerButton.setOnClickListener {
            showColorPicker()
        }

        // Reset Color Button
        binding.resetColorButton.setOnClickListener {
            viewModel.resetColor()
        }

        // Auto Refresh Switch
        binding.autoRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != (viewModel.autoRefresh.value ?: false)) {
                viewModel.toggleAutoRefresh()
            }
        }

        // Refresh Button
        binding.refreshButton.setOnClickListener {
            viewModel.refresh()
        }

        // Power On Button
        binding.powerOnButton.setOnClickListener {
            viewModel.powerOn()
        }

        // Power Off Button
        binding.powerOffButton.setOnClickListener {
            viewModel.powerOff()
        }

        // Clear Screen Button
        binding.clearScreenButton.setOnClickListener {
            viewModel.clearScreen()
        }

        // Connect/Disconnect Button
        binding.connectButton.setOnClickListener {
            val connected = viewModel.deviceConnected.value ?: false
            if (connected) {
                // Disconnect
                lifecycleScope.launch {
                    viewModel.disconnectDevice()
                }
            } else {
                // Connect
                checkBluetoothAndConnect()
                lifecycleScope.launch {
                    viewModel.connectToDevice()
                }
            }
        }
    }

    private fun updateConnectButton(connected: Boolean) {
        if (connected) {
            binding.connectButton.text = getString(R.string.disconnect)
            binding.connectButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.danger)
        } else {
            binding.connectButton.text = getString(R.string.connect_to_led)
            binding.connectButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }
    }

    private fun updateScoreColor() {
        val color = viewModel.customColor.value ?: viewModel.currentTierColor.value ?: "ffffff"
        try {
            val colorInt = Color.parseColor("#$color")
            binding.scoreText.setTextColor(colorInt)
        } catch (e: Exception) {
            binding.scoreText.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
        }
    }

    private fun updateColorPreview() {
        val color = viewModel.customColor.value ?: viewModel.currentTierColor.value ?: "ffffff"
        try {
            val colorInt = Color.parseColor("#$color")
            binding.colorPreview.setBackgroundColor(colorInt)
            binding.colorPickerButton.setBackgroundColor(colorInt)
        } catch (e: Exception) {
            binding.colorPreview.setBackgroundColor(
                ContextCompat.getColor(this, R.color.white)
            )
        }
    }

    private fun updateStatus() {
        val connected = viewModel.deviceConnected.value ?: false
        val power = viewModel.ledPower.value ?: false
        val updateTime = viewModel.lastUpdate.value

        val statusParts = mutableListOf<String>()

        // Connection status
        val statusColor = if (connected) R.color.success else R.color.danger
        val statusDot = "●"
        statusParts.add("$statusDot ${if (connected) getString(R.string.led_connected) else getString(R.string.led_disconnected)}")

        // Power status
        if (connected) {
            statusParts.add("${getString(R.string.power)}: ${if (power) "ON" else "OFF"}")
        }

        // Update time
        if (updateTime != null) {
            statusParts.add("${getString(R.string.updated)}: $updateTime")
        }

        binding.statusText.text = statusParts.joinToString(" | ")
        
        // Set status dot color
        val colorInt = ContextCompat.getColor(this, statusColor)
        val text = binding.statusText.text.toString()
        val spannable = android.text.SpannableString(text)
        if (text.contains("●")) {
            val index = text.indexOf("●")
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(colorInt),
                index,
                index + 1,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.statusText.text = spannable
    }

    private fun showColorPicker() {
        val currentColor = viewModel.customColor.value ?: viewModel.currentTierColor.value ?: "ffffff"
        val colorInt = try {
            Color.parseColor("#$currentColor")
        } catch (e: Exception) {
            Color.WHITE
        }

        val colorPickerView = createColorPickerView(colorInt)
        
        // Create color picker dialog
        val colorPicker = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.color))
            .setView(colorPickerView)
            .setPositiveButton("OK") { _, _ ->
                val colorHelper = colorPickerView.tag as? ColorHelper
                colorHelper?.let {
                    val selectedColor = it.getColor()
                    val hexColor = String.format("%06X", 0xFFFFFF and selectedColor)
                    viewModel.setColor(hexColor)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        colorPicker.show()
    }

    private fun createColorPickerView(initialColor: Int): View {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        // For simplicity, we'll use a color picker library or create a simple one
        // Here's a basic implementation using a color wheel or RGB sliders
        // For production, consider using a library like 'com.github.duanhong169:colorpicker:1.1.6'

        val colorView = View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
            setBackgroundColor(initialColor)
        }

        val redSeekBar = android.widget.SeekBar(this).apply {
            max = 255
            progress = Color.red(initialColor)
        }
        val greenSeekBar = android.widget.SeekBar(this).apply {
            max = 255
            progress = Color.green(initialColor)
        }
        val blueSeekBar = android.widget.SeekBar(this).apply {
            max = 255
            progress = Color.blue(initialColor)
        }

        val updateColor: () -> Unit = {
            val color = Color.rgb(
                redSeekBar.progress,
                greenSeekBar.progress,
                blueSeekBar.progress
            )
            colorView.setBackgroundColor(color)
        }

        redSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: android.widget.SeekBar?, p1: Int, p2: Boolean) {
                    updateColor()
                }
                override fun onStartTrackingTouch(p0: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(p0: android.widget.SeekBar?) {}
            }
        )

        greenSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: android.widget.SeekBar?, p1: Int, p2: Boolean) {
                    updateColor()
                }
                override fun onStartTrackingTouch(p0: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(p0: android.widget.SeekBar?) {}
            }
        )

        blueSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: android.widget.SeekBar?, p1: Int, p2: Boolean) {
                    updateColor()
                }
                override fun onStartTrackingTouch(p0: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(p0: android.widget.SeekBar?) {}
            }
        )

        layout.addView(colorView)
        layout.addView(android.widget.TextView(this).apply {
            text = "Red"
            setPadding(0, 10, 0, 5)
        })
        layout.addView(redSeekBar)
        layout.addView(android.widget.TextView(this).apply {
            text = "Green"
            setPadding(0, 10, 0, 5)
        })
        layout.addView(greenSeekBar)
        layout.addView(android.widget.TextView(this).apply {
            text = "Blue"
            setPadding(0, 10, 0, 5)
        })
        layout.addView(blueSeekBar)

        // Store color picker reference to get final color
        val colorHelper = object : ColorHelper {
            override fun getColor(): Int {
                return Color.rgb(
                    redSeekBar.progress,
                    greenSeekBar.progress,
                    blueSeekBar.progress
                )
            }
        }
        layout.tag = colorHelper

        return layout
    }

    private interface ColorHelper {
        fun getColor(): Int
    }
}

