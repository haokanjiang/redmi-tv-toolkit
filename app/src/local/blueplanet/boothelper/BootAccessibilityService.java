package com.redmi.tvtoolkit;
import android.accessibilityservice.AccessibilityService;import android.view.accessibility.AccessibilityEvent;
public final class BootAccessibilityService extends AccessibilityService { @Override protected void onServiceConnected(){if(!DeviceProfile.detect().blueplanet)disableSelf();} @Override public void onAccessibilityEvent(AccessibilityEvent e){} @Override public void onInterrupt(){} }
