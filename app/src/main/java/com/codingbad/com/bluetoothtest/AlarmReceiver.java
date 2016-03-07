package com.codingbad.com.bluetoothtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Ayelen Chavez on 07.03.16.
 */
public class AlarmReceiver extends BroadcastReceiver {
    public static final int REQUEST_CODE = 12345;

    // Triggered by the Alarm periodically (starts the service to run task that scans bluetooth)
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, ScanBluetoothService.class);
        context.startService(i);
    }
}