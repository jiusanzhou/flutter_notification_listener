package im.zoe.labs.flutter_notification_listener

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import im.zoe.labs.flutter_notification_listener.Utils.Companion.toBitmap
import java.io.ByteArrayOutputStream

class NotificationEvent(context: Context, sbn: StatusBarNotification) {

    var mContext = context
    var mSbn = sbn

    var data: Map<String, Any?> = fromSbn(context, sbn)

    val key: String
        get() = "${genKey(data[NOTIFICATION_PACKAGE_NAME] as String, data[NOTIFICATION_ID] as Int)}"

    companion object {
        private const val NOTIFICATION_PACKAGE_NAME = "package_name"
        private const val NOTIFICATION_TIMESTAMP = "timestamp"
        private const val NOTIFICATION_ID = "id"
        private const val NOTIFICATION_UID = "uid"
        private const val NOTIFICATION_ACTIONS = "actions"
        private const val NOTIFICATION_CAN_TAP = "canTap"

        fun genKey(pkg: String, id: Int): String {
            return "$pkg-$id"
        }

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

            map[NOTIFICATION_CAN_TAP] = notify.contentIntent != null

            map[NOTIFICATION_ACTIONS] = getActions(context, notify)

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

        private fun getActions(context: Context, n: Notification?): List<*>? {
            if (n?.actions == null) return null
            var items: List<Map<String, Any>?> = mutableListOf()
            n.actions.forEachIndexed { idx, act ->
                var map = HashMap<String, Any>()
                map["id"] = idx
                map["title"] = act.title.toString()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    map["semantic"] = act.semanticAction
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    var ins: List<Map<String, Any>?> = mutableListOf()
                    if (act.remoteInputs != null) {
                        act.remoteInputs.forEach {
                            var input = HashMap<String, Any>()
                            input["label"] = it.label.toString()
                            input["key"] = it.resultKey
                            // input["choices"] = it.choices
                            ins = ins + input
                        }
                    }
                    map["inputs"] = ins

                    // val iterator = act.extras.keySet().iterator()
                    // while (iterator.hasNext()) {
                    //     val key = iterator.next()
                    //     Log.d("=====>", "action extra key: $key, value: ${act.extras.get(key)}")
                    // }
                }
                items = items.plus(map)
            }
            return items
        }
    }

}