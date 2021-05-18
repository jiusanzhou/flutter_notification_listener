

import 'dart:convert';

class NotificationEvent {
  DateTime timestamp;
  String packageName;
  String title;
  String text;
  String message;
  dynamic extra;

  dynamic _data;

  NotificationEvent({
    this.timestamp,
    this.packageName,
    this.title,
    this.text,
    this.message,
  });

  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {

    return NotificationEvent(
      timestamp: DateTime.now(),

      packageName: map['package_name'],
      title: map['title'],
      text: map['text'],
      message: map["message"],
    ).._data = map;
  }

  @override
  String toString() {
      return json.encode(this._data).toString();
  }
}

NotificationEvent newEvent(Map<dynamic, dynamic> data) {
  return NotificationEvent.fromMap(data);
}