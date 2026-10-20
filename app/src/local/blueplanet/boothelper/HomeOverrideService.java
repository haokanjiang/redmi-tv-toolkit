package com.redmi.tvtoolkit;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class HomeOverrideService extends AccessibilityService {
    private static final String TAG="RedmiTvToolkit";
    private final HomeOverrideRetry retry=new HomeOverrideRetry();
    private String lastPackage="", lastClass="";
    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo i=new AccessibilityServiceInfo();
        i.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        i.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC; i.flags=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; i.notificationTimeout=50; setServiceInfo(i);
        Log.i(TAG,"HomeOverrideService bound profile="+DeviceProfile.detect().id);
    }
    @Override public boolean onKeyEvent(KeyEvent e) {
        if(e.getAction()!=KeyEvent.ACTION_UP || (e.getKeyCode()!=KeyEvent.KEYCODE_HOME && e.getKeyCode()!=KeyEvent.KEYCODE_ALL_APPS)) return false;
        handle("key", "", "KEY_"+e.getKeyCode()); return false;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if(e==null) return; CharSequence p=e.getPackageName(), c=e.getClassName(); String pkg=p==null?"":p.toString(), cls=c==null?"":c.toString();
        if(pkg.equals(lastPackage)&&cls.equals(lastClass)&&e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
        lastPackage=pkg; lastClass=cls;
        DeviceProfile prof=DeviceProfile.detect(); HomeOverrideConfig cfg=HomeOverrideConfig.load(this);
        if(!prof.homeOverride||!cfg.enabled)return;
        if(pkg.equals(cfg.target.split("/")[0])) { retry.clear(); Log.d(TAG,"foreground target "+pkg+"/"+cls); return; }
        if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if(prof.isSystemHome(pkg,cls)) handle("window",pkg,cls);
        }
    }
    private void handle(String source,String pkg,String cls) {
        HomeOverrideConfig cfg=HomeOverrideConfig.load(this); if(!cfg.enabled||cfg.target.length()==0){Log.d(TAG,"ignore disabled");return;}
        long now=SystemClock.elapsedRealtime(); boolean allowed=retry.allow(now,false); Log.i(TAG,"event source="+source+" package="+pkg+" class="+cls+" time="+now+" target="+cfg.target+" allowed="+allowed);
        if(!allowed)return;
        try { Intent in=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_DEFAULT).addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER); in.setComponent(ComponentName.unflattenFromString(cfg.target)); in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP); startActivity(in); Log.i(TAG,"launch result=success"); }
        catch(Throwable ex){ Log.e(TAG,"target launch failed",ex); if(cfg.fallback.length()>0) try { Intent f=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_DEFAULT); f.setComponent(ComponentName.unflattenFromString(cfg.fallback)); f.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); startActivity(f); Log.i(TAG,"fallback launch result=success"); } catch(Throwable fex){ Log.e(TAG,"fallback launch failed; disabling override",fex); HomeOverrideConfig.disableOnFailure(this); RecoveryActions.removeHomeService(this); } }
    }
    @Override public void onInterrupt() {}
}
