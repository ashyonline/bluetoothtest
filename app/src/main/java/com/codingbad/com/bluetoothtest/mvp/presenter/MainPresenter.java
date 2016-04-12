package com.codingbad.com.bluetoothtest.mvp.presenter;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.codingbad.com.bluetoothtest.BluetoothTestApplication;
import com.codingbad.com.bluetoothtest.Constants;
import com.codingbad.com.bluetoothtest.model.UARTService;
import com.codingbad.com.bluetoothtest.mvp.MainContract;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * Created by Ayelen Chavez on 16.03.16.
 * <p>
 * Main presenter used by main and THE one Activity.
 * For this simple case, I think having a separated model is not necessary
 */
public class MainPresenter implements MainContract.Presenter {
    private static final String TAG = MainPresenter.class.toString();
    private static final String DEFAULT_MESSAGE = "O";
    private static final long SCAN_DURATION = 5000;

    @Inject
    protected Context context;
    protected MainContract.View mainView;
    // save when the activity is binded to the service or not
    private boolean isConnected = false;
    private boolean isScanning = false;
    private android.os.ParcelUuid uuid;
    // handle used to stop scanning with some delay
    private Handler handler = new Handler();

    // save device we are currently connected to
    private String deviceName;

    // save binder to service once it is connected
    // this will be the binder of the Service's communication channel, to make calls on.
    private UARTService.UARTBinder UARTBinder;

    /**
     * Using nordicsemi scan callback to scan devices
     */
    private ScanCallback scanDevicesCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            // Add new scan results to bluetooth list
            mainView.updateScanResults(results);
        }
    };

    // This service connection object receives information as the service is started and stopped
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Log.d(TAG, "UARTService connected");
            isConnected = true;

            // save binder
            UARTBinder = (UARTService.UARTBinder) service;

            mainView.onServiceConnected(deviceName);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Log.d(TAG, "UARTService disconnected");
            isConnected = false;
        }
    };

    public MainPresenter() {
        BluetoothTestApplication.injectMembers(this);
    }

    @Override
    public void attachView(MainContract.View view) {
        this.mainView = view;
    }

    @Override
    public void detachView() {
        stopBluetoothScan();
        this.mainView = null;
    }

    /**
     * Use nordicsemi library to scan bluetooth devices regarding android SDK version used
     */
    @Override
    public void startBluetoothScan() {

        // *** code borrowed from nordicsemi example to scan
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(1000).setUseHardwareBatchingIfSupported(false).build();
        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(uuid).build());
        scanner.startScan(filters, settings, scanDevicesCallback);
        // *** end of borrowed

        isScanning = true;

        // stop scanning after SCAN_DURATION milliseconds
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    stopBluetoothScan();
                }
            }
        }, SCAN_DURATION);
    }


    /**
     * Stop scan if user tap Cancel button
     */
    @Override
    public void stopBluetoothScan() {
        if (isScanning) {
            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(scanDevicesCallback);

            isScanning = false;
        }
    }

    /**
     * Connect to selected device
     *
     * @param device
     * @param name
     */
    @Override
    public void connectToBleDevice(BluetoothDevice device, String name) {
        if (deviceName != null && deviceName.equals(name)) {
            // we are already connected to this device, disconnect
        } else {
            deviceName = name;
        }

        // The device may not be in the range but the service will try to connect to it if it reach it
        // TODO: check if the device is near to connect
        final Intent service = new Intent(context, UARTService.class);
        service.putExtra(Constants.EXTRA_DEVICE_ADDRESS, device.getAddress());
        context.startService(service);

        Log.d(TAG, "Binding to the service...");
        // bind to UARTService
        context.bindService(service, serviceConnection, 0);
    }

    @Override
    public void sendMessageToConnectedBleDevice(String message) {
        if (UARTBinder != null) {
            if (message != null && !message.isEmpty()) {
                UARTBinder.send(message);
            } else {
                UARTBinder.send(DEFAULT_MESSAGE);
            }
        }
    }

    @Override
    public void disconnectFromBleDevice() {
        if (UARTBinder != null) {
            UARTBinder.disconnect();
        }
    }
}
