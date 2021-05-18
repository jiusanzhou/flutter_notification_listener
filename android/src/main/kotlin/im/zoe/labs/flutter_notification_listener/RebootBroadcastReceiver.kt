package im.zoe.labs.flutter_notification_listener

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log


class RebootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.e("NotificationListener", "Registering notification listener, after reboot!")
            FlutterNotificationListenerPlugin.registerAfterReboot(context)
        } else {
            Log.i("NotificationListener", intent.action.toString())
        }
    }
}