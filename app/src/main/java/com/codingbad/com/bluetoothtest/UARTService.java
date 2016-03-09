package com.codingbad.com.bluetoothtest;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.UUID;

/**
 * Created by Ayelen Chavez on 09.03.16.
 */
public class UARTService extends Service {
    public static final String BROADCAST_UART_TX = "no.nordicsemi.android.nrftoolbox.uart.BROADCAST_UART_TX";
    public static final String BROADCAST_UART_RX = "no.nordicsemi.android.nrftoolbox.uart.BROADCAST_UART_RX";
    public static final String EXTRA_DATA = "no.nordicsemi.android.nrftoolbox.uart.EXTRA_DATA";
    /**
     * A broadcast message with this action and the message in {@link Intent#EXTRA_TEXT} will be sent t the UART device.
     */
    public final static String ACTION_SEND = "no.nordicsemi.android.nrftoolbox.uart.ACTION_SEND";
    /**
     * Action send when user press the DISCONNECT button on the notification.
     */
    public final static String ACTION_DISCONNECT = "no.nordicsemi.android.nrftoolbox.uart.ACTION_DISCONNECT";
    /**
     * A source of an action.
     */
    public final static String EXTRA_SOURCE = "no.nordicsemi.android.nrftoolbox.uart.EXTRA_SOURCE";
    public final static int SOURCE_NOTIFICATION = 0;
    public final static int SOURCE_WEARABLE = 1;
    public final static int SOURCE_3RD_PARTY = 2;
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 2;
    /**
     * Nordic UART Service UUID
     */
    private final static UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    /**
     * RX characteristic UUID
     */
    private final static UUID UART_RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    /**
     * TX characteristic UUID
     */
    private final static UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final String TAG = "UARTService";

    private static final String BROADCAST_CONNECTION_STATE = "broadcast_connection_state";
    private static final String EXTRA_CONNECTION_STATE = "extra_connection_state";
    private final UARTBinder mBinder = new UARTBinder();
    private UARTManager mBleManager;

    private String deviceAddress;
    private String deviceName;
    private boolean mConnected;
    private Handler mHandler;
    private boolean mUserDisconnected;
    private BluetoothGatt mBluetoothGatt;
    /**
     * This broadcast receiver listens for {@link #ACTION_DISCONNECT} that may be fired by pressing Disconnect action button on the notification.
     */
    private final BroadcastReceiver mDisconnectActionBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final int source = intent.getIntExtra(EXTRA_SOURCE, SOURCE_NOTIFICATION);
            switch (source) {
                case SOURCE_NOTIFICATION:
                    Log.i(TAG, "[Notification] Disconnect action pressed");
                    break;
            }
            if (isConnected())
                mBinder.disconnect();
            else
                stopSelf();
        }
    };
    private BluetoothGattCharacteristic mRXCharacteristic, mTXCharacteristic;
    private boolean mBinded;
    /**
     * Broadcast receiver that listens for {@link #ACTION_SEND} from other apps. Sends the String or int content of the {@link Intent#EXTRA_TEXT} extra to the remote device.
     * The integer content will be sent as String (65 -> "65", not 65 -> "A").
     */
    private BroadcastReceiver mIntentBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final boolean hasMessage = intent.hasExtra(Intent.EXTRA_TEXT);
            if (hasMessage) {
                String message = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (message == null) {
                    final int intValue = intent.getIntExtra(Intent.EXTRA_TEXT, Integer.MIN_VALUE); // how big is the chance of such data?
                    if (intValue != Integer.MIN_VALUE)
                        message = String.valueOf(intValue);
                }

                if (message != null) {
                    final int source = intent.getIntExtra(EXTRA_SOURCE, SOURCE_3RD_PARTY);
                    switch (source) {
                        case SOURCE_WEARABLE:
                            break;
                        case SOURCE_3RD_PARTY:
                        default:
                            Log.i(TAG, "[Broadcast] " + ACTION_SEND + " broadcast received with data: \"" + message + "\"");
                            break;
                    }
                    mBleManager.send(message);
                    return;
                }
            }
            // No data od incompatible type of EXTRA_TEXT
            if (!hasMessage)
                Log.i(TAG, "[Broadcast] " + ACTION_SEND + " broadcast received no data.");
            else
                Log.i(TAG, "[Broadcast] " + ACTION_SEND + " broadcast received incompatible data type. Only String and int are supported.");
        }
    };

    public void onDeviceDisconnected() {
        mConnected = false;
        deviceAddress = null;
        deviceName = null;

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        stopSelf();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();

        // initialize the manager
        mBleManager = new UARTManager(this);
        mBleManager.setGattCallbacks(this);

        registerReceiver(mDisconnectActionBroadcastReceiver, new IntentFilter(ACTION_DISCONNECT));
        registerReceiver(mIntentBroadcastReceiver, new IntentFilter(ACTION_SEND));
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent == null || !intent.hasExtra(MainActivity.EXTRA_DEVICE_ADDRESS))
            throw new UnsupportedOperationException("No device address at EXTRA_DEVICE_ADDRESS key");

        deviceAddress = intent.getStringExtra(MainActivity.EXTRA_DEVICE_ADDRESS);

        Log.i(TAG, "Service started");

        // notify user about changing the state to CONNECTING
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTING);
        LocalBroadcastManager.getInstance(UARTService.this).sendBroadcast(broadcast);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        final BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
        deviceName = device.getName();

        mBleManager.connect(device);
        return START_REDELIVER_INTENT;
    }

    private void close() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    protected boolean shouldAutoConnect() {
        // We want the connection to be kept
        return true;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mDisconnectActionBroadcastReceiver);
        unregisterReceiver(mIntentBroadcastReceiver);

        super.onDestroy();
    }

    @Override
    public IBinder onBind(final Intent intent) {
        mBinded = true;
        return getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mBinded = false;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        mBinded = true;
    }

    protected UARTBinder getBinder() {
        // default implementation returns the basic binder. You can overwrite the LocalBinder with your own, wider implementation
        return new UARTBinder();
    }

    public void onDataSent(final String data) {
        Log.d(TAG, "\"" + data + "\" sent");

        final Intent broadcast = new Intent(BROADCAST_UART_TX);
        broadcast.putExtra(EXTRA_DATA, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    protected boolean isConnected() {
        return mConnected;
    }

    public class UARTBinder extends Binder {
        public void send(final String text) {
            mBleManager.send(text);
        }

        public final void disconnect() {
            if (!mConnected) {
                onDeviceDisconnected();
                return;
            }

            mUserDisconnected = true;

            if (mConnected && mBluetoothGatt != null) {
                Log.v(TAG, "Disconnecting...");
                Log.d(TAG, "gatt.disconnect()");
                mBluetoothGatt.disconnect();
            }
        }
    }
}
