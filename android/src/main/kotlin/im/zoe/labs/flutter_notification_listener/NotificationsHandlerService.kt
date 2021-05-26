package im.zoe.labs.flutter_notification_listener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class NotificationsHandlerService: MethodChannel.MethodCallHandler, NotificationListenerService() {
    private val queue = ArrayDeque<Any>()
    private lateinit var mBackgroundChannel: MethodChannel
    private lateinit var mContext: Context

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
      when (call.method) {
          "service.initialized" -> {
              Log.d(TAG, "service initialized")
              synchronized(sServiceStarted) {
                  while (!queue.isEmpty()) sendEvent(queue.remove())
                  sServiceStarted.set(true)
              }
          }
          "service.promoteToForeground" -> {
              // add data
              val title = call.argument<String?>("title")
              val content = call.argument<String?>("content")

              // first is not running already, start at first
              if (!FlutterNotificationListenerPlugin.isServiceRunning(mContext, this.javaClass)) {
                  FlutterNotificationListenerPlugin.startService(mContext)
              }

              // take a wake lock
              (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                  newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                      setReferenceCounted(false)
                      acquire()
                  }
              }

              // send a notification
              val channel = NotificationChannel(CHANNEL_ID, "Flutter Notifications Listener Plugin", NotificationManager.IMPORTANCE_LOW)
              val imageId = resources.getIdentifier("ic_launcher", "mipmap", packageName)

              (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
              val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                  .setContentTitle(title).setContentText(content).setSmallIcon(imageId).setPriority(
                      NotificationCompat.PRIORITY_HIGH).build()

              Log.d(TAG, "promote the service to foreground")
              startForeground(ONGOING_NOTIFICATION_ID, notification)
          }
          "service.demoteToBackground" -> {
              Log.d(TAG, "demote the service to background")
              (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                  newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                      if (isHeld) release()
                  }
              }
              stopForeground(true)
          }
          else -> {
              result.notImplemented()
          }
      }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // if get shutdown release the wake lock
        when (intent?.action) {
            ACTION_SHUTDOWN -> {
                (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                    newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                        if (isHeld) release()
                    }
                }
                Log.d(TAG, "stop notification handler service!")
                disableServiceSettings(mContext)
                stopForeground(true)
                stopSelf()
            }
            else -> {

            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startListenerService(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        FlutterInjector.instance().flutterLoader().startInitialization(mContext)
        FlutterInjector.instance().flutterLoader().ensureInitializationComplete(mContext, null)

        val evt = NotificationEvent.fromSbn(sbn)

        synchronized(sServiceStarted) {
            if (!sServiceStarted.get()) {
                Log.d(TAG, "service is not start try to queue the event")
                queue.add(evt)
            } else {
                Log.d(TAG, "send event to flutter side immediately!")
                Handler(mContext.mainLooper).post { sendEvent(evt) }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    companion object {

        var callbackHandle = 0L

        @JvmStatic
        private val TAG = "NotificationsListenerService"

        // private val JOB_ID = UUID.randomUUID().mostSignificantBits.toInt()
        private const val ONGOING_NOTIFICATION_ID = 100
        @JvmStatic
        private val WAKELOCK_TAG = "IsolateHolderService::WAKE_LOCK"
        @JvmStatic
        val ACTION_SHUTDOWN = "SHUTDOWN"

        private const val CHANNEL_ID = "flutter_notifications_listener_channel"

        @JvmStatic
        private var sBackgroundFlutterEngine: FlutterEngine? = null
        @JvmStatic
        private val sServiceStarted = AtomicBoolean(false)

        private const val BG_METHOD_CHANNEL_NAME = "flutter_notification_listener/bg_method"

        private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
        private const val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"

        const val NOTIFICATION_INTENT_KEY = "object"
        const val NOTIFICATION_INTENT = "notification_event"

        fun permissionGiven(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":").toTypedArray()
                for (name in names) {
                    val componentName = ComponentName.unflattenFromString(name)
                    val nameMatch = TextUtils.equals(packageName, componentName?.packageName)
                    if (nameMatch) {
                        return true
                    }
                }
            }

            return false
        }

        fun openPermissionSettings(context: Context): Boolean {
            context.startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }

        fun enableServiceSettings(context: Context) {
            toggleServiceSettings(context, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        }

        fun disableServiceSettings(context: Context) {
            toggleServiceSettings(context, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }

        private fun toggleServiceSettings(context: Context, state: Int) {
            val receiver = ComponentName(context, NotificationsHandlerService::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)
        }
    }

    private fun startListenerService(context: Context) {

        Log.d(TAG, "on call start listener service")

        synchronized(sServiceStarted) {
            mContext = context

            Log.d(TAG, "get the lock")

            // already started
            if (sBackgroundFlutterEngine == null) {

                Log.d(TAG, "ok let's init")

                // start the bg flutter engine
                val callbackDispatchHandle = context.getSharedPreferences(FlutterNotificationListenerPlugin.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                    .getLong(FlutterNotificationListenerPlugin.CALLBACK_DISPATCHER_HANDLE_KEY, 0)

                if (callbackDispatchHandle == 0L) {
                    Log.e(TAG, "Fatal: no callback register")
                    return
                }

                val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackDispatchHandle)

                Log.i(TAG, "create flutter engine")
                sBackgroundFlutterEngine = FlutterEngine(context)

                val args = DartExecutor.DartCallback(context.assets, FlutterInjector.instance().flutterLoader().findAppBundlePath(), callbackInfo)

                // register callback handle
                sBackgroundFlutterEngine!!.dartExecutor.executeDartCallback(args)
            }

        }

        Log.d(TAG, "init finished")

        mBackgroundChannel = MethodChannel(sBackgroundFlutterEngine!!.dartExecutor.binaryMessenger, BG_METHOD_CHANNEL_NAME)
        mBackgroundChannel.setMethodCallHandler(this)
    }

    private fun sendEvent(evt: Any) {
        Log.d(TAG, "send notification event: $evt")
        if (callbackHandle == 0L) {
            callbackHandle = mContext.getSharedPreferences(FlutterNotificationListenerPlugin.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .getLong(FlutterNotificationListenerPlugin.CALLBACK_HANDLE_KEY, 0)
        }

        // why mBackgroundChannel can be null?

        // don't care about the method name
        mBackgroundChannel.invokeMethod("sink_event", listOf(callbackHandle, evt))
    }

}

