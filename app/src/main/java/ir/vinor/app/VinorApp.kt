package ir.vinor.app

import android.app.Application

class VinorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Warm up shared WebView and Chromium process
        WebViewProvider.prewarm(this)
    }
}


