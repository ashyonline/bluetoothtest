package com.codingbad.com.bluetoothtest;

import android.app.Application;
import android.content.Context;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Created by Ayelen Chavez on 07.03.16.
 */
public class BluetoothTestApplication extends Application {

    private static final Injector INJECTOR = Guice.createInjector(new AppModule());
    private static BluetoothTestApplication appContext;

    public static Context getAppContext() {
        return appContext;
    }

    public static Context getContext() {
        return appContext.getBaseContext();
    }

    public static void injectMembers(final Object object) {
        INJECTOR.injectMembers(object);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;
        injectMembers(this);
    }
}
