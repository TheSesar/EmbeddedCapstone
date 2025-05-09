package com.example.smartglassesandroidapp;

/* startup libraries */
import android.os.Bundle;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ButtonBarLayout;
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

// background threads/activities
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;





public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;


    //****************** global variables ***NEW Caylan *********************
    private Button bleScanButton;
    private TextView statusTextView;
    private BluetoothDevice connectedDevice = null;  // To track the currently connected device

    private static final long SCAN_PERIOD = 45000;  // Stop scanning after 45 seconds
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    //****************** global variables *********************





    //============================================bluetooth====================================//
    // BLE adapter to list off BLE devices on screen.
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    /************************************
     ** SCANNING BLUETOOTH LE DEVICES  **
     ************************************/
    // GLOBAL VARIABLES
    public static final String SCAN_TAG = "BLEScan";
    private boolean scanning = false; // - Caylan added
    private final Handler handler = new Handler(Looper.getMainLooper());


    // SCAN CALLBACK
    private final List<BluetoothDevice> leDeviceList = new ArrayList<>();

    ///*causes app to crash*/ private final ArrayAdapter<String> leDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
    private ArrayAdapter<String> leDeviceListAdapter;

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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                                                     ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)

            {


                //****************APP is currently displaying this message!!!!!**********
                if (bluetoothAdapter == null) {
                    Log.e(SCAN_TAG, "Bluetooth is not unavailable.");
                    handler.post(() -> {
                        Toast.makeText(this, "Bluetooth adapter is NULL.", Toast.LENGTH_LONG).show();
                    });
                    return; // Exit the function if Bluetooth is not available
                } else if(!bluetoothAdapter.isEnabled()) {
                    handler.post(() -> {
                        Toast.makeText(this, "Bluetooth adapter failed enabled check.", Toast.LENGTH_LONG).show();
                    });
                }
                //****************APP is currently displaying this message!!!!!**********


                if (bluetoothLeScanner == null) {
                    // Attempt to get the BluetoothLeScanner from the BluetoothAdapter
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    if (bluetoothLeScanner == null) {
                        // If still null, show an error message and stop
                        Log.e(SCAN_TAG, "BluetoothLeScanner is null. Could not initialize scanner.");

                        handler.post(() -> {
                            Toast.makeText(this, "Failed to initialize Bluetooth scanner. Please check your device settings.", Toast.LENGTH_LONG).show();
                         });
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
            // leDeviceList.clear();
            // leDeviceListAdapter.clear();
            // leDeviceListAdapter.notifyDataSetChanged();
            // bleScanButton.setText("Start Scanning"); WE MIGHT NEED BUTTON LATER
        }
    }

    /************************************
     **       PERMISSION REQUEST       **
     ************************************/
    //app needs to get permissions from phone to access phone's hardware and sensitive resources like location/audio
    private static final int MULTIPLE_PERMISSIONS_REQUEST_CODE = 123;
    private static final int BLE_PERMISSION_REQUEST_CODE = 1;


private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 2;

private boolean checkAndRequestPermissions() {
    handler.post(() -> {Toast.makeText(this, "Checking permissions", Toast.LENGTH_LONG).show();});

    // Step 1: Handle regular permissions first
    List<String> regularPermissions = new ArrayList<>();

    // Location permissions (except background)
    regularPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    regularPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

    // Bluetooth permissions based on Android version
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ requires these specific permissions
        regularPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        regularPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
    } else {
        // For older versions
        regularPermissions.add(Manifest.permission.BLUETOOTH);
        regularPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
    }

    // Check which regular permissions need to be requested
    List<String> permissionsToRequest = new ArrayList<>();
    for (String permission : regularPermissions) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(permission);
            Log.i("permissions", permission + " not granted.");
        }
    }

    // Request regular permissions if needed
    if (!permissionsToRequest.isEmpty()) {
        handler.post(() -> {Toast.makeText(this, "Requesting regular permissions", Toast.LENGTH_SHORT).show();});
        ActivityCompat.requestPermissions(this,
                permissionsToRequest.toArray(new String[0]),
                MULTIPLE_PERMISSIONS_REQUEST_CODE);
        Log.i("permissions", "Requesting permissions: " + permissionsToRequest);
        return false;
    }

    // Step 2: Handle background location separately (requires special flow)
    // Background location requires foreground location permissions to be granted first
    // and requires a separate permission request with additional explanation
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Show explanation dialog for background location
            new AlertDialog.Builder(this)
                    .setTitle("Background Location Permission")
                    .setMessage("This app needs background location access to detect beacons when the app is in background. Please grant this permission for full functionality.")
                    .setPositiveButton("Request Permission", (dialog, which) -> {
                        // Request the background permission
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();

            return false;
        }
    }

    // If we reach here, all permissions are granted
    handler.post(() -> {Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();});
    Log.i("permissions", "All permissions granted!");
    return true;
}


    // Override onRequestPermissionsResult to handle permission results
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MULTIPLE_PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // Regular permissions granted, now check if we need background location
                checkAndRequestPermissions();
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Background location permission granted", Toast.LENGTH_SHORT).show();
                // Continue with your app initialization that requires all permissions
            } else {
                Toast.makeText(this, "Background location permission denied", Toast.LENGTH_LONG).show();
            }
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
                registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter(), Context.RECEIVER_NOT_EXPORTED);



            // Bind GATT service if not already bound
            if (GATTService == null) {
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
            handler.post(() -> statusTextView.setText(connecting_message));

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
            String disconnecting_message = getString(R.string.device_connected, deviceName11);
            handler.post(() -> statusTextView.setText(disconnecting_message));

            // Unregister the receiver to stop receiving GATT updates
            unregisterReceiver(gattUpdateReceiver);

            // Stop the BLE service (disconnecting from the device)
            Intent serviceIntent = new Intent(this, GATTClientManager.class);
            stopService(serviceIntent);

            // Unbind from the GATT service
            if (GATTService != null) {
                unbindService(serviceConnection);
                GATTService = null;
            }

            // Reset the connected device reference
            connectedDevice = null;
            Log.i("BLE", "Disconnected from device.");

        } else {
            Log.i("BLE", "No device connected.");
        }
    }


    /************************************
     **  BLUETOOTH SERVICE CONNECTION  **
     ************************************/

    private String deviceAddress;
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


    //BroadcastReceiver
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

        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter(), Context.RECEIVER_NOT_EXPORTED);

        if (GATTService != null) {
            final boolean result = GATTService.connect(deviceAddress);
            Log.d(SERVICE_TAG, "Connect request result=" + result);
        }
    }



    //onPause
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
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
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Navigation bar to switch between pages via bar buttons: Home, Dashboard, Notifications
        BottomNavigationView navView = findViewById(R.id.nav_view);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Instantiate the adapter immediately after setContentView.
        leDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);


        Toast.makeText(this, "Start Checking Permissions...", Toast.LENGTH_LONG).show();
        // 00. Check and request permissions
        if (checkAndRequestPermissions()) {
            Toast.makeText(this, " Result: Permissions Granted...", Toast.LENGTH_LONG).show();
            // 1. Instantiate Bluetooth adapter and scanner if Bluetooth is available
            //bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();

            if (bluetoothAdapter != null) {
                Toast.makeText(this, "bluetoothAdapter created!!!", Toast.LENGTH_LONG).show();
                // 2. Check if Bluetooth is enabled
                if (!isBluetoothEnabled(this)) {
                    Toast.makeText(this, "bluetooth not enabled, please enable!!!", Toast.LENGTH_LONG).show();
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    enableBluetoothLauncher.launch(enableBtIntent);
                    return; // Wait for user to enable Bluetooth before continuing
                } else {
                    Toast.makeText(this, "bluetooth enabled!!!", Toast.LENGTH_LONG).show();
                }

                // 3. Instantiate the BLE scanner
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                if (bluetoothLeScanner == null) {
                    Log.e("BLE", "Failed to initialize BluetoothLeScanner.");
                    Toast.makeText(this, "Bluetooth LE Scanner not initialized.", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    Toast.makeText(this, "Bluetooth LE Scanner initialized!!!", Toast.LENGTH_SHORT).show();
                }

            } else {
                // Bluetooth not supported
                Log.e("BLE", "Bluetooth not supported.");
                Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Result: Permissions NOT Granted...", Toast.LENGTH_LONG).show();
        }

        // Set up Bluetooth scan button
        bleScanButton = findViewById(R.id.scanButton);
        bleScanButton.setOnClickListener(v -> {
            if (!scanning) {
                // Disable the button to prevent multiple clicks during scan
                bleScanButton.setEnabled(false);
                executorService.execute(() -> {
                    startScanDevice();
                    handler.post(() -> {
                        bleScanButton.setText(R.string.stop_scanning);
                        bleScanButton.setEnabled(true); // Re-enable button after scan starts
                        Toast.makeText(this, "Not Scanning....", Toast.LENGTH_LONG).show(); //APP reached here!!!
                    });
                });
            } else {
                executorService.execute(() -> {
                    stopScanDevice();
                    handler.post(() -> {
                        bleScanButton.setText(R.string.start_scanning);
                    });
                    handler.post(() -> {
                        bleScanButton.setEnabled(true); // Re-enable button after scan stops
                    });
                });
            }
            handler.post(() -> {
                Toast.makeText(this, "Reached end of bleScanButton onClickListener", Toast.LENGTH_LONG).show(); //APP reached here!!!
            });


        });

        handler.post(() -> {
            Toast.makeText(this, "Reached ListView section....", Toast.LENGTH_LONG).show(); //APP reached here!!!
        });

        // Set up device status text view
        statusTextView = findViewById(R.id.statusWindow);

        // Set up ListView to display found devices
        ListView listView = findViewById(R.id.listDevices);
        listView.setAdapter(leDeviceListAdapter);

        // Let the user tap on a device name to connect
        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = leDeviceList.get(position);
            deviceAddress = device.getAddress();

            handler.post(() -> {
                Toast.makeText(this, "Reached listView onItemClick...", Toast.LENGTH_LONG).show(); //APP reached here!!!
            });

            if (connectedDevice != null && connectedDevice.equals(device)) {
                handler.post(() -> {
                    Toast.makeText(this, "entering disconnect from device branch", Toast.LENGTH_LONG).show(); //APP reached here!!!
                });
                executorService.execute(() -> {
                    disconnectDevice();
                    handler.post(() -> bleScanButton.setEnabled(true));
                });
            } else {
                handler.post(() -> {
                    Toast.makeText(this, "entering connect to device branch", Toast.LENGTH_LONG).show(); //APP reached here!!!
                });
                executorService.execute(() -> {
                    connectToDevice(device);
                    handler.post(() -> bleScanButton.setEnabled(false));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop scanning if it's running
        if (scanning && bluetoothLeScanner != null) {
            try {
                // Check if Bluetooth permissions are granted before stopping the scan
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(leScanCallback);  // Stop scan using the callback
                    scanning = false;
                } else {
                    // Handle the case where permissions are not granted
                    Log.e("BLE", "Bluetooth permission not granted to stop scan.");
                }
            } catch (SecurityException e) {
                // Handle SecurityException if permissions are not granted
                Log.e("BLE", "Failed to stop scan due to SecurityException: " + e.getMessage());
            }
        }

        // Clear the device list
        leDeviceList.clear();
        leDeviceListAdapter.clear();

        // Unbind the GATT service if bound
        if (GATTService != null) {
            unbindService(serviceConnection);  // Unbind from the service
            GATTService = null;  // Clear the reference to GATTService
        }

        // Unregister the BroadcastReceiver if registered
        try {
            unregisterReceiver(gattUpdateReceiver);
        } catch (IllegalArgumentException e) {
            // Handle case where receiver might not have been registered
            Log.w("BLE", "Receiver not registered", e);
        }

        // Shut down background thread pool (executor service)
        executorService.shutdownNow();  // Prevent thread leaks
    }






//    protected void onCreate(Bundle savedInstanceState) {
//        /* starter code for UI provided by project template selected */
//        super.onCreate(savedInstanceState);
//        binding = ActivityMainBinding.inflate(getLayoutInflater());
//        setContentView(binding.getRoot());
//
//
//        // Navigation bar to switch between pages via bar buttons: Home, Dashboard, Notifications
//        BottomNavigationView navView = findViewById(R.id.nav_view);
//        // Passing each menu ID as a set of Ids because each menu should be considered as top level destinations.
//        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
//                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
//                .build();
//        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
//        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
//        NavigationUI.setupWithNavController(binding.navView, navController);
//
//        // 00. Instantiate the adapter immediately after setContentView.
//        // Must create here in this function instead of during the variable declaration @ line 86
//        leDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
//
//        // 0. must first request phone for permissions to access BLE features
//        // Bluetooth permissions are required in Android for several reasons, mainly to ensure the privacy and security of users when interacting with Bluetooth-enabled devices
//        // permissions, especially for background operations and BLE, protect users' privacy and ensure they’re informed about what data an app might collect.
//        if (checkAndRequestPermissions()) {
//
//            // 1. instantiate adapter
//            // it enables all Bluetooth functionality (including scanning and connection).
//            // BLUETOOTH_SERVICE gives you access to the BluetoothManager, which is the system service that manages overall Bluetooth functionality — including BLE.
//            bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
//            if (bluetoothAdapter != null) {
//
//                // 2. Check if Bluetooth is enabled
//                if (!isBluetoothEnabled(this)) {
//
//                    // trigger a request for the user to enable it by launching an Intent with BluetoothAdapter.ACTION_REQUEST_ENABLE
//                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                    enableBluetoothLauncher.launch(enableBtIntent);
//                    return; // Wait for user to enable Bluetooth before continuing
//                }
//
//                // 3. instantiate scanner used for BLE specifically
//                // It finds nearby BLE devices and reports them through the ScanCallback
//                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
//            } else {
//
//                // when bluetooth not supported by phone, throw an unsupported error message
//                Log.e("BLE", "Bluetooth not supported.");
//                Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show();
//            }
//        }
//
//
//        // Set up bluetooth scan button
//        // Toggles between scanning and not scanning when the button is clicked
//        // Updates the button text to reflect the current state (Start ↔ Stop).
//        bleScanButton = findViewById(R.id.scanButton);
//        bleScanButton.setOnClickListener(v -> {
//            if (!scanning) {
//                startScanDevice();
//                bleScanButton.setText(R.string.start_scanning);
//            } else {
//                stopScanDevice();
//                bleScanButton.setText(R.string.stop_scanning);
//            }
//        });
//
//        // Set up deviceConnected flagger button
//        statusTextView = findViewById(R.id.statusWindow);
//
//        // Set up ListView to display found devices
//        // Binds the leDeviceListAdapter (which stores the names of discovered devices) to the ListView
//        ListView listView = findViewById(R.id.listDevices);
//        listView.setAdapter(leDeviceListAdapter);
//
//
//        // Lets the user tap on a device name in the list
//        // When tapped, it grabs the corresponding BluetoothDevice object (from leDeviceList) and attempts to connect to it via your connectToDevice() method.
//        listView.setOnItemClickListener((parent, view, position, id) -> {
//            BluetoothDevice device = leDeviceList.get(position);
//            deviceAddress = device.getAddress();
//
//            if (connectedDevice != null && connectedDevice.equals(device)) {
//                // Disconnect from the bluetooth device if the same device name is clicked on
//                disconnectDevice();
//                // Re-enable scan button
//                bleScanButton.setEnabled(true);
//            } else {
//                // Connect to a new bluetooth device, the device name just clicked on
//                connectToDevice(device);
//                // Disable scan button
//                bleScanButton.setEnabled(false);
//            }
//        });
//    }
}
