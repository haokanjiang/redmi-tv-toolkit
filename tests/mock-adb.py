"""Offline fake ADB transport. Refuses to run without an explicit fixture state path."""
import base64, json, os, shlex, sys
from pathlib import Path

path = Path(os.environ["TOOLKIT_TEST_STATE"])
state = json.loads(path.read_text(encoding="utf-8-sig"))
args = sys.argv[1:]
if args[:1] == ["-s"]:
    args = args[2:]
if args[:1] == ["shell"]:
    args = ["shell"] + shlex.split(args[1]) if len(args) == 2 else args
state.setdefault("calls", []).append(args)

def finish(output="", code=0):
    path.write_text(json.dumps(state), encoding="utf-8")
    print(output)
    sys.exit(code)

def value(flag, default=""):
    return args[args.index(flag) + 1] if flag in args else default

def extra(key, default=""):
    for i in range(len(args)-2):
        if args[i] == "--es" and args[i+1] == key:
            return args[i+2]
    return default

fault = state.get("fault", "")
if args == ["start-server"]: finish()
if args[:1] == ["connect"]: finish("connected to fake-only")
if args == ["get-state"]: finish("device")
if args[:2] == ["shell", "getprop"]:
    finish({"ro.product.model":"MiTV-MFTR0", "ro.product.device":"freeguy",
            "ro.product.name":"freeguy", "ro.board.platform":"unknown",
            "ro.build.version.release":"14", "ro.build.version.sdk":"34",
            "ro.build.display.id":"OS3.0.107.0.UFTMATV", "ro.product.manufacturer":"xiaomi",
            "persist.adb.tcp.port":state.get("persistPort","5555"),
            "service.adb.tcp.port":state.get("servicePort","5555"),
            "init.svc.adbd":state.get("adbdState","running")}.get(args[2],""))
if args[:3] == ["shell","settings","get"]:
    finish({("global","adb_enabled"):state.get("adbEnabled","1"),
            ("system","start_3rd_app"):state.get("startThirdApp","null"),
            ("secure","enabled_accessibility_services"):state.get("accessibilityServices","null"),
            ("secure","accessibility_enabled"):state.get("accessibilityEnabled","0")}
           .get(tuple(args[3:5]),"null"))
if "query-activities" in args:
    if value("-c").endswith(".HOME"):
        finish("3 activities found:\na.desktop/.Home\nb.desktop/.Home\ncom.mitv.tvhome/.MainActivityUserMode")
    finish("a.desktop/a.desktop.Home\na.desktop/.TvEntry\norg.jellyfin.androidtv/.Startup")
if "resolve-activity" in args: finish("com.mitv.tvhome/.MainActivityUserMode")
if args[:1] == ["install"]: finish("Success")
if args[:3] == ["shell","pm","grant"]:
    if fault == "grantfail": finish("SecurityException: not allowed", 1)
    state["granted"] = fault != "grantmissing"
    finish()
if args[:3] == ["shell","dumpsys","package"]:
    finish("android.permission.WRITE_SECURE_SETTINGS: granted=" + str(state.get("granted",False)).lower())
if args[:4] == ["shell","dumpsys","activity","activities"]:
    target = state.get("target","a.desktop/a.desktop.Home")
    if fault == "noforeground":
        finish("Hist #0: ActivityRecord{fake " + target + "}\n topResumedActivity=ActivityRecord{fake com.mitv.tvhome/.MainActivity}")
    finish("topResumedActivity=ActivityRecord{fake u0 " + target + " t1}")
if args[:3] == ["shell","am","broadcast"]:
    if fault == "denied": finish("Broadcast completed: result=0")
    action=value("-a").split(".")[-1]
    ok=True
    if action == "HOME_SET":
        if fault == "reject": ok=False
        else:
            state.update(enabled=True,recovery=True,target=extra("target"),
                         fallback=extra("fallback"),revision=state.get("revision",0)+1,
                         phase="target_foreground", bound=True)
    elif action == "HOME_RESTORE":
        state.update(enabled=False,recovery=False,bound=False,phase="restored")
    result=dict(protocol=1,ok=ok,message="accepted" if ok else "invalid target",
                enabled=state.get("enabled",False),recovery=state.get("recovery",False),
                target=state.get("target",""),fallback=state.get("fallback",""),
                lastGood=state.get("lastGood",""),revision=state.get("revision",0),
                phase=state.get("phase","idle"),bound=state.get("bound",False))
    if action == "HOME_STATUS" and fault == "mismatch" and state.get("enabled"):
        result["target"]="wrong.app/wrong.app.Home"
    payload=base64.b64encode(json.dumps(result).encode()).decode()
    finish(f'Broadcast completed: result={1 if ok else 2}, data="TOOLKIT1:{payload}"')
finish("Unexpected mock command: "+repr(args), 99)
