@echo off
setlocal EnableDelayedExpansion
title TV Cleanse
cd /d "%~dp0"

set "REL=https://github.com/gblanchard77-beep/tv-cleanse/releases/latest/download"
set "PKG=com.tvcleanse.home"
set "PT=%~dp0platform-tools"

echo.
echo  ===== TV CLEANSE: put the simple home screen on your TV =====
echo.

rem ---- 1. get adb (one time) ----
set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB (
  if not exist "%PT%\adb.exe" (
    echo  Getting the small adb tool from Google, one time only...
    curl -L -s -o platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-windows.zip
    if errorlevel 1 goto :fail_dl
    tar -xf platform-tools.zip
    del platform-tools.zip >nul 2>&1
  )
  if not exist "%PT%\adb.exe" goto :fail_dl
  set "ADB=%PT%\adb.exe"
)

rem ---- 2. ask for the TV address ----
echo.
echo  On the TV: Settings, Network, look for the IP address.
echo  Also turn ON Developer options, then USB debugging and Network debugging.
echo.
set /p IP= Type the TV IP address and press Enter:
if "%IP%"=="" goto :end
set "TV=%IP%:5555"

echo.
echo  Connecting. WATCH THE TV: if it asks "Allow debugging?",
echo  tick "Always allow" and press OK.
"%ADB%" connect %TV% >nul 2>&1
set /a tries=0
:wait
"%ADB%" -s %TV% shell echo ok 2>nul | findstr /c:"ok" >nul
if not errorlevel 1 goto :connected
set /a tries+=1
if %tries% GEQ 20 goto :fail_conn
timeout /t 3 /nobreak >nul
"%ADB%" connect %TV% >nul 2>&1
goto :wait

:connected
echo  Connected.
echo.
echo  What do you want to do?
echo    1 = Install the TV Cleanse home screen
echo    2 = Remove it (put the TV back)
set /p CH= Type 1 or 2 and press Enter:
if "%CH%"=="2" goto :remove

rem ---- 3. download + verify the app ----
echo.
echo  Downloading the home screen app...
set "W=%TEMP%\tvcleanse"
if not exist "%W%" mkdir "%W%"
curl -L -s -o "%W%\tvcleanse-home.apk" %REL%/tvcleanse-home.apk
curl -L -s -o "%W%\tvcleanse-home.apk.sha256" %REL%/tvcleanse-home.apk.sha256
if not exist "%W%\tvcleanse-home.apk" goto :fail_dl
set "WANT="
for /f "usebackq tokens=1" %%a in ("%W%\tvcleanse-home.apk.sha256") do if not defined WANT set "WANT=%%a"
set "GOT="
for /f "skip=1 tokens=*" %%a in ('certutil -hashfile "%W%\tvcleanse-home.apk" SHA256') do if not defined GOT set "GOT=%%a"
set "GOT=!GOT: =!"
if /i not "!GOT!"=="!WANT!" (
  echo  The download did not match its checksum. Stopping to be safe.
  goto :end
)
echo  Download checked OK.

rem ---- 4. install ----
echo.
echo  Installing...
"%ADB%" -s %TV% install -r "%W%\tvcleanse-home.apk" | findstr /c:"Success" >nul
if errorlevel 1 (
  "%ADB%" -s %TV% uninstall %PKG% >nul 2>&1
  "%ADB%" -s %TV% install "%W%\tvcleanse-home.apk" | findstr /c:"Success" >nul
  if errorlevel 1 goto :fail_inst
)

set "SDK=0"
for /f "tokens=*" %%a in ('"%ADB%" -s %TV% shell getprop ro.build.version.sdk') do set "SDK=%%a"
set "SDK=!SDK: =!"
for /f "tokens=* delims=" %%a in ("!SDK!") do set "SDK=%%a"
"%ADB%" -s %TV% shell cmd role add-role-holder --user 0 android.app.role.HOME %PKG% >nul 2>&1

rem Google TV always keeps its own Home button, so also switch on our helper.
rem Android 14 needs the app opened once first.
"%ADB%" -s %TV% shell am start -n %PKG%/.MainActivity >nul 2>&1
timeout /t 2 /nobreak >nul
set "CUR="
for /f "tokens=*" %%a in ('"%ADB%" -s %TV% shell settings get secure enabled_accessibility_services') do set "CUR=%%a"
set "CUR=!CUR: =!"
if /i "!CUR!"=="null" set "CUR="
set "NEW=!CUR!"
echo !CUR! | findstr /c:"%PKG%" >nul
if errorlevel 1 (
  if defined CUR (set "NEW=!CUR!:%PKG%/.HomeRedirect") else (set "NEW=%PKG%/.HomeRedirect")
)
"%ADB%" -s %TV% shell settings put secure enabled_accessibility_services !NEW! >nul
"%ADB%" -s %TV% shell settings put secure accessibility_enabled 1 >nul
set /a r=0
:chk
set /a n=0
:chk2
"%ADB%" -s %TV% shell dumpsys accessibility | findstr /c:"TV Cleanse Home button" | findstr /c:"Bound services" >nul
if not errorlevel 1 goto :bound
set /a n+=1
if %n% LSS 8 (timeout /t 1 /nobreak >nul & goto :chk2)
set /a r+=1
if %r% GEQ 2 goto :bound
rem not started: switch off and on once more
"%ADB%" -s %TV% shell settings put secure enabled_accessibility_services "!CUR!" >nul
timeout /t 2 /nobreak >nul
"%ADB%" -s %TV% shell settings put secure enabled_accessibility_services !NEW! >nul
goto :chk
:bound
"%ADB%" -s %TV% shell am start -n %PKG%/.MainActivity >nul 2>&1
timeout /t 2 /nobreak >nul
"%ADB%" -s %TV% shell input keyevent KEYCODE_HOME >nul
echo.
echo  ===== DONE =====
echo  Press the Home button on the TV remote. You should see TV Cleanse.
echo  If it still shows the old screen, press Home once more.
goto :end

:remove
"%ADB%" -s %TV% shell cmd role remove-role-holder --user 0 android.app.role.HOME %PKG% >nul 2>&1
"%ADB%" -s %TV% uninstall %PKG% >nul 2>&1
"%ADB%" -s %TV% shell input keyevent KEYCODE_HOME >nul
echo.
echo  Removed. Your TV is back to its normal home screen.
goto :end

:fail_dl
echo.
echo  Could not download. Check this PC is online and try again.
goto :end
:fail_conn
echo.
echo  Could not connect. Check the IP, and that Network debugging is ON.
goto :end
:fail_inst
echo.
echo  Install failed. Send a photo of this window.

:end
echo.
"%ADB%" disconnect %TV% >nul 2>&1
pause
endlocal
