package com.example.smartglassesandroidapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.material.bottomnavigation.BottomNavigationView;

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
    private boolean scanning = false;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 45000;    // Stops scanning after 45 seconds.

    // SCAN CALLBACK
    private List<BluetoothDevice> leDeviceList = new ArrayList<>();
    private ArrayAdapter<String> leDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();

            if (ActivityCompat.checkSelfPermission(
                    MainActivity.this,
                    android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            if (device.getName() != null) {
                if (!leDeviceList.contains(device)) {
                    leDeviceList.add(device);
                }
                leDeviceListAdapter.add(device.getName());
                leDeviceListAdapter.notifyDataSetChanged();

                Log.i("onScanResult", "added and Filtered scan results");
                for (int i = 0; i < leDeviceList.toArray().length; i++) {
                    BluetoothDevice printDevice = leDeviceList.get(i);
                    Log.i("onScanResult", "Device: " + printDevice.getName() + " position: " + i);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i("BLEScan", "Scan Failed!");
        }
    };

    // START SCANNING
    private void startScanDevice() {
        if (!scanning) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopScanDevice();
                    }
                }, SCAN_PERIOD);

                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_ADMIN,
                                Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    // STOP SCANNING
    private void stopScanDevice() {
        if (scanning) {
            scanning = false;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothLeScanner.stopScan(leScanCallback);
            leDeviceList.clear();
            leDeviceListAdapter.clear();
            leDeviceListAdapter.notifyDataSetChanged();
            // bleScanButton.setText("Start Scanning"); WE MIGHT NEED BUTTON LATER
        }
    }
    /************************************
     **             END SCAN           **
     ************************************/

    /************************************
     **        HELPER FUNCTIONS        **
     ************************************/
    // CHECK IF DEVICE ACTIVATED BLUETOOTH
    public boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Device doesn't support Bluetooth");
            return false;
        }
        return bluetoothAdapter.isEnabled();
    }
    /************************************
     **           END HELPER           **
     ************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        startScanDevice();
    }

}