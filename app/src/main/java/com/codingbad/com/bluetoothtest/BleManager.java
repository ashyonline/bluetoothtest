package com.codingbad.com.bluetoothtest;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.util.Queue;
import java.util.UUID;

/**
 * Created by Ayelen Chavez on 08.03.16.
 */
public abstract class BleManager<E extends BleManagerCallbacks> {
    private final static String TAG = "BleManager";

    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final static UUID BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    private final static UUID GENERIC_ATTRIBUTE_SERVICE = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
    private final static UUID SERVICE_CHANGED_CHARACTERISTIC = UUID.fromString("00002A05-0000-1000-8000-00805f9b34fb");

    private final static String ERROR_CONNECTION_STATE_CHANGE = "Error on connection state change";
    private final static String ERROR_DISCOVERY_SERVICE = "Error on discovering services";
    private final static String ERROR_AUTH_ERROR_WHILE_BONDED = "Phone has lost bonding information";
    private final static String ERROR_WRITE_DESCRIPTOR = "Error on writing descriptor";
    private final static String ERROR_READ_CHARACTERISTIC = "Error on reading characteristic";
    private static final int PAIRING_VARIANT_PIN = 0;
    private static final int PAIRING_VARIANT_PASSKEY = 1;
    private static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;
    private static final int PAIRING_VARIANT_CONSENT = 3;
    private static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;
    private static final int PAIRING_VARIANT_DISPLAY_PIN = 5;
    private static final int PAIRING_VARIANT_OOB_CONSENT = 6;
    /**
     * The log session or null if nRF Logger is not installed.
     */
    protected E mCallbacks;
    private Handler mHandler;
    private BluetoothGatt mBluetoothGatt;
    private final BroadcastReceiver mPairingRequestBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            // Skip other devices
            if (mBluetoothGatt == null || !device.getAddress().equals(mBluetoothGatt.getDevice().getAddress()))
                return;

            // String values are used as the constants are not available for Android 4.3.
            final int variant = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT"/*BluetoothDevice.EXTRA_PAIRING_VARIANT*/, 0);
            Log.d(TAG, "[Broadcast] Action received: android.bluetooth.device.action.PAIRING_REQUEST"/*BluetoothDevice.ACTION_PAIRING_REQUEST*/ +
                    ", pairing variant: " + pairingVariantToString(variant) + " (" + variant + ")");

            // The API below is available for Android 4.4 or newer.

            // An app may set the PIN here or set pairing confirmation (depending on the variant) using:
            // device.setPin(new byte[] { '1', '2', '3', '4', '5', '6' });
            // device.setPairingConfirmation(true);
        }
    };
    private Context mContext;
    private boolean mUserDisconnected;
    private boolean mConnected;
    private BroadcastReceiver mBondingBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

            // Skip other devices
            if (mBluetoothGatt == null || !device.getAddress().equals(mBluetoothGatt.getDevice().getAddress()))
                return;

            Log.d(TAG, "[Broadcast] Action received: " + BluetoothDevice.ACTION_BOND_STATE_CHANGED + ", bond state changed to: " + bondStateToString(bondState) + " (" + bondState + ")");
            Log.i(TAG, "Bond state changed for: " + device.getName() + " new state: " + bondState + " previous: " + previousBondState);

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

    public BleManager(final Context context) {
        mContext = context;
        mHandler = new Handler();
        mUserDisconnected = false;

        // Register bonding broadcast receiver
        context.registerReceiver(mBondingBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        context.registerReceiver(mPairingRequestBroadcastReceiver, new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST"/*BluetoothDevice.ACTION_PAIRING_REQUEST*/));
    }

    /**
     * Returns the context that the manager was created with.
     *
     * @return the context
     */
    protected Context getContext() {
        return mContext;
    }

    /**
     * This method must return the gatt callback used by the manager.
     * This method must not create a new gatt callback each time it is being invoked, but rather return a single object.
     *
     * @return the gatt callback object
     */
    protected abstract BleManagerGattCallback getGattCallback();

    /**
     * Returns whether to directly connect to the remote device (false) or to automatically connect as soon as the remote
     * device becomes available (true).
     *
     * @return autoConnect flag value
     */
    protected boolean shouldAutoConnect() {
        return false;
    }

    /**
     * Connects to the Bluetooth Smart device
     *
     * @param device a device to connect to
     */
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

    /**
     * Disconnects from the device. Does nothing if not connected.
     *
     * @return true if device is to be disconnected. False if it was already disconnected.
     */
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

    /**
     * Closes and releases resources. May be also used to unregister broadcast listeners.
     */
    public void close() {
        try {
            mContext.unregisterReceiver(mBondingBroadcastReceiver);
            mContext.unregisterReceiver(mPairingRequestBroadcastReceiver);
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
     * Sets the manager callback listener
     *
     * @param callbacks the callback listener
     */
    public void setGattCallbacks(E callbacks) {
        mCallbacks = callbacks;
    }

    /**
     * Returns true if this descriptor is from the Service Changed characteristic.
     *
     * @param descriptor the descriptor to be checked
     * @return true if the descriptor belongs to the Service Changed characteristic
     */
    private boolean isServiceChangedCCCD(final BluetoothGattDescriptor descriptor) {
        if (descriptor == null)
            return false;

        return SERVICE_CHANGED_CHARACTERISTIC.equals(descriptor.getCharacteristic().getUuid());
    }

    /**
     * Returns true if the characteristic is the Battery Level characteristic.
     *
     * @param characteristic the characteristic to be checked
     * @return true if the characteristic is the Battery Level characteristic.
     */
    private boolean isBatteryLevelCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return false;

        return BATTERY_LEVEL_CHARACTERISTIC.equals(characteristic.getUuid());
    }

    /**
     * Returns true if this descriptor is from the Battery Level characteristic.
     *
     * @param descriptor the descriptor to be checked
     * @return true if the descriptor belongs to the Battery Level characteristic
     */
    private boolean isBatteryLevelCCCD(final BluetoothGattDescriptor descriptor) {
        if (descriptor == null)
            return false;

        return BATTERY_LEVEL_CHARACTERISTIC.equals(descriptor.getCharacteristic().getUuid());
    }

    /**
     * When the device is bonded and has the Generic Attribute service and the Service Changed characteristic this method enables indications on this characteristic.
     * In case one of the requirements is not fulfilled this method returns <code>false</code>.
     *
     * @param gatt the gatt device with services discovered
     * @return <code>true</code> when the request has been sent, <code>false</code> when the device is not bonded, does not have the Generic Attribute service, the GA service does not have
     * the Service Changed characteristic or this characteristic does not have the CCCD.
     */
    private boolean ensureServiceChangedEnabled(final BluetoothGatt gatt) {
        if (gatt == null)
            return false;

        // The Service Changed indications have sense only on bonded devices
        final BluetoothDevice device = gatt.getDevice();
        if (device.getBondState() != BluetoothDevice.BOND_BONDED)
            return false;

        final BluetoothGattService gaService = gatt.getService(GENERIC_ATTRIBUTE_SERVICE);
        if (gaService == null)
            return false;

        final BluetoothGattCharacteristic scCharacteristic = gaService.getCharacteristic(SERVICE_CHANGED_CHARACTERISTIC);
        if (scCharacteristic == null)
            return false;

        Log.i(TAG, "Service Changed characteristic found on a bonded device");
        return enableIndications(scCharacteristic);
    }

    /**
     * Enables notifications on given characteristic
     *
     * @return true is the request has been sent, false if one of the arguments was <code>null</code> or the characteristic does not have the CCCD.
     */
    protected final boolean enableNotifications(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            return false;

        Log.d(TAG, "gatt.setCharacteristicNotification(" + characteristic.getUuid() + ", true)");
        gatt.setCharacteristicNotification(characteristic, true);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            Log.v(TAG, "Enabling notifications for " + characteristic.getUuid());
            Log.d(TAG, "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x01-00)");
            return gatt.writeDescriptor(descriptor);
        }
        return false;
    }

    /**
     * Enables indications on given characteristic
     *
     * @return true is the request has been sent, false if one of the arguments was <code>null</code> or the characteristic does not have the CCCD.
     */
    protected final boolean enableIndications(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0)
            return false;

        Log.d(TAG, "gatt.setCharacteristicNotification(" + characteristic.getUuid() + ", true)");
        gatt.setCharacteristicNotification(characteristic, true);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            Log.v(TAG, "Enabling indications for " + characteristic.getUuid());
            Log.d(TAG, "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x02-00)");
            return gatt.writeDescriptor(descriptor);
        }
        return false;
    }

    /**
     * Sends the read request to the given characteristic.
     *
     * @param characteristic the characteristic to read
     * @return true if request has been sent
     */
    protected final boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
            return false;

        Log.v(TAG, "Reading characteristic " + characteristic.getUuid());
        Log.d(TAG, "gatt.readCharacteristic(" + characteristic.getUuid() + ")");
        return gatt.readCharacteristic(characteristic);
    }

    /**
     * Writes the characteristic value to the given characteristic.
     *
     * @param characteristic the characteristic to write to
     * @return true if request has been sent
     */
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

    /**
     * Reads the battery level from the device.
     *
     * @return true if request has been sent
     */
    public final boolean readBatteryLevel() {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null)
            return false;

        final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
        if (batteryService == null)
            return false;

        final BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
        if (batteryLevelCharacteristic == null)
            return false;

        // Check characteristic property
        final int properties = batteryLevelCharacteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            return setBatteryNotifications(true);
        }

        Log.d(TAG, "Reading battery level...");
        return readCharacteristic(batteryLevelCharacteristic);
    }

    /**
     * This method tries to enable notifications on the Battery Level characteristic.
     *
     * @param enable <code>true</code> to enable battery notifications, false to disable
     * @return true if request has been sent
     */
    public boolean setBatteryNotifications(final boolean enable) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null) {
            return false;
        }

        final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
        if (batteryService == null)
            return false;

        final BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
        if (batteryLevelCharacteristic == null)
            return false;

        // Check characteristic property
        final int properties = batteryLevelCharacteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            return false;

        gatt.setCharacteristicNotification(batteryLevelCharacteristic, enable);
        final BluetoothGattDescriptor descriptor = batteryLevelCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            if (enable) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                Log.d(TAG, "Enabling battery level notifications...");
                Log.v(TAG, "Enabling notifications for " + BATTERY_LEVEL_CHARACTERISTIC);
                Log.d(TAG, "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x01-00)");
            } else {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                Log.d(TAG, "Disabling battery level notifications...");
                Log.v(TAG, "Disabling notifications for " + BATTERY_LEVEL_CHARACTERISTIC);
                Log.d(TAG, "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x00-00)");
            }
            return gatt.writeDescriptor(descriptor);
        }
        return false;
    }

    protected String pairingVariantToString(final int variant) {
        switch (variant) {
            case PAIRING_VARIANT_PIN:
                return "PAIRING_VARIANT_PIN";
            case PAIRING_VARIANT_PASSKEY:
                return "PAIRING_VARIANT_PASSKEY";
            case PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                return "PAIRING_VARIANT_PASSKEY_CONFIRMATION";
            case PAIRING_VARIANT_CONSENT:
                return "PAIRING_VARIANT_CONSENT";
            case PAIRING_VARIANT_DISPLAY_PASSKEY:
                return "PAIRING_VARIANT_DISPLAY_PASSKEY";
            case PAIRING_VARIANT_DISPLAY_PIN:
                return "PAIRING_VARIANT_DISPLAY_PIN";
            case PAIRING_VARIANT_OOB_CONSENT:
                return "PAIRING_VARIANT_OOB_CONSENT";
            default:
                return "UNKNOWN";
        }
    }

    protected String bondStateToString(final int state) {
        switch (state) {
            case BluetoothDevice.BOND_NONE:
                return "BOND_NONE";
            case BluetoothDevice.BOND_BONDING:
                return "BOND_BONDING";
            case BluetoothDevice.BOND_BONDED:
                return "BOND_BONDED";
            default:
                return "UNKNOWN";
        }
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

    /**
     * Converts the connection state to String value
     *
     * @param state the connection state
     * @return state as String
     */
    protected String stateToString(final int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "DISCONNECTING";
            default:
                return "DISCONNECTED";
        }
    }

    protected static final class Request {
        private final Type type;
        private final BluetoothGattCharacteristic characteristic;
        private final byte[] value;
        private Request(final Type type, final BluetoothGattCharacteristic characteristic) {
            this.type = type;
            this.characteristic = characteristic;
            this.value = null;
        }

        private Request(final Type type, final BluetoothGattCharacteristic characteristic, final byte[] value) {
            this.type = type;
            this.characteristic = characteristic;
            this.value = value;
        }

        public static Request newReadRequest(final BluetoothGattCharacteristic characteristic) {
            return new Request(Type.READ, characteristic);
        }

        public static Request newWriteRequest(final BluetoothGattCharacteristic characteristic, final byte[] value) {
            return new Request(Type.WRITE, characteristic, value);
        }

        public static Request newEnableNotificationsRequest(final BluetoothGattCharacteristic characteristic) {
            return new Request(Type.ENABLE_NOTIFICATIONS, characteristic);
        }

        public static Request newEnableIndicationsRequest(final BluetoothGattCharacteristic characteristic) {
            return new Request(Type.ENABLE_INDICATIONS, characteristic);
        }

        private enum Type {
            WRITE,
            READ,
            ENABLE_NOTIFICATIONS,
            ENABLE_INDICATIONS
        }
    }

    protected abstract class BleManagerGattCallback extends BluetoothGattCallback {
        private Queue<Request> mInitQueue;
        private boolean mInitInProgress;

        /**
         * This method should return <code>true</code> when the gatt device supports the required services.
         *
         * @param gatt the gatt device with services discovered
         * @return <code>true</code> when the device has teh required service
         */
        protected abstract boolean isRequiredServiceSupported(final BluetoothGatt gatt);

        /**
         * This method should return <code>true</code> when the gatt device supports the optional services.
         * The default implementation returns <code>false</code>.
         *
         * @param gatt the gatt device with services discovered
         * @return <code>true</code> when the device has teh optional service
         */
        protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {
            return false;
        }

        /**
         * This method should return a list of requests needed to initialize the profile.
         * Enabling Service Change indications for bonded devices and reading the Battery Level value and enabling Battery Level notifications
         * is handled before executing this queue. The queue should not have requests that are not available, e.g. should not
         * read an optional service when it is not supported by the connected device.
         * <p>This method is called when the services has been discovered and the device is supported (has required service).</p>
         *
         * @param gatt the gatt device with services discovered
         * @return the queue of requests
         */
        protected abstract Queue<Request> initGatt(final BluetoothGatt gatt);

        /**
         * Called then the initialization queue is complete.
         */
        protected void onDeviceReady() {
            mCallbacks.onDeviceReady();
        }

        /**
         * This method should nullify all services and characteristics of the device.
         */
        protected abstract void onDeviceDisconnected();

        /**
         * Callback reporting the result of a characteristic read operation.
         *
         * @param gatt           GATT client invoked {@link BluetoothGatt#readCharacteristic}
         * @param characteristic Characteristic that was read from the associated
         *                       remote device.
         */
        protected void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }

        /**
         * Callback indicating the result of a characteristic write operation.
         * <p/>
         * <p>If this callback is invoked while a reliable write transaction is
         * in progress, the value of the characteristic represents the value
         * reported by the remote device. An application should compare this
         * value to the desired value to be written. If the values don't match,
         * the application must abort the reliable write transaction.
         *
         * @param gatt           GATT client invoked {@link BluetoothGatt#writeCharacteristic}
         * @param characteristic Characteristic that was written to the associated
         *                       remote device.
         */
        protected void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }

        protected void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }

        protected void onCharacteristicIndicated(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }

        private void onError(final String message, final int errorCode) {
            Log.e(TAG, "Error (0x" + Integer.toHexString(errorCode) + "): " + GattError.parse(errorCode));
            mCallbacks.onError(message, errorCode);
        }

        @Override
        public final void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            Log.d(TAG, "[Callback] Connection state changed with status: " + status + " and new state: " + newState + " (" + stateToString(newState) + ")");

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                // Notify the parent activity/service
                Log.i(TAG, "Connected to " + gatt.getDevice().getAddress());
                mConnected = true;
                mCallbacks.onDeviceConnected();

				/*
                 * The onConnectionStateChange event is triggered just after the Android connects to a device.
				 * In case of bonded devices, the encryption is reestablished AFTER this callback is called.
				 * Moreover, when the device has Service Changed indication enabled, and the list of services has changed (e.g. using the DFU),
				 * the indication is received few milliseconds later, depending on the connection interval.
				 * When received, Android will start performing a service discovery operation itself, internally.
				 *
				 * If the mBluetoothGatt.discoverServices() method would be invoked here, if would returned cached services,
				 * as the SC indication wouldn't be received yet.
				 * Therefore we have to postpone the service discovery operation until we are (almost, as there is no such callback) sure, that it had to be handled.
				 * Our tests has shown that 600 ms is enough. It is important to call it AFTER receiving the SC indication, but not necessarily
				 * after Android finishes the internal service discovery.
				 *
				 * NOTE: This applies only for bonded devices with Service Changed characteristic, but to be sure we will postpone
				 * service discovery for all devices.
				 */
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
            } else {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (status != BluetoothGatt.GATT_SUCCESS)
                        Log.w(TAG, "Error: (0x" + Integer.toHexString(status) + "): " + GattError.parseConnectionError(status));

                    onDeviceDisconnected();
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
                    return;
                }

                // TODO Should the disconnect method be called or the connection is still valid? Does this ever happen?
                Log.e(TAG, "Error (0x" + Integer.toHexString(status) + "): " + GattError.parseConnectionError(status));
                mCallbacks.onError(ERROR_CONNECTION_STATE_CHANGE, status);
            }
        }

        @Override
        public final void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services Discovered");
                if (isRequiredServiceSupported(gatt)) {
                    Log.v(TAG, "Primary service found");
                    final boolean optionalServicesFound = isOptionalServiceSupported(gatt);
                    if (optionalServicesFound)
                        Log.v(TAG, "Secondary service found");

                    // Notify the parent activity
                    mCallbacks.onServicesDiscovered(optionalServicesFound);

                    // Obtain the queue of initialization requests
                    mInitInProgress = true;
                    mInitQueue = initGatt(gatt);

                    // When the device is bonded and has Service Changed characteristic, the indications must be enabled first.
                    // In case this method returns true we have to continue in the onDescriptorWrite callback
                    if (ensureServiceChangedEnabled(gatt))
                        return;

                    // We have discovered services, let's start by reading the battery level value. If the characteristic is not readable, try to enable notifications.
                    // If there is no Battery service, proceed with the initialization queue.
                    if (!readBatteryLevel())
                        nextRequest();
                } else {
                    Log.w(TAG, "Device is not supported");
                    mCallbacks.onDeviceNotSupported();
                    disconnect();
                }
            } else {
                Log.e(TAG, "onServicesDiscovered error " + status);
                onError(ERROR_DISCOVERY_SERVICE, status);
            }
        }

        @Override
        public final void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Read Response received from " + characteristic.getUuid() + ", value: " + ParserUtils.parse(characteristic));

                if (isBatteryLevelCharacteristic(characteristic)) {
                    final int batteryValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d(TAG, "Battery level received: " + batteryValue + "%");
                    mCallbacks.onBatteryValueReceived(batteryValue);

                    // The Battery Level value has been read. Let's try to enable Battery Level notifications.
                    // If the Battery Level characteristic does not have the NOTIFY property, proceed with the initialization queue.
                    if (!setBatteryNotifications(true))
                        nextRequest();
                } else {
                    // The value has been read. Notify the manager and proceed with the initialization queue.
                    onCharacteristicRead(gatt, characteristic);
                    nextRequest();
                }
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    Log.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
                    mCallbacks.onError(ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                Log.e(TAG, "onCharacteristicRead error " + status);
                onError(ERROR_READ_CHARACTERISTIC, status);
            }
        }

        @Override
        public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Data written to " + characteristic.getUuid() + ", value: " + ParserUtils.parse(characteristic.getValue()));
                // The value has been written. Notify the manager and proceed with the initialization queue.
                onCharacteristicWrite(gatt, characteristic);
                nextRequest();
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    Log.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
                    mCallbacks.onError(ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                Log.e(TAG, "onCharacteristicRead error " + status);
                onError(ERROR_READ_CHARACTERISTIC, status);
            }
        }

        @Override
        public final void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Data written to descr. " + descriptor.getUuid() + ", value: " + ParserUtils.parse(descriptor));

                if (isServiceChangedCCCD(descriptor)) {
                    Log.d(TAG, "Service Changed notifications enabled");
                    if (!readBatteryLevel())
                        nextRequest();
                } else if (isBatteryLevelCCCD(descriptor)) {
                    final byte[] value = descriptor.getValue();
                    if (value != null && value.length > 0 && value[0] == 0x01) {
                        Log.d(TAG, "Battery Level notifications enabled");
                        nextRequest();
                    } else
                        Log.d(TAG, "Battery Level notifications disabled");
                } else {
                    nextRequest();
                }
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    Log.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
                    mCallbacks.onError(ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                Log.e(TAG, "onDescriptorWrite error " + status);
                onError(ERROR_WRITE_DESCRIPTOR, status);
            }
        }

        @Override
        public final void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            final String data = ParserUtils.parse(characteristic);

            if (isBatteryLevelCharacteristic(characteristic)) {
                Log.i(TAG, "Notification received from " + characteristic.getUuid() + ", value: " + data);
                final int batteryValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                Log.d(TAG, "Battery level received: " + batteryValue + "%");
                mCallbacks.onBatteryValueReceived(batteryValue);
            } else {
                final BluetoothGattDescriptor cccd = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
                final boolean notifications = cccd == null || cccd.getValue() == null || cccd.getValue().length != 2 || cccd.getValue()[0] == 0x01;

                if (notifications) {
                    Log.i(TAG, "Notification received from " + characteristic.getUuid() + ", value: " + data);
                    onCharacteristicNotified(gatt, characteristic);
                } else { // indications
                    Log.i(TAG, "Indication received from " + characteristic.getUuid() + ", value: " + data);
                    onCharacteristicIndicated(gatt, characteristic);
                }
            }
        }

        /**
         * Executes the next initialization request. If the last element from the queue has been executed a {@link #onDeviceReady()} callback is called.
         */
        private void nextRequest() {
            final Queue<Request> requests = mInitQueue;

            // Get the first request from the queue
            final Request request = requests != null ? requests.poll() : null;

            // Are we done?
            if (request == null) {
                if (mInitInProgress) {
                    mInitInProgress = false;
                    onDeviceReady();
                }
                return;
            }

            switch (request.type) {
                case READ: {
                    readCharacteristic(request.characteristic);
                    break;
                }
                case WRITE: {
                    final BluetoothGattCharacteristic characteristic = request.characteristic;
                    characteristic.setValue(request.value);
                    writeCharacteristic(characteristic);
                    break;
                }
                case ENABLE_NOTIFICATIONS: {
                    enableNotifications(request.characteristic);
                    break;
                }
                case ENABLE_INDICATIONS: {
                    enableIndications(request.characteristic);
                    break;
                }
            }
        }
    }
}
