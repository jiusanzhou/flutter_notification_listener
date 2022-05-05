package im.zoe.labs.flutter_notification_listener

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationsListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        try {
            val evt = NotificationEvent.fromSbn(applicationContext, sbn)

            val intent = Intent(NOTIFICATION_INTENT)
            intent.putExtra(NOTIFICATION_INTENT_KEY, evt.toString())

            sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    companion object {
        const val NOTIFICATION_INTENT = "notification_event"
        const val NOTIFICATION_INTENT_KEY = "object";
    }
}