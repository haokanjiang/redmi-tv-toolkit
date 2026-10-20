package com.redmi.tvtoolkit;

import java.util.*;
import static com.redmi.tvtoolkit.HomeOverrideRetry.Action.*;
import static com.redmi.tvtoolkit.HomeOverrideRetry.Foreground.SYSTEM;
import static com.redmi.tvtoolkit.HomeOverrideRetry.Foreground.UNKNOWN;
import static com.redmi.tvtoolkit.HomeOverrideRetry.Foreground.OTHER;

public final class BehaviorTests {
    static int checks;
    static void eq(Object want, Object actual) {
        checks++;
        if (!Objects.equals(want, actual)) throw new AssertionError("expected " + want + " got " + actual);
    }
    static void throwsError(Runnable task) {
        checks++;
        try { task.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("expected failure");
    }

    static class Memory implements VerifiedStore.Backend {
        Map<String,String> values = new HashMap<>();
        int writes;
        boolean failFirst, corruptFirst, failAll;
        public Map<String,String> read() { return new HashMap<>(values); }
        public boolean write(Map<String,String> next) {
            writes++;
            values = new HashMap<>(next);
            if (corruptFirst && writes == 1) values.put("target", "corrupted");
            return !failAll && !(failFirst && writes == 1);
        }
    }

    static void configuration() {
        Memory store = new Memory();
        Map<String,String> first = ConfigValues.select(store.read(), "a.b/a.b.Home", "");
        VerifiedStore.replace(store, first);
        eq(first, store.read()); eq("true", store.read().get("enabled")); eq("1", store.read().get("revision"));
        // An issued start is not a successful launcher and must not become the rollback target.
        eq(null, store.read().get("lastGood"));
        VerifiedStore.replace(store, ConfigValues.confirm(store.read(), "a.b/a.b.Home", false));
        eq("a.b/a.b.Home", store.read().get("lastGood"));
        Map<String,String> prior = store.read();
        Map<String,String> second = ConfigValues.select(prior, "c.d/c.d.Home", prior.get("lastGood"));
        VerifiedStore.replace(store, second);
        eq("a.b/a.b.Home", store.read().get("fallback"));
        VerifiedStore.replace(store, ConfigValues.confirm(store.read(), "a.b/a.b.Home", true));
        eq("a.b/a.b.Home", store.read().get("target")); eq("", store.read().get("fallback"));
        eq("fallback_foreground", store.read().get("phase"));
        VerifiedStore.replace(store, ConfigValues.disable(store.read(), "restored"));
        eq("false", store.read().get("recovery")); eq("false", store.read().get("enabled"));
        eq("a.b/a.b.Home", store.read().get("target"));
        eq("", ConfigValues.select(prior, "a.b/a.b.Home", "a.b/a.b.Home").get("fallback"));
        for (int fault = 0; fault < 2; fault++) {
            final Memory broken = new Memory();
            broken.values.put("target", "old");
            broken.failFirst = fault == 0; broken.corruptFirst = fault == 1;
            throwsError(() -> VerifiedStore.replace(broken, second));
            eq(Collections.singletonMap("target", "old"), broken.read());
            eq(2, broken.writes);
        }
        final Memory irrecoverable = new Memory(); irrecoverable.failAll = true;
        throwsError(() -> VerifiedStore.replace(irrecoverable, second));
    }

    static void retries() {
        HomeOverrideRetry p = new HomeOverrideRetry();
        eq(NONE, p.step(0, HomeOverrideRetry.Foreground.TARGET, false)); // target already foreground: no start
        eq(NONE, p.step(1, UNKNOWN, false));
        eq(TARGET, p.step(10, SYSTEM, false));
        for (int t : new int[]{11,12,20,100,200,500,900,999}) eq(NONE, p.step(t, SYSTEM, false)); // burst duplicates
        eq(TARGET, p.step(1010, SYSTEM, false)); // trailing Home is revisited by timer
        eq(NONE, p.step(1100, HomeOverrideRetry.Foreground.TARGET, false));
        eq(CONFIRMED_TARGET, p.step(2100, HomeOverrideRetry.Foreground.TARGET, false));
        eq(false, p.active());

        p = new HomeOverrideRetry();
        eq(TARGET, p.step(0, SYSTEM, false));
        eq(TARGET, p.step(1000, SYSTEM, false));
        eq(TARGET, p.step(2000, UNKNOWN, false));
        eq(DISABLE, p.step(3000, UNKNOWN, false));
        eq(false, p.active());

        p = new HomeOverrideRetry();
        eq(TARGET, p.step(0, SYSTEM, true));
        p.launchFailed(); eq(TARGET, p.step(100, SYSTEM, true));
        p.launchFailed(); eq(TARGET, p.step(200, SYSTEM, true));
        p.launchFailed(); eq(FALLBACK, p.step(300, SYSTEM, true));
        eq(NONE, p.step(400, HomeOverrideRetry.Foreground.FALLBACK, true));
        eq(CONFIRMED_FALLBACK, p.step(1400, HomeOverrideRetry.Foreground.FALLBACK, true));

        p = new HomeOverrideRetry();
        eq(TARGET, p.step(0, SYSTEM, true));
        for (int i=1; i<=5; i++) {
            p.launchFailed();
            eq(i<3 ? TARGET : i<5 ? FALLBACK : DISABLE, p.step(i*200, SYSTEM, true));
        }
        eq(false, p.active());
        p = new HomeOverrideRetry();
        p.step(0, SYSTEM, true);
        eq(DISABLE, p.step(10000, UNKNOWN, true));
        p = new HomeOverrideRetry();
        p.step(0, SYSTEM, false);
        eq(NONE, p.step(100, OTHER, false)); eq(false, p.active());

        // Rapid Home/target alternation never resets the episode without a stable foreground.
        p = new HomeOverrideRetry();
        eq(TARGET, p.step(0, SYSTEM, false));
        p.step(200, HomeOverrideRetry.Foreground.TARGET, false);
        eq(TARGET, p.step(1000, SYSTEM, false));
        p.step(1200, HomeOverrideRetry.Foreground.TARGET, false);
        eq(TARGET, p.step(2000, SYSTEM, false));
        p.step(2200, HomeOverrideRetry.Foreground.TARGET, false);
        eq(DISABLE, p.step(3000, SYSTEM, false));
        LaunchBudget circuit = new LaunchBudget();
        for (int i=0; i<10; i++) eq(true, circuit.claim(i*3000));
        eq(false, circuit.claim(31000));
        eq(true, circuit.claim(61000));
    }

    static final class Recorder implements RecoveryPlan.Actions {
        final List<String> calls = new ArrayList<>();
        public void homeAccessibility(){calls.add("home");}
        public void bootAccessibility(){calls.add("boot");}
        public void adb(){calls.add("adb");}
        public void periodicCleaner(){calls.add("periodic");}
        public void startupMediator(){calls.add("startup");}
    }

    static void capabilities() {
        DeviceCapabilities mftr = DeviceCapabilities.match("MiTV-MFTR0","freeguy","freeguy","blueplanet");
        Recorder r = new Recorder(); RecoveryPlan.run(mftr,true,true,r);
        eq(Arrays.asList("adb","home"),r.calls);
        eq(false,mftr.tvManagerBridge); eq(false,mftr.adbRecovery); eq(false,mftr.periodicCleaner);
        eq(true,mftr.adbSettingsRecovery);
        for (DeviceCapabilities unsupported : new DeviceCapabilities[]{
            DeviceCapabilities.match("MiTV-MFTR0","blueplanet","blueplanet","blueplanet"),
            DeviceCapabilities.match("other","freeguy","blueplanet","blueplanet"),
            DeviceCapabilities.match("other","other","other","other")}) {
            r=new Recorder(); RecoveryPlan.run(unsupported,true,true,r); eq(Collections.emptyList(),r.calls);
        }
        DeviceCapabilities old = DeviceCapabilities.match("MiTV-ASTR0","blueplanet","","");
        r=new Recorder(); RecoveryPlan.run(old,true,false,r);
        eq(Arrays.asList("adb","boot","periodic","startup"),r.calls);
        r=new Recorder(); RecoveryPlan.run(old,false,true,r); eq(Collections.emptyList(),r.calls);
        r=new Recorder(); RecoveryPlan.run(mftr,false,true,r); eq(Collections.emptyList(),r.calls);
        eq(true,RecoveryBudget.allowed(9,8,4,0,40000));
        eq(false,RecoveryBudget.allowed(9,9,4,100000,0));
        eq(false,RecoveryBudget.allowed(9,9,1,10100,10000));
        eq(true,RecoveryBudget.allowed(9,9,1,20100,10000));
        eq(false,RecoveryBudget.allowed(-1,-2,0,0,-1));
        AdbState ready = new AdbState(1,"5555","5555","running","");
        eq(true,ready.networkReady()); eq(false,ready.needsRepair());
        for (AdbState broken : Arrays.asList(
                new AdbState(0,"5555","5555","running",""),
                new AdbState(1,"","5555","running",""),
                new AdbState(1,"5555","","running",""),
                new AdbState(1,"5555","5555","stopped",""))) {
            eq(false,broken.networkReady()); eq(true,broken.needsRepair());
        }
    }

    static void services() {
        String own="com.redmi.tvtoolkit/com.redmi.tvtoolkit.HomeOverrideService";
        String others="a.b/.Reader:c.d/.Control";
        eq(others+":"+own,ServiceList.merge(others,own,false));
        eq(others,ServiceList.merge(others+":"+own,own,true));
        eq(others,ServiceList.merge(others+":com.redmi.tvtoolkit/.HomeOverrideService",own,true));
        eq(own,ServiceList.merge(null,own,false));
        eq("",ServiceList.merge(own,own,true));
        eq(others+":"+own,ServiceList.merge(others+":"+own,own,false));
        eq(true, HomeEventHint.matches("com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode", 1100, 1000));
        eq(true, HomeEventHint.matches("com.xiaomi.mitv.settings", ".entry.FallbackHome", 1100, 1000));
        eq(false, HomeEventHint.matches("com.xiaomi.mitv.settings", ".entry.MainActivity", 1100, 1000));
        eq(false, HomeEventHint.matches("com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode", 1600, 1000));
        eq(false, HomeEventHint.matches("com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode", 900, 1000));
    }
    public static void main(String[] args) {
        configuration(); retries(); capabilities(); services();
        System.out.println("PASS: " + checks + " behavioral assertions (configuration, failure rollback, retry timelines, capabilities, service preservation)");
    }
}
