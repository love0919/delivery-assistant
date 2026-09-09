package com.deliverypro.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.regex.Pattern

class AutoOrderAccessibilityService : AccessibilityService() {
    companion object {
        const val ACTION_AUTO_ORDER = "com.deliverypro.app.AUTO_ORDER_DETECTED"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_EVENT_ID = "event_id"
        private const val UBER_PACKAGE = "com.ubercab.driver"
        private const val PREFS = "auto_capture"
        private const val KEY_LAST_AMOUNT = "last_amount"
        private const val KEY_LAST_AT = "last_at"
        private val MONEY_PATTERN = Pattern.compile("(?:[$＄]\\s*)(\\d{1,4}(?:\\.\\d{1,2})?)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != UBER_PACKAGE) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text)
        val screenText = text.toString()
        val matcher = MONEY_PATTERN.matcher(screenText)
        if (!matcher.find()) return

        val amount = matcher.group(1)?.toDoubleOrNull() ?: return
        if (amount < 1.0 || amount > 5000.0) return

        // 同一張進單畫面會連續觸發很多 AccessibilityEvent；短時間相同金額只建立一次。
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAmount = prefs.getString(KEY_LAST_AMOUNT, "")
        val lastAt = prefs.getLong(KEY_LAST_AT, 0L)
        if (lastAmount == String.format(Locale.US, "%.2f", amount) && now - lastAt < 5000L) return

        val eventId = "${now}_${amount.toInt()}"
        prefs.edit()
            .putString(KEY_LAST_AMOUNT, String.format(Locale.US, "%.2f", amount))
            .putLong(KEY_LAST_AT, now)
            .apply()

        enqueue(amount, eventId)
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder) {
        if (node == null) return
        node.text?.toString()?.let { if (it.isNotBlank()) out.append(' ').append(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) out.append(' ').append(it) }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out)
        }
    }

    private fun enqueue(amount: Double, eventId: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val old = prefs.getString("pending", "") ?: ""
        val safeOld = if (old.length > 8000) "" else old
        val item = "{\"amount\":$amount,\"eventId\":\"$eventId\"}"
        val next = if (safeOld.isBlank()) "[$item]" else safeOld.removeSuffix("]") + ",$item]"
        prefs.edit().putString("pending", next).apply()

        sendBroadcast(Intent(ACTION_AUTO_ORDER).apply {
            setPackage(packageName)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_EVENT_ID, eventId)
        })
    }

    override fun onInterrupt() = Unit
}
