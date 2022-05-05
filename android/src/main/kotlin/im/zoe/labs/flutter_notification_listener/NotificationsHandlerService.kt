package im.zoe.labs.flutter_notification_listener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import org.json.JSONObject
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap

class NotificationsHandlerService: MethodChannel.MethodCallHandler, NotificationListenerService() {
    private val queue = ArrayDeque<NotificationEvent>()
    private lateinit var mBackgroundChannel: MethodChannel
    private lateinit var mContext: Context

    // notification event cache: packageName_id -> event
    private val eventsCache = HashMap<String, NotificationEvent>()

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
      when (call.method) {
          "service.initialized" -> {
              initFinish()
              return result.success(true)
          }
          "service.promoteToForeground" -> {
              // add data
              val args = call.arguments<ArrayList<*>>()
              return result.success(promoteToForeground(args!![0]))
          }
          "service.demoteToBackground" -> {
              return result.success(demoteToBackground())
          }
          "service.tap" -> {
              // tap the notification
              Log.d(TAG, "tap the notification")
              val args = call.arguments<ArrayList<*>>()
              val pkg = args[0] as String
              val id = args[1] as Int
              return result.success(tapNotification(pkg, id))
          }
          "service.tap_action" -> {
              // tap the action
              Log.d(TAG, "tap action of notification")
              val args = call.arguments<ArrayList<*>>()
              val pkg = args[0] as String
              val id = args[1] as Int
              val idx = args[2] as Int
              return result.success(tapNotificationAction(pkg, id, idx))
          }
          "service.send_input" -> {
              // send the input data
              Log.d(TAG, "set the content for input and the send action")
              val args = call.arguments<ArrayList<*>>()
              val pkg = args[0] as String
              val id = args[1] as Int
              val idx = args[2] as Int
              val data = args[3] as Map<*, *>
              return result.success(sendNotificationInput(pkg, id, idx, data))
          }
          else -> {
              Log.d(TAG, "unknown method ${call.method}")
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

        // store the service instance
        instance = this

        Log.i(TAG, "notification listener service onCreate")
        startListenerService(this)

        // create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            promoteToForeground(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "notification listener service onDestroy")
        val bdi = Intent(mContext, RebootBroadcastReceiver::class.java)
        sendBroadcast(bdi)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "notification listener service onTaskRemoved")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        FlutterInjector.instance().flutterLoader().startInitialization(mContext)
        FlutterInjector.instance().flutterLoader().ensureInitializationComplete(mContext, null)

        val evt = NotificationEvent(mContext, sbn)

        // store the evt to cache
        eventsCache[evt.key] = evt

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
        if (sbn == null) return
        val evt = NotificationEvent(mContext, sbn)
        // remove the event from cache
        eventsCache.remove(evt.key)
        Log.d(TAG, "notification removed: ${evt.key}")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendNotification(
        foreground: Boolean,
        subTitle: String?, showWhen: Boolean,
        title: String?, description: String?
    ): Boolean {

        // first is not running already, start at first
        if (!FlutterNotificationListenerPlugin.isServiceRunning(mContext, this.javaClass)) {
            FlutterNotificationListenerPlugin.internalStartService(mContext)
            return false
        }

        if (!foreground) {
            Log.d(TAG, "args:  don't promote the service to foreground")
            return false
        }

        // take a wake lock
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(10*60*1000L /*10 minutes*/)
            }
        }

        // send a notification
        val channel = NotificationChannel(CHANNEL_ID, "Flutter Notifications Listener Plugin", NotificationManager.IMPORTANCE_HIGH)
        val imageId = resources.getIdentifier("ic_launcher", "mipmap", packageName)

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(description)
            .setShowWhen(showWhen)
            .setSubText(subTitle)
            .setSmallIcon(imageId)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        Log.d(TAG, "promote the service to foreground")
        startForeground(ONGOING_NOTIFICATION_ID, notification)
        return true
    }

    private fun initFinish() {
        Log.d(TAG, "service's flutter engine initialize finished")
        synchronized(sServiceStarted) {
            while (!queue.isEmpty()) sendEvent(queue.remove())
            sServiceStarted.set(true)
        }
    }

    private fun promoteToForeground(str: Any?): Boolean {
        // get args from store or args
        val argsStr = if (str != null) {
            str as String
        } else {
            getSharedPreferences(FlutterNotificationListenerPlugin.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .getString(FlutterNotificationListenerPlugin.PROMOTE_SERVICE_ARGS_KEY, null)
        }

        var foreground = true
        var subTitle: String? = null
        var title = "Notifications Listener"
        var description = "Service is running"
        var showWhen = false

        if (argsStr!=null) {
            try {
                val args = JSONObject(argsStr)
                foreground = args["foreground"] as Boolean
                subTitle = args["subTitle"] as String
                title = args["title"] as String
                description = args["description"] as String
                showWhen = args["showWhen"] as Boolean
            } catch (e: Exception) {
                Log.d(TAG, "parse the args error:", e)
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sendNotification(foreground, subTitle, showWhen, title, description)
        } else {
            TODO("VERSION.SDK_INT < O")
        }
    }

    private fun demoteToBackground(): Boolean {
        Log.d(TAG, "demote the service to background")
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                if (isHeld) release()
            }
        }
        stopForeground(true)
        return true
    }

    private fun tapNotification(pkg: String, id: Int): Boolean {
        val key = NotificationEvent.genKey(pkg, id)
        Log.d(TAG, "tap the notification: $key")
        if (!eventsCache.containsKey(key)) {
            Log.d(TAG, "notification is not exits: $key")
            return false
        }
        val n = eventsCache[key] ?: return false
        n.mSbn.notification.contentIntent.send()
        return true
    }

    private fun tapNotificationAction(pkg: String, id: Int, idx: Int): Boolean {
        val key = NotificationEvent.genKey(pkg, id)
        Log.d(TAG, "tap the notification action: $key @$idx")
        if (!eventsCache.containsKey(key)) {
            Log.d(TAG, "notification is not exits: $key")
            return false
        }
        val n = eventsCache[key]
        if (n == null) {
            Log.e(TAG, "notification is null: $key")
            return false
        }
        val act = n.mSbn.notification.actions[idx]
        if (act == null) {
            Log.e(TAG, "notification $key action $idx not exits")
            return false
        }
        act.actionIntent.send()
        return true
    }

    private fun sendNotificationInput(pkg: String, id: Int, idx: Int, data: Map<*, *>): Boolean {
        val key = NotificationEvent.genKey(pkg, id)
        Log.d(TAG, "tap the notification action: $key @$idx")
        if (!eventsCache.containsKey(key)) {
            Log.d(TAG, "notification is not exits: $key")
            return false
        }
        val n = eventsCache[key]
        if (n == null) {
            Log.e(TAG, "notification is null: $key")
            return false
        }
        val act = n.mSbn.notification.actions[idx]
        if (act == null) {
            Log.e(TAG, "notification $key action $idx not exits")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            if (act.remoteInputs == null) {
                Log.e(TAG, "notification $key action $idx remote inputs not exits")
                return false
            }

            val intent = Intent()
            val bundle = Bundle()
            act.remoteInputs.forEach {
                if (data.containsKey(it.resultKey as String)) {
                    Log.d(TAG, "add input content: ${it.resultKey} => ${data[it.resultKey]}")
                    bundle.putCharSequence(it.resultKey, data[it.resultKey] as String)
                }
            }
            RemoteInput.addResultsToIntent(act.remoteInputs, intent, bundle)
            act.actionIntent.send(mContext, 0, intent)
            Log.d(TAG, "send the input action success")
            return true
        } else {
            Log.e(TAG, "not implement :sdk < KITKAT_WATCH")
            return false
        }
    }

    companion object {

        var callbackHandle = 0L

        @JvmStatic
        var instance: NotificationsHandlerService? = null

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

        fun updateFlutterEngine(context: Context) {
            Log.d(TAG, "call instance update flutter engine from plugin init")
            instance?.updateFlutterEngine(context)
            // we need to `finish init` manually
            instance?.initFinish()
        }
    }

    private fun getFlutterEngine(context: Context): FlutterEngine {
        var eng = FlutterEngineCache.getInstance().get(FlutterNotificationListenerPlugin.FLUTTER_ENGINE_CACHE_KEY)
        if (eng != null) return eng

        Log.i(TAG, "flutter engine cache is null, create a new one")
        eng = FlutterEngine(context)

        // ensure initialization
        FlutterInjector.instance().flutterLoader().startInitialization(context)
        FlutterInjector.instance().flutterLoader().ensureInitializationComplete(context, arrayOf())

        // call the flutter side init
        // get the call back handle information
        val cb = context.getSharedPreferences(FlutterNotificationListenerPlugin.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
            .getLong(FlutterNotificationListenerPlugin.CALLBACK_DISPATCHER_HANDLE_KEY, 0)

        if (cb != 0L) {
            Log.d(TAG, "try to find callback: $cb")
            val info = FlutterCallbackInformation.lookupCallbackInformation(cb)
            val args = DartExecutor.DartCallback(context.assets,
                FlutterInjector.instance().flutterLoader().findAppBundlePath(), info)
            // call the callback
            eng.dartExecutor.executeDartCallback(args)
        } else {
            Log.e(TAG, "Fatal: no callback register")
        }

        FlutterEngineCache.getInstance().put(FlutterNotificationListenerPlugin.FLUTTER_ENGINE_CACHE_KEY, eng)
        return eng
    }

    private fun updateFlutterEngine(context: Context) {
        Log.d(TAG, "update the flutter engine of service")
        // take the engine
        val eng = getFlutterEngine(context)
        sBackgroundFlutterEngine = eng

        // set the method call
        mBackgroundChannel = MethodChannel(eng.dartExecutor.binaryMessenger, BG_METHOD_CHANNEL_NAME)
        mBackgroundChannel.setMethodCallHandler(this)
    }

    private fun startListenerService(context: Context) {

        Log.d(TAG, "on call start listener service")

        synchronized(sServiceStarted) {
            mContext = context

            // we should to update
            Log.d(TAG, "service's flutter engine is null, should update one")
            updateFlutterEngine(context)

            sServiceStarted.set(true)
        }

        Log.d(TAG, "service init finished")

    }

    private fun sendEvent(evt: NotificationEvent) {
        Log.d(TAG, "send notification event: ${evt.data}")
        if (callbackHandle == 0L) {
            callbackHandle = mContext.getSharedPreferences(FlutterNotificationListenerPlugin.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .getLong(FlutterNotificationListenerPlugin.CALLBACK_HANDLE_KEY, 0)
        }

        // why mBackgroundChannel can be null?

        try {
            // don't care about the method name
            mBackgroundChannel.invokeMethod("sink_event", listOf(callbackHandle, evt.data))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}

