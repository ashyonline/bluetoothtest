package com.codingbad.com.bluetoothtest.mvp;

import android.bluetooth.BluetoothDevice;

import java.util.List;

import no.nordicsemi.android.support.v18.scanner.ScanResult;

/**
 * Created by Ayelen Chavez on 17.03.16.
 */
public interface MainContract {
    interface Presenter {
        void attachView(View view);

        void detachView();

        void startBluetoothScan();

        void stopBluetoothScan();

        void connectToBleDevice(BluetoothDevice device, String name);

        void disconnectFromBleDevice();

        void sendMessageToConnectedBleDevice(String message);
    }

    interface View {
        void updateScanResults(List<ScanResult> results);

        void onServiceConnected(String deviceName);
    }
}
