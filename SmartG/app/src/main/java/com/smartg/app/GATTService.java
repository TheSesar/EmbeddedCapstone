package com.smartg.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;


// Define a Service class to manage GATT (Bluetooth) operations
public class GATTService extends Service {

    /************************************
     **      GLOBALS                  **
     ************************************/
    //UUID
    private static final UUID MY_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID MY_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID METADATA_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID MY_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    //Instantiations
    private Context context;
    private BluetoothAdapter bluetoothAdapter; // adapter
    private BluetoothGatt bluetoothGatt;
    private final ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream(); // to be deleted

    //Strings
    private static final String CHANNEL_ID = "BLE_CONNECTION_CHANNEL";
    public static final String TAG = "GATTService";
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";

    //Integers
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTED = 2;
    private static final int NOTIFICATION_ID = 1001;
    private int expectedImageSize = 0;
    private int connectionState;

    // Track connection state for notification updates
    private String connectedDeviceName = null;
    private BluetoothDevice connectedDevice;

    /************************************
     **        MAIN APP FOR GATT       **
     ************************************/

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "gatt service class onCreate entered");
        context = getApplicationContext();
        Log.d(TAG, "Service created, initializing Bluetooth adapter...");

        // Create notification channel first
        createNotificationChannel();

        boolean initResult = initialize(); // initialize adapter for bluetooth
        if (!initResult) {
            Log.e(TAG, "Bluetooth initialization failed in onCreate(). Stopping service.");
            stopSelf();
            return;
        }

        // Start as foreground service immediately
        startForegroundService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        // Ensure we're running as foreground service
        if (!isRunningAsForegroundService()) {
            startForegroundService();
        }

        return START_STICKY; // Service will be restarted if killed
    }

    /************************************
     **    FOREGROUND SERVICE SETUP    **
     ************************************/

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Smart Glasses BLE Connection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Maintains connection to Smart Glasses device");
            channel.setShowBadge(false);
            channel.setSound(null, null); // Silent notifications

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundService() {
        Notification notification = createNotification("Initializing...", false);
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "Started as foreground service");
    }

    private Notification createNotification(String status, boolean isConnected) {
        // Create intent to open your main activity when notification is tapped
        Intent notificationIntent = new Intent(this, SecondActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = isConnected ?
                "Smart Glasses Connected" :
                "Smart Glasses Service";

        String contentText = isConnected && connectedDeviceName != null ?
                "Connected to " + connectedDeviceName :
                status;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your BLE icon
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Cannot be swiped away
                .setSilent(true) // No sound/vibration
                .setAutoCancel(false)
                .build();
    }

    private void updateNotification(String status, boolean isConnected) {
        Notification notification = createNotification(status, isConnected);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private boolean isRunningAsForegroundService() {
        // Simple check - in a real implementation you might want to track this more precisely
        return true; // Assume it's running if this method is called after startForeground
    }

    /*******************************************************************************************
     **                    Initialize BluetoothAdapter:  helper method                        **
     *******************************************************************************************/

    // helper method to Initialize Bluetooth Adapter:
    public boolean initialize() {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "Unable to initialize BluetoothManager.");
            return false;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled.");
            updateNotification("Bluetooth disabled", false);
            return false; // Ensure Bluetooth is enabled
        }

        updateNotification("Ready to connect", false);
        return true;
    }

    public boolean isBluetoothInitialized() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    // Public method to allow access to BluetoothAdapter
    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    /*******************************************************************************************
     **                  GATT CONNECTION: Service functionalities/actions/events              **
     *******************************************************************************************/

    public boolean connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            updateNotification("Connection failed", false);
            return false;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing necessary permissions!");
            updateNotification("Permissions required", false);
            return false;
        }

        // Reuse bluetoothGatt if already connected
        if (bluetoothGatt != null) {
            Log.d(TAG, "Trying to reuse existing BluetoothGatt connection.");
            return bluetoothGatt.connect();
        }

        try {
            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                Log.e(TAG, "Device not found with provided address.");
                updateNotification("Device not found", false);
                return false;
            }

            // Store device name for notification
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                connectedDeviceName = device.getName();
                connectedDevice = device;
            }

            bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback);
            updateNotification("Connecting...", false);
            Log.d(TAG, "Connecting to GATT server...");
            return true;

        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Device not found with provided address.");
            updateNotification("Connection failed", false);
            return false;
        }
    }

    // save the metadata of the incoming image
    private int parseImageSize(byte[] metadata) {
        // Assuming the first 4 bytes represent the image size
        return ByteBuffer.wrap(metadata, 0, 4).getInt();
    }

    /*******************************************************************************************
     **    BIND CLIENT & SERVICE: allow an activity/page to access connected peripheral       **
     *******************************************************************************************/

    private final IBinder binder = new LocalBinder();

    // helper method for onBind(): Inner class that acts as a custom Binder
    public class LocalBinder extends Binder {
        public GATTService getService() {
            return GATTService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /*******************************************************************************************
     **   UNBIND & CLOSE SERVICE: allow activity/page to stop accessing connected peripheral **
     *******************************************************************************************/

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Activity unbound from service");
        // Don't stop the foreground service when activity unbinds
        // This allows the BLE connection to persist
        return true;
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No permission to disconnect from GATT.");
                return;
            }
            bluetoothGatt.disconnect();
            updateNotification("Disconnecting...", false);
            Log.i(TAG, "Requested to disconnect from peripheral.");
            connectedDeviceName = null;
            connectedDevice = null;
        } else {
            Log.w(TAG, "No active BluetoothGatt to disconnect.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service being destroyed");
        close(); // Clean up BLE connection
        stopForeground(true); // Remove notification
    }

    // helper method for onDestroy()
    private void close() {
        if (bluetoothGatt == null) {
            Log.e(TAG, "No active BLE GATT Service...");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No permission to close GATT connection!");
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
        connectedDeviceName = null;
        connectedDevice = null;
        Log.i(TAG, "GATT Service successfully closed.");
    }

    /*******************************************************************************************
     **                                 callback functions                                    **
     *******************************************************************************************/

    // Reacts to BLE events (connect, discover, read, notify)
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED;
                updateNotification("Connected", true);
                broadcastUpdate(ACTION_GATT_CONNECTED);

                if (ActivityCompat.checkSelfPermission(GATTService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e("Service Permission", "cannot discover services due to permission restrictions.");
                    return;
                }
                gatt.discoverServices();
                bluetoothGatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED;
                updateNotification("Disconnected", false);
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
                Log.w(TAG, "Disconnected from GATT server");
            }
        }

        public boolean isConnected() {
            return connectionState == STATE_CONNECTED && bluetoothGatt != null;
        }

        public int getConnectionState() {
            return connectionState;
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered");
                updateNotification("Services discovered", true);
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

                BluetoothGattService service = gatt.getService(MY_SERVICE_UUID);
                if (service != null) {
                    Log.i(TAG, "Service found!");

                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(MY_CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        Log.i(TAG, "Characteristic found!");

                        if (ActivityCompat.checkSelfPermission(GATTService.this,
                                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
                            return;
                        }

                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor desc = characteristic.getDescriptor(MY_DESCRIPTOR_UUID);

                        if (desc != null) {
                            Log.i(TAG, "Descriptor found!");
                            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(desc);
                            updateNotification("Ready to receive data", true);
                            Log.i(TAG, "Wrote descriptor to enable notifications.");
                        }
                    }
                } else {
                    Log.i(TAG, "Service NOT found!");
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, "CHECK CharChanged");
            if (MY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                String receivedText = new String(value, StandardCharsets.UTF_8);
                Log.i(TAG, "Received BLE text: " + receivedText);

                Intent intent = new Intent("TEXT_DATA_READY");
                intent.putExtra("incoming_text_data", receivedText);
                sendBroadcast(intent);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (METADATA_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                    byte[] metadata = characteristic.getValue();
                    expectedImageSize = parseImageSize(metadata);
                    Log.i(TAG, "ImageSize: " + expectedImageSize);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic written successfully");
            }
        }
    };

    // helper method for callback functions
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /*******************************************************************************************
     **                              PUBLIC UTILITY METHODS                                  **
     *******************************************************************************************/

    public boolean isConnected() {
        return connectionState == STATE_CONNECTED && bluetoothGatt != null;
    }

    public int getConnectionState() {
        return connectionState;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }
}



//package com.smartg.app;
//
//import android.Manifest;
//import android.app.Service;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.bluetooth.BluetoothGatt;
//import android.bluetooth.BluetoothGattCallback;
//import android.bluetooth.BluetoothGattCharacteristic;
//import android.bluetooth.BluetoothGattDescriptor;
//import android.bluetooth.BluetoothGattService;
//import android.bluetooth.BluetoothManager;
//import android.bluetooth.BluetoothProfile;
//import android.content.Context;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.os.Binder;
//import android.os.IBinder;
//import android.util.Log;
//
//import androidx.annotation.Nullable;
//import androidx.core.app.ActivityCompat;
//
//import java.io.ByteArrayOutputStream;
//import java.nio.ByteBuffer;
//import java.nio.charset.StandardCharsets;
//import java.util.UUID;
//
//
//// Define a Service class to manage GATT (Bluetooth) operations
//public class GATTService extends Service {
//
//
//    /************************************
//     **      GLOBALS                  **
//     ************************************/
//    //UUID
//    private static final UUID MY_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
//    private static final UUID MY_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
//    private static final UUID METADATA_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
//    private static final UUID MY_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
//
//
//    //Instantiations
//    private Context context;
//    private BluetoothAdapter bluetoothAdapter; // adapter
//    private BluetoothGatt bluetoothGatt;
//    private final ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream(); // to be deleted
//
//    //Strings
//    private static final String CHANNEL_ID = "SMARTHANDLEBAR_BLE_service_channel";
//    public static final String TAG = "GATTService";
//    public final static String ACTION_GATT_CONNECTED =
//            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
//    public final static String ACTION_GATT_DISCONNECTED =
//            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
//    public final static String ACTION_GATT_SERVICES_DISCOVERED =
//            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
//
//    //Integers
//    private static final int STATE_DISCONNECTED = 0;
//    private static final int STATE_CONNECTED = 2;
//    private int expectedImageSize = 0;
//    private int connectionState;
//
//
//    /************************************
//     **        MAIN APP FOR GATT       **
//     ************************************/
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//
//        Log.i(TAG, "gatt service class onCreate entered");
//        context = getApplicationContext();
//        Log.d(TAG, "Service created, initializing Bluetooth adapter...");
//
//        boolean initResult = initialize(); // initialize adapter for bluetooth
//        if (!initResult) {
//            Log.e(TAG, "Bluetooth initialization failed in onCreate(). Stopping service.");
//            stopSelf();
//        }
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.d(TAG, "Service started");
//        return START_STICKY; // You choose the restart policy
//    }
//
//
//    /*******************************************************************************************
//     **                    Initialize BluetoothAdapter:  helper method                        **
//     *******************************************************************************************/
//
//    // helper method to Initialize Bluetooth Adapter:
//    // Context = Information about the environment the app is running in (like Service, Activity, Application).
//    // To initialize the adapter, call "initialize(this)" since GATTClientManager is a Service
//    public boolean initialize() {
//        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
//        if (bluetoothManager == null) {
//            Log.e(TAG, "Unable to initialize BluetoothManager.");
//            return false;
//        }
//
//        bluetoothAdapter = bluetoothManager.getAdapter();
//        if (bluetoothAdapter == null) {
//            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
//            return false;
//        }
//
//        if (!bluetoothAdapter.isEnabled()) {
//            Log.e(TAG, "Bluetooth is not enabled.");
//            return false; // Ensure Bluetooth is enabled
//        }
//        return true;
//    }
//
//    public boolean isBluetoothInitialized() {
//        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
//    }
//
//    // Public method to allow access to BluetoothAdapter
//    public BluetoothAdapter getBluetoothAdapter() {
//        return bluetoothAdapter;
//    }
//
//
//
//    /*******************************************************************************************
//     **                  GATT CONNECTION: Service functionalities/actions/events              **
//     *******************************************************************************************/
////    This method connect(String address) is
////    the core part of establishing a Bluetooth GATT connection between your Android app (the client) and a BLE peripheral device (the server)
////    1. Validates Bluetooth and permissions.
////    2. Gets the target BLE device using its MAC address.
////    3. Attempts to establish a GATT connection with that device.
//    public boolean connect(final String address) {
//        if (bluetoothAdapter == null || address == null) {
//            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address."); //adapter not created error
//            return false;
//        }
//
//        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
//                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
//                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            Log.w(TAG, "Missing necessary permissions!"); //permissions not granted error
//            return false; // Return false if permissions are missing
//        }
//
//        // Reuse bluetoothGatt if already connected
//        if (bluetoothGatt != null) {
//            Log.d(TAG, "Trying to reuse existing BluetoothGatt connection.");
//            return bluetoothGatt.connect();
//        }
//
//        try {
//            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address); //get address of peripheral
//            if (device == null) {
//                Log.e(TAG, "Device not found with provided address."); //peripheral not found error
//                return false;
//            }
//
//            bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback);
//            Log.d(TAG, "Connecting to GATT server..."); //successfully connected to peripheral
//            return true;
//
//        } catch (IllegalArgumentException exception) {
//            Log.w(TAG, "Device not found with provided address."); //unable to connect to peripheral error
//            return false;
//        }
//    }
//
//    // save the metadata of the incoming image
//    private int parseImageSize(byte[] metadata) {
//        // Assuming the first 4 bytes represent the image size
//        return ByteBuffer.wrap(metadata, 0, 4).getInt();
//    }
//
//
//    /*******************************************************************************************
//     **    BIND CLIENT & SERVICE: allow an activity/page to access connected peripheral   DONE    **
//     *******************************************************************************************/
//
//    private final IBinder binder = new LocalBinder();
//
//    // helper method for onBind(): Inner class that acts as a custom Binder
//    public class LocalBinder extends Binder {
//        public GATTService getService() {
//            return GATTService.this; // 'this' would refer to LocalBinder, so we use GATTClientManager.this to get the outer class
//        }
//    }
//
//    @Nullable
//    @Override
//    // Called when a client (like an Activity) binds to this Service
//    public IBinder onBind(Intent intent) {
//        return binder; // Return a Binder that the client will use to interact with the Service
//    }
//
//
//
//    /*******************************************************************************************
//     **   UNBIND & CLOSE SERVICE: allow activity/page to stop accessing connected peripheral **
//     *******************************************************************************************/
//
//    @Override
//    //notification callback that lets your service know "an activity just unbound from me
//    public boolean onUnbind(Intent intent) {
//        Log.d(TAG, "Activity unbound from service");
//        return true;
//    }
//
//    public void disconnect() {
//        if (bluetoothGatt != null) {
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                Log.e(TAG, "No permission to disconnect from GATT.");
//                return;
//            }
//            bluetoothGatt.disconnect();
//            Log.i(TAG, "Requested to disconnect from peripheral.");
//        } else {
//            Log.w(TAG, "No active BluetoothGatt to disconnect.");
//        }
//    }
//
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        close(); // Clean up BLE connection
//    }
//
//    // helper method for onDestroy()
//    private void close() {
//        if (bluetoothGatt == null) {
//            Log.e(TAG, "No active BLE GATT Service...");
//            return;
//        }
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//            Log.e(TAG, "No permission to close GATT connection!");
//            return;
//        }
//        bluetoothGatt.close();
//        bluetoothGatt = null;
//        Log.i(TAG, "GATT Service successfully closed.");
//    }
//
//    /*******************************************************************************************
//     **                                 callback functions                                    **
//     *******************************************************************************************/
//    /*
//     Connect to device → onConnectionStateChange()
//                           ↓
//              Discover services → onServicesDiscovered()
//                           ↓
//     Find your characteristic and enable notifications
//                           ↓
//     Device sends data     → onCharacteristicChanged()
//     You request data      → onCharacteristicRead()
//    */
//
//    // Reacts to BLE events (connect, discover, read, notify)
//    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
//        // Called when connection state changes (connected/disconnected)
//        @Override
//        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//            if (newState == BluetoothProfile.STATE_CONNECTED) {
//                // successfully connected to the GATT Server
//                connectionState = STATE_CONNECTED;
//                broadcastUpdate(ACTION_GATT_CONNECTED);
//                if (ActivityCompat.checkSelfPermission(GATTService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                    Log.e("Service Permission", "cannot discover services due to permission restrictions.");
//                    return;
//                }
//                gatt.discoverServices(); // Calls onServiceDiscovered
//                bluetoothGatt.discoverServices(); // Discover services after successful connection
//            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//                // disconnected from the GATT Server
//                connectionState = STATE_DISCONNECTED;
//                broadcastUpdate(ACTION_GATT_DISCONNECTED);
//                // Don't call close() here - let the service decide when to close
//                Log.w(TAG, "Disconnected from GATT server");
//            }
//        }
//
//        public boolean isConnected() {
//            return connectionState == STATE_CONNECTED && bluetoothGatt != null;
//        }
//
//        public int getConnectionState() {
//            return connectionState;
//        }
//
//        // Exploring what services and characteristics a BLE device offers.
//        @Override
//        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//            super.onServicesDiscovered(gatt, status);
//
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.i(TAG, "Services discovered");
//                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
//
//                // Step 1: Access the custom service
//                BluetoothGattService service = gatt.getService(MY_SERVICE_UUID);
//                if (service != null) {
//                    Log.i(TAG, "Service found!");
//
//
//                    // Step 2: Enable notifications for the actual image data
//                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(MY_CHARACTERISTIC_UUID);
//                    if (characteristic != null) {
//                        Log.i(TAG, "Characteristic found!");
//
//                        if (ActivityCompat.checkSelfPermission(GATTService.this,
//                                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
//                            stopSelf();
//                            return;
//                        }
//
//                        // Enable notifications via descriptor
//                        // this call enables or disables local notification tracking for a given characteristic.
//                        gatt.setCharacteristicNotification(characteristic, true); // Calls onCharacteristicChanged
//                        BluetoothGattDescriptor desc = characteristic.getDescriptor(MY_DESCRIPTOR_UUID);
//
//                        if (desc != null) {
//                            Log.i(TAG, "Descriptor found!");
//                            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//                            gatt.writeDescriptor(desc);
//                            Log.i(TAG, "Wrote descriptor to enable notifications.");
//                        }
//                    }
//                } else {
//                    Log.i(TAG, "Service NOT found!");
//                }
//            } else {
//                Log.w(TAG, "onServicesDiscovered received: " + status);
//            }
//        }
//
//        // When the remote device sends notifications or indications, handles received data
//        @Override
//        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
//            Log.i(TAG, "CHECK CharChanged");
//            if (MY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
//                byte[] value = characteristic.getValue();
//                String receivedText = new String(value, StandardCharsets.UTF_8);
//                Log.i(TAG, "Received BLE text: " + receivedText);
//
//                Intent intent = new Intent("TEXT_DATA_READY");
//                intent.putExtra("incoming_text_data", receivedText);
//                sendBroadcast(intent);  // same as sending action to intent!!
//            }
//        }
//
//        // Used to read characteristics manually (e.g., metadata like image size).
//        @Override
//        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                if (METADATA_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
//                    byte[] metadata = characteristic.getValue();
//                    expectedImageSize = parseImageSize(metadata);
//                    Log.i(TAG, "ImageSize: " + expectedImageSize);
//                }
//            }
//        }
//
//        @Override
//        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.i(TAG, "Characteristic written successfully");
//            }
//        }
//    };
//
//    // helper method for callback functions:
//    // BROADCASTING: Sends simple messages (like "connected", "disconnected", or "data received") from your Service to your Activity or other components.
//    private void broadcastUpdate(final String action) {
//        final Intent intent = new Intent(action);
//        sendBroadcast(intent);
//    }
//}
