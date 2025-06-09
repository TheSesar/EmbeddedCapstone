package com.smartg.app;
//LIBRARIES
import static androidx.constraintlayout.widget.ConstraintLayoutStates.TAG;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
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
import android.bluetooth.BluetoothProfile;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;


// second activity is an activity class
// this activity is the android page that starts bluetooth service, scans for available BLE devices, allows pairing and unpairing to a BLE device
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
    private static final int MAX_CONNECTION_ATTEMPTS = 2;
    private int connectionAttempts = 0;

    //Booleans
    private boolean connected = false;
    private boolean scanning = false;
    private boolean isServiceBound = false;
    private boolean isServiceReady = false;
    private boolean shouldStartScan = false;
    private boolean shouldConnectToDevice = false;
    private boolean isReceiverRegistered = false; // Track receiver registration

    //Instantiations
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private GATTService gattService;
    private final List<BluetoothDevice> bleDeviceList = new ArrayList<>();
    private ArrayAdapter<String> bleDeviceListAdapter;
    private Button bleScanButton;
    private Button unpairButton;
    private TextView statusTextView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothDevice pendingDeviceToConnect;

    /********************************************************
     **            LIFECYCLE methods of ACTIVITY            **
     ********************************************************/
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        // setup intent for button that navigates second activity to third activity
        // Initialize buttons using findViewById
        Button translatorActivity = findViewById(R.id.translator_button);
        translatorActivity.setOnClickListener(v -> {
            Intent intent = new Intent(SecondActivity.this, ThirdActivity.class);
            startActivity(intent);
        });

        // Start service as foreground service and bind activity to service
        Intent serviceIntent = new Intent(this, GATTService.class);
        // Start as foreground service immediately using startForegroundService() not startService().
        // Check if service is already running before starting it
        if (!GATTSingleton.getInstance().isServiceRunning()) {
            startForegroundService(serviceIntent);
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // bluetooth scan and pair UI elements
        bleScanButton = findViewById(R.id.bluetooth_switch);
        unpairButton = findViewById(R.id.unpairButton);
        statusTextView = findViewById(R.id.connectedDevice);
        bleDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView listView = findViewById(R.id.listDevices);
        listView.setAdapter(bleDeviceListAdapter);

        // Disable scan button until service is ready
        bleScanButton.setEnabled(false);
        // Scan button click handler
        bleScanButton.setOnClickListener(v -> {
            if (isServiceReady && bluetoothLeScanner != null) {
                startScanDevice();
            } else {
                shouldStartScan = true;
                Toast.makeText(this, "Bluetooth not ready. Will scan when ready.", Toast.LENGTH_SHORT).show();
            }
        });

        // ListView click handler
        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = bleDeviceList.get(position);
            Log.i("BLE", "Clicked device: " + (device != null ? device.getName() + " [" + device.getAddress() + "]" : "NULL"));
            //Use Singleton to determine if a device is already connected
            BluetoothDevice currentlyConnected = gattService.getConnectedDevice();
            if (currentlyConnected != null) {
                Log.i("BLE", "ACT2: onCreate -- listview -- a device is currently paired");
                // A device is currently connected
                if (currentlyConnected.getAddress().equals(device.getAddress())) {
                    Log.i("BLE", "ACT2: onCreate -- listview -- clicked current device and now calling disconnectDevice method ");
                    // Clicking on the currently connected device - disconnect it
                    disconnectDevice();
                } else {
                    Log.i("BLE", "ACT2: onCreate -- listview -- clicked a device that's not the paired device -- displaying warning message ");
                    // Clicking on a different device - inform user to disconnect first
                    Toast.makeText(this, "Disconnect current device first: " + currentlyConnected, Toast.LENGTH_SHORT).show();
                    //getSafeDeviceName(currentlyConnected)
                }
            } else {
                Log.i("BLE", "ACT2: onCreate -- listview -- a device is currently not paired");
                // No device connected - connect to selected device
                if (isServiceReady) {
                    Log.w(SERVICE_TAG, "service is ready. Begin pairing device");
                    connectToDevice(device);
                } else {
                    Log.w(SERVICE_TAG, "service not ready. Device is pending in its pairing");
                    shouldConnectToDevice = true;
                    pendingDeviceToConnect = device;
                    Toast.makeText(this, "Service not ready. Will connect when ready.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register receivers only if not already registered
        if (!isReceiverRegistered) {
            registerReceiver(connectionReceiver, makeGattUpdateIntentFilter(), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(textReceiver, new IntentFilter("TEXT_DATA_READY"), Context.RECEIVER_NOT_EXPORTED);
            isReceiverRegistered = true;
        }

        // Verify service readiness before trying to reconnect
        if (isServiceBound && isServiceReady && deviceAddress != null) {
            final boolean result = gattService.connect(deviceAddress);
            Log.d(SERVICE_TAG, "Connect request result=" + result);
        } else {
            Log.w(SERVICE_TAG, "Skipping reconnection; service not ready.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister receivers if they were registered
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(connectionReceiver);
                unregisterReceiver(textReceiver);
                isReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
                Log.w("Pause", "Receiver was not registered: " + e.getMessage());
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanning) {
            stopScanDevice();
        }
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        if (GATTSingleton.getInstance().getBluetoothGatt() != null) {
            GATTSingleton.getInstance().disconnect(); // Properly cleanup GATT & stop service if safe
        }
        if (gattService != null) {
            gattService.disconnect(true); // Ensure full BLE cleanup before app closes
        }
        if (!GATTSingleton.getInstance().isServiceRunning()) {
            stopService(new Intent(this, GATTService.class)); // Stop service only if no longer needed
        }
    }

    /*********************************************************
     **           DONE! BLUETOOTH SERVICE CONNECTION         **
     *********************************************************/
    //DONE
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        //DONE
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            gattService = ((GATTService.LocalBinder) service).getService();
            if (gattService != null) {
                if (!gattService.initialize()) {
                    Log.e(SERVICE_TAG, "Unable to initialize Bluetooth");
                    if (!gattService.isBluetoothInitialized()) {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        enableBluetoothLauncher.launch(enableBtIntent); //ask the user to enable Bluetooth instead of immediately closing the app.
                        return;
                    }
                    Log.e(SERVICE_TAG, "Bluetooth cannot be initialized. Exiting.");
                    finish();
                    return;
                }
                isServiceBound = true;
                Log.e(SERVICE_TAG, "gattService has been initialized and service is now bound.");
                // Update UI based on current connection state
                updateConnectionUI();
                if (!checkAndRequestPermissions()) {
                    Log.e(SERVICE_TAG, "Permissions NOT Granted...");
                    return;
                }

                bluetoothAdapter = GATTSingleton.getInstance().getBluetoothAdapter();
                if (bluetoothAdapter == null) {
                    Log.e(SERVICE_TAG, "Bluetooth not supported on this device.");
                    return;
                }

                if (!gattService.isBluetoothInitialized()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    enableBluetoothLauncher.launch(enableBtIntent);
                    return;
                }

                // Service is ready
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                isServiceReady = true;
                bleScanButton.setEnabled(true);

                // Execute deferred actions
                if (shouldStartScan) {
                    Log.e(SERVICE_TAG, "deferred scan is now being executed.");
                    startScanDevice();
                    shouldStartScan = false;
                } else {
                    Log.e(SERVICE_TAG, "no deferred scan exists.");
                }

                if (shouldConnectToDevice && pendingDeviceToConnect != null) {
                    Log.e(SERVICE_TAG, "deferred device pairing is now being executed.");
                    connectToDevice(pendingDeviceToConnect);
                    pendingDeviceToConnect = null;
                    shouldConnectToDevice = false;
                } else {
                    Log.e(SERVICE_TAG, "no deferred device pairing exists.");
                }
            } else {
                Log.e(SERVICE_TAG, "the bluetooth gatt service is null.");
            }
        }
        //DONE
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(SERVICE_TAG, "GATT Service has been unbound unexpectedly.");
            gattService = null;
            isServiceBound = false;
            isServiceReady = false;
            //Reset Singleton values.
            GATTSingleton.getInstance().setBluetoothGatt(null);
            GATTSingleton.getInstance().setBluetoothAdapter(null);
            //reset this activity's values.
            bluetoothAdapter = null;
            bluetoothLeScanner = null;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isServiceBound) {
                    Log.w(SERVICE_TAG, "Attempting to rebind BLE service...");
                    Intent serviceIntent = new Intent(getApplicationContext(), GATTService.class);
                    bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                }
            }, 3000);
            runOnUiThread(() -> {
                statusTextView.setText(R.string.no_device);
                bleScanButton.setEnabled(false);
            });
        }
    };

    /************************************************
     **     DONE! SCANNING BLUETOOTH LE DEVICES     **
     ************************************************/

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();

            if (ActivityCompat.checkSelfPermission(SecondActivity.this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (device.getName() != null && !bleDeviceList.contains(device)) {
                bleDeviceList.add(device);
                bleDeviceListAdapter.add(device.getName());
                bleDeviceListAdapter.notifyDataSetChanged();
                Log.i(SCAN_TAG, "Device Added: " + device.getName() + ", Total Devices: " + bleDeviceList.size());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(SCAN_TAG, "Scan Failed with error code: " + errorCode);
            runOnUiThread(() -> {
                scanning = false;
                bleScanButton.setText(R.string.start_scanning);
                Toast.makeText(SecondActivity.this, "Scan failed. Please try again.", Toast.LENGTH_SHORT).show();
            });
        }
    };

    private void startScanDevice() {
        if (!scanning) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                    Toast.makeText(this, "Bluetooth is not available or enabled.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (bluetoothLeScanner == null) {
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    if (bluetoothLeScanner == null) {
                        Toast.makeText(this, "Failed to initialize Bluetooth scanner.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                // Clear previous results
                bleDeviceList.clear();
                bleDeviceListAdapter.clear();
                bleDeviceListAdapter.notifyDataSetChanged();

                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
                bleScanButton.setText("Stop Scanning");
                Log.i(SCAN_TAG, "Started BLE Scan");

                handler.postDelayed(this::stopScanDevice, SCAN_PERIOD);

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION},
                        BLE_PERMISSION_REQUEST_CODE);
            }
        } else {
            stopScanDevice();
        }
    }

    private void stopScanDevice() {
        if (scanning) {
            scanning = false;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                if (bluetoothLeScanner != null) {
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }
            runOnUiThread(() -> bleScanButton.setText(R.string.start_scanning));
            Log.i(SCAN_TAG, "Stopped BLE Scan");
        }
    }

    /****************************************************
     **        DONE! DEVICE CONNECTION FUNCTIONS       **
     ****************************************************/
    //DONE
    //Auto-Retry for Connection Failures
    //Instead of failing immediately, you could allow a second attempt after a short delay
    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            handlePermissionsNotGranted();
            return;
        } else {
            Log.w("BLE", "ACT2 connectToDevice: passed permissions");
        }
        if (device == null) {
            Log.w("BLE", "ACT2 connectToDevice: Cannot connect -- device null.");
            return;
        }

        deviceAddress = device.getAddress();

        // Call Service Method Before Checking Singleton
        boolean serviceConnected = gattService.connect(deviceAddress);
        if (!serviceConnected) {
            Log.w("BLE", "ACT2 connectToDevice: Service failed to initiate connection.");
            return;
        } else {
            Log.w("BLE", "ACT2 connectToDevice: Service successful in initiating connection.");
        }

        // Now Check if BluetoothGatt was Assigned
        if (GATTSingleton.getInstance().getBluetoothGatt() == null) {
            Log.w("BLE", "ACT2 connectToDevice: Singleton's BluetoothGatt STILL null after connect call.");
            return;
        } else {
            Log.w("BLE", "ACT2 connectToDevice: Singleton's BluetoothGatt initialized properly after connect call.");
        }

        boolean connectionRequested = GATTSingleton.getInstance().getBluetoothGatt().connect();
        if (connectionRequested) {
            Log.i("BLE", "Connection requested for device: " + getSafeDeviceName(device));
            String name_connected_device = gattService.getConnectedDeviceName();
            Toast.makeText(this, "Connecting to " + name_connected_device + "...", Toast.LENGTH_SHORT).show(); //Caylan -- this method reaches until here.
            updateConnectionUI();
            bleScanButton.setEnabled(false);
            connectionAttempts = 0;
        } else {
            connectionAttempts++;
            if (connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
                Log.w("BLE", "Connection failed. Retrying in 3 seconds...");
                new Handler(Looper.getMainLooper()).postDelayed(() -> connectToDevice(device), 3000);
            } else {
                Log.e("BLE", "Max connection attempts reached. Failing.");
                Toast.makeText(this, "Failed to connect after multiple attempts.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //DONE
    private void disconnectDevice() {
        Log.i("BLE", "ACT2: disconnectDevice -- entered this method");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            handlePermissionsNotGranted();
            return;
        } else {
            Log.i("BLE", "ACT2: disconnectDevice -- passed permissions");
        }

        if (GATTSingleton.getInstance().getBluetoothGatt() != null) {
            Log.i("BLE", "ACT2: disconnectDevice -- Disconnection requested valid since singleton's global BluetoothGatt NOT null");
            gattService.disconnect(true);  //CAYLAN -- don't use the singleton's disconnect() instead use the service's disconnect() for cleaner unpairing.
            Toast.makeText(this, "Disconnecting...", Toast.LENGTH_SHORT).show();
            updateConnectionUI();
        } else {
            Log.i("BLE", "ACT2: disconnectDevice -- Disconnection requested NOT valid since singleton's global BluetoothGatt IS null");
            Log.w("BLE", "ACT2: disconnectDevice -- Cannot disconnect: Singleton reference is null.");
            Toast.makeText(this, "ACT2: Service not available for disconnection", Toast.LENGTH_SHORT).show();
        }
    }


    /************************************************
     **           DONE! BROADCAST RECEIVERS        **
     ************************************************/

    //DONE
    //helper function for the broadcastReceivers.
    private void updateConnectionUI() {
        Log.w("BLE", "ACT2: entered updateConnectionUI");

        BluetoothGatt gatt = GATTSingleton.getInstance().getBluetoothGatt();
        boolean isConnected = gattService != null && gattService.getConnectionState() == BluetoothProfile.STATE_CONNECTED;
        String currentDeviceName = gattService.getConnectedDeviceName();

        if (gatt == null || !isConnected) { // ✅ Check both the singleton and service state
            statusTextView.setText("No device connected"); // ✅ Properly reset UI
            Log.i(TAG, "ACT2: updateConnectionUI -- UI updated when app reopens: No BLE device connected.");
        } else if ((currentDeviceName != null) && (GATTSingleton.getInstance().getBluetoothGatt() != null)) {
            Log.w("BLE", "ACT2: updateConnectionUI -- checking for paired device ---- pairing exists -- display connected device");
            Log.w("BLE", "ACT2: displaying paired device" + currentDeviceName);
            statusTextView.setText(currentDeviceName);
            bleScanButton.setEnabled(false);
            //unpairButton.setEnabled(true);
        } else if (GATTSingleton.getInstance().getBluetoothGatt() == null) { // ✅ Properly clear UI state
            Log.w("BLE", "ACT2: updateConnectionUI -- checking for paired device ---- non existing pairing -- no device displayed for the connected device");
            statusTextView.setText(R.string.no_device);
            bleScanButton.setEnabled(isServiceReady);
            //unpairButton.setEnabled(false);
        }
    }

    //DONE
    /*  This helper method creates an IntentFilter and registers it to listen for:
        ACTION_GATT_CONNECTED → When the BLE device connects.
        ACTION_GATT_DISCONNECTED → When the BLE device disconnects.
    */
    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GATTService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(GATTService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }

    //DONE
    /*  Handles Incoming BLE Text Data
        Listens for "TEXT_DATA_READY" broadcasts from the service.
        Extracts the received text data (intent.getStringExtra("incoming_text_data")).
        Logs the data and can process it further (e.g., display in UI).
    */
    private final BroadcastReceiver textReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("Data Receiver", "OnReceive Activated");
            if ("TEXT_DATA_READY".equals(intent.getAction())) {
                String text = intent.getStringExtra("incoming_text_data");
                Log.i("TextReceiver", "CHECK TEXT: " + text);
                // Handle text data as needed
            }
        }
    };

    //DONE
    /*  connectionReceiver – Monitors BLE Connection Changes
        Listens for "ACTION_GATT_CONNECTED" and "ACTION_GATT_DISCONNECTED".
        Updates the UI based on the connection state.
        Shows a Toast notification when a device connects/disconnects.
        Clears deviceAddress when disconnected to prevent stale connections.
    */
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d("ConnectionReceiver", "Received action: " + action);
            if (GATTService.ACTION_GATT_CONNECTED.equals(action)) {
                connected = true;
                runOnUiThread(() -> {
                    updateConnectionUI();
                    Toast.makeText(SecondActivity.this, "Device connected successfully", Toast.LENGTH_SHORT).show();
                });
            } else if (GATTService.ACTION_GATT_DISCONNECTED.equals(action)) {
                connected = false;
                deviceAddress = null; // Clear stored address
                runOnUiThread(() -> {
                    updateConnectionUI();
                    Toast.makeText(SecondActivity.this, "Device disconnected", Toast.LENGTH_SHORT).show();
                });
            }
        }
    };

    /**********************************************
     **      DONE! PERMISSION HANDLING       **
     **********************************************/

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanDevice();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for scanning.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkAndRequestPermissions() {
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
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, MULTIPLE_PERMISSIONS_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void handlePermissionsNotGranted() {
        Log.w("Permissions", "BLUETOOTH_CONNECT permission not granted");
        Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
    }

    /***********************************************
     **     additional HELPER FUNCTIONS          **
     ***********************************************/

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    isServiceReady = true;
                    bleScanButton.setEnabled(true);
                    Toast.makeText(this, "Bluetooth enabled.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Bluetooth must be enabled to use this feature.", Toast.LENGTH_LONG).show();
                }
            });

    private String getSafeDeviceName(BluetoothDevice device) {
        if (device == null) return "Unknown";
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            String name = device.getName();
            return (name != null && !name.isEmpty()) ? name : "Unknown Device";
        }
        return "Unknown Device";
    }
}