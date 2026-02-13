<div align="center">

# Flutter Notification Listener

**A powerful Flutter plugin for listening to all incoming notifications on Android**

[![Pub Version](https://img.shields.io/pub/v/flutter_notification_listener?color=blue&logo=dart)](https://pub.dev/packages/flutter_notification_listener)
[![Pub Points](https://img.shields.io/pub/points/flutter_notification_listener?color=green&logo=dart)](https://pub.dev/packages/flutter_notification_listener/score)
[![GitHub Stars](https://img.shields.io/github/stars/jiusanzhou/flutter_notification_listener?style=flat&logo=github)](https://github.com/jiusanzhou/flutter_notification_listener)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

[Features](#-features) · [Installation](#-installation) · [Quick Start](#-quick-start) · [API Reference](#-api-reference) · [Contributing](#-contributing)

</div>

---

## ✨ Features

<table>
<tr>
<td>

**🔔 Real-time Listening**

Capture all system notifications as they arrive, from any app installed on the device.

</td>
<td>

**🎯 Simple API**

Clean and intuitive API design. Access notification fields with ease.

</td>
</tr>
<tr>
<td>

**⚡ Background Execution**

Run Dart code in the background. Auto-start service after device reboot.

</td>
<td>

**🎮 Interactive**

Tap notifications, trigger actions, and even auto-reply to messages directly from Flutter.

</td>
</tr>
</table>

### Compatibility

| Android Version | API Level | Status |
|-----------------|-----------|--------|
| Android 14 | API 34 | ✅ Fully Supported |
| Android 13 | API 33 | ✅ Fully Supported |
| Android 12 | API 31-32 | ✅ Fully Supported |
| Android 11 and below | API ≤ 30 | ✅ Fully Supported |

---

## 📦 Installation

Add the dependency to your `pubspec.yaml`:

```yaml
dependencies:
  flutter_notification_listener: ^1.4.0
```

Then run:

```bash
flutter pub get
```

---

## 🚀 Quick Start

### Step 1: Configure Android Manifest

Add the notification listener service inside the `<application>` tag:

```xml
<service 
    android:name="im.zoe.labs.flutter_notification_listener.NotificationsHandlerService"
    android:label="Flutter Notifications Handler"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true"
    android:foregroundServiceType="specialUse">
    <property 
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="notification_listener"/>
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

Add required permissions:

```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
```

> **Note:** For Android 13+, request the `POST_NOTIFICATIONS` permission at runtime before starting the foreground service.

### Step 2: Initialize the Plugin

```dart
import 'package:flutter_notification_listener/flutter_notification_listener.dart';

void onData(NotificationEvent event) {
  print('${event.packageName}: ${event.title} - ${event.text}');
}

Future<void> initPlatformState() async {
  NotificationsListener.initialize();
  NotificationsListener.receivePort?.listen((evt) => onData(evt));
}
```

### Step 3: Start the Service

```dart
Future<void> startListening() async {
  final hasPermission = await NotificationsListener.hasPermission ?? false;
  
  if (!hasPermission) {
    NotificationsListener.openPermissionSettings();
    return;
  }

  final isRunning = await NotificationsListener.isRunning ?? false;
  
  if (!isRunning) {
    await NotificationsListener.startService(
      foreground: true,
      title: "Listening for notifications",
    );
  }
}
```

> 📖 See the [example app](./example/lib/main.dart) for a complete implementation.

---

## 📖 Usage Guide

### Auto-start After Reboot

Register a broadcast receiver to automatically restart the service after device reboot:

```xml
<receiver 
    android:name="im.zoe.labs.flutter_notification_listener.RebootBroadcastReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

Add the boot permission:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
```

### Background Processing

For reliable background execution, use a static callback function:

```dart
@pragma('vm:entry-point')
static void _callback(NotificationEvent evt) {
  // Persist data immediately
  db.save(evt);
  
  // Forward to UI thread if needed
  final send = IsolateNameServer.lookupPortByName("_listener_");
  send?.send(evt);
}

Future<void> initPlatformState() async {
  NotificationsListener.initialize(callbackHandle: _callback);
}
```

> **Important:** The `@pragma('vm:entry-point')` annotation is required for Flutter 3.x to prevent the function from being stripped in release builds.

### Interactive Notifications

**Tap a notification:**

```dart
void onData(NotificationEvent event) {
  if (event.canTap) {
    event.tap();
  }
}
```

**Auto-reply to messages:**

```dart
void onData(NotificationEvent event) {
  for (final action in event.actions ?? []) {
    if (action.semantic == 1) { // SEMANTIC_ACTION_REPLY
      final inputs = <String, dynamic>{};
      for (final input in action.inputs ?? []) {
        inputs[input.resultKey ?? ''] = "Auto-reply from Flutter";
      }
      action.postInputs(inputs);
    }
  }
}
```

### Customize Service Notification

```dart
await NotificationsListener.startService(
  foreground: true,
  title: "My App",
  description: "Listening for notifications...",
);
```

---

## 📚 API Reference

### NotificationEvent

| Property | Type | Description |
|----------|------|-------------|
| `uniqueId` | `String` | Unique identifier generated from key |
| `packageName` | `String` | Source application package name |
| `title` | `String?` | Notification title |
| `text` | `String?` | Notification body text |
| `timestamp` | `int` | Post time (milliseconds since epoch) |
| `channelId` | `String?` | Notification channel ID (API 26+) |
| `largeIcon` | `Uint8List?` | Large icon as bytes |
| `canTap` | `bool` | Whether notification has a tap action |
| `actions` | `List<Action>?` | Available notification actions |
| `raw` | `Map<String, dynamic>` | Raw notification data |

**Methods:**

| Method | Returns | Description |
|--------|---------|-------------|
| `tap()` | `Future<bool>` | Trigger the notification's tap action |
| `getFull()` | `Future<dynamic>` | Get complete notification data |

### Action

| Property | Type | Description |
|----------|------|-------------|
| `id` | `int` | Action index |
| `title` | `String?` | Action button text |
| `semantic` | `int` | Semantic type (see below) |
| `inputs` | `List<ActionInput>?` | Input fields for reply actions |

**Semantic Types:**

| Constant | Value | Description |
|----------|-------|-------------|
| `SEMANTIC_ACTION_NONE` | 0 | No semantic |
| `SEMANTIC_ACTION_REPLY` | 1 | Quick reply |
| `SEMANTIC_ACTION_MARK_AS_READ` | 2 | Mark as read |
| `SEMANTIC_ACTION_MARK_AS_UNREAD` | 3 | Mark as unread |
| `SEMANTIC_ACTION_DELETE` | 4 | Delete |
| `SEMANTIC_ACTION_ARCHIVE` | 5 | Archive |
| `SEMANTIC_ACTION_MUTE` | 6 | Mute |
| `SEMANTIC_ACTION_UNMUTE` | 7 | Unmute |

### NotificationsListener

| Method | Description |
|--------|-------------|
| `initialize({callbackHandle})` | Initialize the plugin |
| `hasPermission` | Check notification access permission |
| `isRunning` | Check if service is running |
| `openPermissionSettings()` | Open system permission settings |
| `startService({...})` | Start the listener service |
| `stopService()` | Stop the listener service |
| `promoteToForeground({...})` | Promote service to foreground |
| `demoteToBackground()` | Demote service to background |

---

## ⚠️ Known Issues

- Service may fail to start after reboot if not running in foreground mode.

---

## 💖 Support

If you find this plugin useful, please consider:

- ⭐ Starring the repository
- 🐛 Reporting bugs
- 💡 Suggesting new features
- 🤝 Contributing code

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

<div align="center">

**Made with ❤️ by [Zoe](https://github.com/jiusanzhou)**

</div>
