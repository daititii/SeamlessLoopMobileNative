
@echo off
echo [1/3] Pushing APK...
adb push "app\build\intermediates\apk\debug\app-debug.apk" /data/local/tmp/app.apk
 
echo [2/3] Installing...
adb shell su -c "pm install -r -d -t -g /data/local/tmp/app.apk"
 
echo [3/3] Launching...
adb shell su -c "am start -n com.cpu.seamlessloopmobile/.MainActivity"
 
echo Done!
 