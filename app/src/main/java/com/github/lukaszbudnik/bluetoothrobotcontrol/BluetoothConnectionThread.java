package com.github.lukaszbudnik.bluetoothrobotcontrol;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;

public class BluetoothConnectionThread extends Thread {

    public static char EOT = (char) 3;

    private static final String TAG = "BluetoothConnection";

    private BluetoothDevice peer;
    private BluetoothSocket socket;

    public BluetoothConnectionThread(BluetoothDevice peer) {
            this.peer = peer;
    }

    public void run() {
        createSocket();
        while (true) {
            if (socket.isConnected()) {
                sleep(1);
                continue;
            }
            try {
                // attempt to re-create socket
                createSocket();
                socket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Could not connect to device = " + socket.getRemoteDevice().getName(), e);
            }
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    public void write(Integer code) throws IOException {
        try {
            if (isConnected()) {
                String data = code.toString();
                // Get the message bytes and send it
                byte[] message = (data + EOT).getBytes();
                socket.getOutputStream().write(message);
                Log.i(TAG, "Sent => " + data);
            }
        } catch (IOException e) {
            // handles broken pipe exceptions, happened to me only once
            // suspect there was a HC-06 connection issue so adding it just in case
            cancel();
            throw e;
        }
    }

    public void cancel() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket = " + socket.getRemoteDevice().getName(), e);
        }
    }

    public void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
        }
    }

    private void createSocket() {
        try {
            cancel();
            ParcelUuid[] uuids = peer.getUuids();
            socket = peer.createRfcommSocketToServiceRecord(uuids[0].getUuid());
        } catch (IOException e) {
            Log.e(TAG, "Could not create socket to device = " + peer.getName(), e);
            sleep(5);
        }
    }

}
