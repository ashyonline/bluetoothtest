package com.codingbad.com.bluetoothtest.model;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.codingbad.com.bluetoothtest.Constants;

/**
 * Created by Ayelen Chavez on 09.03.16.
 */
public class UARTService extends Service implements BleManagerCallbacks {
    private static final String TAG = UARTService.class.toString();

    private UARTManager uartManager;

    private String deviceAddress;
    private boolean connected;
    private boolean serverBinded;

    @Override
    public void onDeviceConnected() {
        connected = true;
    }

    @Override
    public void onDeviceDisconnecting() {

    }

    public void onDeviceDisconnected() {
        connected = false;
        deviceAddress = null;

        stopSelf();
    }

    @Override
    public void onLinklossOccur() {

    }

    @Override
    public void onCreate() {
        super.onCreate();

        // initialize the manager
        uartManager = new UARTManager(this);
        uartManager.setGattCallbacks(this);
    }

    /**
     * Connect to the selected device
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent == null || !intent.hasExtra(Constants.EXTRA_DEVICE_ADDRESS))
            throw new UnsupportedOperationException("No device address at EXTRA_DEVICE_ADDRESS key");

        deviceAddress = intent.getStringExtra(Constants.EXTRA_DEVICE_ADDRESS);

        Log.i(TAG, "Service started");

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        final BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);

        this.uartManager.connect(device);
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        serverBinded = true;
        return getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        serverBinded = false;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        serverBinded = true;
    }

    /**
     * Return binder to communicate with service from outside
     *
     * @return
     */
    protected UARTBinder getBinder() {
        // default implementation returns the basic binder.
        // You can overwrite the LocalBinder with your own, wider implementation
        return new UARTBinder();
    }

    public void onDataSent(final String data) {
        Log.d(TAG, "\"" + data + "\" sent");

        // if I would like to communicate with activity, I could send a message using broadcast
        // receiver in the activity side, and call
        // LocalBroadcastManager.getInstance(this).sendBroadcast here
    }

    public class UARTBinder extends Binder {
        public void send(final String text) {
            uartManager.send(text);
        }

        public final void disconnect() {
            if (!connected) {
                onDeviceDisconnected();
                return;
            }

            uartManager.disconnect();
        }
    }
}
