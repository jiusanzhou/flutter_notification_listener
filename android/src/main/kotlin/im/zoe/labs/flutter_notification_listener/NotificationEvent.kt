package im.zoe.labs.flutter_notification_listener

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import java.lang.reflect.Field


class NotificationEvent {
    companion object {
        private const val NOTIFICATION_PACKAGE_NAME = "package_name"
        private const val NOTIFICATION_TITLE = "title"
        private const val NOTIFICATION_TEXT = "text"
        private const val NOTIFICATION_MESSAGE = "message"

        fun fromSbn(sbn: StatusBarNotification): Map<String, *> {
            val map = HashMap<String, Any?>()

            // Retrieve package name to set as title.
            val packageName = sbn.packageName

            // Retrieve extra object from notification to extract payload.
            val notify = sbn.notification
            val extras = notify?.extras

            map[NOTIFICATION_PACKAGE_NAME] =  packageName
            map[NOTIFICATION_TITLE] =   extras?.getString(Notification.EXTRA_TITLE)
            map[NOTIFICATION_TEXT] =   extras?.getString(Notification.EXTRA_TEXT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                map[NOTIFICATION_MESSAGE] =   extras?.getString(Notification.EXTRA_MESSAGES)
            }

            return map
        }

        @Suppress("DEPRECATION")
        private fun getNotifyInfo(notification: Notification?): Map<String, Any?>? {
            var key = 0
            if (notification == null) return null
            val views = notification.contentView ?: return null
            val secretClass: Class<*> = views.javaClass
            try {
                val text: MutableMap<String, Any?> = HashMap()
                val outerFields: Array<Field> = secretClass.declaredFields
                for (i in outerFields.indices) {
                    if (!outerFields[i].name.equals("mActions")) continue
                    outerFields[i].isAccessible = true
                    val actions = outerFields[i].get(views) as ArrayList<*>
                    for (action in actions) {
                        val innerFields: Array<Field> = action.javaClass.declaredFields
                        var value: Any? = null
                        var type: Int? = null
                        for (field in innerFields) {
                            field.isAccessible = true
                            if (field.name.equals("value")) {
                                value = field.get(action)
                            } else if (field.name.equals("type")) {
                                type = field.getInt(action)
                            }
                        }
                        // 经验所得 type 等于9 10为短信title和内容，不排除其他厂商拿不到的情况
                        if (type != null && (type == 9 || type == 10)) {
                            when (key) {
                                0 -> {
                                    text["title"] = value?.toString() ?: ""
                                }
                                1 -> {
                                    text["text"] = value?.toString() ?: ""
                                }
                                else -> {
                                    text[key.toString()] = value?.toString()
                                }
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
    }

}