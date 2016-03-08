package com.codingbad.com.bluetoothtest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.google.inject.Inject;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements BluetoothDevicesAdapter.RecyclerViewListener, CompoundButton.OnCheckedChangeListener,
        ScannerFragment.OnDeviceSelectedListener {

    protected static final int REQUEST_ENABLE_BT = 2;
    private static final long INTERVAL_MILLIS = 6000;
    private static final int PERMISSIONS_REQUEST_BLUETOOTH_ADMIN = 1;
    private static final String TAG = MainActivity.class.toString();


    @Bind(R.id.scan_button)
    protected Switch scanButton;

    @Inject
    protected OttoBus ottoBus;
    @Bind(R.id.bluetooth_list)
    protected RecyclerView scannedBluetooth;
    private BluetoothDevicesAdapter bluetoothDevicesAdapter;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Create a new device item
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                BluetoothDeviceWithStrength newDevice = new BluetoothDeviceWithStrength(device, rssi);
                // Add it to our adapter
                bluetoothDevicesAdapter.addItem(newDevice);
            }
        }
    };
    private List<BluetoothDeviceWithStrength> foundBluetooth;
    private BluetoothAdapter bTAdapter;
    private BleProfileService mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothTestApplication.injectMembers(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        scanButton.setOnCheckedChangeListener(this);
        bTAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean isEnabled = bTAdapter.isEnabled();
        if (!isEnabled) {
            bTAdapter.enable();
        }

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
    public void onStart() {
        super.onStart();
        ottoBus.register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        ottoBus.unregister(this);
    }

    @Override
    public void onItemClickListener(View view, int position) {
        try {
            new BluetoothConnector(bluetoothDevicesAdapter.getItemAtPosition(position).getDevice(), false, bTAdapter, null).connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onDeviceFound(ScanBluetoothService.DeviceFoundEvent deviceFoundEvent) {
        BluetoothDeviceWithStrength bluetoothDeviceWithStrength = new BluetoothDeviceWithStrength(deviceFoundEvent.getDevice(), deviceFoundEvent.getStrength());
        bluetoothDevicesAdapter.addItem(bluetoothDeviceWithStrength);
    }

    protected boolean isBLEEnabled() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    public void onConnectClicked() {
        if (isBLEEnabled()) {
            if (mService == null) {
                setDefaultUI();
                showDeviceScanningDialog(getFilterUUID());
            } else {
                mService.disconnect();
            }
        } else {
            showBLEDialog();
        }
    }

    protected void showBLEDialog() {
        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

    protected UUID getFilterUUID() {
        return null; // not used
    }

    private void showDeviceScanningDialog(final UUID filter) {
        final ScannerFragment dialog = ScannerFragment.getInstance(filter);
        dialog.show(getSupportFragmentManager(), "scan_fragment");
    }

    protected void setDefaultUI() {
        // empty
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            onConnectClicked();
        } else {
            unregisterReceiver(broadcastReceiver);
            bTAdapter.cancelDiscovery();
            bluetoothDevicesAdapter.removeAll();
        }
    }

    @Override
    public void onDeviceSelected(final BluetoothDevice device, final String name) {

    }

    @Override
    public void onDialogCanceled() {

    }
}
