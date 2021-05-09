import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'dart:async';

import 'package:flutter_notification_listener/flutter_notification_listener.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: NotificationsLog(),
    );
  }
}

class NotificationsLog extends StatefulWidget {
  @override
  _NotificationsLogState createState() => _NotificationsLogState();
}

class _NotificationsLogState extends State<NotificationsLog> {
  AndroidNotificationListener _notifications;
  StreamSubscription<NotificationEvent> _subscription;
  List<NotificationEvent> _log = [];
  bool started = false;

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    _notifications = AndroidNotificationListener();
  }

  void onData(NotificationEvent event) {
    setState(() {
      _log.add(event);
    });
    print(event.toString());
  }

  void startListening() async {
    var hasPermission = await _notifications.hasPermission;
    if (!hasPermission) {
      _notifications.openPermissionSettings();
      return;
    }

    try {
      _subscription = _notifications.notificationStream.listen(onData);
      setState(() => started = true);
    } on Exception catch (exception) {
      print(exception);
    }
  }

  void stopListening() {
    _subscription.cancel();
    setState(() => started = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
          title: Text('Notifications Listener Example'),
        ),
        body: Center(
            child: ListView.builder(
                itemCount: _log.length,
                reverse: true,
                itemBuilder: (BuildContext context, int idx) {
                  final entry = _log[idx];
                  return ListTile(
                      trailing: Text(entry.packageName.toString().split('.').last),
                      title: Container(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(entry.title??"<<no title>>"),
                            Text(entry.timestamp.toString().substring(0, 19)),
                          ],
                        ),
                      ));
                })),
        floatingActionButton: FloatingActionButton(
          onPressed: started ? stopListening : startListening,
          tooltip: 'Start/Stop sensing',
          child: started ? Icon(Icons.stop) : Icon(Icons.play_arrow),
        ),
      );
  }
}

class PermissionLand extends StatefulWidget {
  @override
  _PermissionLandState createState() => _PermissionLandState();
}

class _PermissionLandState extends State<PermissionLand> {

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      
    );
  }
}