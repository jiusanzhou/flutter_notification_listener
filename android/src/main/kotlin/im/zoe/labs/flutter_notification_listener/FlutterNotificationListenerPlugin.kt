package im.zoe.labs.flutter_notification_listener

import android.app.ActivityManager
import android.content.*
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.*


class FlutterNotificationListenerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
  private var eventSink: EventChannel.EventSink? = null

  private lateinit var mContext: Context

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Log.i(TAG, "on attached to engine")

    mContext = flutterPluginBinding.applicationContext

    val binaryMessenger = flutterPluginBinding.binaryMessenger

    // event stream channel
    EventChannel(binaryMessenger, EVENT_CHANNEL_NAME).setStreamHandler(this)
      // method channel
    MethodChannel(binaryMessenger, METHOD_CHANNEL_NAME).setMethodCallHandler(this)

    // TODO: remove those code
    val receiver = NotificationReceiver()
    val intentFilter = IntentFilter()
    intentFilter.addAction(NotificationsHandlerService.NOTIFICATION_INTENT)
    mContext.registerReceiver(receiver, intentFilter)

    Log.i(TAG, "attached engine finished")
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    // methodChannel.setMethodCallHandler(null)
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  override fun onListen(o: Any?, eventSink: EventChannel.EventSink?) {
    this.eventSink = eventSink
  }

  override fun onCancel(o: Any?) {
    eventSink = null
  }

  internal inner class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      eventSink?.success(intent.getStringExtra(NotificationsHandlerService.NOTIFICATION_INTENT_KEY)?:"{}")
    }
  }

  companion object {
    const val TAG = "NOTIFICATION_PLUGIN"

    private const val EVENT_CHANNEL_NAME = "flutter_notification_listener/events"
    private const val METHOD_CHANNEL_NAME = "flutter_notification_listener/method"

    const val SHARED_PREFERENCES_KEY = "flutter_notification_cache"

    const val CALLBACK_DISPATCHER_HANDLE_KEY = "callback_dispatch_handler"
    const val CALLBACK_HANDLE_KEY = "callback_handler"

    private val sNotificationCacheLock = Object()

    fun registerAfterReboot(context: Context) {
      synchronized(sNotificationCacheLock) {
        startService(context)
      }
    }

    private fun initialize(context: Context, args: ArrayList<*>?) {
      Log.d(TAG, "install callback dispatch ...")
      val callbackHandle = args!![0] as Long
      context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        .edit()
        .putLong(CALLBACK_DISPATCHER_HANDLE_KEY, callbackHandle)
        .apply()
    }

    fun startService(context: Context): Boolean {
      if (!NotificationsHandlerService.permissionGiven(context)) {
        return false
      }

      // and try to toggle the service to trigger rebind
      with(NotificationsHandlerService) {
        /* Start the notification service once permission has been given. */
        val listenerIntent = Intent(context, NotificationsHandlerService::class.java)
        context.startService(listenerIntent)

        // and try to toggle the service to trigger rebind
        disableServiceSettings(context)
        enableServiceSettings(context)
      }

      return true
    }

    fun stopService(context: Context): Boolean {
      if (!isServiceRunning(context, NotificationsHandlerService::class.java)) return true

      val intent = Intent(context, NotificationsHandlerService::class.java)
      intent.action = NotificationsHandlerService.ACTION_SHUTDOWN
      context.startService(intent)
      // context.stopService(intent)
      return true
    }

    @Suppress("DEPRECATION")
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
      val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
      for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name.equals(service.service.className)) {
          return true
        }
      }
      return false
    }

    fun registerEventHandle(context: Context, args: ArrayList<*>?) {
      val callbackHandle = args!![0] as Long
      context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        .edit()
        .putLong(CALLBACK_HANDLE_KEY, callbackHandle)
        .apply()
    }
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    val args = call.arguments<ArrayList<*>>()
    when (call.method) {
      "plugin.initialize" -> {
        initialize(mContext, args)
        return result.success(true)
      }
      "plugin.startService" -> {
        return result.success(startService(mContext))
      }
      "plugin.stopService" -> {
        return result.success(stopService(mContext))
      }
      "plugin.hasPermission" -> {
        return result.success(NotificationsHandlerService.permissionGiven(mContext))
      }
      "plugin.openPermissionSettings" -> {
        return result.success(NotificationsHandlerService.openPermissionSettings(mContext))
      }
      "plugin.isServiceRunning" -> {
        return result.success(
          isServiceRunning(
            mContext,
            NotificationsHandlerService::class.java
          )
        )
      }
      "plugin.registerEventHandle" -> {
        return registerEventHandle(mContext, args)
      }
      // TODO: register handle with filter
      "setFilter" -> {
        // TODO
      }
      else -> result.notImplemented()
    }
  }
}
