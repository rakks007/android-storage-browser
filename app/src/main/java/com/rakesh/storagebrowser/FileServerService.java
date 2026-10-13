package com.rakesh.storagebrowser;

import android.app.*;
import android.content.*;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class FileServerService extends Service {
    public static final String ACTION_STATUS="com.rakesh.storagebrowser.STATUS";
    public static final String ACTION_QUERY="com.rakesh.storagebrowser.QUERY";
    private static final long IDLE_CHECK_INTERVAL_MS=30_000;
    private HttpFileServer server; private String url="", pin="", startupError="";
    private final Handler idleHandler=new Handler(Looper.getMainLooper());
    private final Runnable idleCheck=new Runnable(){@Override public void run(){if(server!=null&&server.isIdle()){stopSelf();return;}if(server!=null)idleHandler.postDelayed(this,IDLE_CHECK_INTERVAL_MS);}};
    private final BroadcastReceiver queryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){broadcast(server!=null);}};
    @Override public void onCreate(){super.onCreate();createChannel();registerReceiver(queryReceiver,new IntentFilter(ACTION_QUERY),Context.RECEIVER_NOT_EXPORTED);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        startForeground(7,new NotificationCompat.Builder(this,"share").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Storage sharing active").setContentText("Tap Stop in the app when finished").setOngoing(true).build());
        try{if(server!=null)server.close();idleHandler.removeCallbacks(idleCheck);server=new HttpFileServer(this);server.start();url="http://"+server.getLanAddress()+":"+server.getPort();pin=server.getPin();startupError="";broadcast(true);idleHandler.postDelayed(idleCheck,IDLE_CHECK_INTERVAL_MS);}catch(Exception e){android.util.Log.e("StorageBrowser","Unable to start HTTP file server",e);startupError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();broadcast(false);stopSelf();}
        return START_NOT_STICKY;
    }
    private void broadcast(boolean running){sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName()).putExtra("running",running).putExtra("url",url).putExtra("pin",pin).putExtra("error",running?"":startupError));}
    private void createChannel(){if(android.os.Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("share","Storage sharing",NotificationManager.IMPORTANCE_LOW));}
    @Override public void onDestroy(){idleHandler.removeCallbacks(idleCheck);if(server!=null)server.close();unregisterReceiver(queryReceiver);broadcast(false);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
