package com.rakesh.storagebrowser;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class FileServerService extends Service {
    public static final String ACTION_STATUS="com.rakesh.storagebrowser.STATUS";
    public static final String ACTION_QUERY="com.rakesh.storagebrowser.QUERY";
    private HttpFileServer server; private String url="", pin="";
    private final BroadcastReceiver queryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){broadcast(server!=null);}};
    @Override public void onCreate(){super.onCreate();createChannel();registerReceiver(queryReceiver,new IntentFilter(ACTION_QUERY),Context.RECEIVER_NOT_EXPORTED);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String value=intent==null?null:intent.getStringExtra("folderUri"); if(value==null)return START_NOT_STICKY;
        startForeground(7,new NotificationCompat.Builder(this,"share").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Storage sharing active").setContentText("Tap Stop in the app when finished").setOngoing(true).build());
        try{if(server!=null)server.close();server=new HttpFileServer(this,Uri.parse(value),8787);server.start();url="http://"+server.getLanAddress()+":8787";pin=server.getPin();broadcast(true);}catch(Exception e){broadcast(false);stopSelf();}
        return START_NOT_STICKY;
    }
    private void broadcast(boolean running){sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName()).putExtra("running",running).putExtra("url",url).putExtra("pin",pin));}
    private void createChannel(){if(android.os.Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("share","Storage sharing",NotificationManager.IMPORTANCE_LOW));}
    @Override public void onDestroy(){if(server!=null)server.close();unregisterReceiver(queryReceiver);broadcast(false);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
