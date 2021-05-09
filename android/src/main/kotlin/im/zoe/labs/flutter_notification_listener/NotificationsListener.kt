package im.zoe.labs.flutter_notification_listener

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Field
import java.time.LocalDateTime


class NotificationsListener : NotificationListenerService {
    constructor(): super()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Retrieve package name to set as title.
        val packageName = sbn?.packageName

        // filter at here

        // Retrieve extra object from notification to extract payload.
        val notify = sbn?.notification
        val extras = notify?.extras


        val intent = Intent(NOTIFICATION_INTENT)

        intent.putExtra(NOTIFICATION_PACKAGE_NAME, packageName)

        intent.putExtra(NOTIFICATION_TITLE, extras?.getString(Notification.EXTRA_TITLE))
        intent.putExtra(NOTIFICATION_TEXT, extras?.getString(Notification.EXTRA_TEXT))
        intent.putExtra(NOTIFICATION_MESSAGE, extras?.getString(Notification.EXTRA_MESSAGES))

        // TODO: improve data at here
        intent.putExtra(NOTIFICATION_EXTRA, convertBumbleToJsonString(extras))
        // intent.putExtra(NOTIFICATION_OBJECT, JSONObject(getNotifyInfo(notify)).toString())

        sendBroadcast(intent)

        super.onNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    companion object {
        const val NOTIFICATION_INTENT = "notification_event"
        const val NOTIFICATION_PACKAGE_NAME = "package_name"

        const val NOTIFICATION_EXTRA = "extra";
        const val NOTIFICATION_OBJECT = "notification_object";

        const val NOTIFICATION_TITLE = "title";
        const val NOTIFICATION_TEXT = "text";
        const val NOTIFICATION_MESSAGE = "message"
        const val NOTIFICATION_TIMESTAMP = "timestamp"
    }
}

private fun convertBumbleToJsonString(extra: Bundle?): String? {
    if (extra == null) return null
    val json = JSONObject()
    val keys = extra.keySet()
    for (key in keys) {
        try {
            json.put(key, JSONObject.wrap(extra.get(key)))
        } catch (e: JSONException) {
            //Handle exception here
        }
    }
    return json.toString()
}


private fun getNotifyInfo(notification: Notification?): Map<String, Any?>? {
    var key = 0
    if (notification == null) return null
    val views: RemoteViews = notification.contentView ?: return null
    val secretClass: Class<*> = views.javaClass
    try {
        val text: MutableMap<String, Any?> = HashMap()
        val outerFields: Array<Field> = secretClass.declaredFields
        for (i in outerFields.indices) {
            if (!outerFields[i].getName().equals("mActions")) continue
            outerFields[i].setAccessible(true)
            val actions = outerFields[i].get(views) as ArrayList<Any>
            for (action in actions) {
                val innerFields: Array<Field> = action.javaClass.declaredFields
                var value: Any? = null
                var type: Int? = null
                for (field in innerFields) {
                    field.setAccessible(true)
                    if (field.getName().equals("value")) {
                        value = field.get(action)
                    } else if (field.getName().equals("type")) {
                        type = field.getInt(action)
                    }
                }
                // 经验所得 type 等于9 10为短信title和内容，不排除其他厂商拿不到的情况
                if (type != null && (type == 9 || type == 10)) {
                    if (key == 0) {
                        text["title"] = value?.toString() ?: ""
                    } else if (key == 1) {
                        text["text"] = value?.toString() ?: ""
                    } else {
                        text[Integer.toString(key)] = value?.toString()
                    }
                    key++
                }
            }
            key = 0
        }
        return text
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}