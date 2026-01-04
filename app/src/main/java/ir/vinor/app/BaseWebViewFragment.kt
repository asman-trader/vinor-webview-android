package ir.vinor.app

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import ir.vinor.app.databinding.FragmentWebviewBinding

/**
 * Base Fragment برای تمام تب‌های WebView
 * شامل: Loader, Offline handling, Back navigation
 */
abstract class BaseWebViewFragment : Fragment() {

    protected lateinit var binding: FragmentWebviewBinding
    protected lateinit var webView: WebView
    abstract val targetUrl: String
    abstract val fragmentTag: String // برای لاگ
    
    // Public getters for MainActivity access
    fun getCurrentUrl(): String? {
        return if (::webView.isInitialized) webView.url else null
    }

    private var canGoBack = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWebviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        setupRetryButton()
        loadUrl()
    }

    private fun setupRetryButton() {
        binding.retryButton.setOnClickListener {
            retryLoad()
        }
    }

    private fun setupWebView() {
        webView = binding.webView

        // تنظیمات امنیتی و عملکردی WebView
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        // setAppCacheEnabled deprecated و حذف شده - از cacheMode استفاده می‌کنیم
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.userAgentString = settings.userAgentString + " VinorApp/Android"

        // WebViewClient برای مدیریت navigation و errors
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                
                // DeepLink: لینک‌های vinor.ir داخل WebView باز شود
                if (url.contains("vinor.ir")) {
                    return false // اجازه بارگذاری در WebView
                }
                
                // لینک‌های خارجی با مرورگر سیستم باز شود
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(fragmentTag, "Error opening external link: $url", e)
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                showLoader()
                hideOffline()
                canGoBack = view?.canGoBack() == true
                Log.d(fragmentTag, "Page started: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                hideLoader()
                canGoBack = view?.canGoBack() == true
                Log.d(fragmentTag, "Page finished: $url")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    if (!isOnline()) {
                        showOffline()
                    }
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && errorResponse?.statusCode == 404) {
                    Log.w(fragmentTag, "404 error for: ${request.url}")
                }
            }
        }

        // WebChromeClient برای progress bar
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.progress = newProgress
                if (newProgress == 100) {
                    binding.progressBar.visibility = View.GONE
                } else {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun loadUrl() {
        if (!isOnline()) {
            showOffline()
            return
        }
        hideOffline()
        Log.d(fragmentTag, "Loading URL: $targetUrl")
        webView.loadUrl(targetUrl)
    }

    /**
     * بررسی اتصال به اینترنت
     */
    protected fun isOnline(): Boolean {
        val connectivityManager =
            requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * نمایش Loader/Skeleton
     */
    private fun showLoader() {
        binding.loaderContainer.visibility = View.VISIBLE
        binding.skeletonView.visibility = View.VISIBLE
    }

    /**
     * مخفی کردن Loader
     */
    private fun hideLoader() {
        binding.loaderContainer.visibility = View.GONE
        binding.skeletonView.visibility = View.GONE
    }

    /**
     * نمایش صفحه Offline
     */
    private fun showOffline() {
        binding.offlineContainer.visibility = View.VISIBLE
        binding.webView.visibility = View.GONE
    }

    /**
     * مخفی کردن صفحه Offline
     */
    private fun hideOffline() {
        binding.offlineContainer.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
    }

    /**
     * Retry برای بارگذاری مجدد
     */
    fun retryLoad() {
        loadUrl()
    }

    /**
     * بررسی اینکه آیا WebView می‌تواند به عقب برگردد
     */
    fun canGoBackInWebView(): Boolean {
        return if (::webView.isInitialized) {
            webView.canGoBack()
        } else {
            false
        }
    }

    /**
     * برگشت در WebView history
     */
    fun goBackInWebView(): Boolean {
        return if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            true
        } else {
            false
        }
    }

    /**
     * بررسی اینکه آیا در ریشه تب هستیم (URL اصلی)
     */
    fun isAtRoot(): Boolean {
        if (!::webView.isInitialized) return true
        val currentUrl = webView.url ?: ""
        if (currentUrl.isEmpty()) return true
        
        // بررسی اینکه آیا URL فعلی همان targetUrl است یا به آن ختم می‌شود
        val cleanTarget = targetUrl.replace("https://", "").replace("http://", "")
        val cleanCurrent = currentUrl.replace("https://", "").replace("http://", "")
        
        return currentUrl == targetUrl || 
               currentUrl.endsWith(targetUrl) ||
               cleanCurrent == cleanTarget ||
               cleanCurrent.startsWith(cleanTarget + "/")
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
        }
    }

    override fun onDestroyView() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroyView()
    }
}

