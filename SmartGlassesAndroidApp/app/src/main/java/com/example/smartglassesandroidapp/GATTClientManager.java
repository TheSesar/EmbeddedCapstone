package com.example.smartglassesandroidapp;

import android.Manifest;
import android.app.Activity;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

// Define a Service class to manage GATT (Bluetooth) operations
public class GATTClientManager extends Service {

    private static final String CHANNEL_ID = "SMARTHANDLEBAR_BLE_service_channel";
    private static final UUID IMAGE_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID IMAGE_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID IMAGE_METADATA_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID MY_DESCRIPTOR_UUID = UUID.fromString("2901");

    private Context context;
    private BluetoothAdapter bluetoothAdapter;

    public GATTClientManager(Context context) {
        this.context = context.getApplicationContext(); // safer, avoid leaking Activity
    }

    /************************************
     **      BluetoothAdapter INIT     **
     ************************************/

    // GLOBAL VARIABLES
    public static final String TAG = "GATTClientManager";

    // Method to Initialize Bluetooth Adapter
    // Context = Information about the environment the app is running in (like Service, Activity, Application).
    // To initialize the adapter, call "initialize(this)" since GATTClientManager is a Service
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
            return false; // Ensure Bluetooth is enabled
        }
        return true;
    }


    /************************************
     **        GATT CONNECTION         **
     ************************************/

    private BluetoothGatt bluetoothGatt;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTED = 2;

    private ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream();
    private int expectedImageSize = 0;
    private int connectionState;

    // BROADCASTING
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }


    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                connectionState = STATE_CONNECTED;
                broadcastUpdate(ACTION_GATT_CONNECTED);
                if (ActivityCompat.checkSelfPermission(GATTClientManager.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e("Service Permission", "cannot discover services due to permission restrictions.");
                    return;
                }
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                connectionState = STATE_DISCONNECTED;
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered");
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

                // Step 1: Access the custom service
                BluetoothGattService service = gatt.getService(IMAGE_SERVICE_UUID);
                if (service != null) {
                    Log.i(TAG, "Image service found!");

                    // Step 2: Read the metadata characteristic (to get image size, etc.)
                    BluetoothGattCharacteristic metadataCharacteristic = service.getCharacteristic(IMAGE_METADATA_CHARACTERISTIC_UUID);
                    if (metadataCharacteristic != null) {
                        if (ActivityCompat.checkSelfPermission(GATTClientManager.this,
                                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
                            stopSelf();
                            return;
                        }

                        gatt.readCharacteristic(metadataCharacteristic);
                    }

                    // Step 3: Enable notifications for the actual image data
                    BluetoothGattCharacteristic imageCharacteristic = service.getCharacteristic(IMAGE_CHARACTERISTIC_UUID);
                    if (imageCharacteristic != null) {
                        Log.i(TAG, "Image characteristic found!");

                        if (ActivityCompat.checkSelfPermission(GATTClientManager.this,
                                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
                            stopSelf();
                            return;
                        }

                        gatt.setCharacteristicNotification(imageCharacteristic, true);
                        BluetoothGattDescriptor desc = imageCharacteristic.getDescriptor(MY_DESCRIPTOR_UUID);

                        if (desc != null) {
                            Log.i(TAG, "Descriptor found!");
                            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(desc);
                            Log.i(TAG, "Wrote descriptor to enable notifications.");
                        }
                    }
                } else {
                    Log.i(TAG, "Image service NOT found!");
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (IMAGE_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] chunk = characteristic.getValue();
                imageBuffer.write(chunk, 0, chunk.length);

                // Check if the entire image has been received
                if (imageBuffer.size() >= expectedImageSize) {
                    byte[] fullImage = imageBuffer.toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(fullImage, 0, fullImage.length);

                    Intent intent = new Intent("IMAGE_DATA_READY");
                    intent.putExtra("image_bitmap", fullImage);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

                    imageBuffer.reset(); // Clear buffer for next image
                }
            }
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic read: " + characteristic.getStringValue(0));
            } if (IMAGE_METADATA_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] metadata = characteristic.getValue();
                // Parse metadata to extract image size
                expectedImageSize = parseImageSize(metadata);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic written successfully");
            }
        }
    };

    // save the metadata of the incoming image
    private int parseImageSize(byte[] metadata) {
        // Assuming the first 4 bytes represent the image size
        return ByteBuffer.wrap(metadata, 0, 4).getInt();
    }


    public boolean connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing necessary permissions!");
            return false; // Return false if permissions are missing
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
                return false;
            }

            bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback);
            Log.d(TAG, "Connecting to GATT server...");
            return true;

        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Device not found with provided address.");
            return false;
        }
    }


    /************************************
     **    BINDING CLIENT & SERVICE    **
     ************************************/

    @Override
    // Called when a client (like an Activity) binds to this Service
    public IBinder onBind(Intent intent) {
        // Return a Binder that the client will use to interact with the Service
        return new LocalBinder();
    }

    // Inner class that acts as a custom Binder
    class LocalBinder extends Binder {
        public GATTClientManager getService() {
            // 'this' would refer to LocalBinder, so we use GATTClientManager.this to get the outer class
            return GATTClientManager.this;
        }
    }

    /************************************
     **   UNBINDING & CLOSING SERVICE  **
     ************************************/

    @Override
    public boolean onUnbind(Intent intent) {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Unbinding failed due to permission restrictions.");
                return false;
            }
            bluetoothGatt.disconnect();
            close();
        }
        return super.onUnbind(intent);
    }

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
        Log.i(TAG, "GATT Service successfully closed.");
    }


    /************************************
     **        MAIN APP FOR GATT       **
     ************************************/

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created, initializing Bluetooth adapter...");

        boolean initResult = initialize();
        if (!initResult) {
            Log.e(TAG, "Bluetooth initialization failed in onCreate(). Stopping service.");
            stopSelf();
        }
    }
}
