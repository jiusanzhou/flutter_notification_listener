import 'dart:isolate';
import 'dart:ui';
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import './event.dart';

typedef EventCallbackFunc = void Function(NotificationEvent evt);

class NotificationsListener {
  static const CHANNELID = "flutter_notification_listener";
  static const SEND_PORT_NAME = "notifications_send_port";

  static const MethodChannel _methodChannel =
      const MethodChannel('$CHANNELID/method');

  static const MethodChannel _bgMethodChannel =
      const MethodChannel('$CHANNELID/bg_method');

  static ReceivePort _receivePort;

  static ReceivePort get receivePort {
    if (_receivePort == null) {
      _receivePort = ReceivePort();
      // remove the old one at first.
      IsolateNameServer.removePortNameMapping(SEND_PORT_NAME);
      IsolateNameServer.registerPortWithName(_receivePort.sendPort, SEND_PORT_NAME);
    }
    return _receivePort;
  }

  static Future<bool> get hasPermission async {
    return await _methodChannel.invokeMethod('plugin.hasPermission');
  }

  static Future<void> openPermissionSettings() async {
    return await _methodChannel.invokeMethod('plugin.openPermissionSettings');
  }

  // Initialize the plugin and request relevant permissions from the user.
  static Future<void> initialize({
    EventCallbackFunc callbackHandle = _defaultCallbackHandle,
  }) async {
    assert(callbackHandle != null);
    
    final CallbackHandle _callbackDispatch =
        PluginUtilities.getCallbackHandle(callbackDispatcher);
    await _methodChannel.invokeMethod('plugin.initialize',
        <dynamic>[_callbackDispatch.toRawHandle()]);

    // register event handler
    // register the default event handler
    await registerEventHandle(callbackHandle);
  }

  static Future<void> registerEventHandle(EventCallbackFunc callback) async {
    final CallbackHandle  _callback =
        PluginUtilities.getCallbackHandle(callback);
    await _methodChannel.invokeMethod('plugin.registerEventHandle',
        <dynamic>[_callback.toRawHandle()]);
  }

  static Future<bool> get isRunning async {
    return await _methodChannel.invokeMethod('plugin.isServiceRunning');
  }

  static Future<bool> startService() async {
    return await _methodChannel.invokeMethod('plugin.startService');
  }

  static Future<bool> stopService() async {
    return await _methodChannel.invokeMethod('plugin.stopService');
  }

  static Future<void> promoteToForeground(String title, String content) async =>
      await _bgMethodChannel.invokeMethod('service.promoteToForeground', {
        "title": title, "content": content,
      });

  static Future<void> demoteToBackground() async =>
      await _bgMethodChannel.invokeMethod('service.demoteToBackground');

  static void _defaultCallbackHandle(NotificationEvent evt) {
    final SendPort _send = IsolateNameServer.lookupPortByName(SEND_PORT_NAME);
    print("[default callback handler] [send isolate nameserver]");
    if (_send == null) print("IsolateNameServer: can not find send $SEND_PORT_NAME");
    _send?.send(evt);
  }
}

void callbackDispatcher() {

  WidgetsFlutterBinding.ensureInitialized();

  NotificationsListener._bgMethodChannel.setMethodCallHandler((MethodCall call) async {
    try {
      switch (call.method) {
        case "sink_event": {
          final List<dynamic> args = call.arguments;
          final evt = NotificationEvent.fromMap(args[1]);

          final Function callback = PluginUtilities.getCallbackFromHandle(
              CallbackHandle.fromRawHandle(args[0]));

          if (callback == null) {
            print("callback is not register: ${args[0]}");
            return;
          }

          callback(evt);
        }
        break;
        default: {
          print("unknown bg_method: ${call.method}");
        }
      }

    } catch (e) {
      print(e);
    }
  });

  NotificationsListener._bgMethodChannel.invokeMethod('service.initialized');
}