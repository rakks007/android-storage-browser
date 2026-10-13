package com.rakesh.storagebrowser;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView folderText, statusText, urlText, pinText;
    private Button start, stop;
    private Uri folderUri;

    private final ActivityResultLauncher<Uri> picker = registerForActivityResult(
        new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri == null) return;
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (SecurityException ignored) {}
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("folderUri", uri.toString()).apply();
            folderUri = uri; folderText.setText(uri.toString());
        });

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!FileServerService.ACTION_STATUS.equals(i.getAction())) return;
            boolean running = i.getBooleanExtra("running", false);
            statusText.setText(running ? "Status: sharing" : "Status: stopped");
            urlText.setText(running ? "Open on another device: " + i.getStringExtra("url") : "");
            pinText.setText(running ? "PIN: " + i.getStringExtra("pin") : "");
            start.setEnabled(!running); stop.setEnabled(running);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        folderText=findViewById(R.id.folderText); statusText=findViewById(R.id.statusText);
        urlText=findViewById(R.id.urlText); pinText=findViewById(R.id.pinText);
        start=findViewById(R.id.startServer); stop=findViewById(R.id.stopServer);
        String saved=getSharedPreferences("settings",MODE_PRIVATE).getString("folderUri",null);
        if(saved!=null){folderUri=Uri.parse(saved);folderText.setText(saved);}
        findViewById(R.id.selectFolder).setOnClickListener(v->picker.launch(folderUri));
        start.setOnClickListener(v->startSharing());
        stop.setOnClickListener(v->stopService(new Intent(this,FileServerService.class)));
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    }
    private void startSharing(){
        if(folderUri==null){Toast.makeText(this,"Select a folder first",Toast.LENGTH_SHORT).show();return;}
        Intent i=new Intent(this,FileServerService.class).putExtra("folderUri",folderUri.toString());
        startForegroundService(i);
    }
    @Override protected void onStart(){super.onStart();registerReceiver(receiver,new IntentFilter(FileServerService.ACTION_STATUS),Context.RECEIVER_NOT_EXPORTED);sendBroadcast(new Intent(FileServerService.ACTION_QUERY).setPackage(getPackageName()));}
    @Override protected void onStop(){super.onStop();unregisterReceiver(receiver);}
}
