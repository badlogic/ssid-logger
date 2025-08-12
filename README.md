# SSID Logger

An Android application that monitors Wi-Fi network changes and logs SSID transitions to a remote endpoint.

## 📱 Download

**[Download Latest APK →](https://github.com/badlogic/ssid-logger/releases/latest)**

Direct APK downloads available - no unzipping required! New builds are automatically created for every push to main.

## Features

- Prompts for endpoint URL on startup
- Runs as a background service monitoring Wi-Fi SSID changes
- Sends JSON logs containing timestamp, previous SSID, and new SSID
- Displays notifications when the endpoint is unreachable

## Log Format

```json
{
  "timestamp": "2025-01-12T10:30:00Z",
  "previousSSID": "HomeNetwork",
  "newSSID": "OfficeWiFi"
}
```

## Technical Decisions

### Platform Requirements
- **Minimum SDK**: API 31 (Android 12)
- **Target SDK**: Latest available
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose or Material 3 with View Binding
- **Build System**: Gradle with Kotlin DSL (build.gradle.kts)

### Architecture Choices
- **Background Monitoring**: Foreground Service with persistent notification
- **Network Detection**: NetworkCallback via ConnectivityManager
- **Location Permission**: ACCESS_COARSE_LOCATION only (no GPS usage)
- **Battery Optimization**: Event-driven monitoring, no polling

### Design Principles
- Modern Material 3 (Material You) design
- Minimal UI with focus on functionality
- Dynamic color theming support
- Single activity architecture

## Development & Deployment Instructions (For AI Agents)

### Test Server

The `/server` directory contains a simple Node.js Express server for testing the SSID logger.

**Setup & Run:**
```bash
# Install dependencies
cd server && npm install

# Start server (runs on port 3000)
npm start
```

**Using with terminal-src (for AI agents):**
```bash
# Start in background
mcp__terminal-src__terminal action=start command="cd server && npm start" name="SSID Test Server"

# Check output
mcp__terminal-src__terminal action=stream id="<proc-id>" since_last=true

# Stop server
mcp__terminal-src__terminal action=stop id="<proc-id>"
```

**Endpoints:**
- `POST http://localhost:3000/log` - Receives SSID change logs
- `GET http://localhost:3000/health` - Health check

**For Android emulator to reach host machine:**
- Use `http://10.0.2.2:3000/log` instead of localhost

### Prerequisites
- Android Studio project located in `/app` folder
- Android SDK at `/Users/badlogic/Library/Android/sdk`
- Running emulator (check with `adb devices`)

### Workflow for Code Changes

1. **Check emulator status**:
```bash
/Users/badlogic/Library/Android/sdk/platform-tools/adb devices
```

2. **Modify code files** in `app/app/src/main/`

3. **Build and deploy** using the helper script:
```bash
cd app && ./deploy.sh
```

Or manually:
```bash
# Build APK
cd app && ./gradlew assembleDebug

# Install to emulator
/Users/badlogic/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
/Users/badlogic/Library/Android/sdk/platform-tools/adb shell am start -n at.mariozechner.ssid_logger/.MainActivity
```

4. **Take screenshot** to verify changes:
```bash
/Users/badlogic/Library/Android/sdk/platform-tools/adb exec-out screencap -p > emulator_screenshot.png
```
Then read the PNG file to view it.

### Debugging

- **View logs**: 
```bash
/Users/badlogic/Library/Android/sdk/platform-tools/adb logcat | grep "at.mariozechner.ssid_logger"
```

- **Check app is running**:
```bash
/Users/badlogic/Library/Android/sdk/platform-tools/adb shell ps | grep ssid_logger
```

- **Clear app data**:
```bash
/Users/badlogic/Library/Android/sdk/platform-tools/adb shell pm clear at.mariozechner.ssid_logger
```

## Implementation Plan

### Phase 0: Test Server Setup
- [x] Create Node.js Express server in `/server` directory
- [x] Implement `/log` endpoint to receive SSID changes
- [x] Add health check endpoint at `/health`
- [x] Configure to run on port 3000

### Phase 1: Project Setup
- [x] Create Android Studio project with Empty Compose Activity
- [x] Configure Gradle dependencies for Material 3
- [x] Set up proper package structure
- [x] Configure minimum SDK 31 in build.gradle.kts

### Phase 2: Permissions & Manifest
- [x] Add ACCESS_WIFI_STATE permission
- [x] Add ACCESS_COARSE_LOCATION permission
- [x] Add ACCESS_BACKGROUND_LOCATION permission
- [x] Add ACCESS_NETWORK_STATE permission
- [x] Add FOREGROUND_SERVICE permission
- [x] Configure foreground service in manifest

### Phase 3: UI Implementation
- [x] Create main screen with URL input field
- [x] Add validation for URL format
- [x] Implement Material 3 theming
- [x] Add start/stop monitoring button
- [x] Create settings storage (SharedPreferences/DataStore)

### Phase 4: Foreground Service
- [x] Create WifiMonitorService extending Service
- [x] Implement foreground notification
- [x] Add start/stop service controls
- [x] Handle service lifecycle properly

### Phase 5: Network Monitoring
- [x] Implement NetworkCallback
- [x] Register callback with ConnectivityManager
- [x] Extract SSID from WifiInfo
- [x] Track previous SSID state
- [x] Detect SSID changes

### Phase 6: Data Model & Logging
- [x] Create data class for SSID change event
- [x] Implement JSON serialization (Gson/Kotlinx.serialization)
- [x] Add timestamp generation
- [ ] Queue events if offline

### Phase 7: Network Communication
- [x] Set up HTTP client (OkHttp/Retrofit)
- [x] Implement POST request to endpoint
- [ ] Add retry logic with exponential backoff
- [x] Handle network failures

### Phase 8: Error Handling & Notifications
- [x] Create notification channel
- [x] Show notification on endpoint unreachable
- [x] Implement error recovery
- [x] Add user-friendly error messages

### Phase 9: Testing & Optimization
- [ ] Test SSID change detection
- [ ] Verify background operation
- [ ] Test battery impact
- [ ] Handle edge cases (no Wi-Fi, airplane mode)

### Phase 10: Polish
- [ ] Add app icon
- [ ] Implement proper logging
- [ ] Add export/import settings
- [ ] Create about screen with version info