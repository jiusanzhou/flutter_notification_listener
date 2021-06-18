import 'dart:convert';

/// NotificationEvent is the object converted from notification
class NotificationEvent {
  DateTime? createAt;
  int? timestamp;
  String? packageName;
  String? title;
  String? text;
  String? message;

  dynamic _data;

  NotificationEvent({
    this.createAt,
    this.packageName,
    this.title,
    this.text,
    this.message,
    this.timestamp,
  });

  Map<dynamic, dynamic>? get raw => _data;

  /// Create the event from a map
  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {
    return NotificationEvent(
        createAt: DateTime.now(),
        packageName: map['package_name'],
        title: map['title'],
        text: map['text'],
        message: map["message"],
        timestamp: map["timestamp"])
      .._data = map;
  }

  @override
  String toString() {
    return json.encode(this._data).toString();
  }
}

/// newEvent package level function create event from map
NotificationEvent newEvent(Map<dynamic, dynamic> data) {
  return NotificationEvent.fromMap(data);
}
