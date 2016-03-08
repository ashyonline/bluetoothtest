package com.codingbad.com.bluetoothtest;

/**
 * Created by Ayelen Chavez on 08.03.16.
 */
public interface UARTManagerCallbacks extends BleManagerCallbacks {

    public void onDataReceived(final String data);

    public void onDataSent(final String data);
}