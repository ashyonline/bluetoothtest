package com.codingbad.com.bluetoothtest;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
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
     * The peer can send data to the device by writing to the RX Characteristic of the service.
     * ATT Write Request or ATT Write Command can be used. The received data is sent on the UART
     * interface.
     */
    private final static UUID UART_RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    /**
     * TX characteristic UUID
     * If the peer has enabled notifications for the TX Characteristic, the application
     * can send data to the peer as notifications. The application will transmit all data
     * received over UART as notifications.
     */
    private final static UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    /**
     * The maximum packet size is 20 bytes.
     */
    private static final int MAX_PACKET_SIZE = 20;
    private static final String TAG = UARTManager.class.toString();
    private final Context context;
    private final Handler handler;

    private BluetoothGattCharacteristic rxCharacteristic;
    private byte[] outgoingBuffer;
    private int bufferOffset;

    // indicates whether we are connected to a bluetooth device
    private boolean connected;

    // initialized when connected to a device
    private BluetoothGatt bluetoothGatt;

    // indicates if user disconnected from the device
    private boolean userDisconnected;

    // way to communicate with the UARTService itself, or anyone that wants to listen to some
    // events
    private BleManagerCallbacks bleManagerCallbacks;

    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                Log.d(TAG, "Connected to " + gatt.getDevice().getAddress() + " - " + gatt.getDevice().getName());
                bleManagerCallbacks.onDeviceConnected();

                handler.postDelayed(new Runnable() {
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
                bleManagerCallbacks.onDeviceDisconnected();

                connected = false;

                if (userDisconnected) {
                    Log.i(TAG, "Disconnected");
                    bleManagerCallbacks.onDeviceDisconnected();
                    close();
                } else {
                    Log.w(TAG, "Connection lost");
                    bleManagerCallbacks.onLinklossOccur();
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
                rxCharacteristic = service.getCharacteristic(UART_RX_CHARACTERISTIC_UUID);
            }

            boolean writeRequest = false;
            boolean writeCommand = false;
            if (rxCharacteristic != null) {
                final int rxProperties = rxCharacteristic.getProperties();
                writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
                writeCommand = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0;

                // Set the WRITE REQUEST type when the characteristic supports it. This will allow to send long write (also if the characteristic support it).
                // In case there is no WRITE REQUEST property, this manager will divide texts longer then 20 bytes into up to 20 bytes chunks.
                if (writeRequest)
                    rxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }

            return rxCharacteristic != null && (writeRequest || writeCommand);
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
            final byte[] buffer = outgoingBuffer;
            if (bufferOffset == buffer.length) {
                try {
                    bleManagerCallbacks.onDataSent(new String(buffer, "UTF-8"));
                } catch (final UnsupportedEncodingException e) {
                    // do nothing
                }
                outgoingBuffer = null;
            } else { // Otherwise...
                final int length = Math.min(buffer.length - bufferOffset, MAX_PACKET_SIZE);
                final byte[] data = new byte[length]; // We send at most 20 bytes
                System.arraycopy(buffer, bufferOffset, data, 0, length);
                bufferOffset += length;
                rxCharacteristic.setValue(data);
                writeCharacteristic(rxCharacteristic);
            }
        }

        // Note: this will only write characters and send to bluetooth device, but not read
        // in order to do so, implement onCharacteristicRead

        protected void onDeviceDisconnected() {
            rxCharacteristic = null;
        }
    };

    public UARTManager(final Context context) {
        this.context = context;
        handler = new Handler();
        userDisconnected = false;
    }

    public boolean disconnect() {
        userDisconnected = true;

        if (connected && bluetoothGatt != null) {
            Log.v(TAG, "Disconnecting...");
            bleManagerCallbacks.onDeviceDisconnecting();
            Log.d(TAG, "gatt.disconnect()");
            bluetoothGatt.disconnect();
            return true;
        }
        return false;
    }

    public void close() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        userDisconnected = false;
    }

    /**
     * Sends the given text to RX characteristic.
     *
     * @param text the text to be sent
     */
    public void send(final String text) {
        // Are we connected?
        if (rxCharacteristic == null)
            return;

        // An outgoing buffer may not be null if there is already another packet being sent. We do nothing in this case.
        if (!TextUtils.isEmpty(text) && outgoingBuffer == null) {
            final byte[] buffer = outgoingBuffer = text.getBytes();
            bufferOffset = 0;

            // Depending on whether the characteristic has the WRITE REQUEST property or not, we will either send it as it is (hoping the long write is implemented),
            // or divide it into up to 20 bytes chunks and send them one by one.
            final boolean writeRequest = (rxCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;

            if (!writeRequest) { // no WRITE REQUEST property
                final int length = Math.min(buffer.length, MAX_PACKET_SIZE);
                final byte[] data = new byte[length]; // We send at most 20 bytes
                System.arraycopy(buffer, 0, data, 0, length);
                bufferOffset += length;
                rxCharacteristic.setValue(data);
            } else { // there is WRITE REQUEST property
                rxCharacteristic.setValue(buffer);
                bufferOffset = buffer.length;
            }
            writeCharacteristic(rxCharacteristic);
        }
    }

    protected final boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = bluetoothGatt;

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
        this.bleManagerCallbacks = callbacks;
    }

    public void connect(final BluetoothDevice device) {
        if (connected)
            return;

        if (bluetoothGatt != null) {
            Log.d(TAG, "gatt.close()");
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        final boolean autoConnect = shouldAutoConnect();
        userDisconnected = !autoConnect; // We will receive Linkloss events only when the device is connected with autoConnect=true
        Log.v(TAG, "Connecting...");
        Log.d(TAG, "gatt = device.connectGatt(autoConnect = " + autoConnect + ")");
        bluetoothGatt = device.connectGatt(context, autoConnect, getGattCallback());
    }

    private BluetoothGattCallback getGattCallback() {
        return bluetoothGattCallback;
    }

    protected boolean shouldAutoConnect() {
        return true;
    }
}
