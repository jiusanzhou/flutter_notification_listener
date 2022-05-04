package im.zoe.labs.flutter_notification_listener

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import im.zoe.labs.flutter_notification_listener.Utils.Companion.toBitmap
import java.io.ByteArrayOutputStream
import java.lang.reflect.Field

class NotificationEvent {
    companion object {
        private const val NOTIFICATION_PACKAGE_NAME = "package_name"
        private const val NOTIFICATION_TIMESTAMP = "timestamp"
        private const val NOTIFICATION_ID = "id"
        private const val NOTIFICATION_UID = "uid"
        private const val NOTIFICATION_INTENT = "intent"

        /**
        private const val NOTIFICATION_MESSAGE = "message"
        private const val NOTIFICATION_OBJECT = "object"
        private const val NOTIFICATION_TITLE = "title"
        private const val NOTIFICATION_TEXT = "text"*/

        // https://developer.android.com/guide/topics/ui/notifiers/notifications
        // extra more fields from docs
        fun fromSbn(context: Context, sbn: StatusBarNotification): Map<String, Any?> {
            // val map = HashMap<String, Any?>()

            // Retrieve extra object from notification to extract payload.
            val notify = sbn.notification

            val map = turnExtraToMap(context, notify?.extras)

            // add 3 sbn fields
            map[NOTIFICATION_TIMESTAMP] = sbn.postTime
            map[NOTIFICATION_PACKAGE_NAME] =  sbn.packageName
            map[NOTIFICATION_ID] = sbn.id

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                map[NOTIFICATION_UID] = sbn.uid
            }

            // map[NOTIFICATION_OBJECT] = getNotifyInfo(notify)

            return map
        }

        private val EXTRA_KEYS_WHITE_LIST = arrayOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_TEXT_LINES,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_SHOW_WHEN,
            Notification.EXTRA_LARGE_ICON,
            Notification.EXTRA_LARGE_ICON_BIG
        )

        private fun turnExtraToMap(context: Context, extras: Bundle?): HashMap<String, Any?> {
            val map = HashMap<String, Any?>()
            if (extras == null) return map
            val ks: Set<String> = extras.keySet()
            val iterator = ks.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (!EXTRA_KEYS_WHITE_LIST.contains(key)) continue

                val bits = key.split(".")
                val nKey = bits[bits.size - 1]

                map[nKey] = marshalled(context, extras.get(key))
            }
            return map
        }

        private fun marshalled(context: Context, v: Any?): Any? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                when (v) {
                    is Icon -> {
                        convertIconToByteArray(context, v)
                    }
                    else -> internalMarshalled(context, v)
                }
            } else {
                internalMarshalled(context, v)
            }
        }

        private fun internalMarshalled(context: Context, v: Any?): Any? {
            return when (v) {
                is CharSequence -> v.toString()
                is Array<*> -> v.map { marshalled(context, it) }
                // TODO: turn other types which cause exception
                else -> v
            }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun convertIconToByteArray(context: Context, icon: Icon): ByteArray {
             val stream = ByteArrayOutputStream()
             icon.loadDrawable(context).toBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
             return stream.toByteArray()
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
                    if (outerFields[i].name != "mActions") continue
                    outerFields[i].isAccessible = true
                    val actions = outerFields[i].get(views) as ArrayList<*>
                    for (action in actions) {
                        val innerFields: Array<Field> = action.javaClass.declaredFields
                        var value: Any? = null
                        var type: Int? = null
                        for (field in innerFields) {
                            field.isAccessible = true
                            if (field.name == "value") {
                                value = field.get(action)
                            } else if (field.name == "type") {
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