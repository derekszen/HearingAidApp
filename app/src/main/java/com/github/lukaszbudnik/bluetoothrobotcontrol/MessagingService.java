package com.github.lukaszbudnik.bluetoothrobotcontrol;

import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MessagingService extends FirebaseMessagingService {

    public static final String VOICE_COMMAND_RECEIVED = "lukaszbudnik.robot.VOICE_COMMAND_RECEIVED";

    private static final String TAG = "MessagingService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Notification Message Body: " + remoteMessage.getData().get("code"));
        Intent intent = new Intent(VOICE_COMMAND_RECEIVED);
        intent.putExtra("code", new Integer(remoteMessage.getData().get("code")));
        sendBroadcast(intent);
    }
}
