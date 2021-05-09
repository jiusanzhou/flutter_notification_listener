package im.zoe.labs.flutter_notification_listener

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.annotation.NonNull
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class NotificationsCallHandler constructor(
    private val context: Context,
    private val plugin: FlutterNotificationListenerPlugin
) : MethodChannel.MethodCallHandler {
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
      when (call.method) {
          "hasPermission" -> {
              return result.success(permissionGiven(context))
          }
          "openPermissionSettings" -> {
              return result.success(openPermissionSettings(context))
          }
          "startService" -> {
              return result.success(plugin.startService())
          }
          "setFilter" -> {
              // TODO
          }
          else -> {
              result.notImplemented()
          }
      }
    }

    companion object {

        private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
        private const val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"

        /**
         * For all enabled notification listeners, check if any of them matches the package name of this application.
         * If any match is found, return true. Otherwise if no matches were found, return false.
         */
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
    }
}

