package com.example.blefindmyboard

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
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

        setContent { SimpleBLEApp() }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun SimpleBLEApp() {
    val context = LocalContext.current
    val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    val scanner = bluetoothAdapter?.bluetoothLeScanner
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var gatt by remember { mutableStateOf<BluetoothGatt?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("No device selected") }

    var bluetoothEnabled by remember { mutableStateOf(bluetoothAdapter.isEnabled) }
    var locationEnabled by remember {
        mutableStateOf(
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        )
    }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (!devices.contains(device)) devices = devices + device
                statusText = "Devices found: ${devices.size}"
            }

            override fun onScanFailed(errorCode: Int) {
                statusText = "Scan failed: $errorCode"
                isScanning = false
            }
        }
    }

    // Monitor BT & Location
    LaunchedEffect(Unit) {
        while (true) {
            bluetoothEnabled = bluetoothAdapter.isEnabled
            locationEnabled =
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            delay(1000)
        }
    }

    // -------------------- UI --------------------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status
        Text(
            text = when {
                connectedDevice != null -> "Connected: ${connectedDevice!!.name ?: connectedDevice!!.address}"
                selectedDevice != null -> "Selected: ${selectedDevice!!.name ?: selectedDevice!!.address}"
                isScanning -> "Scanning..."
                else -> statusText
            },
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Yellow, RoundedCornerShape(8.dp))
                .padding(12.dp)
        )

        Spacer(Modifier.height(20.dp))

        // Enable BT / Location
        if (!bluetoothEnabled) Button(onClick = { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) { Text("Enable Bluetooth") }
        if (!locationEnabled) Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Enable Location") }

        Spacer(Modifier.height(10.dp))

        if (bluetoothEnabled && locationEnabled) {
            Button(onClick = {
                devices = emptyList()
                selectedDevice = null
                connectedDevice = null
                statusText = "Scanning..."
                scanner?.startScan(scanCallback)
                isScanning = true
            }) { Text("Scan Devices") }

            Spacer(Modifier.height(10.dp))

            Button(onClick = {
                scanner?.stopScan(scanCallback)
                isScanning = false
                statusText = "Scan stopped"
            }, enabled = isScanning) { Text("Stop Scan") }

            Spacer(Modifier.height(10.dp))

            Button(onClick = {
                selectedDevice?.let { device ->
                    gatt?.disconnect()
                    gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) connectedDevice = device
                            else if (newState == BluetoothProfile.STATE_DISCONNECTED) connectedDevice = null
                        }
                    })
                    Toast.makeText(context, "Connecting to ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                }
            }, enabled = selectedDevice != null) { Text("Connect") }
        }

        Spacer(Modifier.height(20.dp))

        LazyColumn {
            items(devices) { device ->
                val isSelected = selectedDevice == device
                Text(
                    text = device.name ?: device.address,
                    fontSize = 16.sp,
                    color = if (isSelected) Color.White else Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDevice = device }
                        .background(if (isSelected) Color.Blue else Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                )
            }
        }
    }
}
