package com.smartg.app;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

public class SecondActivity extends AppCompatActivity {

    /************************************
     **           GLOBALS              **
     ************************************/

    // Strings
    private String deviceAddress;
    public static final String SERVICE_TAG = "BLEService";
    public static final String SCAN_TAG = "BLEScan";

    //Integers and Longs
    private static final int MULTIPLE_PERMISSIONS_REQUEST_CODE = 123;
    private static final int BLE_PERMISSION_REQUEST_CODE = 1;
    private static final long SCAN_PERIOD = 45000;    // Stops scanning after 45 seconds.

    //Booleans
    private boolean connected = false;
    private boolean scanning = false;
    private boolean isServiceBound = false;

    private boolean isServiceReady = false;
    private boolean shouldStartScan = false;
    private boolean shouldConnectToDevice = false;


    //Instantiations
    private BluetoothAdapter bluetoothAdapter; // Adapter class
    private BluetoothLeScanner bluetoothLeScanner; // Scanner class
    private GATTService gattService; // Service class
    private BluetoothDevice connectedDevice = null;  //  connected bluetooth device
    private final List<BluetoothDevice> bleDeviceList = new ArrayList<>(); // list of bluetooth devices
    private ArrayAdapter<String> bleDeviceListAdapter; // adapter for list of bluetooth devices
    private Button bleScanButton; // UI button
    private Button unpairButton; // UI button
    private TextView statusTextView; // UI textview
    private final Handler handler = new Handler(Looper.getMainLooper()); // Handler class: “Create a Handler that runs code on the UI (main) thread.”
    private BluetoothDevice pendingDeviceToConnect;

    /************************************
     **      LIFECYCLE of ACTIVITY      **
     ************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        // Initialize buttons using findViewById
        Button translatorActivity = findViewById(R.id.translator_button);
        // Add click listener for next button
        translatorActivity.setOnClickListener(v -> {
            // Intent to start ThirdActivity
            Intent intent = new Intent(SecondActivity.this, ThirdActivity.class);
            // Start the activity
            startActivity(intent);
        });

        // Start and bind service
        Intent serviceIntent = new Intent(this, GATTService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // UI setup
        bleScanButton = findViewById(R.id.bluetooth_switch);
        unpairButton = findViewById(R.id.unpairButton);
        statusTextView = findViewById(R.id.connectedDevice);
        bleDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView listView = findViewById(R.id.listDevices);
        listView.setAdapter(bleDeviceListAdapter);

        // Disable scan button until service is ready
        bleScanButton.setEnabled(false);

        bleScanButton.setOnClickListener(v -> {
            if (isServiceReady && bluetoothLeScanner != null) {
                startScanDevice();
            } else {
                shouldStartScan = true; // Delay it
                Toast.makeText(this, "Bluetooth not ready. Will scan when ready.", Toast.LENGTH_SHORT).show();
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = bleDeviceList.get(position);
            deviceAddress = device.getAddress();

            if (connectedDevice != null) {
                if (gattService.getConnectedDevice().equals(device)) {
                    disconnectDevice();
                    bleScanButton.setEnabled(true);
                } else {
                    Toast.makeText(this, "Disconnect current device before connecting to another.", Toast.LENGTH_SHORT).show();
                    Toast.makeText(this, gattService.getConnectedDeviceName(), Toast.LENGTH_SHORT).show();

                }
            } else {
                if (isServiceReady) {
                    connectToDevice(device);
                    bleScanButton.setEnabled(false);
                } else {
                    shouldConnectToDevice = true;
                    pendingDeviceToConnect = device;
                    Toast.makeText(this, "Service not ready. Will connect when ready.", Toast.LENGTH_SHORT).show();
                }
            }
        });


        unpairButton.setOnClickListener(v, (parent, view, position, id)-> {
            BluetoothDevice device = bleDeviceList.get(position);
            deviceAddress = device.getAddress();
            if (connectedDevice != null) {
                if (gattService.getConnectedDevice().equals(device)) {
                    disconnectDevice();
                    bleScanButton.setEnabled(true);
                } else {
                    Toast.makeText(this, "Disconnect current device before connecting to another.", Toast.LENGTH_SHORT).show();
                    Toast.makeText(this, gattService.getConnectedDeviceName(), Toast.LENGTH_SHORT).show();

                }
            } else {
                if (isServiceReady) {
                    connectToDevice(device);
                    bleScanButton.setEnabled(false);
                } else {
                    shouldConnectToDevice = true;
                    pendingDeviceToConnect = device;
                    Toast.makeText(this, "Service not ready. Will connect when ready.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Registering in onResume() ensures your receiver is only active while the activity is visible.
    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(connectionReceiver, makeGattUpdateIntentFilter(), Context.RECEIVER_NOT_EXPORTED);
        if (gattService != null) {
            final boolean result = gattService.connect(deviceAddress);
            Log.d(SERVICE_TAG, "Connect request result=" + result);
        }
        IntentFilter filter = new IntentFilter("TEXT_DATA_READY");
        //LocalBroadcastManager.getInstance(this).registerReceiver(textReceiver, filter);
        registerReceiver(textReceiver, new IntentFilter("TEXT_DATA_READY"), Context.RECEIVER_NOT_EXPORTED);

    }


    // Unregistering in onPause() prevents memory leaks or unwanted callbacks when the activity is not in the foreground.
    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(connectionReceiver);
        } catch (IllegalArgumentException e) {
            Log.w("Pause", "connectionReceiver was not registered");
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(textReceiver);
    }


    // unbind from service when activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }


    /************************************
     ** SCANNING BLUETOOTH LE DEVICES  **
     ************************************/

    //ScanCallback adds found bluetooth devices to List: leDeviceList
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (ActivityCompat.checkSelfPermission(
                    SecondActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (device.getName() != null && !bleDeviceList.contains(device)) {
                bleDeviceList.add(device);
                bleDeviceListAdapter.add(device.getName());
                bleDeviceListAdapter.notifyDataSetChanged();
                Log.i("BLEScan", "Device Added: " + device.getName() + ", Total Devices: " + bleDeviceList.size());
            }

//            // Auto-connect to SmartGlassesMCU
//            if ("SmartGlassesMCU".equals(device.getName())) {
//                deviceAddress = device.getAddress();
//                Log.i("BLEScan", "Target device found: " + device.getName() + ": "+ deviceAddress);
//
//                // Actually connect to the auto-discovered device:
//                handler.post(() -> {
//                    if (isServiceReady && connectedDevice == null) {
//                        connectToDevice(device);
//                        stopScanDevice(); // Stop scanning once target found
//                    }
//                });
//            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(SCAN_TAG, "Scan Failed!");
        }
    };


    //startScanDevice method
    private void startScanDevice() {
        if (!scanning) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (bluetoothAdapter == null) {
                    Log.e(SCAN_TAG, "Bluetooth is unavailable.");
                    Toast.makeText(this, "Bluetooth is not available. Please connect bluetoothAdapter and try again.", Toast.LENGTH_LONG).show();
                    return; // Exit the function if Bluetooth is not available
                } else if (!bluetoothAdapter.isEnabled()) {
                    Log.e(SCAN_TAG, "Bluetooth is not enabled.");
                    Toast.makeText(this, "Bluetooth is not enabled. Please enable Bluetooth and try again.", Toast.LENGTH_LONG).show();
                    return; // Exit the function if Bluetooth not enabled
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
                handler.postDelayed(() -> {
                    stopScanDevice();
                    scanning = false;
                    runOnUiThread(() -> bleScanButton.setText(R.string.start_scanning));
                }, SCAN_PERIOD);
            } else {
                ActivityCompat.requestPermissions(this,                             // public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                        new String[]{Manifest.permission.BLUETOOTH_ADMIN,                  // to handle the case where the user grants the permission.
                                Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }


    //stopScanDevice method
    private void stopScanDevice() {
        if (scanning) {
            scanning = false;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w(SCAN_TAG, "Missing permissions on stopScanDevice");
                return;
            }
            bluetoothLeScanner.stopScan(leScanCallback);
            Log.i(SCAN_TAG, "Stopped BLE Scan");
        }
    }


    /***********************************************************************************************************************
     **       PERMISSION REQUEST:
     * app needs to get permissions from phone to access phone's hardware and sensitive resources like location/audio     **
     **********************************************************************************************************************/

    //onRequestPermissionsResult method
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

    //checkAndRequestPermissions method
    private boolean checkAndRequestPermissions() {

        Toast.makeText(this, "Checking Permissions...", Toast.LENGTH_LONG).show();

        String[] permissions = new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
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
            Toast.makeText(this, "False Result on Permissions", Toast.LENGTH_LONG).show();
            return false;
        } else {
            Log.i("permissions", "Permissions granted!");
            Toast.makeText(this, "Positive Result on Permissions", Toast.LENGTH_LONG).show();
            return true;
        }
    }


    /************************************+++++++++++++++++++++++++++++++++++++++
     **        HELPER FUNCTIONS: check if bluetooth has been activated       **
     **************************************************************************/

//    //isBluetoothEnabled method
//    public boolean isBluetoothEnabled(Context context) {
//        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
//        if (bluetoothManager == null) {
//            Log.e("BluetoothManager", "Unable to initialize BluetoothManager.");
//            return false;
//        }
//
//        bluetoothAdapter = bluetoothManager.getAdapter();
//        if (bluetoothAdapter == null) {
//            Log.e("Bluetooth", "Device doesn't support Bluetooth");
//            return false;
//        }
//        return bluetoothAdapter.isEnabled();
//    }

    //handlePermissionsNotGranted method
    private void handlePermissionsNotGranted() {
        Log.i("Permissions",
                Manifest.permission.BLUETOOTH_CONNECT + "not granted. Discovered in permission check before function call.");
    }


    /************************************+++++++++++++++++++++++++++++++++++++++
     **        DEVICE CONNECTION FUNCTIONS:
     *  connect or disconnect from bluetooth ble device                       **
     **************************************************************************/

    //connectToDevice method
    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            handlePermissionsNotGranted();
            return;
        }
        if (device == null) {
            Log.w("BLE", "No device provided for connection");
            return;
        }

        // Register BroadcastReceiver to listen to connection updates
        // registerReceiver(connectionReceiver, makeGattUpdateIntentFilter(), Context.RECEIVER_NOT_EXPORTED); //duplicate line!
        if (gattService != null && isServiceBound) {
            // Ask the service to connect to this device
            gattService.connect(device.getAddress());
            connectedDevice = device;
            Log.i("BLE", "Requested service to connect to device: " + device.getName());
            // Update UI
            //statusTextView.setText(getSafeDeviceName(device));
            if (connectedDevice.equals(gattService.getConnectedDevice())) {
                statusTextView.setText(gattService.getConnectedDeviceName());
            }
        } else {
            Log.w("BLE", "Service not bound, cannot connect");
        }
    }


    //disconnectDevice method
    private void disconnectDevice() {
        // Check for BLUETOOTH_CONNECT permission
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            handlePermissionsNotGranted();
            return;
        }

        if (gattService!= null && isServiceBound && connectedDevice != null) {
            // Ask the service to disconnect
            gattService.disconnect();
            // Unregister receivers
            try {
                unregisterReceiver(connectionReceiver);
            } catch (IllegalArgumentException e) {
                Log.w("BLE", "connectionReceiver was not registered");
            }

            connectedDevice = null;
            if (gattService.getConnectedDevice() == null) {
                // Update UI
                statusTextView.setText(R.string.no_device);
            } else {
                // Update UI
                statusTextView.setText(R.string.device_still_linked);
                //statusTextView.setText(gattService.getConnectedDeviceName());
            }
            Log.i("BLE", "Requested service to disconnect device.");
        } else {
            Log.w("BLE", "No connected device or service not bound.");
        }
    }

    // In SecondActivity, add method to check service connection state:
    private boolean isDeviceConnected() {
        return gattService != null && gattService.isConnected();
    }


    /************************************
     **  BLUETOOTH SERVICE CONNECTION  **
     ************************************/

    //ServiceConnection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            gattService = ((GATTService.LocalBinder) service).getService();
            if (gattService != null) {
                if (!gattService.initialize()) {
                    Log.e(SERVICE_TAG, "Unable to initialize Bluetooth");
                    finish();
                    return;
                }
                isServiceBound = true;

                // Check if device is already connected
                if (gattService.isConnected()) {
                    // Device is connected, ready for reads/writes
                    String deviceName = gattService.getConnectedDeviceName();
                    // Update UI accordingly
                    // Update the TextView on the UI thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (deviceName != null && !deviceName.isEmpty()) {
                                statusTextView.setText(deviceName);
                            } else {
                                statusTextView.setText("Unknown Device");
                            }
                        }
                    });
                }

                if (!checkAndRequestPermissions()) {
                    //Toast.makeText(this, "Permissions NOT Granted...", Toast.LENGTH_LONG).show();
                    Log.e(SERVICE_TAG, "Permissions NOT Granted...");
                    return;
                }

                bluetoothAdapter = gattService.getBluetoothAdapter();
                if (bluetoothAdapter == null) {
                    //Toast.makeText(this, "Bluetooth not supported on this device.", Toast.LENGTH_LONG).show();
                    Log.e(SERVICE_TAG, "Bluetooth not supported on this device.");
                    return;
                }

                // Enable Bluetooth if not initialized
                if (!gattService.isBluetoothInitialized()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    enableBluetoothLauncher.launch(enableBtIntent);
                    return; // Wait for user to enable
                }

                // Everything is ready
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                isServiceReady = true;
                bleScanButton.setEnabled(true); // Re-enable now that it's ready

                // ✅ Execute any deferred actions
                if (shouldStartScan) {
                    startScanDevice();
                    shouldStartScan = false;
                }

                if (shouldConnectToDevice && pendingDeviceToConnect != null) {
                    connectToDevice(pendingDeviceToConnect);
                    pendingDeviceToConnect = null;
                    shouldConnectToDevice = false;
                    bleScanButton.setEnabled(false);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            gattService = null;
            isServiceBound = false;
        }
    };

    /*************************************************************
     **     GATT SERVICE LISTENING:
     *         [BLE Device Sends Data]
     *                     ↓
     *         [BluetoothGattCallback onCharacteristicChanged()]
     *                     ↓
     *         Send Local Broadcast ("IMAGE_DATA_READY")
     *                     ↓
     *         [BroadcastReceiver onReceive()]
     *                     ↓
     *         Update UI (imageView.setImageBitmap, etc.)       **
     *************************************************************/

    //Broadcast receiver For image/data handling: Gets data from BLE layer (e.g. images) and updates UI
    private final BroadcastReceiver textReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("Data Receiver", "OnReceive Activated");
            if ("TEXT_DATA_READY".equals(intent.getAction())) {
                String text = intent.getStringExtra("incoming_text_data");
                Log.i("TextReceiver", "CHECK TEXT: " + text);
                // ADD UI
            }
        }
    };

    // Broadcast receiver for connection state
//    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            final String action = intent.getAction();
//            if (GATTService.ACTION_GATT_CONNECTED.equals(action)) {
//                connected = true;
//            } else if (GATTService.ACTION_GATT_DISCONNECTED.equals(action)) {
//                connected = false;
//            }
//        }
//    };

    // Update your connectionReceiver to use service state:
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (GATTService.ACTION_GATT_CONNECTED.equals(action)) {
                connected = true;
                runOnUiThread(() -> updateConnectionUI());
            } else if (GATTService.ACTION_GATT_DISCONNECTED.equals(action)) {
                connected = false;
                runOnUiThread(() -> updateConnectionUI());
            }
        }
    };

    private void updateConnectionUI() {
        if (gattService != null && gattService.isConnected()) {
            String deviceName = gattService.getConnectedDeviceName();
            statusTextView.setText(deviceName != null ? deviceName : "Connected Device");
            bleScanButton.setEnabled(false);
        } else {
            statusTextView.setText(R.string.no_device);
            bleScanButton.setEnabled(true);
            connectedDevice = null;
        }
    }

    //makeGattUpdateIntentFilter method
    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GATTService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(GATTService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }



    //============================================helper functions *** Caylan====================================//
    // Launcher to handle the result of enabling Bluetooth
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Bluetooth is now enabled; safe to get scanner
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    Toast.makeText(this, "Bluetooth enabled.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Bluetooth must be enabled to use this feature.", Toast.LENGTH_LONG).show();
                }
            });

    // check bluetooth permission before getting bluetooth device name, returns device name
    private String getSafeDeviceName(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            return device != null ? device.getName() : "Unknown";
        }
        return "Unknown";
    }
    //============================================helper functions *** Caylan====================================//


}