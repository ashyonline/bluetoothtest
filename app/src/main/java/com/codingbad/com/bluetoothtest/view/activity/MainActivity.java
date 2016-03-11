package com.codingbad.com.bluetoothtest.view.activity;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.codingbad.com.bluetoothtest.model.BluetoothDeviceWithStrength;
import com.codingbad.com.bluetoothtest.BluetoothTestApplication;
import com.codingbad.com.bluetoothtest.Constants;
import com.codingbad.com.bluetoothtest.R;
import com.codingbad.com.bluetoothtest.model.UARTService;
import com.codingbad.com.bluetoothtest.view.BluetoothDevicesAdapter;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * Main Activity includes all UI
 * <p/>
 * A switch to scan bluetooth and the list of known devices.
 * <p/>
 * When connected, shows options to disconnect from the connected bluetooth or send a command.
 */
public class MainActivity extends Activity implements BluetoothDevicesAdapter.RecyclerViewListener {

    // user for log purposes
    private static final String TAG = MainActivity.class.toString();
    private static final long SCAN_DURATION = 5000;
    private static final String DEFAULT_MESSAGE = "O";

    // UI binded using Butterknife
    @Bind(R.id.bluetooth_list)
    protected RecyclerView scannedBluetoothList;
    @Bind(R.id.connected_layout)
    protected LinearLayout connectionLayout;
    @Bind(R.id.connected_device_name)
    protected TextView connectedDeviceName;
    @Bind(R.id.message)
    protected EditText userMessage;

    // recycler view adapter
    private BluetoothDevicesAdapter bluetoothDevicesAdapter;

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
            bluetoothDevicesAdapter.update(results);
        }
    };

    // flag to know the scanning state
    private boolean isScanning = false;

    // handle used to stop scanning with some delay
    private Handler handler = new Handler();

    // uuid which right now is null, for the scanning process
    private ParcelUuid uuid;

    // save device we are currently connected to
    private String deviceName;

    // save binder to service once it is connected
    // this will be the binder of the Service's communication channel, to make calls on.
    private UARTService.UARTBinder UARTBinder;

    // save when the activity is binded to the service or not
    private boolean isConnected = false;

    // This service connection object receives information as the service is started and stopped
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Log.d(TAG, "UARTService connected");
            isConnected = true;

            // save binder
            UARTBinder = (UARTService.UARTBinder) service;

            // show disconnect button
            connectionLayout.setVisibility(View.VISIBLE);

            // show user the current device we are connected to
            connectedDeviceName.setText(getString(R.string.connected_device, deviceName));
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Log.d(TAG, "UARTService disconnected");
            isConnected = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothTestApplication.injectMembers(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setUpBlueToothList();
    }

    /**
     * Set up recycler view to list bluetooth devices
     */
    private void setUpBlueToothList() {
        scannedBluetoothList.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        scannedBluetoothList.setLayoutManager(layoutManager);

        bluetoothDevicesAdapter = new BluetoothDevicesAdapter(this);
        bluetoothDevicesAdapter.addItemList(new ArrayList<BluetoothDeviceWithStrength>());
        scannedBluetoothList.setAdapter(bluetoothDevicesAdapter);
    }

    /**
     * When one of the devices is clicked, connect to it
     *
     * @param view
     * @param position
     */
    @Override
    public void onItemClickListener(View view, int position) {
        stopBluetoothScan();
        BluetoothDeviceWithStrength selectedDevice = bluetoothDevicesAdapter.getItemAtPosition(position);
        onDeviceSelected(selectedDevice.getDevice(), selectedDevice.getName());
    }

    /**
     * Connect to selected device
     *
     * @param device
     * @param name
     */
    public void onDeviceSelected(final BluetoothDevice device, final String name) {
        if (deviceName != null && deviceName.equals(name)) {
            // we are already connected to this device, disconnect
        } else {
            deviceName = name;
        }

        // The device may not be in the range but the service will try to connect to it if it reach it
        // TODO: check if the device is near to connect
        final Intent service = new Intent(this, UARTService.class);
        service.putExtra(Constants.EXTRA_DEVICE_ADDRESS, device.getAddress());
        startService(service);

        Log.d(TAG, "Binding to the service...");
        // bind to UARTService
        bindService(service, serviceConnection, 0);
    }

    /**
     * Use nordicsemi library to scan bluetooth devices regarding android SDK version used
     */
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
    private void stopBluetoothScan() {
        if (isScanning) {
            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(scanDevicesCallback);

            isScanning = false;
        }
    }

    /**
     * Start and stop scanning
     *
     * @param buttonView
     * @param isChecked
     */
    @OnCheckedChanged(R.id.scan_button)
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            startBluetoothScan();
        } else {
            stopBluetoothScan();
            bluetoothDevicesAdapter.removeAll();
        }
    }

    /**
     * Disconnect from binder
     */
    @OnClick(R.id.disconnect_button)
    public void onConnectClicked() {
        if (UARTBinder != null) {
            UARTBinder.disconnect();
            connectionLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Hide soft keyboard
     */
    public void hideSoftKeyboard() {
        if (getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @OnClick(R.id.send_button)
    public void onSendClicked() {
        if (UARTBinder != null) {
            hideSoftKeyboard();
            String message = userMessage.getText().toString();
            if (message != null && !message.isEmpty()) {
                UARTBinder.send(message);
            } else {
                UARTBinder.send(DEFAULT_MESSAGE);
            }
        }
    }
}
