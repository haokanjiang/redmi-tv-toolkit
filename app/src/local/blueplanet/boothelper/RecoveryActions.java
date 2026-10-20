package com.redmi.tvtoolkit;
import android.content.Context;
final class RecoveryActions {
    static final String TAG="RedmiTvToolkit";
    static final String HOME_SERVICE="com.redmi.tvtoolkit/com.redmi.tvtoolkit.HomeOverrideService";
    private RecoveryActions(){}
    static void run(Context c){DeviceProfile p=DeviceProfile.detect(); if(!p.supported)return; HomeOverrideConfig x=HomeOverrideConfig.load(c); if(p.homeOverride&&x.enabled&&x.recoveryEnabled)enableHomeService(c);}
    static void prepareSoftWakeNow(Context c){run(c);} static void ensureNetworkAdbNow(Context c){}
    static void enableHomeService(Context c){try{String r=android.provider.Settings.Secure.getString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);java.util.LinkedHashSet<String>s=new java.util.LinkedHashSet<>();if(r!=null&&!r.isEmpty()&&!r.equals("null"))for(String x:r.split(":"))if(!x.isEmpty())s.add(x);s.add(HOME_SERVICE);android.provider.Settings.Secure.putString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,android.text.TextUtils.join(":",s));android.provider.Settings.Secure.putInt(c.getContentResolver(),android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,1);}catch(Throwable e){android.util.Log.e(TAG,"enable failed",e);}}
    static void removeHomeService(Context c){try{String r=android.provider.Settings.Secure.getString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);java.util.LinkedHashSet<String>s=new java.util.LinkedHashSet<>();if(r!=null&&!r.isEmpty()&&!r.equals("null"))for(String x:r.split(":"))if(!x.equals(HOME_SERVICE)&&!x.isEmpty())s.add(x);android.provider.Settings.Secure.putString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,android.text.TextUtils.join(":",s));if(s.isEmpty())android.provider.Settings.Secure.putInt(c.getContentResolver(),android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,0);}catch(Throwable e){android.util.Log.e(TAG,"remove failed",e);}}
}
