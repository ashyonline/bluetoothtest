package com.codingbad.com.bluetoothtest;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class MainActivity extends Activity implements BluetoothDevicesAdapter.RecyclerViewListener, CompoundButton.OnCheckedChangeListener {

    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    private static final String TAG = MainActivity.class.toString();
    private static final long SCAN_DURATION = 5000;
    @Bind(R.id.scan_button)
    protected Switch scanButton;

    @Bind(R.id.bluetooth_list)
    protected RecyclerView scannedBluetooth;
    @Bind(R.id.connected_layout)
    protected LinearLayout connectButton;
    private BluetoothDevicesAdapter bluetoothDevicesAdapter;
    private List<BluetoothDeviceWithStrength> foundBluetooth;
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            Log.d(TAG, "one device found");
            BluetoothDeviceWithStrength newDevice = new BluetoothDeviceWithStrength(result.getDevice(), result.getRssi());
            // Add it to our adapter
            bluetoothDevicesAdapter.addItem(newDevice);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            bluetoothDevicesAdapter.update(results);
        }
    };
    private boolean isScanning = false;
    private Handler handler = new Handler();
    private android.os.ParcelUuid uuid;
    private String deviceName;
    private UARTService.UARTBinder UARTBinder;
    private boolean isConnected = false;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Log.d(TAG, "service connected");
            isConnected = true;
            // show disconnect button
            UARTBinder = (UARTService.UARTBinder) service;

            // show disconnect button
            connectButton.setVisibility(View.VISIBLE);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Log.d(TAG, "service disconnected");
            isConnected = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothTestApplication.injectMembers(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        scanButton.setOnCheckedChangeListener(this);
        setUpBlueToothDevices();
    }

    private void setUpBlueToothDevices() {
        scannedBluetooth.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        scannedBluetooth.setLayoutManager(layoutManager);

        bluetoothDevicesAdapter = new BluetoothDevicesAdapter(this);
        foundBluetooth = new ArrayList<BluetoothDeviceWithStrength>();
        bluetoothDevicesAdapter.addItemList(foundBluetooth);
        scannedBluetooth.setAdapter(bluetoothDevicesAdapter);
    }

    @Override
    public void onItemClickListener(View view, int position) {
        stopScan();
        BluetoothDeviceWithStrength selectedDevice = bluetoothDevicesAdapter.getItemAtPosition(position);
        onDeviceSelected(selectedDevice.getDevice(), selectedDevice.getName());
    }

    public void onDeviceSelected(final BluetoothDevice device, final String name) {

        if (deviceName != null && deviceName.equals(name)) {
            // we are already connected to this device, disconnect
        } else {
            deviceName = name;
        }

        // The device may not be in the range but the service will try to connect to it if it reach it
        final Intent service = new Intent(this, UARTService.class);
        service.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
        startService(service);
        Log.d(TAG, "Binding to the service...");
        bindService(service, mServiceConnection, 0);
    }

    public void startScanning() {
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(1000).setUseHardwareBatchingIfSupported(false).build();
        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(uuid).build());
        scanner.startScan(filters, settings, scanCallback);

        isScanning = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    stopScan();
                }
            }
        }, SCAN_DURATION);
    }

    /**
     * Stop scan if user tap Cancel button
     */
    private void stopScan() {
        if (isScanning) {
            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(scanCallback);

            isScanning = false;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            startScanning();
        } else {
            stopScan();
            bluetoothDevicesAdapter.removeAll();
        }
    }

    @OnClick(R.id.disconnect_button)
    public void onConnectClicked() {
        if (UARTBinder != null) {
            UARTBinder.disconnect();
            connectButton.setVisibility(View.GONE);
        }
    }

    @OnClick(R.id.send_button)
    public void onSendClicked() {
        if (UARTBinder != null)
            UARTBinder.send("O");
    }
}
