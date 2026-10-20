package com.redmi.tvtoolkit;
public final class HomeOverrideRetry {
    public static final int MAX_ATTEMPTS=3; public static final long WINDOW_MS=5000L, MIN_INTERVAL_MS=250L;
    private int attempts; private long first=-1,last=-1;
    public synchronized boolean allow(long now, boolean targetForeground) { if(targetForeground)return false; if(first<0||now-first>WINDOW_MS){first=now;attempts=0;last=-1;} if(attempts>=MAX_ATTEMPTS || (last>=0&&now-last<MIN_INTERVAL_MS))return false; attempts++;last=now;return true; }
    public synchronized void clear(){attempts=0;first=-1;last=-1;}
}
