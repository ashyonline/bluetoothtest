package com.codingbad.com.bluetoothtest;

import android.content.Context;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;

/**
 * Created by Ayelen Chavez on 07.03.16.
 * <p/>
 * Module used for Guice configuration to inject objects
 * <p/>
 * Mostly boilerplate code.
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Context.class).toProvider(new Provider<Context>() {
            @Override
            public Context get() {
                return BluetoothTestApplication.getContext();
            }
        });
        bind(BluetoothTestApplication.class).toProvider(new Provider<BluetoothTestApplication>() {
            @Override
            public BluetoothTestApplication get() {
                return (BluetoothTestApplication) BluetoothTestApplication.getAppContext();
            }
        });
    }
}