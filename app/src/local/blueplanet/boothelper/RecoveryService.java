package com.redmi.tvtoolkit;
import android.app.Service;import android.content.Intent;import android.os.IBinder;
public final class RecoveryService extends Service { @Override public int onStartCommand(Intent i,int f,int id){RecoveryActions.run(this);stopSelf(id);return START_NOT_STICKY;} @Override public IBinder onBind(Intent i){return null;} }
