package com.redmi.tvtoolkit;
import android.app.Activity;import android.os.Bundle;import android.widget.TextView;
public final class MainActivity extends Activity { @Override protected void onCreate(Bundle b){super.onCreate(b);DeviceProfile p=DeviceProfile.detect();HomeOverrideConfig c=HomeOverrideConfig.load(this);TextView v=new TextView(this);v.setTextSize(22f);v.setPadding(48,48,48,48);v.setText("Redmi TV Toolkit\nProfile: "+p.id+"\nOverride: "+c.enabled+"\nTarget: "+c.target);setContentView(v);} }
