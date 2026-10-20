package com.redmi.tvtoolkit;
import android.app.Activity;import android.os.Bundle;
public final class ControlActivity extends Activity { @Override protected void onCreate(Bundle b){super.onCreate(b);String a=getIntent().getStringExtra("action");if("repair".equals(a))RecoveryActions.run(this);if("restore".equals(a)){HomeOverrideConfig.restore(this);RecoveryActions.removeHomeService(this);}finish();} }
