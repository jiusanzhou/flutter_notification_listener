package im.zoe.labs.flutter_notification_listener

import android.app.Notification
import android.content.*
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel


class FlutterNotificationListenerPlugin : FlutterPlugin, EventChannel.StreamHandler {

  private lateinit var methodChannel : MethodChannel
  private lateinit var eventChannel : EventChannel

  private var eventSink: EventChannel.EventSink? = null
  private var methodHandler : NotificationsCallHandler? = null

  private lateinit var mContext: Context

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Log.e(TAG, "on attached to engine")

    mContext = flutterPluginBinding.applicationContext

    methodHandler = NotificationsCallHandler(mContext, this)

    // setup the channel

    val binaryMessenger = flutterPluginBinding.binaryMessenger

    // event stream channel
    EventChannel(binaryMessenger, EVENT_CHANNEL_NAME).setStreamHandler(this)

      // method channel
    MethodChannel(binaryMessenger, METHOD_CHANNEL_NAME).setMethodCallHandler(methodHandler)

    initPlugin()
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    methodChannel.setMethodCallHandler(null)
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  override fun onListen(o: Any?, eventSink: EventChannel.EventSink?) {
    this.eventSink = eventSink

    val listenerIntent = Intent(mContext, NotificationsListener::class.java)
    mContext.startService(listenerIntent)
  }

  override fun onCancel(o: Any?) {
    eventSink = null
  }

  internal inner class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

      val map = HashMap<String, Any?>()
      map[NotificationsListener.NOTIFICATION_PACKAGE_NAME] = intent.getStringExtra(NotificationsListener.NOTIFICATION_PACKAGE_NAME)
      map[NotificationsListener.NOTIFICATION_TITLE] = intent.getStringExtra(NotificationsListener.NOTIFICATION_TITLE)
      map[NotificationsListener.NOTIFICATION_TEXT] = intent.getStringExtra(NotificationsListener.NOTIFICATION_TEXT)
      map[NotificationsListener.NOTIFICATION_MESSAGE] = intent.getStringExtra(NotificationsListener.NOTIFICATION_MESSAGE)
      map[NotificationsListener.NOTIFICATION_EXTRA] = intent.getStringExtra(NotificationsListener.NOTIFICATION_EXTRA)

      eventSink?.success(map)
    }
  }

  companion object {
    const val TAG = "NOTIFICATION_PLUGIN"

    private const val EVENT_CHANNEL_NAME = "flutter_notification_listener/events"
    private const val METHOD_CHANNEL_NAME = "flutter_notification_listener/method"
  }

  fun startService(): Boolean {
    if (!NotificationsCallHandler.permissionGiven(mContext)) return false;
    /* Start the notification service once permission has been given. */
    val listenerIntent = Intent(mContext, NotificationsListener::class.java)
    mContext.startService(listenerIntent)
    return true
  }

  private fun initPlugin() {
    val receiver = NotificationReceiver()
    val intentFilter = IntentFilter()
    intentFilter.addAction(NotificationsListener.NOTIFICATION_INTENT)
    mContext.registerReceiver(receiver, intentFilter)

    startService()
  }
}
