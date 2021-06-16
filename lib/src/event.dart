

import 'dart:convert';

class NotificationEvent {
  DateTime createAt;
  int timestamp;
  String packageName;
  String title;
  String text;
  String message;

  dynamic _data;

  NotificationEvent({
    this.createAt,
    this.packageName,
    this.title,
    this.text,
    this.message,
    this.timestamp,
  });

  Map<dynamic, dynamic> get raw => _data;

  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {

    return NotificationEvent(
      createAt: DateTime.now(),
      packageName: map['package_name'],
      title: map['title'],
      text: map['text'],
      message: map["message"],
      timestamp: map["timestamp"]
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