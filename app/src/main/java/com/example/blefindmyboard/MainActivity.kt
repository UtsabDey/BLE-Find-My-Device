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
import androidx.annotation.RequiresPermission
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

        setContent { BLEApp() }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun BLEApp() {
    val context = LocalContext.current
    val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    val scanner = bluetoothAdapter?.bluetoothLeScanner
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    val selectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
    val connectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
    val gatt = remember { mutableStateOf<BluetoothGatt?>(null) }
    val isScanning = remember { mutableStateOf(false) }

    val bluetoothEnabled = remember { mutableStateOf(bluetoothAdapter.isEnabled) }
    val locationEnabled = remember {
        mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    }

    // Monitor BT & Location status
    LaunchedEffect(Unit) {
        while (true) {
            bluetoothEnabled.value = bluetoothAdapter.isEnabled
            locationEnabled.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.LightGray).padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                connectedDevice.value != null -> "Connected: ${connectedDevice.value!!.name ?: connectedDevice.value!!.address}"
                selectedDevice.value != null -> "Selected: ${selectedDevice.value!!.name ?: selectedDevice.value!!.address}"
                else -> "No device selected"
            },
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(Color.Yellow, RoundedCornerShape(8.dp)).padding(12.dp)
        )

        Spacer(Modifier.height(20.dp))

        if (!bluetoothEnabled.value) {
            Button(onClick = { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
                Text("Enable Bluetooth")
            }
            Spacer(Modifier.height(10.dp))
        }

        if (!locationEnabled.value) {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text("Enable Location")
            }
            Spacer(Modifier.height(10.dp))
        }

        if (bluetoothEnabled.value && locationEnabled.value) {
            Button(onClick = {
                devices.clear()
                scanner?.startScan(object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        if (!devices.contains(result.device)) devices.add(result.device)
                    }
                })
                isScanning.value = true
            }) { Text("Scan Devices") }

            Spacer(Modifier.height(10.dp))

            Button(onClick = {
                scanner?.stopScan(object : ScanCallback() {})
                isScanning.value = false
            }, enabled = isScanning.value) { Text("Stop Scan") }

            Spacer(Modifier.height(10.dp))

            Button(onClick = {
                selectedDevice.value?.let { device ->
                    gatt.value?.disconnect()
                    gatt.value = device.connectGatt(context, false, object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                connectedDevice.value = device
                                gatt.discoverServices()
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                connectedDevice.value = null
                            }
                        }
                    })
                    Toast.makeText(context, "Connecting to ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                }
            }, enabled = selectedDevice.value != null) { Text("Connect") }
        }

        Spacer(Modifier.height(20.dp))
        LazyColumn {
            items(devices) { device ->
                Text(
                    text = device.name ?: device.address,
                    color = if (selectedDevice.value == device) Color.White else Color.Black,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDevice.value = device }
                        .background(
                            if (selectedDevice.value == device) Color.Blue else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                )
            }
        }
    }
}
