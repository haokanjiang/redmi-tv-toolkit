package com.redmi.tvtoolkit;
import android.content.*;
import android.os.Bundle;
import android.util.Log;
public final class ToolkitControlReceiver extends BroadcastReceiver {
    public static final String SET="com.redmi.tvtoolkit.action.HOME_SET", RESTORE="com.redmi.tvtoolkit.action.HOME_RESTORE", STATUS="com.redmi.tvtoolkit.action.HOME_STATUS";
    @Override public void onReceive(Context c, Intent i) {
        if(i==null)return; String a=i.getAction(); if(!authorized())return;
        if(SET.equals(a)){ boolean ok=HomeOverrideConfig.set(c,i.getStringExtra("target"),i.getStringExtra("fallback")); if(ok)RecoveryActions.enableHomeService(c); Log.i("RedmiTvToolkit","set target="+ok); }
        else if(RESTORE.equals(a)){HomeOverrideConfig.restore(c);RecoveryActions.removeHomeService(c);}
        else if(STATUS.equals(a)){HomeOverrideConfig x=HomeOverrideConfig.load(c); String data="enabled="+x.enabled+";recovery="+x.recoveryEnabled+";target="+x.target+";fallback="+x.fallback; setResultData(data); Log.i("RedmiTvToolkit","status "+data);}
    }
    private boolean authorized(){int u=android.os.Binder.getCallingUid();return u==0||u==2000||u==android.os.Process.myUid();}
}
