package com.codingbad.com.bluetoothtest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.Bind;
import butterknife.ButterKnife;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class MainActivity extends Activity implements BluetoothDevicesAdapter.RecyclerViewListener, CompoundButton.OnCheckedChangeListener {

    private static final String TAG = MainActivity.class.toString();
    private static final long SCAN_DURATION = 5000;
    @Bind(R.id.scan_button)
    protected Switch scanButton;

    @Bind(R.id.bluetooth_list)
    protected RecyclerView scannedBluetooth;
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
            Log.d(TAG, "found: " + results.size());
            for (ScanResult result : results) {
                BluetoothDeviceWithStrength newDevice = new BluetoothDeviceWithStrength(result.getDevice(), result.getRssi());
                // Add it to our adapter
                bluetoothDevicesAdapter.addItem(newDevice);
            }
        }
    };
    private boolean mIsScanning = false;
    private Handler mHandler = new Handler();
    private android.os.ParcelUuid mUuid;

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

    }

    public void startScanning2() {
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(1000).setUseHardwareBatchingIfSupported(false).build();
        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(mUuid).build());
        scanner.startScan(filters, settings, scanCallback);

        mIsScanning = true;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mIsScanning) {
                    stopScan();
                }
            }
        }, SCAN_DURATION);
    }

    /**
     * Stop scan if user tap Cancel button
     */
    private void stopScan() {
        if (mIsScanning) {
            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(scanCallback);

            mIsScanning = false;
        }
    }

    private void startScanning() {
        BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(1000)
                .setUseHardwareBatchingIfSupported(false).build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(mUuid).build());
        scanner.startScan(filters, settings, scanCallback);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            startScanning();
        } else {
            stopScanning();
        }
    }

    private void stopScanning() {
        BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        scanner.stopScan(scanCallback);
    }
}
