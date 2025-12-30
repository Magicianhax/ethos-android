# Ethos LED Android App

Native Android app for controlling LED displays with Ethos scores. This app replicates all functionality from the Flask web app.

## Features

- ✅ Fetch Ethos scores from API
- ✅ Display score with tier-based colors
- ✅ BLE communication with LED device
- ✅ Brightness control
- ✅ Custom color picker
- ✅ Auto-refresh every minute
- ✅ Manual refresh
- ✅ LED power on/off
- ✅ Clear screen
- ✅ Connection status display

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 26 (Android 8.0) or higher
- Bluetooth Low Energy (BLE) enabled device
- LED device with MAC address: `5D:C8:1C:36:B7:AC` (configurable in code)

## Setup

1. Open the project in Android Studio
2. Sync Gradle files
3. Update the device MAC address in `BleService.kt` if needed:
   ```kotlin
   const val DEVICE_ADDRESS = "5D:C8:1C:36:B7:AC"
   ```
4. Build and run on your Android device

## Building APK

### Using Android Studio
1. Build → Generate Signed Bundle / APK
2. Select APK
3. Follow the signing wizard
4. Build → Build Bundle(s) / APK(s) → Build APK(s)

### Using Gradle (Command Line)

**Windows PowerShell:**
```powershell
cd ethos_android_app

# First, download the wrapper JAR if missing:
.\download-wrapper.ps1

# Then build:
.\gradlew.bat assembleRelease
```

**Linux/Mac:**
```bash
cd ethos_android_app
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`

**Note:** If you get an error about missing `gradle-wrapper.jar`, run `.\download-wrapper.ps1` first, or use Android Studio which will generate it automatically.

## Permissions

The app requires:
- Bluetooth
- Bluetooth Admin
- Bluetooth Scan (Android 12+)
- Bluetooth Connect (Android 12+)
- Internet
- Network State

All permissions are declared in `AndroidManifest.xml`.

## BLE Protocol

The app uses the following BLE UUIDs:
- Service UUID: `0000fa00-0000-1000-8000-00805f9b34fb`
- Write Characteristic: `0000fa02-0000-1000-8000-00805f9b34fb`

**Note:** The BLE packet protocol in `BleService.kt` may need adjustment based on your specific LED device. The current implementation is a simplified version. If your device uses a different protocol (like the one in `pypixelcolor`), you'll need to update the `buildPacket()` method in `BleService.kt`.

## Configuration

### Change Device Address
Edit `app/src/main/java/com/ethos/led/ble/BleService.kt`:
```kotlin
const val DEVICE_ADDRESS = "YOUR:DEVICE:MAC:ADDRESS"
```

### Change API Endpoint
Edit `app/src/main/java/com/ethos/led/api/ApiClient.kt`:
```kotlin
private const val BASE_URL = "https://api.ethos.network/"
```

## Troubleshooting

### BLE Connection Issues
- Ensure Bluetooth is enabled
- Check device MAC address is correct
- Verify LED device is powered on and in pairing mode
- Check Android permissions are granted

### API Issues
- Verify internet connection
- Check API endpoint is accessible
- Ensure username is valid

### Build Issues
- Sync Gradle files: File → Sync Project with Gradle Files
- Clean project: Build → Clean Project
- Invalidate caches: File → Invalidate Caches / Restart

## Project Structure

```
ethos_android_app/
├── app/
│   ├── src/main/
│   │   ├── java/com/ethos/led/
│   │   │   ├── MainActivity.kt          # Main UI
│   │   │   ├── api/                    # API services
│   │   │   ├── ble/                    # BLE communication
│   │   │   ├── model/                  # Data models
│   │   │   └── viewmodel/              # ViewModel for state
│   │   ├── res/                        # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## License

Same as the original Flask app.

