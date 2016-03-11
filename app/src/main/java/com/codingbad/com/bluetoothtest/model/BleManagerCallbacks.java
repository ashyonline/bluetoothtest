package com.codingbad.com.bluetoothtest.model;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;

/**
 * Created by Ayelen Chavez on 10.03.16.
 */
public interface BleManagerCallbacks {
    public void onDataSent(final String data);

    /**
     * Called when the device has been connected. This does not mean that the application may start communication. A service discovery will be handled automatically after this call.
     */
    public void onDeviceConnected();

    /**
     * Called when user initialized disconnection.
     */
    public void onDeviceDisconnecting();

    /**
     * Called when the device has disconnected (when the callback returned {@link BluetoothGattCallback#onConnectionStateChange(BluetoothGatt, int, int)} with state DISCONNECTED.
     */
    public void onDeviceDisconnected();

    /**
     * This callback is invoked when the Ble Manager lost connection to a device that has been connected with autoConnect option. Otherwise a {@link #onDeviceDisconnected()}
     * method will be called on such event.
     */
    public void onLinklossOccur();

}
