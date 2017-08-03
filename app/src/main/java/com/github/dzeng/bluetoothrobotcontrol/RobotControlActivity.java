package com.github.lukaszbudnik.bluetoothrobotcontrol;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RobotControlActivity extends AppCompatActivity {

    private static final String TAG = "RobotControlActivity";

    private static final int REQUEST_ENABLE_BT = 123;

    private static final int RC_SIGN_IN = 456;

    private static final List<String> PEER_DEVICES = Arrays.asList("HC-06");

    private BluetoothConnectionThread bluetoothConnection;

    private VoiceCommandReceiver voiceCommandReceiver;

    private RecordingThread recording;

    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthStateListener;
    private GoogleApiClient googleApiClient;

    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_panel);

        requestQueue = Volley.newRequestQueue(this.getApplicationContext());

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    String uid = user.getUid();
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + uid);
                    DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("/users/" + uid + "/settings");
                    Map<String, String> params = new HashMap<>();
                    params.put("registrationToken", FirebaseInstanceId.getInstance().getToken());
                    databaseReference.setValue(params);
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
            }
        };

        ToggleButton languageToggleButton = (ToggleButton) findViewById(R.id.language);
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.voice_progress);
        recording = new RecordingThread(progressBar, languageToggleButton, requestQueue);
        recording.start();

        voiceCommandReceiver = new VoiceCommandReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MessagingService.VOICE_COMMAND_RECEIVED);
        registerReceiver(voiceCommandReceiver, intentFilter);

        WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            startConnection();
        }

        final List<Integer> ids = Arrays.asList(R.id.n, R.id.ne, R.id.e, R.id.se, R.id.s, R.id.sw, R.id.w, R.id.nw, R.id.rec, R.id.lights);

        for (Integer id : ids) {
            Button button = (Button) findViewById(id);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Integer id = ids.indexOf(view.getId());
                    Log.i(TAG, "Button clicked = " + ((Button) view).getText().toString() + " code = " + id);
                    sendRobotCommand(id);
                }
            });
        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
            }
        }

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                startConnection();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        firebaseAuth.addAuthStateListener(firebaseAuthStateListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (firebaseAuthStateListener != null) {
            firebaseAuth.removeAuthStateListener(firebaseAuthStateListener);
        }
    }


    @Override
    protected void onDestroy() {
        if (bluetoothConnection != null) {
            bluetoothConnection.cancel();
        }
        unregisterReceiver(voiceCommandReceiver);
        super.onDestroy();
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(getApplicationContext(), "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    protected void startConnection() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            Log.i(TAG, "Found paired device: " + device.getName());
            if (PEER_DEVICES.indexOf(device.getName()) >= 0) {
                Log.i(TAG, "Attempting to connect to: " + device.getName());
                bluetoothConnection = new BluetoothConnectionThread(device);
                bluetoothConnection.start();
            }
        }
    }

    private void sendRobotCommand(Integer id) {
        if (bluetoothConnection != null && bluetoothConnection.isConnected()) {
            try {
                bluetoothConnection.write(id);
            } catch (IOException e) {
                Log.e(TAG, "Error while sending messages to robot", e);
                Toast.makeText(getApplicationContext(), "Error while sending messages to robot", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Could not connect to robot. Check connection.", Toast.LENGTH_SHORT).show();
        }
    }

    private class VoiceCommandReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context ctx, Intent intent) {
            int code = intent.getIntExtra("code", 0);
            sendRobotCommand(code);
        }

    }
}
