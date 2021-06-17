<div align="center">

# flutter_notification_listener

Flutter Plugin to listen to all incoming notifications for Android.

> :warning: This plugin is Android only.

[![Version](https://img.shields.io/pub/v/flutter_notification_listener.svg)](https://pub.dartlang.org/packages/flutter_notification_listener)
[![License](https://img.shields.io/badge/license-AL2-blue.svg)](https://github.com/jiusanzhou/flutter_notification_listener/blob/master/LICENSE)


</div>

### Features

- **Service**: start a service to listen the notifications.
- **Easy**: you can get the notifaction fields: `timestamp`, `title`, `message` and `package`.
- **Backgrounded**: execute the dart code in the background.

**Note:** If have any fields to add, feel free to pull request.

### Get Start

**0. 💻 Install package**

In the `dependencies:` section of your `pubspec.yaml`, add the following line:

[![Version](https://img.shields.io/pub/v/flutter_notification_listener.svg)](https://pub.dartlang.org/packages/flutter_notification_listener)

```
flutter_notification_listener: <latest_version>
```

**1. Register the service in the manifest**

The plugin uses an Android system service to track notifications. To allow this service to run on your application, the following code should be put inside the Android manifest, between the tags.

```xml
<service android:name="im.zoe.labs.flutter_notification_listener.NotificationsHandlerService"
    android:label="Flutter Notifications Handler"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

If you want to start the service after reboot, also should put the following code.

```xml
<receiver android:name="im.zoe.labs.flutter_notification_listener.RebootBroadcastReceiver"
    android:enabled="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

**2. Init the plugin and add listen handelr**

```dart
void onData(NotificationEvent event) {
    print(event.toString());
}

Future<void> initPlatformState() async {
    NotificationsListener.initialize();
    NotificationsListener.receivePort.listen((evt) => onData(evt));

    // or you can register your static function
    NotificationsListener.initialize(callbackHandle: _callback);
}

static void _callback(NotificationEvent evt) {
    print("send evt to ui: $evt");
    final SendPort send = IsolateNameServer.lookupPortByName("_listener_");
    if (send == null) print("can't find the sender");
    send?.send(evt);
}
```

**3. Check permission and start the service**


```dart
void startListening() async {
    print("start listening");
    var hasPermission = await NotificationsListener.hasPermission;
    if (!hasPermission) {
        print("no permission, so open settings");
        NotificationsListener.openPermissionSettings();
        return;
    }

    var isR = await NotificationsListener.isRunning;

    if (!isR) {
        await NotificationsListener.startService();
    }

    setState(() => started = true);
}
```

Please check the [./example/lib/main.dart](./example/lib/main.dart) for more detail.

### APIs

**NotificationsListener** static methods or fields.

- **hasPermission()**: check if we have grant the listen notifaction permission.
- **openPermissionSettings()**: open the system listen notifactoin permission setting page.
- **initialize()**: int the service, this should be called at first.
- **isRunning**: check if the service is already running.
- **startService()**: start the listening service.
- **stopService()**: stop the listening service.
- **registerEventHandle(Function callback)**:  register the event handler which will be called from android service, **shoube be static function**.

