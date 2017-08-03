package com.github.lukaszbudnik.bluetoothrobotcontrol;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;


public class FirebaseService extends FirebaseInstanceIdService {

    private static final String TAG = "FirebaseService";

    private String firebaseToken;

    @Override
    public void onTokenRefresh() {
        firebaseToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + firebaseToken);
    }
}
