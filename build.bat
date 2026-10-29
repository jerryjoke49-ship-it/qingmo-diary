@echo off
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=%~dp0..\tools\jdk-17.0.20.1+1"
if not defined SDK_ROOT if defined ANDROID_SDK_ROOT set "SDK_ROOT=%ANDROID_SDK_ROOT%"
if not defined SDK_ROOT if defined ANDROID_HOME set "SDK_ROOT=%ANDROID_HOME%"
if not defined SDK_ROOT set "SDK_ROOT=%~dp0..\android-sdk"
set "ANDROID_JAR=%SDK_ROOT%\platforms\android-35\android.jar"
set "BUILD_TOOLS=%SDK_ROOT%\build-tools\35.0.0"
set "BUILD_DIR=%~dp0build"
set "KEYSTORE_DIR=%~dp0.signing"
set "OUTPUT_DIR=%~dp0dist"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%ANDROID_JAR%" (
  echo Android SDK not found.
  exit /b 1
)

if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%\classes"
mkdir "%BUILD_DIR%\dex"
mkdir "%BUILD_DIR%\gen"
mkdir "%KEYSTORE_DIR%" 2>nul
mkdir "%OUTPUT_DIR%" 2>nul

echo [1/6] Compiling resources...
"%BUILD_TOOLS%\aapt2.exe" compile --dir res -o "%BUILD_DIR%\resources.zip"
if errorlevel 1 exit /b 1

echo [2/6] Linking resources and generating R...
"%BUILD_TOOLS%\aapt2.exe" link ^
  -o "%BUILD_DIR%\unsigned.apk" ^
  -I "%ANDROID_JAR%" ^
  --manifest AndroidManifest.xml ^
  --java "%BUILD_DIR%\gen" ^
  --min-sdk-version 24 ^
  --target-sdk-version 34 ^
  --version-code 1 ^
  --version-name 1.0 ^
  "%BUILD_DIR%\resources.zip"
if errorlevel 1 exit /b 1

echo [3/6] Compiling Java...
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 ^
  -classpath "%ANDROID_JAR%" ^
  -d "%BUILD_DIR%\classes" ^
  src\com\local\qingmo\*.java ^
  "%BUILD_DIR%\gen\com\local\qingmo\R.java"
if errorlevel 1 exit /b 1

echo [4/6] Creating dex...
"%JAVA_HOME%\bin\jar.exe" cf "%BUILD_DIR%\classes.jar" -C "%BUILD_DIR%\classes" .
if errorlevel 1 exit /b 1
call "%BUILD_TOOLS%\d8.bat" --release --min-api 24 --lib "%ANDROID_JAR%" ^
  --output "%BUILD_DIR%\dex" "%BUILD_DIR%\classes.jar"
if errorlevel 1 exit /b 1

echo [5/6] Packaging and aligning...
"%JAVA_HOME%\bin\jar.exe" uf "%BUILD_DIR%\unsigned.apk" assets
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\jar.exe" uf "%BUILD_DIR%\unsigned.apk" -C "%BUILD_DIR%\dex" classes.dex
if errorlevel 1 exit /b 1
"%BUILD_TOOLS%\zipalign.exe" -f 4 "%BUILD_DIR%\unsigned.apk" "%BUILD_DIR%\aligned.apk"
if errorlevel 1 exit /b 1

echo [6/6] Signing...
if not exist "%KEYSTORE_DIR%\qingji-local.keystore" (
  "%JAVA_HOME%\bin\keytool.exe" -genkeypair ^
    -keystore "%KEYSTORE_DIR%\qingji-local.keystore" ^
    -storepass qingji-local-2026 ^
    -keypass qingji-local-2026 ^
    -alias qingji ^
    -keyalg RSA ^
    -keysize 2048 ^
    -validity 10000 ^
    -dname "CN=QingJi Local, OU=Personal, O=Local, L=Shanghai, ST=Shanghai, C=CN"
  if errorlevel 1 exit /b 1
)
"%BUILD_TOOLS%\apksigner.bat" sign ^
  --ks "%KEYSTORE_DIR%\qingji-local.keystore" ^
  --ks-key-alias qingji ^
  --ks-pass pass:qingji-local-2026 ^
  --key-pass pass:qingji-local-2026 ^
  --out "%OUTPUT_DIR%\QingMoDiary-v1.0.apk" ^
  "%BUILD_DIR%\aligned.apk"
if errorlevel 1 exit /b 1

"%BUILD_TOOLS%\apksigner.bat" verify --verbose "%OUTPUT_DIR%\QingMoDiary-v1.0.apk"
echo.
echo Built: %OUTPUT_DIR%\QingMoDiary-v1.0.apk
endlocal
