package com.example.smartglassesandroidapp;

import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.content.Intent; // FOR HELPER

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

// java library functions
import java.util.*;
import java.io.*;

// Bluetooth LE libraries we need to use for the connection
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.widget.ArrayAdapter;
import android.widget.Toast;


import com.example.smartglassesandroidapp.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // BLE adapter to list off BLE devices on screen.
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    /************************************
     ** SCANNING BLUETOOTH LE DEVICES  **
     ************************************/
    // GLOBAL VARIABLES
    public static final String SCAN_TAG = "BLEScan";
    private boolean scanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long SCAN_PERIOD = 45000;    // Stops scanning after 45 seconds.

    // SCAN CALLBACK
    private final List<BluetoothDevice> leDeviceList = new ArrayList<>();
    private final ArrayAdapter<String> leDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();

            if (ActivityCompat.checkSelfPermission(
                    MainActivity.this,
                    android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (device.getName() != null && !leDeviceList.contains(device)) {
                leDeviceList.add(device);
                leDeviceListAdapter.add(device.getName());
                leDeviceListAdapter.notifyDataSetChanged();
                Log.i("BLEScan", "Device Added: " + device.getName() + ", Total Devices: " + leDeviceList.size());
            }
        }


        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(SCAN_TAG, "Scan Failed!");
        }
    };

    // START SCANNING
    private void startScanDevice() {
        if (!scanning) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                    Log.e(SCAN_TAG, "Bluetooth is not enabled or unavailable.");
                    Toast.makeText(this, "Bluetooth is not enabled. Please enable Bluetooth and try again.", Toast.LENGTH_LONG).show();
                    return; // Exit the function if Bluetooth is not available
                }

                if (bluetoothLeScanner == null) {
                    // Attempt to get the BluetoothLeScanner from the BluetoothAdapter
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

                    if (bluetoothLeScanner == null) {
                        // If still null, show an error message and stop
                        Log.e(SCAN_TAG, "BluetoothLeScanner is null. Could not initialize scanner.");
                        Toast.makeText(this, "Failed to initialize Bluetooth scanner. Please check your device settings.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
                Log.i(SCAN_TAG, "Started BLE Scan");

                handler.postDelayed(this::stopScanDevice, SCAN_PERIOD);

            } else {
                ActivityCompat.requestPermissions(this,                             // public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                        new String[]{Manifest.permission.BLUETOOTH_ADMIN,                  // to handle the case where the user grants the permission.
                                Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    // STOP SCANNING
    private void stopScanDevice() {
        if (scanning) {
            scanning = false;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w(SCAN_TAG, "Missing permissions on stopScanDevice");
                return;
            }
            bluetoothLeScanner.stopScan(leScanCallback);
            Log.i(SCAN_TAG, "Stopped BLE Scan");

            // leDeviceList.clear();
            // leDeviceListAdapter.clear();
            // leDeviceListAdapter.notifyDataSetChanged();
            // bleScanButton.setText("Start Scanning"); WE MIGHT NEED BUTTON LATER
        }
    }

    /************************************
     **       PERMISSION REQUEST       **
     ************************************/


    private static final int MULTIPLE_PERMISSIONS_REQUEST_CODE = 123;
    private static final int BLE_PERMISSION_REQUEST_CODE = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start your Bluetooth operations
                startScanDevice();
            } else {
                // Permission denied, handle accordingly
                Toast.makeText(this, "Bluetooth permissions are required for scanning.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE,
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this,
                    permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                Log.i("permissions", permission + "not granted.");
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions,
                    MULTIPLE_PERMISSIONS_REQUEST_CODE);
            Log.i("permissions", "Permissions not granted!");
            return false;
        } else {
            Log.i("permissions", "Permissions granted!");
            return true;
        }
    }

    /************************************
     **        HELPER FUNCTIONS        **
     ************************************/
    // CHECK IF DEVICE ACTIVATED BLUETOOTH
    public boolean isBluetoothEnabled(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e("BluetoothManager", "Unable to initialize BluetoothManager.");
            return false;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Device doesn't support Bluetooth");
            return false;
        }
        return bluetoothAdapter.isEnabled();
    }

    private void handlePermissionsNotGranted(String permission) {
        Log.i("Permissions",
                permission + "not granted. Discovered in permission check before function call.");
    }

    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            handlePermissionsNotGranted(android.Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }
        // Starts bluetooth service using the device selected.
        if (device != null) {
            Intent serviceIntent = new Intent(this, GATTClientManager.class);
            serviceIntent.putExtra("GATTServiceDevice", device);
            // Starts foregrounded BLE and location service.
            startService(serviceIntent);
        }
    }

    private void disconnectDevice() {
        // Stop the BLE service
        Intent serviceIntent = new Intent(this, GATTClientManager.class);
        stopService(serviceIntent);
    }


    /************************************
     **  BLUETOOTH SERVICE CONNECTION  **
     ************************************/

    private String deviceAddress;
    public static final String SERVICE_TAG = "BLEService";
    private GATTClientManager GATTService;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GATTService = ((GATTClientManager.LocalBinder) service).getService();
            if (GATTService != null) {
                if (!GATTService.initialize()) {
                    Log.e(SERVICE_TAG, "Unable to initialize Bluetooth");
                    finish();
                }
                // perform device connection
                GATTService.connect(deviceAddress);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            GATTService = null;
        }
    };

    /************************************
     **     GATT SERVICE LISTENING     **
     ************************************/
    private boolean connected = false;
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (GATTClientManager.ACTION_GATT_CONNECTED.equals(action)) {
                connected = true;
            } else if (GATTClientManager.ACTION_GATT_DISCONNECTED.equals(action)) {
                connected = false;
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (GATTService != null) {
            final boolean result = GATTService.connect(deviceAddress);
            Log.d(SERVICE_TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GATTClientManager.ACTION_GATT_CONNECTED);
        intentFilter.addAction(GATTClientManager.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }


    /************************************
     **            MAIN APP            **
     ************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate the layout using ViewBinding (ActivityMainBinding)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up BottomNavigationView and AppBarConfiguration
        BottomNavigationView navView = findViewById(R.id.nav_view);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Initialize Bluetooth components
        if (isBluetoothEnabled(this)) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            // If Bluetooth is enabled, start scanning for BLE devices
            startScanDevice();
        } else {
            Log.e(SCAN_TAG, "Bluetooth is not enabled. Please enable Bluetooth and try again.");
            Toast.makeText(this, "Bluetooth is not enabled. Please enable Bluetooth and try again.", Toast.LENGTH_LONG).show();
        }

        // Bind GATT service
        Intent gattServiceIntent = new Intent(this, GATTClientManager.class);
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // UI

//        // Set up scan result list adapter for displaying found devices in the UI
//        binding.ListView.setAdapter(leDeviceListAdapter);
//
//        // Set up a listener for when a user selects a device from the list
//        binding.deviceListView.setOnItemClickListener((parent, view, position, id) -> {
//            BluetoothDevice selectedDevice = leDeviceList.get(position);  // Get the selected device
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                return;
//            }
//            Log.i(SCAN_TAG, "Device selected: " + selectedDevice.getName());
//            deviceAddress = selectedDevice.getAddress(); // Save device address for connection
//
//            // Try to connect to the selected Bluetooth device
//            connectToDevice(selectedDevice);
//        });
    }

}