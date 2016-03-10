package com.codingbad.com.bluetoothtest;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

/**
 * Created by Ayelen Chavez on 09.03.16.
 */
public class UARTManager {
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
    /**
     * The maximum packet size is 20 bytes.
     */
    private static final int MAX_PACKET_SIZE = 20;
    private static final String TAG = UARTManager.class.toString();
    private final Context mContext;
    private final Handler mHandler;

    private BluetoothGattCharacteristic mRXCharacteristic, mTXCharacteristic;
    private byte[] mOutgoingBuffer;
    private int mBufferOffset;
    private boolean mConnected;
    private BluetoothGatt mBluetoothGatt;
    private boolean mUserDisconnected;
    private BleManagerCallbacks mCallbacks;

    /**
     * Listen to whenever a device is bonded
     */
    private BroadcastReceiver mBondingBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

            // Skip other devices
            if (mBluetoothGatt == null || !device.getAddress().equals(mBluetoothGatt.getDevice().getAddress()))
                return;

            Log.d(TAG, "[Broadcast] Action received: " + BluetoothDevice.ACTION_BOND_STATE_CHANGED + ", bond state changed to: " + bondState);

            switch (bondState) {
                case BluetoothDevice.BOND_BONDING:
                    mCallbacks.onBondingRequired();
                    break;
                case BluetoothDevice.BOND_BONDED:
                    Log.i(TAG, "Device bonded");
                    mCallbacks.onBonded();

                    // Start initializing again.
                    // In fact, bonding forces additional, internal service discovery (at least on Nexus devices), so this method may safely be used to start this process again.
                    Log.v(TAG, "Discovering Services...");
                    Log.d(TAG, "gatt.discoverServices()");
                    mBluetoothGatt.discoverServices();
                    break;
            }
        }
    };
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                mConnected = true;
                Log.d(TAG, "Connected to " + gatt.getDevice().getAddress() + " - " + gatt.getDevice().getName());
                mCallbacks.onDeviceConnected();

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Some proximity tags (e.g. nRF PROXIMITY) initialize bonding automatically when connected.
                        if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDING) {
                            Log.v(TAG, "Discovering Services...");
                            Log.d(TAG, "gatt.discoverServices()");
                            gatt.discoverServices();
                        }
                    }
                }, 600);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Gatt Error: 0x" + Integer.toHexString(status));
                }

                onDeviceDisconnected();
                mCallbacks.onDeviceDisconnected();

                mConnected = false;

                if (mUserDisconnected) {
                    Log.i(TAG, "Disconnected");
                    mCallbacks.onDeviceDisconnected();
                    close();
                } else {
                    Log.w(TAG, "Connection lost");
                    mCallbacks.onLinklossOccur();
                    // We are not closing the connection here as the device should try to reconnect automatically.
                    // This may be only called when the shouldAutoConnect() method returned true.
                }
                Log.d(TAG, "Disconnected from " + gatt.getDevice().getAddress() + " - " + gatt.getDevice().getName());
                // stopSelf();
                close();
            }
        }

        public boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
            final BluetoothGattService service = gatt.getService(UART_SERVICE_UUID);
            if (service != null) {
                mRXCharacteristic = service.getCharacteristic(UART_RX_CHARACTERISTIC_UUID);
                mTXCharacteristic = service.getCharacteristic(UART_TX_CHARACTERISTIC_UUID);
            }

            boolean writeRequest = false;
            boolean writeCommand = false;
            if (mRXCharacteristic != null) {
                final int rxProperties = mRXCharacteristic.getProperties();
                writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
                writeCommand = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0;

                // Set the WRITE REQUEST type when the characteristic supports it. This will allow to send long write (also if the characteristic support it).
                // In case there is no WRITE REQUEST property, this manager will divide texts longer then 20 bytes into up to 20 bytes chunks.
                if (writeRequest)
                    mRXCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }

            return mRXCharacteristic != null && mTXCharacteristic != null && (writeRequest || writeCommand);
        }

        @Override
        public final void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services Discovered");
                isRequiredServiceSupported(gatt);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            final byte[] buffer = mOutgoingBuffer;
            if (mBufferOffset == buffer.length) {
                try {
                    mCallbacks.onDataSent(new String(buffer, "UTF-8"));
                } catch (final UnsupportedEncodingException e) {
                    // do nothing
                }
                mOutgoingBuffer = null;
            } else { // Otherwise...
                final int length = Math.min(buffer.length - mBufferOffset, MAX_PACKET_SIZE);
                final byte[] data = new byte[length]; // We send at most 20 bytes
                System.arraycopy(buffer, mBufferOffset, data, 0, length);
                mBufferOffset += length;
                mRXCharacteristic.setValue(data);
                writeCharacteristic(mRXCharacteristic);
            }
        }

        protected void onDeviceDisconnected() {
            mRXCharacteristic = null;
            mTXCharacteristic = null;
        }
    };

    public UARTManager(final Context context) {
        this.mContext = context;
        mHandler = new Handler();
        mUserDisconnected = false;

        // Register bonding broadcast receiver
        context.registerReceiver(mBondingBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }

    public boolean disconnect() {
        mUserDisconnected = true;

        if (mConnected && mBluetoothGatt != null) {
            Log.v(TAG, "Disconnecting...");
            mCallbacks.onDeviceDisconnecting();
            Log.d(TAG, "gatt.disconnect()");
            mBluetoothGatt.disconnect();
            return true;
        }
        return false;
    }

    public void close() {
        try {
            mContext.unregisterReceiver(mBondingBroadcastReceiver);
            // mContext.unregisterReceiver(mPairingRequestBroadcastReceiver);
        } catch (Exception e) {
            // the receiver must have been not registered or unregistered before
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        mUserDisconnected = false;
    }

    /**
     * Sends the given text to RX characteristic.
     *
     * @param text the text to be sent
     */
    public void send(final String text) {
        // Are we connected?
        if (mRXCharacteristic == null)
            return;

        // An outgoing buffer may not be null if there is already another packet being sent. We do nothing in this case.
        if (!TextUtils.isEmpty(text) && mOutgoingBuffer == null) {
            final byte[] buffer = mOutgoingBuffer = text.getBytes();
            mBufferOffset = 0;

            // Depending on whether the characteristic has the WRITE REQUEST property or not, we will either send it as it is (hoping the long write is implemented),
            // or divide it into up to 20 bytes chunks and send them one by one.
            final boolean writeRequest = (mRXCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;

            if (!writeRequest) { // no WRITE REQUEST property
                final int length = Math.min(buffer.length, MAX_PACKET_SIZE);
                final byte[] data = new byte[length]; // We send at most 20 bytes
                System.arraycopy(buffer, 0, data, 0, length);
                mBufferOffset += length;
                mRXCharacteristic.setValue(data);
            } else { // there is WRITE REQUEST property
                mRXCharacteristic.setValue(buffer);
                mBufferOffset = buffer.length;
            }
            writeCharacteristic(mRXCharacteristic);
        }
    }

    protected final boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;

        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0)
            return false;

        Log.v(TAG, "Writing characteristic " + characteristic.getUuid() + " (" + getWriteType(characteristic.getWriteType()) + ")");
        Log.d(TAG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
        return gatt.writeCharacteristic(characteristic);
    }

    protected String getWriteType(final int type) {
        switch (type) {
            case BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:
                return "WRITE REQUEST";
            case BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:
                return "WRITE COMMAND";
            case BluetoothGattCharacteristic.WRITE_TYPE_SIGNED:
                return "WRITE SIGNED";
            default:
                return "UNKNOWN: " + type;
        }
    }

    public void setGattCallbacks(BleManagerCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    public void connect(final BluetoothDevice device) {
        if (mConnected)
            return;

        if (mBluetoothGatt != null) {
            Log.d(TAG, "gatt.close()");
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        final boolean autoConnect = shouldAutoConnect();
        mUserDisconnected = !autoConnect; // We will receive Linkloss events only when the device is connected with autoConnect=true
        Log.v(TAG, "Connecting...");
        Log.d(TAG, "gatt = device.connectGatt(autoConnect = " + autoConnect + ")");
        mBluetoothGatt = device.connectGatt(mContext, autoConnect, getGattCallback());
    }

    private BluetoothGattCallback getGattCallback() {
        return mGattCallback;
    }

    protected boolean shouldAutoConnect() {
        return true;
    }
}
