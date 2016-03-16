package com.codingbad.com.bluetoothtest.mvp.view;

import java.util.List;

import no.nordicsemi.android.support.v18.scanner.ScanResult;

/**
 * Created by Ayelen Chavez on 16.03.16.
 */
public interface IMainView {
    void updateScanResults(List<ScanResult> results);

    void onServiceConnected(String deviceName);
}
