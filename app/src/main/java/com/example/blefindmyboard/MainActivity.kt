package com.example.blefindmyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow  // if you also use TextOverflow

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.location.LocationManager
import android.content.Intent
import android.provider.Settings
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request permissions if not granted
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
        }

        setContent {
            BLEFindMyBoardApp()
        }
    }
}

// State Holder
data class BLEUiState(
    val devices: List<String> = emptyList(),
    val selectedDevice: BluetoothDevice? = null,
    val bluetoothEnabled: Boolean = false,
    val locationEnabled: Boolean = false
)

//StatusText Computation
@Composable
fun getStatusText(
    bluetoothEnabled: Boolean,
    locationEnabled: Boolean,
    selectedDevice: BluetoothDevice?,
    devicesCount: Int
): String = when {
    !bluetoothEnabled && !locationEnabled ->
        "Enable Bluetooth and Location to scan BLE devices"
    !bluetoothEnabled -> "Please enable Bluetooth"
    !locationEnabled -> "Please enable Location"
    selectedDevice == null && devicesCount == 0 -> "Device Not Connected"
    selectedDevice == null -> "Devices found: $devicesCount. Select one to connect."
    else -> "Device Connected: ${selectedDevice.name}"
}

//BLE Scanner Logic
@RequiresApi(Build.VERSION_CODES.M)
fun startBLEScan(
    bleScanner: BluetoothLeScanner?,
    devicesMap: SnapshotStateMap<String, BluetoothDevice>,
    devices: MutableState<List<String>>,
) {
    val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device.name?.let { name ->
                if (name.isNotBlank() && name !in devices.value) {
                    devices.value = devices.value + name
                    devicesMap[name] = result.device
                }
            }
        }

        override fun onScanFailed(errorCode: Int) { /* handle error */ }
    }

    bleScanner?.startScan(scanCallback)
}

//Bluetooth and Location Listeners
@Composable
fun BluetoothStateListener(
    bluetoothEnabled: MutableState<Boolean>,
    selectedDevice: MutableState<BluetoothDevice?>,
    context: Context
) {
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    bluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                    if (!bluetoothEnabled.value) selectedDevice.value = null
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
}

@Composable
fun LocationStateListener(
    locationEnabled: MutableState<Boolean>,
    selectedDevice: MutableState<BluetoothDevice?>,
    locationManager: LocationManager
) {
    LaunchedEffect(Unit) {
        while (true) {
            val loc = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (loc != locationEnabled.value) {
                locationEnabled.value = loc
                if (!loc) selectedDevice.value = null
            }
            kotlinx.coroutines.delay(1000L)
        }
    }
}

//Device List Composable
@Composable
fun DeviceList(
    devices: List<String>,
    devicesMap: SnapshotStateMap<String, BluetoothDevice>,
    selectedDevice: MutableState<BluetoothDevice?>
) {
    LazyColumn(modifier = Modifier.fillMaxWidth(0.8f)) {
        items(devices) { name ->
            val isSelected = selectedDevice.value?.name == name
            Text(
                text = name,
                fontSize = 15.sp,
                color = if (isSelected) Color.White else Color.Black,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .background(if (isSelected) Color.Blue else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable { selectedDevice.value = devicesMap[name] }
                    .padding(8.dp)
            )
        }
    }
}

// Main Function
@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun BLEFindMyBoardApp() {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val bleScanner = bluetoothAdapter?.bluetoothLeScanner
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // States
    val selectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
    val devices = remember { mutableStateOf(listOf<String>()) }
    val devicesMap = remember { mutableStateMapOf<String, BluetoothDevice>() }
    val bluetoothEnabled = remember { mutableStateOf(bluetoothAdapter.isEnabled) }
    val locationEnabled = remember {
        mutableStateOf(
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        )
    }

    // Listeners
    BluetoothStateListener(bluetoothEnabled, selectedDevice, context)
    LocationStateListener(locationEnabled, selectedDevice, locationManager)

    // Status Text
    val statusText = getStatusText(bluetoothEnabled.value, locationEnabled.value, selectedDevice.value, devices.value.size)

    // UI
    Column(
        modifier = Modifier.fillMaxSize().background(Color.LightGray).padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = statusText,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(Color.Yellow, RoundedCornerShape(8.dp)).padding(12.dp),
            maxLines = 3,
            overflow = TextOverflow.Visible
        )

        Spacer(Modifier.height(25.dp))

        if (!bluetoothEnabled.value) {
            Button(
                onClick = { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                modifier = Modifier.fillMaxWidth(0.7f).height(55.dp)
            ) { Text("Enable Bluetooth", fontSize = 16.sp) }

            Spacer(Modifier.height(15.dp))
        }

        if (!locationEnabled.value) {
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(0.7f).height(55.dp)
            ) { Text("Enable Location", fontSize = 16.sp) }

            Spacer(Modifier.height(15.dp))
        }

        Button(
            onClick = { startBLEScan(bleScanner, devicesMap, devices) },
            enabled = bluetoothEnabled.value && locationEnabled.value,
            modifier = Modifier.fillMaxWidth(0.7f).height(55.dp)
        ) { Text("Scan BLE Devices", fontSize = 16.sp) }

        Spacer(Modifier.height(15.dp))

        Button(
            onClick = { /* Connect logic */ },
            enabled = selectedDevice.value != null,
            modifier = Modifier.fillMaxWidth(0.7f).height(55.dp)
        ) { Text("Connect Device", fontSize = 16.sp) }

        Spacer(Modifier.height(15.dp))

        Button(
            onClick = { /* Find command */ },
            enabled = selectedDevice.value != null,
            modifier = Modifier.fillMaxWidth(0.7f).height(55.dp)
        ) { Text("Find My Board", fontSize = 16.sp) }

        Spacer(Modifier.height(20.dp))

        DeviceList(devices.value, devicesMap, selectedDevice)
    }
}
