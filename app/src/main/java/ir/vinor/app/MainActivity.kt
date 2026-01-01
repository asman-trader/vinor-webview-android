package ir.vinor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import android.view.View
import android.widget.Toast
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import androidx.activity.OnBackPressedCallback
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import ir.vinor.app.databinding.ActivityMainBinding
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import java.io.File
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private val targetUrl = "https://vinor.ir"
    private val targetHost: String = Uri.parse(targetUrl).host ?: "vinor.ir"
    private val memoryCache = object : LruCache<String, ByteArray>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size // real bytes, not entry count
    } // ~2MB small-asset hot cache
    private var loadStartMs: Long = 0L
    private var pageCommitMs: Long = 0L
    private var loaderRunnable: Runnable? = null
    private val loaderDelayMs = 0L // show immediately to mask flash
    private var rendererRestartedOnce = false
    private var lastBackPressTs: Long = 0L

    private fun interceptToOkHttp(request: WebResourceRequest): WebResourceResponse? {
        val offline = !isOnline()
        if (!shouldIntercept(request, offline)) return null
        val url = request.url.toString()
        return try {
            memoryCache.get(url)?.let { bytes ->
                return WebResourceResponse(
                    guessMime(url),
                    "utf-8",
                    ByteArrayInputStream(bytes)
                ).apply { setStatusCodeAndReasonPhrase(200, "OK") }
            }
            val reqBuilder = Request.Builder().url(url)
            if (offline) {
                reqBuilder.cacheControl(CacheControl.FORCE_CACHE)
            }
            val resp = if (shouldPreferCache(request, offline)) {
                fetchCacheFirst(reqBuilder)
            } else {
                httpClient.newCall(reqBuilder.build()).execute()
            }
            if (resp == null) return offlineIfNeeded(request)
            if (resp.code == 504) return offlineIfNeeded(request)
            val body = resp.body ?: return null
            val mime = resp.header("Content-Type")?.substringBefore(";") ?: guessMime(url)
            val encoding = "utf-8"
            val contentLength = body.contentLength()
            val canMemCache = shouldMemCache(request, url, contentLength, offline)
            val bodyBytes = if (canMemCache) runCatching { body.bytes() }.getOrNull() else null
            val stream: InputStream = if (bodyBytes != null) {
                memoryCache.put(url, bodyBytes)
                ByteArrayInputStream(bodyBytes)
            } else {
                body.byteStream()
            }
            val response = WebResourceResponse(mime, encoding, stream)
            val headers = HashMap<String, String>()
            resp.headers.names().forEach { name ->
                headers[name] = resp.header(name).orEmpty()
            }
            response.responseHeaders = headers
            response.setStatusCodeAndReasonPhrase(resp.code, resp.message)
            response
        } catch (_: Exception) {
            offlineIfNeeded(request)
        }
    }

    private fun shouldIntercept(request: WebResourceRequest, offline: Boolean): Boolean {
        if (request.method != "GET") return false
        val url = request.url
        val host = url.host ?: return false
        val isTargetHost = host == targetHost
        val isStatic = isStaticAsset(url.toString())
        // Intercept only first-party static assets (or when offline) to avoid slowing HTML/docs
        return isTargetHost && (isStatic || offline)
    }

    private fun shouldPreferCache(request: WebResourceRequest, offline: Boolean): Boolean =
        shouldIntercept(request, offline) && isStaticAsset(request.url.toString())

    private fun shouldMemCache(request: WebResourceRequest, url: String, contentLength: Long, offline: Boolean): Boolean {
        if (!shouldPreferCache(request, offline)) return false
        // Guard against unknown sizes; keep only reasonably small assets
        if (contentLength in 1..(256 * 1024)) return true
        // Unknown length (-1) often means chunked; avoid to prevent OOM
        return false
    }

    private fun isStaticAsset(url: String): Boolean {
        return url.endsWith(".js") || url.endsWith(".css") ||
                url.endsWith(".png") || url.endsWith(".jpg") || url.endsWith(".jpeg") ||
                url.endsWith(".webp") || url.endsWith(".gif") || url.endsWith(".svg") ||
                url.endsWith(".woff") || url.endsWith(".woff2") || url.endsWith(".ttf")
    }

    private fun fetchCacheFirst(reqBuilder: Request.Builder): Response? {
        // Try cache instantly; if miss (504) then go network
        return try {
            val cacheResp = httpClient.newCall(
                reqBuilder.cacheControl(CacheControl.FORCE_CACHE).build()
            ).execute()
            if (cacheResp.isSuccessful && cacheResp.body != null) {
                cacheResp
            } else {
                cacheResp.close()
                httpClient.newCall(reqBuilder.cacheControl(CacheControl.FORCE_NETWORK).build()).execute()
            }
        } catch (_: Exception) {
            try {
                httpClient.newCall(reqBuilder.cacheControl(CacheControl.FORCE_NETWORK).build()).execute()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun guessMime(url: String): String {
        return when {
            url.endsWith(".js") -> "application/javascript"
            url.endsWith(".css") -> "text/css"
            url.endsWith(".png") -> "image/png"
            url.endsWith(".jpg") || url.endsWith(".jpeg") -> "image/jpeg"
            url.endsWith(".webp") -> "image/webp"
            url.endsWith(".gif") -> "image/gif"
            url.endsWith(".mp4") -> "video/mp4"
            url.endsWith(".webm") -> "video/webm"
            url.endsWith(".m4v") -> "video/mp4"
            url.endsWith(".mov") -> "video/quicktime"
            url.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            url.endsWith(".mpd") -> "application/dash+xml"
            url.endsWith(".ts") -> "video/mp2t"
            url.endsWith(".svg") -> "image/svg+xml"
            url.endsWith(".woff") -> "font/woff"
            url.endsWith(".woff2") -> "font/woff2"
            url.endsWith(".ttf") -> "font/ttf"
            else -> "text/plain"
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nw = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(nw) ?: return false
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                @Suppress("DEPRECATION")
                val ni = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                ni != null && ni.isConnected
            }
        } catch (_: Exception) {
            true
        }
    }
    private val smsConsentResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        val message = data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return@registerForActivityResult
        // Try to extract a 4-8 digit code from the SMS text
        val code = Regex("""\b(\d{4,8})\b""").find(message)?.value
        // For now, just inject the code into the page if there is a handler (adjust selector if needed)
        code?.let { onOtpReceived(it) }
    }

    private val smsBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION != intent?.action) return
            val extras = intent.extras ?: return
            val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return
            when (status.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                    consentIntent?.let { smsConsentResultLauncher.launch(it) }
                }
                CommonStatusCodes.TIMEOUT -> {
                    // Ignore; no SMS received within the timeout window
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = WebViewProvider.attach(this, binding.webViewContainer)
        binding.retryButton.setOnClickListener {
            hideErrorBanner()
            scheduleLoader()
            webView.reload()
        }
        binding.debugButton.setOnClickListener {
            Log.d("VinorWebView", "t0=$loadStartMs, commit=$pageCommitMs, now=${System.currentTimeMillis()}")
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleSmartBack()
            }
        })

        // Prepare OkHttp cache (100 MB) برای کش بهتر تصاویر/ویدیو
        val cacheDir = File(cacheDir, "http")
        val cache = Cache(cacheDir, 100L * 1024L * 1024L)
        val dispatcher = Dispatcher().apply {
            // Allow more parallel asset fetches to speed up first paint
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        httpClient = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .cache(cache)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Enable Brotli to reduce payload sizes (server permitting)
            .addInterceptor(BrotliInterceptor)
            // Add default caching for static/media/HTML when server doesn't set it
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val url = request.url.toString()
                val contentType = response.header("Content-Type") ?: ""
                val isStatic = url.endsWith(".js") || url.endsWith(".css") ||
                        url.endsWith(".png") || url.endsWith(".jpg") ||
                        url.endsWith(".jpeg") || url.endsWith(".webp") || url.endsWith(".gif") ||
                        url.endsWith(".svg") ||
                        // video formats
                        url.endsWith(".mp4") || url.endsWith(".webm") || url.endsWith(".m4v") ||
                        url.endsWith(".mov") || url.endsWith(".m3u8") || url.endsWith(".ts") ||
                        url.endsWith(".mpd") ||
                        url.endsWith(".woff") ||
                        url.endsWith(".woff2") || url.endsWith(".ttf")
                val isImage = contentType.startsWith("image/")
                val isVideo = contentType.startsWith("video/") ||
                        contentType.contains("application/vnd.apple.mpegurl") ||
                        contentType.contains("application/dash+xml")
                val isHtml = contentType.startsWith("text/html") || !(isStatic || url.contains("."))
                val isHashed = Regex("([a-f0-9]{8,})").containsMatchIn(url) || url.contains("v=") || url.contains("ver=")
                if (response.header("Cache-Control") == null) {
                    val maxAge = when {
                        isHashed && isStatic -> 365 * 24 * 3600 // 1 year for fingerprinted assets
                        isImage || url.endsWith(".png") || url.endsWith(".jpg") || url.endsWith(".jpeg") ||
                                url.endsWith(".webp") || url.endsWith(".gif") -> 7 * 24 * 3600 // 7 روز
                        isVideo || url.endsWith(".mp4") || url.endsWith(".webm") || url.endsWith(".m4v") ||
                                url.endsWith(".mov") || url.endsWith(".m3u8") || url.endsWith(".ts") ||
                                url.endsWith(".mpd") -> 3 * 24 * 3600 // 3 روز
                        isHtml -> 3600 // 1 ساعت
                        else -> 86400 // 1 روز
                    }
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=$maxAge")
                        .build()
                } else response
            }
            // Use cache only when offline
            .addInterceptor { chain ->
                val original = chain.request()
                if (!isOnline()) {
                    val offline = original.newBuilder()
                        .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(30, TimeUnit.DAYS).build())
                        .build()
                    chain.proceed(offline)
                } else {
                    chain.proceed(original)
                }
            }
            .build()

        configureWebView()

        // Warm-up connection (DNS/TLS) in background to reduce TTFB on first load
        try {
            InetAddress.getByName(targetHost)
            httpClient.newCall(Request.Builder().url(targetUrl).head().build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
            })
        } catch (_: Exception) {}

        loadStartMs = System.currentTimeMillis()
        webView.loadUrl(targetUrl)

        // Start SMS User Consent (no SMS permission required)
        SmsRetriever.getClient(this).startSmsUserConsent(null)

        // Route service worker network to OkHttp cache as well (Android N+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    return interceptToOkHttp(request)
                }
            })
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            val swController = ServiceWorkerControllerCompat.getInstance()
            swController.serviceWorkerWebSettings.apply {
                setAllowContentAccess(true)
                setAllowFileAccess(false)
            }
            swController.setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    return interceptToOkHttp(request)
                }
            })
        }
    }

    private var isSmsReceiverRegistered: Boolean = false
    private var pendingOtp: String? = null
    private lateinit var httpClient: OkHttpClient

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(smsBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(smsBroadcastReceiver, filter)
        }
        isSmsReceiverRegistered = true
    }

    override fun onStop() {
        if (isSmsReceiverRegistered) {
            try {
                unregisterReceiver(smsBroadcastReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver already unregistered; ignore
            }
            isSmsReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun configureWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.setBackgroundColor(Color.TRANSPARENT)

        with(webView.settings) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                offscreenPreRaster = true // render first screen before display to reduce perceived LCP
            }
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            allowContentAccess = true
            allowFileAccess = false
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
            runCatching { javaClass.getMethod("setAppCacheEnabled", Boolean::class.java).invoke(this, true) }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
            WebSettingsCompat.setOffscreenPreRaster(webView.settings, true)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_ON)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
            WebViewCompat.startSafeBrowsing(this, null)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ALLOWLIST)) {
            WebViewCompat.setSafeBrowsingAllowlist(setOf(targetHost)) { /* ignore */ }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 60) hideLoader()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("tel:")) {
                    handleTelLink(url)
                    return true
                }
                if (url.startsWith("sms:") || url.startsWith("smsto:")) {
                    handleSmsLink(url)
                    return true
                }
                // Keep internal navigation inside WebView; external/custom schemes go out
                if (url.startsWith("http://") || url.startsWith("https://")) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } catch (_: Exception) {
                    false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadStartMs = System.currentTimeMillis()
                hideErrorBanner()
                captureSnapshot()
                scheduleLoader()
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                pageCommitMs = System.currentTimeMillis()
                logPerf("onPageCommitVisible", url)
                cancelLoaderSchedule()
                hideLoader()
                clearSnapshot()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pendingOtp?.let { injectOtpToWebView(it) }
                logPerf("onPageFinished", url)
                cancelLoaderSchedule()
                hideLoader()
                clearSnapshot()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showErrorBanner(error?.description?.toString() ?: "خطا در بارگذاری")
                    cancelLoaderSchedule()
                    hideLoader()
                    clearSnapshot()
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request ?: return super.shouldInterceptRequest(view, request)
                return interceptToOkHttp(request) ?: super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun hideLoader() {
        val loader = binding.loader
        if (loader.visibility == View.VISIBLE) {
            loader.animate().alpha(0f).setDuration(200).withEndAction {
                loader.visibility = View.GONE
                loader.alpha = 1f
            }.start()
        }
    }

    private fun handleSmartBack() {
        checkModalOrMenuHandled { handled ->
            if (handled) return@checkModalOrMenuHandled
            val current = UrlUtils.normalize(webView.url.orEmpty())
            val isVinor = UrlUtils.isVinorDomain(current)
            val isHome = UrlUtils.isHomePage(current)

            if (webView.canGoBack() && !isHome) {
                webView.goBack()
                return@checkModalOrMenuHandled
            }

            if (!isVinor) {
                webView.loadUrl(targetUrl)
                return@checkModalOrMenuHandled
            }

            val now = System.currentTimeMillis()
            if (now - lastBackPressTs < 2000) {
                moveTaskToBack(true)
            } else {
                lastBackPressTs = now
                Toast.makeText(this, "برای خروج دوباره بزن", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkModalOrMenuHandled(cb: (Boolean) -> Unit) {
        val js = """
            (function(){
                try {
                    if (window.VinorBackHandler) {
                        return !!window.VinorBackHandler();
                    }
                } catch(e){}
                return false;
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            val handled = result?.contains("true") == true
            cb(handled)
        }
    }

    private fun scheduleLoader() {
        cancelLoaderSchedule()
        loaderRunnable = Runnable {
            if (binding.loader.visibility != View.VISIBLE) {
                binding.loader.alpha = 1f
                binding.loader.visibility = View.VISIBLE
            }
        }.also { binding.loader.postDelayed(it, loaderDelayMs) }
    }

    private fun cancelLoaderSchedule() {
        loaderRunnable?.let { binding.loader.removeCallbacks(it) }
        loaderRunnable = null
    }

    private fun captureSnapshot() {
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) return
        runCatching {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            webView.draw(canvas)
            binding.snapshotOverlay.setImageBitmap(bmp)
            binding.snapshotOverlay.visibility = View.VISIBLE
        }
    }

    private fun clearSnapshot() {
        binding.snapshotOverlay.setImageDrawable(null)
        binding.snapshotOverlay.visibility = View.GONE
    }

    private fun showErrorBanner(message: String) {
        binding.errorText.text = message
        if (binding.errorBanner.visibility != View.VISIBLE) {
            binding.errorBanner.alpha = 0f
            binding.errorBanner.visibility = View.VISIBLE
            binding.errorBanner.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun hideErrorBanner() {
        if (binding.errorBanner.visibility == View.VISIBLE) {
            binding.errorBanner.animate().alpha(0f).setDuration(180).withEndAction {
                binding.errorBanner.visibility = View.GONE
                binding.errorBanner.alpha = 1f
            }.start()
        }
    }

    private fun logPerf(event: String, url: String?) {
        val now = System.currentTimeMillis()
        val start = loadStartMs.takeIf { it > 0 } ?: now
        val elapsed = now - start
        Log.d("VinorWebView", "$event at ${elapsed}ms url=${url.orEmpty()}")
    }

    private fun handleSmsLink(url: String) {
        // Prefer ACTION_SENDTO to let user confirm sending; works without SEND_SMS
        val smsUri = if (url.startsWith("sms:")) {
            Uri.parse(url.replaceFirst("sms:", "smsto:"))
        } else Uri.parse(url)
        val intent = Intent(Intent.ACTION_SENDTO, smsUri)
        // Some sites pass ?body=...; Android reads it automatically for smsto:
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun onOtpReceived(code: String) {
        pendingOtp = code
        webView.post { injectOtpToWebView(code) }
    }

    private fun injectOtpToWebView(code: String) {
        val escaped = code.replace("'", "\\'")
        val js = """
            (function(){
                var code = '$escaped';
                function setValue(el){
                    if(!el) return false;
                    try{
                        el.focus();
                        el.value = code;
                        el.setAttribute('value', code);
                        var evts=['input','change','keyup','keydown','blur'];
                        evts.forEach(function(t){ try{ el.dispatchEvent(new Event(t,{bubbles:true})); }catch(e){} });
                        return true;
                    }catch(e){ return false; }
                }
                var selectors = [
                    "input[autocomplete='one-time-code']",
                    "input[name*='otp' i]",
                    "input[name*='code' i]",
                    "input[id*='otp' i]",
                    "input[id*='code' i]",
                    "input[class*='otp' i]",
                    "input[class*='code' i]",
                    "input[type='tel']",
                    "input[type='number']"
                ];
                for (var i=0;i<selectors.length;i++){
                    var el = document.querySelector(selectors[i]);
                    if (setValue(el)) return 'ok';
                }
                return 'notfound';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, ValueCallback { result ->
            if (result?.contains("ok") == true) {
                pendingOtp = null
            }
        })
    }

    private fun handleTelLink(url: String) {
        // Always hand off to the dialer; no CALL_PHONE permission is requested
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // No dialer present; ignore
        }
    }

    private fun offlineIfNeeded(request: WebResourceRequest): WebResourceResponse? {
        if (isOnline()) return null
        val url = request.url
        if (url.host != targetHost || request.method != "GET") return null
        return try {
            val stream = assets.open("offline.html")
            WebResourceResponse("text/html", "utf-8", stream).apply {
                setStatusCodeAndReasonPhrase(200, "OK")
            }
        } catch (_: Exception) {
            null
        }
    }
}

