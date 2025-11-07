package com.example.androidmouse

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import com.example.androidmouse.databinding.ActivityMainBinding

@SuppressLint("MissingPermission") // Uprawnienia sprawdzamy dynamicznie
class MainActivity : AppCompatActivity(), BluetoothStateListener {

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val bluetoothManager: BluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }
    private val bluetoothController: BluetoothController by lazy { BluetoothController(this) }
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    // Lista sparowanych urządzeń - będziemy ją przechowywać
    private var pairedDevices: List<BluetoothDevice> = emptyList()

    // --- ActivityResult Handlers (bez zmian) ---
    private val requestEnableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            showToast("Bluetooth musi być włączony.")
        }
    }
    private val requestBluetoothPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { !it }) {
            showToast("Uprawnienia Bluetooth są wymagane.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (bluetoothAdapter == null) {
            showToast("Urządzenie nie wspiera Bluetooth.")
            finish()
            return
        }

        // 1. Najpierw prosimy o uprawnienia
        requestBluetoothPermissions()
        // 2. Konfigurujemy UI
        setupUIListeners()
    }

    // --- Implementacja interfejsu (bez zmian) ---
    override fun onStateChanged(state: String) {
        runOnUiThread { binding.tvStatus.text = "Status: $state" }
    }
    override fun onDataSent(data: String) {
        runOnUiThread { binding.tvStatus.text = "Wysłano: $data" }
    }

    private fun setupUIListeners() {
        setupTouchpad()
        setupClickListeners()

        // NOWY LISTENER DLA PRZYCISKU "POŁĄCZ"
        binding.btnConnect.setOnClickListener {
            handleConnectionRequest()
        }
    }

    // --- Logika połączenia ---
    private fun handleConnectionRequest() {
        // Sprawdzamy czy Bluetooth jest włączony
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBluetooth.launch(enableBtIntent)
            showToast("Proszę włączyć Bluetooth.")
            return
        }
        // Pokazujemy listę urządzeń do wyboru
        showPairedDevicesDialog()
    }

    private fun showPairedDevicesDialog() {
        try {
            // Pobieramy listę sparowanych urządzeń
            pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

            if (pairedDevices.isEmpty()) {
                showToast("Brak sparowanych urządzeń. Sparuj komputer w ustawieniach Bluetooth.")
                return
            }

            // Tworzymy listę nazw urządzeń do wyświetlenia
            val deviceNames = pairedDevices.map { it.name }

            // Tworzymy i pokazujemy okno dialogowe z listą
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Wybierz urządzenie (komputer)")
            builder.setItems(deviceNames.toTypedArray()) { _, which ->
                // Użytkownik wybrał urządzenie, 'which' to jego indeks na liście
                val selectedDevice = pairedDevices[which]
                // Rozpoczynamy połączenie z wybranym urządzeniem
                bluetoothController.connect(selectedDevice)
            }
            builder.show()

        } catch (e: SecurityException) {
            showToast("Błąd uprawnień przy dostępie do listy urządzeń.")
        }
    }

    // --- Reszta kodu (touchpad, kliki, onDestroy) - bez większych zmian ---
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchpad() {
        binding.touchpadView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.x - lastX).toInt()
                    val deltaY = (event.y - lastY).toInt()
                    val moveMessage = "MOVE:$deltaX,$deltaY\n"
                    bluetoothController.write(moveMessage.toByteArray())
                    lastX = event.x
                    lastY = event.y
                }
            }
            true
        }
    }

    private fun setupClickListeners() {
        binding.btnLeftClick.setOnClickListener { bluetoothController.write("L_CLICK\n".toByteArray()) }
        binding.btnRightClick.setOnClickListener { bluetoothController.write("R_CLICK\n".toByteArray()) }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothController.cancel()
    }


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
