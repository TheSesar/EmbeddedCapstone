package com.smartg.app;
//LIBRARIES
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;

//Singleton class to ensures only one instance of the Bluetooth connection/communication channel exists across the entire app
public class GATTSingleton {

    /************************************
     **           GLOBALS              **
     ************************************/
    private static GATTSingleton instance;
    private BluetoothGatt bluetoothGatt;
    private BluetoothAdapter bluetoothAdapter;
    public static final String TAG = "GATTSingleton";
    private boolean serviceRunning = false;
    /***************************************
     **          constructor              **
     ***************************************/

    //DONE
    // Private constructor to enforce Singleton
    private GATTSingleton() {}

    /*******************************************************************
     **         public functions available for app wide use            **
     *******************************************************************/

    //DONE
    //Singleton instance method
    public static synchronized GATTSingleton getInstance() {
        if (instance == null) {
            Log.i(TAG, "new instance of singleton constructed");
            instance = new GATTSingleton();
        }
        return instance;
    }

    //DONE
    //defines if gatt service is active
    public void setServiceRunning(boolean running) {
        serviceRunning = running;
    }

    //DONE
    //returns boolean, checks if gatt service is active
    public boolean isServiceRunning() {
        return serviceRunning;
    }

    //DONE
    //defines the global reference bluetoothGatt
    public void setBluetoothGatt(BluetoothGatt gatt) {
        Log.i(TAG, "setting singleton BluetoothGatt");
        this.bluetoothGatt = gatt;
    }

    //DONE
    //defines the global reference bluetoothAdapter
    public void setBluetoothAdapter(BluetoothAdapter adapter) {
        Log.i(TAG, "setting singleton BluetoothAdapter");
        this.bluetoothAdapter = adapter;
    }

    //DONE
    //returns the global reference bluetoothAdapter
    public BluetoothAdapter getBluetoothAdapter() {
        Log.i(TAG, "retrieving singleton BluetoothAdapter");
        return bluetoothAdapter;
    }

    //DONE
    //returns the global reference bluetoothGatt
    public BluetoothGatt getBluetoothGatt() {
        Log.i(TAG, "retrieving singleton BluetoothGatt");
        return bluetoothGatt;
    }

    //DONE
    //Cleans up all global references back to null.
    //stops connection to communication channel and closes the channel.
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void disconnect() {
        Log.i(TAG, "Singleton: disconnect -- entered this method");
        if (bluetoothGatt != null) {
            Log.i(TAG, "Singleton: disconnect -- Disconnecting singleton BLE GATT...");
            if (bluetoothGatt.getDevice() != null) {
                Log.w(TAG, "Disconnecting from: " + bluetoothGatt.getDevice().getAddress());
            }


            Log.i(TAG, "Disconnecting singleton BLE GATT...");
            bluetoothGatt.disconnect();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                    Log.i(TAG, "GATT in singleton successfully closed.");
                } else {
                    Log.w(TAG, "Singleton: Skipping cleanup -- BluetoothGatt already null.");
                }
            }, 2000); // ✅ Adds slight delay for clean disconnect
        }
    }
}


