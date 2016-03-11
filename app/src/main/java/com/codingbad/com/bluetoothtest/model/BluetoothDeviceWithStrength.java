package com.codingbad.com.bluetoothtest.model;

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

    /**
     * Compare two BluetoothDevicesWithStrength by name
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BluetoothDeviceWithStrength)) {
            return false;
        }

        BluetoothDeviceWithStrength temp = (BluetoothDeviceWithStrength) o;
        String name = temp.device.getName();

        if (name == null && device.getName() == null) {
            return true;
        }

        if (name == null || device.getName() == null) {
            return false;
        }

        if (name.equals(device.getName())) {
            return true;
        }

        return super.equals(o);
    }
}
