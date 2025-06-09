package com.smartg.app;
//Libraries
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.smartg.app.GATTSingleton;

// GATTService is a Service class to manage GATT (Bluetooth) operations
// The service is started in only one activity (the second activity), and all activities are to bind/unbind to this service to access the communication channel.
// Gatt Client is the Android Phone
// Gatt Server is the target BLE device
// Saves global references to a singleton class (GATTSingleton)
public class GATTService extends Service {

    /************************************
     **      GLOBALS                  **
     ************************************/
    //UUID to communicate between phone app and target microcontroller (xiao sense ESP32-S3)
    private static final UUID MY_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID MY_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID METADATA_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID MY_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    //Instantiations
    private Context context; //store a reference to the application's context,
    private BluetoothAdapter bluetoothAdapter; // store a reference to the device's Bluetooth adapter
    private BluetoothGatt bluetoothGatt; // store a reference to the communication channel with a BLE device (aka gatt server)
    private final ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream(); // to be deleted
    // Track connection state for notification updates
    private String connectedDeviceName = null; //stores a reference to the BLE device's name
    private String audioText = null; //stores a reference to the BLE device's audio-text
    private BluetoothDevice connectedDevice; // stores a reference to the BLE device
    private final IBinder binder = new LocalBinder();
    private TextUpdateListener listener;

    //Strings
    private static final String CHANNEL_ID = "BLE_CONNECTION_CHANNEL";
    public static final String TAG = "GATTService";
    public final static String ACTION_GATT_CONNECTED = "com.smartg.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED ="com.smartg.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED ="com.smartg.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public static final String ACTION_GATT_NOTIFICATION_UNSUPPORTED = "com.smartg.ACTION_GATT_NOTIFICATION_UNSUPPORTED";
    public static final String ACTION_GATT_CLOSED = "com.smartg.ACTION_GATT_CLOSED";

    //Integers
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTED = 2;
    private static final int NOTIFICATION_ID = 1001;
    private int expectedImageSize = 0;
    private int connectionState;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;



    /****************************************
     **        DONE! Lifecycle for Service       **
     ***************************************/

    //DONE
    // Step 1. when the activity class starts the service,
    // the lifecycle of the service shall begin starting from this onCreate() Method.
    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service: oncreate -- app just opened -- GATTService restarted, resetting connection state...");
        if (GATTSingleton.getInstance().getBluetoothGatt() == null) {
            connectionState = STATE_DISCONNECTED; // ✅ Ensure service reflects actual state
            Log.i(TAG, "Service: onCreate -- app just opened -- connectionState set to STATE_DISCONNECTED");
        }

        Log.i(TAG, "gatt service class onCreate entered");
        context = getApplicationContext();
        Log.d(TAG, "Service created, initializing Bluetooth adapter...");
        // Create notification channel first
        createNotificationChannel();
        // initialize adapter for bluetooth
        boolean initResult = initialize();
        if (!initResult) {
            Log.e(TAG, "Bluetooth initialization failed in onCreate(). Stopping service.");
            stopSelf();
            return;
        } else {
            Log.e(TAG, "Bluetooth initialization success in onCreate(). starting service.");
        }
        GATTSingleton.getInstance().setServiceRunning(true);
        //immediately prompt a foreground service
        //and provide a notification that the foreground service is now active, yet currently no BLE device is paired
        startForeground(NOTIFICATION_ID, createNotification("Service Active", false));
    }

    //DONE
    // Step 2. in the service's lifecycle is onStartCommand() Method.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        // Ensure we're running as foreground service
        if (!isRunningAsForegroundService()) {
            startForegroundService();
        }
        return START_STICKY; // set Service to be restarted if killed.
    }

    //DONE
    // Step 3.
    //stops foreground.
    //sets Singleton app wide references to null.
    //fully closes/cleans up all BLE resources.
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service: onDestroy -- Service being destroyed");
        close(); // Ensure BLE cleanup
        // Additional cleanup for service shutdown
        stopForeground(STOP_FOREGROUND_REMOVE); // API 33+ compatible: remove notifications.
        //GATTSingleton.getInstance().disconnect(); // Ensure singleton is cleaned up
        GATTSingleton.getInstance().setServiceRunning(false);
        connectionState = STATE_DISCONNECTED; // ✅ Ensure state resets after service stops
        Log.i(TAG, "Service: onDestroy -- GATTService is shutting down, resetting connection state...");
        Log.d(TAG, "Service: onDestroy -- GATT Service destroyed successfully.");
    }

    //DONE
    // helper method for onDestroy()
    //sets global references to null.
    //broadcasts closure of gatt service.
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
        audioText = null;
        // Notify the app that BLE resources were released
        broadcastUpdate(ACTION_GATT_CLOSED);
        Log.i(TAG, "GATT Service successfully closed.");
    }

    /********************************************
     **     DONE! FOREGROUND SERVICE SETUP    **
     ********************************************/

    //DONE
    //starts the service as a foreground service
    //and notifies the start of the service, yet no paired BLE device just yet.
    private void startForegroundService() {
        Notification notification = createNotification("Initializing...", false);
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "Started as foreground service");
    }

    //DONE
    //checks if the service is running as a foreground service.
    private boolean isRunningAsForegroundService() {
        // Simple check - in a real implementation you might want to track this more precisely
        return true; // Assume it's running if this method is called after startForeground
    }

    /*******************************************************
     **     DONE! Initialize Notification Channel         **
     *******************************************************/

    //DONE
    //creates a new notification manager.
    //creates a new notification channel.
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

    //DONE
    //sends notifications to the activity/page currently opened when in the app.
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

    //DONE
    //sets the notification's content/message
    private void updateNotification(String status, boolean isConnected) {
        Notification notification = createNotification(status, isConnected);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /*******************************************************************************************
     **                     DONE! Initialize BluetoothAdapter:  helper method                        **
     *******************************************************************************************/

    //DONE
    // helper method to Initialize Bluetooth Adapter:
    // creates instance of bluetooth manager.
    // creates instance of bluetooth adapter.
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

        // ✅ Set adapter in Singleton here instead of connect()
        GATTSingleton.getInstance().setBluetoothAdapter(bluetoothAdapter);

        //Yes! The function call bluetoothAdapter.isEnabled()
        //is provided by Android's predefined BluetoothAdapter class, which is part of the Android Bluetooth API.
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled.");
            updateNotification("Bluetooth disabled", false);
            return false; // Ensure Bluetooth is enabled
        }
        updateNotification("Ready to connect", false);
        return true;
    }

    /********************************************************************************************************************
     **                  DONE! GATT CONNECTION: Service's communication channel and pairing of target device            **
     ********************************************************************************************************************/

    //DONE
    //saves a global reference to the selected target BLE device.
    //creates the communication channel(chatroom) if channel not already created. Joins both the phone(gatt client) and the target peripheral(gatt server) to the channel.
    public boolean connect(final String address) {
        Log.w(TAG, "Service: connect -- entered this method");
        //check for bluetooth adapter for Android bluetooth
        if ( !bluetoothAdapter.isEnabled() || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            updateNotification("Connection failed", false);
            return false;
        } else {
            Log.w(TAG, "Service: BluetoothAdapter initialized and specified address.");
        }
        //check for phone permissions for bluetooth
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing necessary permissions!");
            updateNotification("Permissions required", false);
            return false;
        } else {
            Log.w(TAG, "Service: connect -- permissions allowed");
        }
        // A. Reuse bluetoothGatt (aka communication channel/chatroom) if already connected
        // occurs when communication channel to communicate another device is already established.
        if (bluetoothGatt != null) {
                Log.d(TAG, "Existing GATT session detected.");
//                bluetoothGatt.disconnect();
//                // Ensure no active connection. This is not the same as deleting the channel/chatroom itself.
//                // bluetoothGatt.disconnect() tells the GATT server (BLE device) that the phone wants to terminate the connection.
//                // The communication channel (BluetoothGatt instance) still exists, but it’s now empty—not linked to any BLE device.
//                return bluetoothGatt.connect();
                // bluetoothGatt.connect() is a method provided by the Android Bluetooth API,
                // reconnect to the same channel, though phone may be pairing with a different BLE device from before.
                // bluetoothGatt.connect() attempts to re-establish communication with the previously connected BLE device.
                // It does not create a new BluetoothGatt instance—it simply reconnects the existing one.
                // If the BLE device is still available, the phone rejoins the chatroom.
                // If a new BLE device is being paired, the existing communication channel is reused for the new device.
            if (connectionState == STATE_DISCONNECTED) { // ✅ Only reconnect if device was disconnected properly
                Log.d(TAG, "Reconnecting to previous BLE device...");
                return bluetoothGatt.connect();
            } else {
                Log.w(TAG, "Previous connection is still active; switching devices.");
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        } else {
            Log.w(TAG, "Service: connect -- bluetoothGatt IS Null, set up new communication channel");
        }
        // B. Otherwise if no communication channel set up yet to actually begin communicating with another device, start the channel.
        try {
            //1. stores reference to the target BLE device (more exactly it's MAC address)
            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            if (device == null) { //the provided address was invalid
                Log.e(TAG, "Device not found with provided address.");
                updateNotification("Device not found", false);
                return false;
            } else {
                Log.w(TAG, "Service: connect -- device to pair to IS found");
            }
            //2. Otherwise, when address is valid, Store device name for notification, Save a global reference to the target BLE device in this class.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Service: connect -- passed permissions -- setting global device references");
                connectedDeviceName = device.getName(); //save device name
                connectedDevice = device;
            }
            //3.creating a communication channel whereby both the selected BLE device and the phone is connected to the channel.
            // However, if the BLE device is later disconnected, the channel would still exist but just without a BLE device on the other end.
            // auto-connection is disabled since we may want to be connecting to a different target BLE device after an unpairing event.
            bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback);
            if (bluetoothGatt != null) {
                GATTSingleton.getInstance().setBluetoothGatt(bluetoothGatt);
                Log.i("BLE", "Service: connect -- BluetoothGatt initialized and stored in Singleton.");
                return true;
            } else {
                Log.e("BLE", "Failed to initialize BluetoothGatt and did not store in Singleton.");
                return false;
            }
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Invalid device address provided.");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected Bluetooth connection error: " + e.getMessage());
        }
        updateNotification("Connection failed", false);
        return false;
    }

    //DONE
    //Clears stored references of the connection in the singleton
    //Fully closes communication channel only when switching devices or recovering from failure
    //Added parameter to control full release.
    public void disconnect(boolean shouldCloseGatt) {
        Log.e(TAG, "Service: disconnect -- entered this method");
        //verify there is a communication channel/chatroom existing.
        if (bluetoothGatt != null) {

            Log.e(TAG, "Service: disconnect -- valid bluetoothGatt");
            //verify phone permissions.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No permission to disconnect from GATT.");
                return;
            } else {
                Log.e(TAG, "Service: disconnect -- passed permissions");
            }
            //message about ongoing disconnection.
            Log.i(TAG, "Disconnecting from peripheral...");
            //unpairs the BLE device from the communication channel/chatroom.
            bluetoothGatt.disconnect();
            //notes the type of unpairing as intentional or unexpected.
            if (connectionState == STATE_CONNECTED) {
                Log.w(TAG, "Intentional disconnect.");
            } else {
                Log.e(TAG, "Unexpected disconnect detected.");
            }
            //notify about the disconnection from channel.
            updateNotification("Disconnecting...", false);
            Log.i(TAG, "Requested to disconnect from peripheral.");
            // ✅ Close only when switching devices or recovering from failure
            if (shouldCloseGatt) {
                bluetoothGatt.close();
                bluetoothGatt = null;
                GATTSingleton.getInstance().setBluetoothGatt(null);
            }
            //Clear global device references to prevent stale references in this class and in the singleton.
            Log.e(TAG, "Service: disconnect -- clearing global device references");
            connectedDeviceName = null;
            connectedDevice = null;
            GATTSingleton.getInstance().disconnect();
        }
        //Otherwise, note no channel/chatroom exists in the first place.
        else {Log.w(TAG, "No active BluetoothGatt to disconnect.");}
    }

    /**************************************************************************************************
     **    DONE! BIND CLIENT & SERVICE: allow an activity/page to access connected peripheral       **
     **************************************************************************************************/
    //DONE
    // helper method for onBind(): Inner class that acts as a custom Binder
    public class LocalBinder extends Binder {
        public GATTService getService() {
            return GATTService.this;
        }
    }

    //DONE
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    //DONE
    //allows activity/page to unbind from the service
    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Activity unbound from service");
        // ✅ Keep service alive if pairing hasn't happened yet or a device might reconnect
        if (bluetoothGatt == null || connectionState == STATE_DISCONNECTED) {
            Log.d(TAG, "Service is running, but no active BLE connection. Keeping it alive for potential reconnections.");
            return true;
        }
        Log.d(TAG, "Service unbound, but an active BLE session exists.");
        return true; // Service continues running
    }


    /*************************************************************************************************
     **                                 DONE! callback functions                                    **
     ************************************************************************************************/

    //DONE
    // helper method for callback functions
    // Q: adds new meesage to be broadcasted??
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    //DONE
    //helper method for onConnectionStateChange()
    //In cases where reconnection fails repeatedly,  limit retry attempts to avoid unnecessary battery drain.
    //Implement a retry counter:
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void handleConnectionFailure() {
        if (bluetoothGatt != null) {
            Log.w(TAG, "Attempting to recover BLE connection...");
            bluetoothGatt.disconnect();
            bluetoothGatt.close(); // Fully reset session
            bluetoothGatt = null;

            if (connectedDevice != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                Log.i(TAG, "Scheduling BLE reconnection in 5 seconds... Attempt " + (reconnectAttempts + 1));
                reconnectAttempts++;

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.i(TAG, "Trying to reconnect to previous BLE device...");
                    bluetoothGatt = connectedDevice.connectGatt(getApplicationContext(), false, bluetoothGattCallback);
                }, 5000);
            } else {
                Log.e(TAG, "Max reconnection attempts reached or no device found. Cannot auto-reconnect.");
            }
        }
    }

    //DONE
    // Reacts to BLE events (connect, discover, read, notify)
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        //DONE
        public boolean isConnected() {return connectionState == STATE_CONNECTED && bluetoothGatt != null;}

        //DONE
        public int getConnectionState() {return connectionState;}

        //DONE
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.e(TAG, "Service: onConnectionStateChange -- entered this method");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, "Service: onConnectionStateChange -- bluetooth profile new state is STATE_CONNECTED");
                connectionState = STATE_CONNECTED;
                updateNotification("Connected", true);
                broadcastUpdate(ACTION_GATT_CONNECTED);
                if (ActivityCompat.checkSelfPermission(GATTService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e("Service Permission", "cannot discover services due to permission restrictions.");
                    return;
                } else {
                    Log.e(TAG, "Service: onConnectionStateChange -- passed permissions");
                }

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (gatt.getService(MY_SERVICE_UUID) == null) { // check explicitly whether the expected service is available.
                        gatt.discoverServices();
                    }
                }, 500);
                if (newState == BluetoothProfile.STATE_DISCONNECTED) { //determine if disconnection intentional or unexpected, and handle the failure case.
                    Log.e(TAG, "Service: onConnectionStateChange -- bluetooth profile new state is STATE_DISCONNECTED");
                    connectionState = STATE_DISCONNECTED;
                    updateNotification("Disconnected", false);
                    broadcastUpdate(ACTION_GATT_DISCONNECTED);
                    Log.w(TAG, "Disconnected from GATT server");
                    if (status != BluetoothGatt.GATT_SUCCESS) { // Error occurred
                        Log.e(TAG, "Unexpected disconnection! Status: " + status);
                        handleConnectionFailure(); // ✅ Implement failure recovery: Automatically triggers reconnection only for unexpected failures.
                    }
                }
            } else {
                Log.e(TAG, "Service: onConnectionStateChange -- bluetooth profile new state is STATE_DISCONNECTED");
            }
        }

        //DONE
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
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


                        //very notifications supported. Also broadcast the fact if not supported.
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                            Log.e(TAG, "Characteristic does not support notifications.");
                            updateNotification("Notifications unsupported", false); // Inform user
                            broadcastUpdate(ACTION_GATT_NOTIFICATION_UNSUPPORTED);
                            return;
                        }
                        //verify permissions allowed.
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
//                            //Enable notifications without setting descriptor manually
//                            bluetoothGatt.setCharacteristicNotification(characteristic, true);
//                            //Increase MTU for better data handling (if needed)
//                            bluetoothGatt.requestMtu(512);
//                            updateNotification("Ready to receive data", true);
//                            Log.i(TAG, "Enabled BLE notifications using recommended approach.");
                        } else {
                            Log.e(TAG, "Descriptor not found, cannot enable notifications.");
                        }
                    }
                } else {Log.i(TAG, "Service NOT found!");}
            } else {Log.w(TAG, "onServicesDiscovered received: " + status);}
        }

        //DONE
        //when value/data stored in characteristic/package is modified.
        //calls gatt.readCharacteristic() to get the entire message
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, "Service: onCharacterChange -- entered this method");
            if (MY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                Log.i(TAG, "Service: onCharacterChange -- Received BLE text character byte VALUE: " + value.length);
                String receivedText = new String(value, StandardCharsets.UTF_8);
                Log.i(TAG, "Service: onCharacterChange -- Received BLE text: " + receivedText);

                //audioText = receivedText; // Store the text to the global input audio text reference
                Log.i(TAG, "Service: onCharacterChange -- saved global text reference: " + audioText);
                Intent intent = new Intent("TEXT_DATA_READY");
                intent.putExtra("incoming_text_data", receivedText);
                sendBroadcast(intent);

                Log.i(TAG, "Reading characteristic...");
                gatt.readCharacteristic(characteristic); // 🔥 Read data immediately after discovery


            } else {
                Log.i(TAG, "Service: onCharacterChange -- fails MY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())");
                Log.i(TAG, "Service: onCharacterChange -- MY_CHARACTERISTIC_UUID:" + MY_CHARACTERISTIC_UUID);
                Log.i(TAG, "Service: onCharacterChange -- characteristic.getUuid():" + characteristic.getUuid());
            }
        }

        //DONE
        //when read event is completed.
        // saves read characteristic value to global String audioText
        // uses the global listener to set fourth activity's UI element (nestedTextView_In_act4_1) to audioText
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                //irrelevant since image no longer being received from BLE device
                if (METADATA_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                    byte[] metadata = characteristic.getValue();
                    expectedImageSize = parseImageSize(metadata);
                    Log.i(TAG, "ImageSize: " + expectedImageSize);
                }

                //saves the audio transcript and updates UI in fourth activity
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] value = characteristic.getValue();
                    String receivedText = new String(value, StandardCharsets.UTF_8);
                    audioText = receivedText;
                    if (listener != null) {
                        listener.onTextReceived(audioText);
                    }

                    Log.i(TAG, "Service: onCharacterRead -- Read Full Data: " + receivedText);
                } else {
                    Log.e(TAG, "Service: onCharacterRead -- Failed to read characteristic, status: " + status);
                }
            }
        }

        //when write event is completed.
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic written successfully");
            }
        }
    };

    /*************************************************************************************************
     **                              DONE! PUBLIC UTILITY METHODS                                  **
     ************************************************************************************************/


    public boolean isConnected() {
        if (connectionState == STATE_CONNECTED) {
            Log.w(TAG, "Service: isConnected-- connection state is STATE_CONNECTED");
        } else {
            Log.w(TAG, "Service: isConnected-- connection state IS not STATE_CONNECTED");
        }

        if (bluetoothGatt != null) {
            Log.w(TAG, "Service: isConnected-- bluetoothGatt IS not null");
        } else {
            Log.w(TAG, "Service: isConnected-- bluetoothGatt IS null");
        }
        return connectionState == STATE_CONNECTED && bluetoothGatt != null;
    }


    public int getConnectionState() {
        return connectionState;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public String getConnectedDeviceAddress() {
        return connectedDevice.getAddress();
    }

    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    //newly DONE: check if audio text is available
    public boolean availableAudioText() {
        return (audioText != null);
    }

    //newly DONE: returns audio's text transcript
    public String getAudioText(){
        return audioText;
    }

    //DONE
    //public method to check if bluetooth adapter is created and also enabled from outside this class.
    public boolean isBluetoothInitialized() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    //DONE
    // Public method to allow access to BluetoothAdapter from outside this class
    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    //DONE
    // public method for setting the global listener of service to the desired activity (which implements the interface: TextUpdateListener)
    public void setTextUpdateListener(TextUpdateListener listener) {
        this.listener = listener;
    }

    /*****************************************************************************************************************
     **                              Read and Write Events with target BLE device                                   **
     *****************************************************************************************************************/

    //DONE
    //unused method since not dealing with image data anymore!
    //saves the metadata of the incoming image
    private int parseImageSize(byte[] metadata) {
        // Assuming the first 4 bytes represent the image size
        return ByteBuffer.wrap(metadata, 0, 4).getInt();
    }
}
