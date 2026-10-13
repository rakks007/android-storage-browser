package com.rakesh.storagebrowser;

import android.Manifest;
import android.app.Dialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String SETTINGS_NAME = "settings";
    private static final String PIN_KEY = "accessPin";
    private TextView statusText, urlText, pinText, fingerprintText;
    private Button start, stop;
    private final ActivityResultLauncher<Intent> accessSettings = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (hasStorageAccess()) startServer();
            else Toast.makeText(this, "Allow access to manage all files to browse storage", Toast.LENGTH_LONG).show();
        });
    private final ActivityResultLauncher<String> storagePermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startServer();
            else Toast.makeText(this, "Storage access is required to browse files", Toast.LENGTH_LONG).show();
        });

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!FileServerService.ACTION_STATUS.equals(i.getAction())) return;
            boolean running = i.getBooleanExtra("running", false);
            String error = i.getStringExtra("error");
            statusText.setText(running ? "Sharing is active" : error == null || error.isEmpty() ? "Sharing is stopped" : "Server error: " + error);
            urlText.setText(running ? i.getStringExtra("url") : "The connection address will appear here.");
            pinText.setText(running ? "PIN: " + i.getStringExtra("pin") : "PIN will appear while sharing is active.");
            fingerprintText.setText(running ? "Certificate SHA-256:\n" + i.getStringExtra("fingerprint") : "Certificate fingerprint will appear while sharing is active.");
            start.setEnabled(!running); stop.setEnabled(running);
            if (!running && error != null && !error.isEmpty()) Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        statusText=findViewById(R.id.statusText);
        urlText=findViewById(R.id.urlText); pinText=findViewById(R.id.pinText);
        fingerprintText=findViewById(R.id.fingerprintText);
        start=findViewById(R.id.startServer); stop=findViewById(R.id.stopServer);
        requireAppUnlock();
        start.setOnClickListener(v->startSharing());
        stop.setOnClickListener(v->stopService(new Intent(this,FileServerService.class)));
    }
    private void requireAppUnlock() {
        String configuredPin = getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE).getString(PIN_KEY, null);
        if (configuredPin == null || !configuredPin.matches("\\d{6}")) {
            requestNotificationPermission();
            return;
        }

        EditText pinInput = new EditText(this);
        pinInput.setHint("6-digit PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        pinInput.setSingleLine(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("App locked")
            .setMessage("Enter your configured PIN to access Storage Browser.")
            .setView(pinInput)
            .setPositiveButton("Unlock", null)
            .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (configuredPin.equals(pinInput.getText().toString())) {
                dialog.dismiss();
                requestNotificationPermission();
            } else {
                pinInput.setError("Incorrect PIN");
                pinInput.setText("");
            }
        }));
        dialog.show();
    }
    private void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    }
    private void startSharing(){
        String configuredPin = getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE).getString(PIN_KEY, null);
        if (configuredPin != null && configuredPin.matches("\\d{6}")) {
            requestStorageAccess();
            return;
        }

        EditText pinInput = new EditText(this);
        pinInput.setHint("6-digit PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        pinInput.setSingleLine(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Set app lock PIN")
            .setMessage("Choose a 6-digit PIN to protect the app. A separate server PIN will be generated when sharing starts.")
            .setView(pinInput)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Start sharing", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = pinInput.getText().toString();
            if (!value.matches("\\d{6}")) {
                pinInput.setError("Enter exactly 6 digits");
                return;
            }
            getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE).edit().putString(PIN_KEY, value).apply();
            dialog.dismiss();
            requestStorageAccess();
        }));
        dialog.show();
    }
    private void requestStorageAccess(){
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Android 10 does not support broad storage access for this app", Toast.LENGTH_LONG).show();
        } else if (hasStorageAccess()) {
            startServer();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            try {
                accessSettings.launch(intent);
            } catch (android.content.ActivityNotFoundException e) {
                accessSettings.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }
    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) return false;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ? Environment.isExternalStorageManager()
            : checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    private void startServer() {
        Intent i=new Intent(this,FileServerService.class);
        startForegroundService(i);
    }
    @Override protected void onStart(){super.onStart();registerReceiver(receiver,new IntentFilter(FileServerService.ACTION_STATUS),Context.RECEIVER_NOT_EXPORTED);sendBroadcast(new Intent(FileServerService.ACTION_QUERY).setPackage(getPackageName()));}
    @Override protected void onStop(){super.onStop();unregisterReceiver(receiver);}
}
