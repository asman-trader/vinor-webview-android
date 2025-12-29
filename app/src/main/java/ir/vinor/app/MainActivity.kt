package ir.vinor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.util.LruCache
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
    private val targetUrl = "https://vinor.ir"
    private val targetHost: String = Uri.parse(targetUrl).host ?: "vinor.ir"
    private val memoryCache = object : LruCache<String, ByteArray>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size // real bytes, not entry count
    } // ~2MB small-asset hot cache

    private fun interceptToOkHttp(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (request.method != "GET") return null
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return null
        val host = request.url.host ?: return null
        if (host != targetHost) return null // skip third-party to avoid overhead; let WebView fetch directly
        return try {
            memoryCache.get(url)?.let { bytes ->
                return WebResourceResponse(
                    guessMime(url),
                    "utf-8",
                    ByteArrayInputStream(bytes)
                ).apply { setStatusCodeAndReasonPhrase(200, "OK") }
            }
            val reqBuilder = Request.Builder().url(url)
            if (!isOnline()) {
                reqBuilder.cacheControl(CacheControl.FORCE_CACHE)
            }
            val resp = if (shouldPreferCache(request)) {
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
            val canMemCache = shouldMemCache(request, url, contentLength)
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

    private fun shouldPreferCache(request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (request.method != "GET") return false
        val host = request.url.host ?: return false
        val isTargetHost = host == targetHost
        return isTargetHost && isStaticAsset(url)
    }

    private fun shouldMemCache(request: WebResourceRequest, url: String, contentLength: Long): Boolean {
        if (!shouldPreferCache(request)) return false
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

        // Prepare OkHttp cache (100 MB) برای کش بهتر تصاویر/ویدیو
        val cacheDir = File(cacheDir, "http")
        val cache = Cache(cacheDir, 100L * 1024L * 1024L)
        httpClient = OkHttpClient.Builder()
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

        binding.webView.loadUrl(targetUrl)

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
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun configureWebView() {
        // Use hardware acceleration and pre-raster for smoother first paint
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        with(binding.webView.settings) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                offscreenPreRaster = true // render first screen before display to reduce perceived LCP
            }
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = if (isOnline()) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
            databaseEnabled = true
            allowContentAccess = true
            allowFileAccess = false
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        binding.webView.webViewClient = object : WebViewClient() {
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
                // Let WebView handle http/https internally to avoid double navigation and extra round-trips
                if (url.startsWith("http://") || url.startsWith("https://")) return false
                // For any other custom scheme, try to hand off; fall back to WebView if it cannot be handled
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } catch (_: Exception) {
                    false
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pendingOtp?.let { injectOtpToWebView(it) }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request ?: return super.shouldInterceptRequest(view, request)
                return interceptToOkHttp(request) ?: super.shouldInterceptRequest(view, request)
            }
        }
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
        binding.webView.post { injectOtpToWebView(code) }
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

        binding.webView.evaluateJavascript(js, ValueCallback { result ->
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

