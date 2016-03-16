package com.codingbad.com.bluetoothtest.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.codingbad.com.bluetoothtest.R;
import com.codingbad.com.bluetoothtest.model.BluetoothDeviceWithStrength;

/**
 * Created by Ayelen Chavez on 07.03.16.
 */
public class BluetoothDeviceView extends LinearLayout {

    private TextView name;
    private TextView address;
    private TextView strength;

    public BluetoothDeviceView(Context context) {
        super(context);
        init();
    }

    public BluetoothDeviceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BluetoothDeviceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_bluetooth_device, this);
        name = (TextView) findViewById(R.id.bluetooth_name);
        address = (TextView) findViewById(R.id.bluetooth_address);
        strength = (TextView) findViewById(R.id.bluetooth_strength);
    }

    public void fill(BluetoothDeviceWithStrength bluetoothDevice) {
        // fill bluetoothDevice UI
        name.setText(bluetoothDevice.getName());
        address.setText(bluetoothDevice.getAddress());
        strength.setText("Strength: " + bluetoothDevice.getStrength());
    }

}

