package com.codingbad.com.bluetoothtest;

import android.bluetooth.BluetoothDevice;

/**
 * Created by Ayelen Chavez on 07.03.16.
 */
public class BluetoothDeviceWithStrength {
    private final BluetoothDevice device;
    private final int strength;

    public BluetoothDeviceWithStrength(BluetoothDevice device, int strength) {
        this.device = device;
        this.strength = strength;
    }

    public String getName() {
        return device.getName();
    }

    public String getAddress() {
        return device.getAddress();
    }

    public int getStrength() {
        return strength;
    }

    public BluetoothDevice getDevice() {

        return device;
    }
}
