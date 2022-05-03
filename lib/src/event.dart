import 'dart:convert';
import 'dart:typed_data';

/// NotificationEvent is the object converted from notification
/// Notification anatomy:
///   https://developer.android.com/guide/topics/ui/notifiers/notifications
class NotificationEvent {
  /// the notification id
  int? id;

  /// the notification create time in flutter side
  DateTime? createAt;

  /// the nofication create time in the android side
  int? timestamp;

  /// the package name of the notification
  String? packageName;

  /// the title of the notification
  String? title;

  /// the content of the notification
  String? text;

  /// DEPRECATE
  String? message;

  /// icon of the notification which setted by setSmallIcon, 
  /// at most time this is icon of the application package.
  /// So no need to set this, use a method to take from android.
  /// To display as a image use the Image.memory widget.
  /// Example:
  /// 
  /// ```
  /// Image.memory(evt.icon)
  /// ```
  // Uint8List? icon;

  /// if we have the large icon
  bool? hasLargeIcon;
  
  /// large icon of the notification which setted by setLargeIcon.
  /// To display as a image use the Image.memory widget.
  /// Example:
  /// 
  /// ```
  /// Image.memory(evt.largeIcon)
  /// ```
  Uint8List? largeIcon;

  /// the raw notifaction data from android
  dynamic _data;

  NotificationEvent({
    this.id,
    this.createAt,
    this.packageName,
    this.title,
    this.text,
    this.message,
    this.timestamp,
    // this.icon,
    this.hasLargeIcon,
    this.largeIcon,
  });

  Map<dynamic, dynamic>? get raw => _data;

  /// Create the event from a map
  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {
    map['hasLargeIcon'] = map['largeIcon'] != null && (map['largeIcon'] as Uint8List).isNotEmpty;
    return NotificationEvent(
        createAt: DateTime.now(),
        id: map['id'],
        packageName: map['package_name'],
        title: map['title'],
        text: map['text'],
        message: map["message"],
        timestamp: map["timestamp"],
        // icon: map['icon'],
        hasLargeIcon: map['hasLargeIcon'],
        largeIcon: map['largeIcon'],
    )
      .._data = map;
  }

  @override
  String toString() {
    var tmp = Map<dynamic, dynamic>.from(this._data)
      ..remove('icon')
      ..remove('largeIcon');
    return json.encode(tmp).toString();
  }
}

/// newEvent package level function create event from map
NotificationEvent newEvent(Map<dynamic, dynamic> data) {
  return NotificationEvent.fromMap(data);
}
