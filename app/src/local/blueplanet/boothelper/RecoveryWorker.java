package com.redmi.tvtoolkit;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.BroadcastReceiver.PendingResult;
public final class RecoveryWorker extends Thread { private final Context c; private final PendingResult p; RecoveryWorker(Context c,PendingResult p){this.c=c;this.p=p;} public void run(){try{RecoveryActions.run(c);}finally{if(p!=null)p.finish();}} }
