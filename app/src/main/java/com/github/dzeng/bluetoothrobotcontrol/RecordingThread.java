package com.github.lukaszbudnik.bluetoothrobotcontrol;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RecordingThread extends Thread {

    private static final String TAG = "Recording";

    private static final int RECORDING_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            RECORDING_RATE, CHANNEL, FORMAT);

    private final ProgressBar progressBar;
    private final ToggleButton languageToggleButton;
    private final RequestQueue requestQueue;
    private final AudioRecord recorder;

    private long startOfIteration = 0;
    private long currentIteration = 0;
    private long iterationLenght = 1500;

    public RecordingThread(ProgressBar progressBar, ToggleButton languageToggleButton, RequestQueue requestQueue) {
        this.progressBar = progressBar;
        this.requestQueue = requestQueue;
        this.languageToggleButton = languageToggleButton;
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 100);
    }

    public void run() {
        // wait until user signs in
        while (FirebaseAuth.getInstance().getCurrentUser() == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
        recorder.startRecording();

        startOfIteration = System.currentTimeMillis();
        byte[] buffer = new byte[BUFFER_SIZE];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
            currentIteration = System.currentTimeMillis() - startOfIteration;
            int read = recorder.read(buffer, 0, buffer.length);
            baos.write(buffer, 0, read);
            if (currentIteration > iterationLenght) {
                currentIteration = 0;
                startOfIteration = System.currentTimeMillis();

                final String language = languageToggleButton.getText().toString();
                final FirebaseStorage storage = FirebaseStorage.getInstance();
                StorageReference storageRef = storage.getReferenceFromUrl("gs://" + storage.getApp().getOptions().getStorageBucket());

                final String aid = UUID.randomUUID().toString();
                final String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                StorageReference recordingRef = storageRef.child("users/" + uid + "/audio/" + aid + ".raw");

                Log.d(TAG, "About to publish " + language + " recording: " + recordingRef);

                UploadTask uploadTask = recordingRef.putBytes(baos.toByteArray());
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.e(TAG, "Failed to upload to GS", exception);
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Uri downloadUrl = taskSnapshot.getDownloadUrl();
                        Log.i(TAG, "Recording uploaded & available at " + downloadUrl);
                        sendToQueue(storage.getApp().getOptions().getStorageBucket(), uid, aid, language);
                    }
                });
                baos.reset();
            }
            progressBar.setProgress((int) (currentIteration * 100 / iterationLenght));
        }
    }

    private void sendToQueue(String project, String uid, String aid, String language) {
        String url = "https://speech-recognition-dot-" + project + "/recognize";
        Map<String, String> params = new HashMap<>();
        params.put("uid", uid);
        params.put("aid", aid);
        params.put("lang", language);

        JSONObject jsonBody = new JSONObject(params);
        JsonObjectRequest postRequest = new JsonObjectRequest(url, jsonBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("Response", response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Error.Response", error.getMessage(), error);
                    }
                }
        );
        requestQueue.add(postRequest);
    }
}
