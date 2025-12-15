package com.example.evafinal

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.DecimalFormat

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Vistas
    private lateinit var bluetoothSwitch: SwitchMaterial
    private lateinit var wifiSwitch: SwitchMaterial
    private lateinit var locationSwitch: SwitchMaterial
    private lateinit var bluetoothStatusText: TextView
    private lateinit var wifiStatusText: TextView
    private lateinit var locationStatusText: TextView
    private lateinit var coordinatesText: TextView
    private lateinit var pitchText: TextView
    private lateinit var rollText: TextView
    private lateinit var azimuthText: TextView

    // Managers del sistema
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var wifiManager: WifiManager
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Valores de los sensores
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Launchers para permisos y ajustes
    private val requestBluetoothPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) requestEnableBluetooth() else updateSwitchStates()
    }

    private val requestEnableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        updateSwitchStates()
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Cuando el usuario responde al permiso, actualizamos el estado
        updateSwitchStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupManagers()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onResume() {
        super.onResume()
        updateSwitchStates()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                updateSwitchStates()
                Toast.makeText(this, "Estado actualizado", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        bluetoothSwitch = findViewById(R.id.bluetooth_switch)
        wifiSwitch = findViewById(R.id.wifi_switch)
        locationSwitch = findViewById(R.id.location_switch)
        bluetoothStatusText = findViewById(R.id.bluetooth_status_text)
        wifiStatusText = findViewById(R.id.wifi_status_text)
        locationStatusText = findViewById(R.id.location_status_text)
        coordinatesText = findViewById(R.id.coordinates_text)

        pitchText = findViewById(R.id.pitch_text)
        rollText = findViewById(R.id.roll_text)
        azimuthText = findViewById(R.id.azimuth_text)
    }

    private fun setupManagers() {
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private fun updateSwitchStates() {
        bluetoothSwitch.setOnCheckedChangeListener(null)
        wifiSwitch.setOnCheckedChangeListener(null)
        locationSwitch.setOnCheckedChangeListener(null)

        val isBluetoothEnabled = bluetoothAdapter.isEnabled
        bluetoothSwitch.isChecked = isBluetoothEnabled
        bluetoothStatusText.text = if (isBluetoothEnabled) "Activado" else "Desactivado"

        val isWifiEnabled = wifiManager.isWifiEnabled
        wifiSwitch.isChecked = isWifiEnabled
        wifiStatusText.text = if (isWifiEnabled) "Activado" else "Desactivado"

        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        locationSwitch.isChecked = isLocationEnabled
        locationStatusText.text = if (isLocationEnabled) "Activada" else "Desactivada"

        // Actualizar coordenadas
        if (isLocationEnabled) {
            fetchCoordinates()
        } else {
            coordinatesText.text = ""
        }

        setupListeners()
    }

    private fun setupListeners() {
        bluetoothSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) handleBluetoothOn() else startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        wifiSwitch.setOnCheckedChangeListener { _, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_WIFI))
            } else {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }

        locationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Si el usuario enciende el switch, verificamos el permiso
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    // Si el permiso ya está, pero la ubicación está desactivada, abrimos ajustes
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                }
            } else {
                // Si apaga el switch, siempre vamos a ajustes
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
    }

    @SuppressLint("MissingPermission") // La permiso se verifica antes de llamar
    private fun fetchCoordinates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = String.format("%.4f", location.latitude)
                    val lon = String.format("%.4f", location.longitude)
                    coordinatesText.text = "Lat: $lat, Lon: $lon"
                } else {
                    coordinatesText.text = "Buscando..."
                }
            }
        } else {
            coordinatesText.text = "Permiso denegado"
        }
    }

    // --- SensorEventListener Implementation ---
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        updateOrientationAngles()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { /* No es necesario */ }

    private fun updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toInt()
        val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toInt()
        val roll = Math.toDegrees(orientationAngles[2].toDouble()).toInt()

        azimuthText.text = "Azimuth: $azimuth°"
        pitchText.text = "Pitch: $pitch°"
        rollText.text = "Roll: $roll°"
    }
    
    // --- Código de Bluetooth sin cambios ---
    private fun handleBluetoothOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requestEnableBluetooth()
        }
    }

    private fun requestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        requestEnableBluetoothLauncher.launch(enableBtIntent)
    }
}
