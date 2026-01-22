# AceStream Engine Integration Guide

This document explains how to integrate the AceStream Engine into your Android application.

---

## 🎯 Integration Options

### Option 1: Use External AceStream App (Simplest) ✅

**How it works:** Your app communicates with an installed AceStream app via HTTP API on `localhost:6878`.

**Pros:**
- No native libraries needed
- Always up-to-date engine
- Legal and supported

**Cons:**
- User must install AceStream separately
- Requires AceStream app to be running

### Option 2: Embed Native Libraries (Advanced) 🔧

**How it works:** Extract `.so` files from AceStream APK and include in your app.

**Pros:**
- Single APK installation
- Full control over engine

**Cons:**
- Complex integration
- May violate AceStream license
- Need to update manually

### Option 3: Engine Client Module (Recommended) ⭐

**How it works:** Use AceStream's official `acestream-engine-client` to start the engine from an external app.

---

## 📋 Quick Start: Option 1 (External App)

### Step 1: Check if AceStream is installed

```kotlin
val api = AceStreamEngineApi(context)
if (!api.isAceStreamInstalled()) {
    // Prompt user to install
    api.openAceStreamDownload()
    return
}
```

### Step 2: Start playback

```kotlin
// The engine API handles everything
val streamUrl = api.getStreamUrl("c1ee6e1e0ef5928d8261f855c417de21794f437d")

// Use ExoPlayer to play
exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
exoPlayer.prepare()
exoPlayer.play()
```

### Step 3: Check engine status

```kotlin
lifecycleScope.launch {
    if (api.isEngineRunning()) {
        val version = api.getEngineVersion()
        Log.d("AceStream", "Engine version: $version")
    } else {
        // Engine not running, try to start it
        api.requestEngineStart()
        api.waitForEngine(timeoutMs = 30000)
    }
}
```

---

## 🔧 Advanced: Option 2 (Native Libraries)

### Step 1: Download AceStream APK

Download from:
- [APKMirror](https://www.apkmirror.com/?s=ace+stream+engine) - Safe, verified APKs
- [APKPure](https://apkpure.com/ace-stream-media/org.acestream.media)

### Step 2: Extract native libraries

Run the extraction script:

```powershell
cd C:\Users\DELL\Desktop\streamacexml\AceStreamTV
.\extract_acestream_libs.ps1 -ApkPath "C:\path\to\acestream.apk" -ProjectPath "."
```

This will:
1. Extract all `.so` files from the APK
2. Copy them to `app/src/main/jniLibs/`
3. Organize by architecture (armeabi-v7a, arm64-v8a, x86)

### Step 3: Load native libraries

Add to `AceStreamService.kt`:

```kotlin
companion object {
    init {
        try {
            // Load AceStream native libraries
            System.loadLibrary("python3.8")
            System.loadLibrary("crypto")
            System.loadLibrary("ssl")
            System.loadLibrary("acestream")
            Log.d("AceStream", "Native libraries loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("AceStream", "Failed to load native libs: ${e.message}")
        }
    }
}
```

### Step 4: Initialize Python environment

AceStream uses an embedded Python runtime. You need to:
1. Copy Python standard library to assets
2. Set environment variables
3. Initialize the interpreter

```kotlin
private fun initPythonEngine() {
    val pythonPath = File(filesDir, "python")
    Os.setenv("PYTHONHOME", pythonPath.absolutePath, true)
    Os.setenv("PYTHONPATH", "${pythonPath}/lib/python3.8", true)
    
    // Native init call
    nativeInit(pythonPath.absolutePath)
}

private external fun nativeInit(pythonHome: String)
```

> ⚠️ **Warning**: This approach is complex and may break with AceStream updates.

---

## 📦 Files Created

| File | Description |
|------|-------------|
| `AceStreamEngineApi.kt` | HTTP API client for AceStream Engine |
| `AceStreamService.kt` | Foreground service for engine lifecycle |
| `AceStreamManager.kt` | Manager singleton for easy access |
| `extract_acestream_libs.ps1` | PowerShell script to extract .so files |

---

## 🌐 AceStream HTTP API Reference

Base URL: `http://127.0.0.1:6878`

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `/webui/api/service?method=get_version` | Get engine version |
| `/ace/getstream?id={content_id}` | Get stream URL |
| `/ace/manifest.m3u8?id={content_id}` | Get HLS manifest |
| `/ace/getstat?id={content_id}` | Get stream statistics |
| `/ace/stop?id={content_id}` | Stop stream |

### Example: Get version

```bash
curl http://127.0.0.1:6878/webui/api/service?method=get_version
```

Response:
```json
{
  "result": {
    "version": "3.1.77",
    "code": 3017700
  }
}
```

---

## ✅ Testing

1. Install AceStream from Play Store on your device/emulator
2. Open AceStream and let it initialize
3. Run your app
4. Select a channel - it should start playing

If you see the video, the integration is working! 🎉

---

## 🐛 Troubleshooting

### "Engine not responding"

1. Check if AceStream app is installed and running
2. Verify port 6878 is not blocked
3. Check LogCat for connection errors

### "Native library not found"

1. Verify `.so` files are in `app/src/main/jniLibs/{arch}/`
2. Check your device architecture matches available libs
3. Clean and rebuild project

### "Playback error"

1. Check stream URL is correct
2. Verify the content ID exists and is active
3. Check network connectivity

---

## 📚 Resources

- [AceStream Documentation](https://wiki.acestream.media/)
- [AceStream GitHub](https://github.com/acestream)
- [Engine Core Library](https://github.com/acestream/acestream-engine-android-core)
