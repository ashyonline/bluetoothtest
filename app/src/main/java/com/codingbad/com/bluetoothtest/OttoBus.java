package com.codingbad.com.bluetoothtest;

import android.os.Handler;
import android.os.Looper;

import com.google.inject.Singleton;
import com.squareup.otto.Bus;

/**
 * Created by Ayelen Chavez on 07.03.16.
 */

@Singleton
public class OttoBus extends Bus {
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    public OttoBus() {
    }

    public void post(final Object event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            super.post(event);
        } else {
            this.mainThread.post(new Runnable() {
                public void run() {
                    OttoBus.super.post(event);
                }
            });
        }

    }
}