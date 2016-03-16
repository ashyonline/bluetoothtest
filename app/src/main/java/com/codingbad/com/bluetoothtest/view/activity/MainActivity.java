package com.codingbad.com.bluetoothtest.view.activity;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.codingbad.com.bluetoothtest.BluetoothTestApplication;
import com.codingbad.com.bluetoothtest.R;
import com.codingbad.com.bluetoothtest.model.BluetoothDeviceWithStrength;
import com.codingbad.com.bluetoothtest.mvp.presenter.MainPresenter;
import com.codingbad.com.bluetoothtest.mvp.view.IMainView;
import com.codingbad.com.bluetoothtest.view.BluetoothDevicesAdapter;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import no.nordicsemi.android.support.v18.scanner.ScanResult;

/**
 * Main Activity includes all UI
 * <p/>
 * A switch to scan bluetooth and the list of known devices.
 * <p/>
 * When connected, shows options to disconnect from the connected bluetooth or send a command.
 */
public class MainActivity extends Activity implements BluetoothDevicesAdapter.RecyclerViewListener, IMainView {

    // user for log purposes
    private static final String TAG = MainActivity.class.toString();

    // UI binded using Butterknife
    @Bind(R.id.bluetooth_list)
    protected RecyclerView scannedBluetoothList;
    @Bind(R.id.connected_layout)
    protected LinearLayout connectionLayout;
    @Bind(R.id.connected_device_name)
    protected TextView connectedDeviceName;
    @Bind(R.id.message)
    protected EditText userMessage;

    protected MainPresenter mainPresenter;

    // recycler view adapter
    private BluetoothDevicesAdapter bluetoothDevicesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothTestApplication.injectMembers(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mainPresenter = new MainPresenter(this);
        mainPresenter.attachView(this);
        setUpBlueToothList();
    }

    /**
     * Set up recycler view to list bluetooth devices
     */
    private void setUpBlueToothList() {
        scannedBluetoothList.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        scannedBluetoothList.setLayoutManager(layoutManager);

        bluetoothDevicesAdapter = new BluetoothDevicesAdapter(this);
        bluetoothDevicesAdapter.addItemList(new ArrayList<BluetoothDeviceWithStrength>());
        scannedBluetoothList.setAdapter(bluetoothDevicesAdapter);
    }

    /**
     * When one of the devices is clicked, connect to it
     *
     * @param view
     * @param position
     */
    @Override
    public void onItemClickListener(View view, int position) {
        mainPresenter.stopBluetoothScan();
        BluetoothDeviceWithStrength selectedDevice = bluetoothDevicesAdapter.getItemAtPosition(position);
        mainPresenter.onDeviceSelected(selectedDevice.getDevice(), selectedDevice.getName());
    }

    /**
     * Start and stop scanning
     *
     * @param buttonView
     * @param isChecked
     */
    @OnCheckedChanged(R.id.scan_button)
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            mainPresenter.startBluetoothScan();
        } else {
            mainPresenter.stopBluetoothScan();
            bluetoothDevicesAdapter.removeAll();
        }
    }

    /**
     * Disconnect from binder
     */
    @OnClick(R.id.disconnect_button)
    public void onConnectClicked() {
        mainPresenter.onConnectionClicked();
        connectionLayout.setVisibility(View.GONE);
    }

    /**
     * Hide soft keyboard
     */
    public void hideSoftKeyboard() {
        if (getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @OnClick(R.id.send_button)
    public void onSendClicked() {
        hideSoftKeyboard();
        String message = userMessage.getText().toString();
        mainPresenter.onSendClicked(message);
    }

    @Override
    public void updateScanResults(List<ScanResult> results) {
        bluetoothDevicesAdapter.update(results);
    }

    @Override
    public void onServiceConnected(String deviceName) {
        // show disconnect button
        connectionLayout.setVisibility(View.VISIBLE);

        // show user the current device we are connected to
        connectedDeviceName.setText(getString(R.string.connected_device, deviceName));
    }
}
