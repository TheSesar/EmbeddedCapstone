package com.example.smartglassesandroidapp;
// libraries
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class BLEManager {

    private final Context context; // Add this to access main activity context

    //****************** global variables ***NEW Caylan *********************
    private static final long SCAN_PERIOD = 45000;  // Stop scanning after 45 seconds
    // BLE ADAPTER to list off BLE devices on screen.
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    public static final String SCAN_TAG = "BLEScan";
    private boolean scanning = false; // - Caylan added
    private final Handler handler = new Handler(Looper.getMainLooper());
    // SCAN CALLBACK
    private final List<BluetoothDevice> leDeviceList = new ArrayList<>();
    private final ArrayAdapter<String> leDeviceListAdapter;

    // helper functions and gatt client
    private GATTClientManager GATTService;
    //****************** global variables *********************


    public BLEManager(Context context, GATTClientManager gattService, ArrayAdapter<String> leDeviceListAdapter) {
        this.context = context.getApplicationContext();
        this.GATTService = gattService;
        this.leDeviceListAdapter = leDeviceListAdapter;
    }






    /************************************
     ** SCANNING BLUETOOTH LE DEVICES  **
     ************************************/
    // BLE adapter to list off BLE devices on screen.
    // SCAN CALLBACK component
    // it adds found bluetooth devices to List: leDeviceList
    public final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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

    // START SCANNING function
    public void startScanDevice() {
        if (!scanning) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)

            {

                if (bluetoothAdapter == null) {
                    Log.e(SCAN_TAG, "Bluetooth is not unavailable.");
                    handler.post(() -> {
                        Toast.makeText(context, "Bluetooth adapter is NULL.", Toast.LENGTH_LONG).show();
                    });
                    return; // Exit the function if Bluetooth is not available
                } else if(!bluetoothAdapter.isEnabled()) {
                    handler.post(() -> {
                        Toast.makeText(context, "Bluetooth adapter failed enabled check.", Toast.LENGTH_LONG).show();
                    });
                }


                if (bluetoothLeScanner == null) {
                    // Attempt to get the BluetoothLeScanner from the BluetoothAdapter
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    if (bluetoothLeScanner == null) {
                        // If still null, show an error message and stop
                        Log.e(SCAN_TAG, "BluetoothLeScanner is null. Could not initialize scanner.");

                        handler.post(() -> {
                            Toast.makeText(context, "Failed to initialize Bluetooth scanner. Please check your device settings.", Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                }
                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
                Log.i(SCAN_TAG, "Started BLE Scan");
                handler.postDelayed(this::stopScanDevice, SCAN_PERIOD);
            } else {
                ActivityCompat.requestPermissions((Activity) context,                             // public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                        new String[]{Manifest.permission.BLUETOOTH_ADMIN,                  // to handle the case where the user grants the permission.
                                Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    // STOP SCANNING function
    public void stopScanDevice() {
        if (scanning) {
            scanning = false;
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w(SCAN_TAG, "Missing permissions on stopScanDevice");
                return;
            }
            bluetoothLeScanner.stopScan(leScanCallback);
            Log.i(SCAN_TAG, "Stopped BLE Scan");
        }
    }



    /************************************
     **       PERMISSION REQUEST       **
     ************************************/
    //app needs to get permissions from phone to access phone's hardware and sensitive resources like location/audio

    public static List<String> getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // Location permissions (except background)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // Bluetooth permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        } else {
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }

        return permissions;
    }

    //isBluetoothEnabled
    public boolean isBluetoothEnabled(Context context) {
        android.bluetooth.BluetoothManager bluetoothManager = (android.bluetooth.BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
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



    /************************************
     **  BLUETOOTH SERVICE CONNECTION  **
     ************************************/
    //disconnect
    public void disconnect() {
        if (GATTService != null) {
            GATTService.disconnect();  // this calls the low-level BLE disconnect
        }
    }

}
