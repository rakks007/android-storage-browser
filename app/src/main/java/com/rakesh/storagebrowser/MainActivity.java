package com.rakesh.storagebrowser;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView statusText, urlText, pinText;
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
            statusText.setText(running ? "Sharing is active" : "Sharing is stopped");
            urlText.setText(running ? i.getStringExtra("url") : "The connection address will appear here.");
            pinText.setText(running ? "PIN: " + i.getStringExtra("pin") : "PIN will appear while sharing is active.");
            start.setEnabled(!running); stop.setEnabled(running);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        statusText=findViewById(R.id.statusText);
        urlText=findViewById(R.id.urlText); pinText=findViewById(R.id.pinText);
        start=findViewById(R.id.startServer); stop=findViewById(R.id.stopServer);
        start.setOnClickListener(v->startSharing());
        stop.setOnClickListener(v->stopService(new Intent(this,FileServerService.class)));
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    }
    private void startSharing(){
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
