package com.deliverypro.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.WindowCompat
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pageReady = false

    private val autoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            consumePendingOrders()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = Color.rgb(7, 21, 29)
        window.navigationBarColor = Color.rgb(7, 21, 29)

        webView = WebView(this)
        webView.setBackgroundColor(Color.rgb(7, 21, 29))
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                consumePendingOrders()
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            allowFileAccess = true
            allowContentAccess = true
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")

        val filter = IntentFilter(AutoOrderAccessibilityService.ACTION_AUTO_ORDER)
        registerReceiver(autoReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        if (!isAutoCaptureEnabled()) {
            webView.postDelayed({ showAutoCaptureSetup() }, 900)
        }
    }

    private fun isAutoCaptureEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun showAutoCaptureSetup() {
        android.app.AlertDialog.Builder(this)
            .setTitle("開啟自動抓單")
            .setMessage("允許「外送助手 Pro」讀取 Uber Driver 進單畫面的平台金額。偵測到例如 $97 時，會自動建立訂單並開始獨立計時。資料只在手機本機使用。")
            .setNegativeButton("稍後") { d, _ -> d.dismiss() }
            .setPositiveButton("前往設定") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    private fun consumePendingOrders() {
        if (!pageReady) return
        val prefs = getSharedPreferences("auto_capture", MODE_PRIVATE)
        val raw = prefs.getString("pending", "") ?: return
        if (raw.isBlank() || raw == "[]") return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val amount = item.optDouble("amount", 0.0)
                val eventId = item.optString("eventId", "")
                if (amount > 0 && eventId.isNotBlank()) {
                    val js = "window.autoAddOrder(${amount}, ${org.json.JSONObject.quote(eventId)});"
                    webView.evaluateJavascript(js, null)
                }
            }
            prefs.edit().remove("pending").apply()
        } catch (_: Exception) {
            prefs.edit().remove("pending").apply()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.postDelayed({ consumePendingOrders() }, 250)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        unregisterReceiver(autoReceiver)
        webView.destroy()
        super.onDestroy()
    }
}
