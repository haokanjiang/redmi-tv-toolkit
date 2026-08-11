$argumentsList = @($args)

if ($argumentsList.Count -eq 0) { exit 1 }
if ($argumentsList[0] -eq "start-server") { exit 0 }
if ($argumentsList[0] -eq "connect") {
    Write-Output "connected to $($argumentsList[1])"
    exit 0
}

if ($argumentsList[0] -eq "-s") {
    if ($argumentsList.Count -lt 3) { exit 1 }
    $argumentsList = @($argumentsList[2..($argumentsList.Count - 1)])
}

if ($argumentsList[0] -eq "get-state") {
    Write-Output "device"
    exit 0
}

$command = $argumentsList -join " "
switch -Regex ($command) {
    '^shell getprop ro\.product\.model$' { Write-Output "MiTV-ASTR0"; exit 0 }
    '^shell getprop ro\.product\.name$' { Write-Output "blueplanet"; exit 0 }
    '^shell getprop ro\.product\.device$' { Write-Output "blueplanet"; exit 0 }
    '^shell getprop ro\.board\.platform$' { Write-Output "blueplanet"; exit 0 }
    '^shell getprop ro\.build\.version\.release$' { Write-Output "11"; exit 0 }
    '^shell getprop ro\.build\.version\.sdk$' { Write-Output "30"; exit 0 }
    '^shell getprop ro\.build\.fingerprint$' {
        Write-Output "Xiaomi/blueplanet/blueplanet:11/test/OS2.0.5.0.RSTAATV:user/release-keys"
        exit 0
    }
    '^shell getprop ro\.build\.version\.incremental$' { Write-Output "OS2.0.5.0.RSTAATV"; exit 0 }
    '^shell getprop init\.svc\.adbd$' { Write-Output "running"; exit 0 }
    '^shell getprop persist\.adb\.tcp\.port$' { Write-Output "5555"; exit 0 }
    '^shell getprop service\.adb\.tcp\.port$' { Write-Output "5555"; exit 0 }
    '^shell cmd package resolve-activity --brief ' {
        Write-Output "com.spocky.projengmenu/.ui.home.MainActivity"
        exit 0
    }
    '^shell pm path com\.spocky\.projengmenu$' { Write-Output "package:/data/app/projectivy/base.apk"; exit 0 }
    '^shell pm path local\.blueplanet\.boothelper$' { Write-Output "package:/data/app/helper/base.apk"; exit 0 }
    '^shell settings get secure enabled_accessibility_services$' {
        Write-Output "com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService:local.blueplanet.boothelper/local.blueplanet.boothelper.BootAccessibilityService"
        exit 0
    }
    '^shell settings get secure accessibility_enabled$' { Write-Output "1"; exit 0 }
    '^shell settings get global adb_enabled$' { Write-Output "1"; exit 0 }
    '^shell settings get system start_3rd_app$' { Write-Output "local.blueplanet.boothelper"; exit 0 }
    '^shell dumpsys periodic$' { Write-Output "PeriodicCleaner: Enable=false"; exit 0 }
    '^shell dumpsys accessibility$' {
        Write-Output "Bound services: com.spocky.projengmenu"
        Write-Output "Crashed services: none"
        exit 0
    }
    '^shell dumpsys device_policy$' {
        Write-Output "local.blueplanet.boothelper/.BootAdminReceiver"
        exit 0
    }
    default { exit 0 }
}

