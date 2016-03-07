package com.codingbad.com.bluetoothtest;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.google.inject.Inject;

/**
 * Created by Ayelen Chavez on 07.03.16.
 */
public class ScanBluetoothService extends IntentService {
    private static final String TAG = ScanBluetoothService.class.toString();
    private static final String DOOR_NAME = "1AIM_SM";
    @Inject
    protected OttoBus ottoBus;
    private BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver broadcastReceiver;
    private boolean registered = false;

    public ScanBluetoothService() {
        super("ScanBluetoothService");
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        BluetoothTestApplication.injectMembers(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        // WakefulBroadcastReceiver.completeWakefulIntent(intent);

        // Do the task here
        Log.i("ScanBluetoothService", "Service running");

        // scan bluetooth only if option selected
        broadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                Log.d(TAG, "on action received: " + action);

                //Finding devices
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Add the name and address to an array adapter to show in a ListView
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                    ottoBus.post(new DeviceFoundEvent(device, rssi));
//                        if (device.getName().contains(DOOR_NAME)) {
//                            // TODO: do something with strength
//
//                            Log.d(TAG, "Strength: " + rssi);
//
//                            BluetoothConnector connector = new BluetoothConnector(device, true, bluetoothAdapter, null);
//                            try {
//                                connector.connect();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//
//                            // if door were found, stop discovering
//                            stopDiscovering();
//                        }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
//            filter.addAction(BluetoothDevicesAdapter.ACTION_DISCOVERY_FINISHED);
//            filter.addAction(BluetoothDevicesAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(broadcastReceiver, filter);
        registered = true;

        stopDiscovering();
        bluetoothAdapter.startDiscovery();
        bluetoothAdapter.getBondedDevices();
    }

    /**
     * If bluetooth adapter is discovering, cancel discovery
     */
    private void stopDiscovering() {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    private void destroyService() {
        stopDiscovering();
        if (registered) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    public class DeviceFoundEvent {
        private final BluetoothDevice device;
        private final int strength;

        public DeviceFoundEvent(BluetoothDevice device, int rssi) {
            this.device = device;
            this.strength = rssi;
        }

        public BluetoothDevice getDevice() {
            return device;
        }

        public int getStrength() {
            return strength;
        }
    }
}