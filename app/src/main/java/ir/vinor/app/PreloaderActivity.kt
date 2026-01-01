package ir.vinor.app

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight entry screen focused on fastest first frame and immediate feedback.
 * - No animations or delays
 * - Checks connectivity, pre-warms WebView after first frame, then hands off
 */
class PreloaderActivity : AppCompatActivity() {

    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var retry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preloader)

        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.statusText)
        retry = findViewById(R.id.retryButton)

        retry.setOnClickListener {
            retry.isVisible = false
            statusText.text = getString(R.string.preloader_connecting)
            startFlow()
        }

        // Kick off work after first frame to avoid blocking first draw
        window.decorView.post { startFlow() }
    }

    private fun startFlow() {
        lifecycleScope.launch {
            val hasInternet = withContext(Dispatchers.IO) { isOnline() }
            if (!hasInternet) {
                showNoInternet()
                return@launch
            }
            prewarmWebView()
            goToMain()
        }
    }

    private suspend fun prewarmWebView() {
        // WebView creation must run on main; keep minimal and avoid extra allocations
        withContext(Dispatchers.Main) {
            runCatching { WebViewProvider.prewarm(this@PreloaderActivity) }
        }
    }

    private fun goToMain() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(0, 0) // avoid visual flash
        finish()
    }

    private fun showNoInternet() {
        progress.visibility = View.GONE
        retry.isVisible = true
        statusText.text = getString(R.string.preloader_no_internet)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(nw) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val ni = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            ni != null && ni.isConnected
        }
    }
}


