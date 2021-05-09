
import 'dart:io';

import 'package:flutter/services.dart';

import './event.dart';

class AndroidNotificationListener {
  static const MethodChannel _methodChannel =
      const MethodChannel('flutter_notification_listener/method');

  static const EventChannel _eventChannel =
    const EventChannel('flutter_notification_listener/events');

  Stream<NotificationEvent> _notificationStream;

  Stream<NotificationEvent> get notificationStream {
    if (Platform.isAndroid) {

      if (_notificationStream == null) {
        _notificationStream = _eventChannel
            .receiveBroadcastStream()
            .map((event) => newEvent(event));
      }
      return _notificationStream;
    }
    throw Exception(
        'Notification API exclusively available on Android!');
  }

  Future<bool> get hasPermission async {
    return await _methodChannel.invokeMethod('hasPermission');
  }

  Future<void> openPermissionSettings() async {
    return await _methodChannel.invokeMethod('openPermissionSettings');
  }
}
