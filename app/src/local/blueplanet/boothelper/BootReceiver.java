package com.redmi.tvtoolkit;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;
public final class BootReceiver extends BroadcastReceiver { @Override public void onReceive(Context c, Intent i){ RecoveryActions.run(c); } }
