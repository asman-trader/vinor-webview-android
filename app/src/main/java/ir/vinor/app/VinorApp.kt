package ir.vinor.app

import android.app.Application

class VinorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WebViewProvider.prewarm(this)
    }
}


