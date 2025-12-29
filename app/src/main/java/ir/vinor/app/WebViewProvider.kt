package ir.vinor.app

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebView

object WebViewProvider {
    @Volatile
    private var cachedWebView: WebView? = null

    fun prewarm(context: Context) {
        if (cachedWebView != null) return
        synchronized(this) {
            if (cachedWebView == null) {
                cachedWebView = createWebView(context.applicationContext)
            }
        }
    }

    fun attach(activity: Activity, container: ViewGroup): WebView {
        val webView = obtain(activity)
        val ctxWrapper = webView.context as MutableContextWrapper
        ctxWrapper.baseContext = activity
        (webView.parent as? ViewGroup)?.removeView(webView)
        container.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        return webView
    }

    private fun obtain(context: Context): WebView {
        prewarm(context)
        return cachedWebView ?: createWebView(context.applicationContext).also { cachedWebView = it }
    }

    private fun createWebView(appContext: Context): WebView {
        val ctx = MutableContextWrapper(appContext)
        return WebView(ctx)
    }
}


