package com.example.smartglassesandroidapp;

/* startup libraries */
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ButtonBarLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.example.smartglassesandroidapp.databinding.ActivityMainBinding;

/* additional libraries */
import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.content.Intent; // FOR HELPER
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
//android widget libraries
import android.widget.Button;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

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
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Button bleScanButton;
    private TextView statusTextView;
    private BluetoothDevice connectedDevice = null;  // To track the currently connected device
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    public static final String SCAN_TAG = "BLEScan";
    private boolean scanning = false; // - Caylan added
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long SCAN_PERIOD = 45000;    // Stops scanning after 45 seconds.
    private String deviceAddress;

    // SCAN CALLBACK
    private final List<BluetoothDevice> leDeviceList = new ArrayList<>();
    private ArrayAdapter<String> leDeviceListAdapter;

    private static final int MULTIPLE_PERMISSIONS_REQUEST_CODE = 123;
    private static final int BLE_PERMISSION_REQUEST_CODE = 1;
    private boolean connected = false;



    //============================================bluetooth====================================//
    // BLE adapter to list off BLE devices on screen.

    /************************************
     ** SCANNING BLUETOOTH LE DEVICES  **
     ************************************/
    // GLOBAL VARIABLES



    //ScanCallback
    // it adds found bluetooth devices to List: leDeviceList
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

            // Auto-connect to SmartGlassesMCU
            if ("SmartGlassesMCU".equals(device.getName())) {
                deviceAddress = device.getAddress();
                Log.i("BLEScan", "Target device found: " + device.getName() + ": "+ deviceAddress);
            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(SCAN_TAG, "Scan Failed!");
        }
    };

    // START SCANNING
    //startScanDevice method
    private void startScanDevice() {
        if (!scanning) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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

    // STOP SCANNING
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

    /************************************
     **       PERMISSION REQUEST       **
     ************************************/
    //app needs to get permissions from phone to access phone's hardware and sensitive resources like location/audio



    //onRequestPermissionsResult
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

    //checkAndRequestPermissions
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

    /************************************
     **        HELPER FUNCTIONS        **
     ************************************/
    // CHECK IF DEVICE ACTIVATED BLUETOOTH

    //isBluetoothEnabled
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

    //handlePermissionsNotGranted
    private void handlePermissionsNotGranted(String permission) {
        Log.i("Permissions",
                permission + "not granted. Discovered in permission check before function call.");
    }


    //connectToDevice
    //***NEWly modified Caylan
    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            handlePermissionsNotGranted(android.Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }
        // Starts bluetooth service using the device selected.
        if (device != null) {

            // Register the BroadcastReceiver to start receiving GATT updates
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(connectionReceiver, makeGattUpdateIntentFilter(), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(connectionReceiver, makeGattUpdateIntentFilter());
            }


            // Bind GATT service if not already bound
            if (GATTService == null) {
                Log.i("GATT", "Connecting to the service");
                // create intent to bind to Gatt Client Manager Service
                Intent gattServiceIntent = new Intent(this, GATTClientManager.class);
                // use serviceConnection() to connect to Gatt service
                bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            }

            // Create an intent to start the GATT client manager service
            Intent serviceIntent = new Intent(this, GATTClientManager.class);

            // Pass the Bluetooth device to the GATT service using an extra in the intent
            serviceIntent.putExtra("GATTServiceDevice", device);

            // Starts foregrounded BLE and location service.
            // Start the service. This will initiate the Bluetooth Low Energy (BLE) connection to the device.
            startService(serviceIntent);

            // Update connected device reference
            connectedDevice = device;
            Log.i("BLE", "Connected to device: " + device.getName());

            // UI connecting message
            //--must check for permission again to get device name
            String deviceName1 = getSafeDeviceName(connectedDevice);
            String connecting_message = getString(R.string.device_disconnected, deviceName1);
            statusTextView.setText(connecting_message);
            Log.i("BLE", "Connected status: " + connected);
        }
    }


    //disconnectDevice
    //***NEWly modified Caylan
    private void disconnectDevice() {
        // Check for BLUETOOTH_CONNECT permission
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            handlePermissionsNotGranted(android.Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }

        // Stop the BLE service
        if (connectedDevice != null) {
            Log.i("BLE", "Disconnecting from device: " + connectedDevice.getName());

            // UI disconnecting message
            //--must check for permission again to get device name
            String deviceName11 = getSafeDeviceName(connectedDevice);
            String connecting_message = getString(R.string.device_connected, deviceName11);
            statusTextView.setText(connecting_message);

            // Unregister the receiver to stop receiving GATT updates
            unregisterReceiver(connectionReceiver);

            // Stop the BLE service (disconnecting from the device)
            Intent serviceIntent = new Intent(this, GATTClientManager.class);
            stopService(serviceIntent);

            // Unbind from the GATT service
            if (GATTService != null) {
                Log.i("GATT", "Disconnecting from service.");
                unbindService(serviceConnection);
                GATTService = null;
            }

            // Reset the connected device reference
            connectedDevice = null;
            Log.i("BLE", "Disconnected from device.");

            if (GATTService == null) {
                Log.i("GATT", "Disconnected from service.");
            }

            Log.i("BLE", "Connected status: " + connected);
        } else {
            Log.i("BLE", "No device connected.");
        }
    }


    /************************************
     **  BLUETOOTH SERVICE CONNECTION  **
     ************************************/

    public static final String SERVICE_TAG = "BLEService";
    private GATTClientManager GATTService;


    //ServiceConnection
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
                if (deviceAddress != null) {
                    GATTService.connect(deviceAddress);
                    Log.i(SERVICE_TAG, "GATT successfully connected");
                } else {
                    Log.e(SERVICE_TAG, "GATT connection failed due to scanning could not find the mcu!");
                }

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

    /*
        [BLE Device Sends Data]
                    ↓
        [BluetoothGattCallback onCharacteristicChanged()]
                    ↓
        Send Local Broadcast ("IMAGE_DATA_READY")
                    ↓
        [BroadcastReceiver onReceive()]
                    ↓
        Update UI (imageView.setImageBitmap, etc.)
    */
    private ImageView imageView; // Widget

    //BroadcastReceiver

    // For image handling
    // Gets data from BLE layer (e.g. images) and updates UI
    private final BroadcastReceiver imageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("IMAGE_DATA_READY".equals(intent.getAction())) {
                byte[] imageData = intent.getByteArrayExtra("image_bitmap");
                assert imageData != null;
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                imageView.setImageBitmap(bitmap);
            }
        }
    };

    // For connection state
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
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


    // Registering in onResume() ensures your receiver is only active while the activity is visible.
    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectionReceiver, makeGattUpdateIntentFilter(), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(connectionReceiver, makeGattUpdateIntentFilter());
        }
        if (GATTService != null) {
            final boolean result = GATTService.connect(deviceAddress);
            Log.d(SERVICE_TAG, "Connect request result=" + result);
        }
        // Simple communication from BLE handler to UI
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(imageReceiver, new IntentFilter("IMAGE_DATA_READY"));
    }


    // Unregistering in onPause() prevents memory leaks or unwanted callbacks when the activity is not in the foreground.
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(connectionReceiver);
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(imageReceiver);
    }


    //makeGattUpdateIntentFilter
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GATTClientManager.ACTION_GATT_CONNECTED);
        intentFilter.addAction(GATTClientManager.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }


    //============================================bluetooth====================================//


    //============================================helper functions ***NEW Caylan====================================//
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
    //============================================helper functions ***NEW Caylan====================================//






    //========================== main method, upon creation of the application the following actions should execute====================//
    //onCreate
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* starter code for UI provided by project template selected */
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        imageView = findViewById(R.id.image_view);

        leDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);


        // Navigation bar to switch between pages via bar buttons: Home, Dashboard, Notifications
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // 0. must first request phone for permissions to access BLE features
        // Bluetooth permissions are required in Android for several reasons, mainly to ensure the privacy and security of users when interacting with Bluetooth-enabled devices
        // permissions, especially for background operations and BLE, protect users' privacy and ensure they’re informed about what data an app might collect.
        if (checkAndRequestPermissions()) {
            Toast.makeText(this, "Permissions Granted...", Toast.LENGTH_LONG).show();

            // 1. instantiate adapter
            // it enables all Bluetooth functionality (including scanning and connection).
            // BLUETOOTH_SERVICE gives you access to the BluetoothManager, which is the system service that manages overall Bluetooth functionality — including BLE.
            bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
            if (bluetoothAdapter != null) {

                // 2. Check if Bluetooth is enabled
                if (!isBluetoothEnabled(this)) {

                    // trigger a request for the user to enable it by launching an Intent with BluetoothAdapter.ACTION_REQUEST_ENABLE
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    enableBluetoothLauncher.launch(enableBtIntent);
                    return; // Wait for user to enable Bluetooth before continuing
                }

                // 3. instantiate scanner used for BLE specifically
                // It finds nearby BLE devices and reports them through the ScanCallback
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            } else {

                // when bluetooth not supported by phone, throw an unsupported error message
                Log.e("BLE", "Bluetooth not supported.");
                Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show();
            }


        } else {
            Toast.makeText(this, "Permissions NOT Granted...", Toast.LENGTH_LONG).show();
        }


        // Set up bluetooth scan button
        // Toggles between scanning and not scanning when the button is clicked
        // Updates the button text to reflect the current state (Start ↔ Stop).
        bleScanButton = findViewById(R.id.scanButton);
        bleScanButton.setOnClickListener(v -> {
            if (!scanning) {
                startScanDevice();
                bleScanButton.setText(R.string.stop_scanning);
            } else {
                stopScanDevice();
                bleScanButton.setText(R.string.start_scanning);
            }
        });

        // Set up deviceConnected flagger button
        statusTextView = findViewById(R.id.statusWindow);

        // Set up ListView to display found devices
        // Binds the leDeviceListAdapter (which stores the names of discovered devices) to the ListView
        ListView listView = findViewById(R.id.listDevices);
        listView.setAdapter(leDeviceListAdapter);


        // Lets the user tap on a device name in the list
        // When tapped, it grabs the corresponding BluetoothDevice object (from leDeviceList) and attempts to connect to it via your connectToDevice() method.
        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = leDeviceList.get(position);
            deviceAddress = device.getAddress();

            if (connectedDevice != null && connectedDevice.equals(device)) {
                // Disconnect from the bluetooth device if the same device name is clicked on
                disconnectDevice();
                // Re-enable scan button
                bleScanButton.setEnabled(true);
            } else {
                // Connect to a new bluetooth device, the device name just clicked on
                connectToDevice(device);
                // Disable scan button
                bleScanButton.setEnabled(false);
            }
        });
    }
}
