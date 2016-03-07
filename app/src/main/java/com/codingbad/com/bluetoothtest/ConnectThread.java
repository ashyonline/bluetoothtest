package com.codingbad.com.bluetoothtest;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Created by Ayelen Chavez on 07.03.16.
 */
public class ConnectThread extends Thread {
    private static final String TAG = ConnectThread.class.toString();
    private BluetoothSocket bTSocket;
    private BluetoothSocket fallbackSocket;

    public boolean connect(BluetoothDevice bTDevice, UUID mUUID) {

        BluetoothSocket temp = null;
        try {
            temp = bTDevice.createRfcommSocketToServiceRecord(mUUID);
        } catch (IOException e) {
            Log.d(TAG, "Could not create RFCOMM socket:" + e.toString());
            return false;
        }

        Class<?> clazz = temp.getRemoteDevice().getClass();
        Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};

        Method m = null;
        try {
            m = clazz.getMethod("createInsecureRfcommSocket", paramTypes);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        Object[] params = new Object[]{Integer.valueOf(1)};

        try {
            fallbackSocket = (BluetoothSocket) m.invoke(temp.getRemoteDevice(), params);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        try {
            fallbackSocket.connect();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                fallbackSocket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
                return false;
            }
        }

        return true;
    }

    public boolean cancel() {
        try {
            fallbackSocket.close();
        } catch (IOException e) {
            Log.d(TAG, "Could not close connection:" + e.toString());
            return false;
        }
        return true;
    }
}