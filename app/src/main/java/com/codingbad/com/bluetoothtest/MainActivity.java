package com.codingbad.com.bluetoothtest;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.google.inject.Inject;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends Activity implements BluetoothDevicesAdapter.RecyclerViewListener, CompoundButton.OnCheckedChangeListener {

    private static final long INTERVAL_MILLIS = 6000;
    private static final int PERMISSIONS_REQUEST_BLUETOOTH_ADMIN = 1;

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

    @TargetApi(Build.VERSION_CODES.M)
    private void promptUserForBluetoothAccess() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_ADMIN);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_ADMIN)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                        PERMISSIONS_REQUEST_BLUETOOTH_ADMIN);

                // PERMISSIONS_REQUEST_BLUETOOTH_ADMIN is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // if permission is granted, turn on bluetooth
            registerBluetoothReceiver();
        }
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        bluetoothDevicesAdapter.removeAll();
        registerReceiver(broadcastReceiver, filter);
        bTAdapter.startDiscovery();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_BLUETOOTH_ADMIN: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    registerBluetoothReceiver();
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void scanBluetooth() {
        // Construct an intent that will execute the AlarmReceiver
        Intent intent = new Intent(getApplicationContext(), AlarmReceiver.class);
        // Create a PendingIntent to be triggered when the alarm goes off
        final PendingIntent pIntent = PendingIntent.getBroadcast(this, AlarmReceiver.REQUEST_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        long firstMillis = System.currentTimeMillis(); // alarm is set right away
        AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        // First parameter is the type: ELAPSED_REALTIME, ELAPSED_REALTIME_WAKEUP, RTC_WAKEUP
        // Interval can be INTERVAL_FIFTEEN_MINUTES, INTERVAL_HALF_HOUR, INTERVAL_HOUR, INTERVAL_DAY
        alarm.setRepeating(AlarmManager.RTC_WAKEUP, firstMillis,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, pIntent);
    }

    @Override
    public void onItemClickListener(View view, int position) {
        new ConnectThread().connect(bluetoothDevicesAdapter.getItemAtPosition(position).getDevice(), UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
    }

    @Subscribe
    public void onDeviceFound(ScanBluetoothService.DeviceFoundEvent deviceFoundEvent) {
        BluetoothDeviceWithStrength bluetoothDeviceWithStrength = new BluetoothDeviceWithStrength(deviceFoundEvent.getDevice(), deviceFoundEvent.getStrength());
        bluetoothDevicesAdapter.addItem(bluetoothDeviceWithStrength);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            promptUserForBluetoothAccess();
        } else {
            unregisterReceiver(broadcastReceiver);
            bTAdapter.cancelDiscovery();
        }
    }
}
