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
    
    // URL داینامیک - می‌تواند از منوی API تنظیم شود
    var dynamicUrl: String? = null
    
    // Public getters for MainActivity access
    fun getCurrentUrl(): String? {
        return try {
            if (::webView.isInitialized && ::binding.isInitialized && view != null && !isDetached && isAdded) {
                webView.url
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error getting current URL", e)
            null
        }
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
        try {
            setupWebView()
            setupRetryButton()
            // تاخیر کوتاه برای اطمینان از آماده بودن Context
            view.post {
                if (isAdded && !isDetached && view != null) {
                    loadUrl()
                }
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error in onViewCreated", e)
        }
    }

    private fun setupRetryButton() {
        binding.retryButton.setOnClickListener {
            retryLoad()
        }
    }

    private fun setupWebView() {
        try {
            if (!isAdded || isDetached || context == null) {
                Log.w(fragmentTag, "Fragment not attached, skipping WebView setup")
                return
            }
            
            webView = binding.webView
            
            if (webView == null) {
                Log.e(fragmentTag, "WebView is null in binding")
                return
            }

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
        
        // پشتیبانی از تم تاریک در WebView
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            settings.forceDark = WebSettings.FORCE_DARK_ON
        }

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
                try {
                    super.onPageStarted(view, url, favicon)
                    if (!isDetached && view != null) {
                        showLoader()
                        hideOffline()
                        canGoBack = view.canGoBack()
                        Log.d(fragmentTag, "Page started: $url")
                    }
                } catch (e: Exception) {
                    Log.e(fragmentTag, "Error in onPageStarted", e)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                try {
                    super.onPageFinished(view, url)
                    if (!isDetached && view != null) {
                        hideLoader()
                        canGoBack = view.canGoBack()
                        Log.d(fragmentTag, "Page finished: $url")
                        
                        // مخفی کردن منوی فوتر سایت در اپلیکیشن
                        hideFooterMenu()
                        
                        // فعال کردن تم تاریک در صفحه وب
                        enableDarkMode()
                    }
                } catch (e: Exception) {
                    Log.e(fragmentTag, "Error in onPageFinished", e)
                }
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
                try {
                    super.onProgressChanged(view, newProgress)
                    if (!isDetached && this@BaseWebViewFragment.view != null && ::binding.isInitialized) {
                        binding.progressBar.progress = newProgress
                        if (newProgress == 100) {
                            binding.progressBar.visibility = View.GONE
                        } else {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                    }
                } catch (e: Exception) {
                    Log.e(fragmentTag, "Error in onProgressChanged", e)
                }
            }
        }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error setting up WebView", e)
        }
    }

    private fun loadUrl() {
        try {
            // بررسی lifecycle
            if (isDetached || view == null || !isAdded || context == null) {
                Log.w(fragmentTag, "Fragment not attached, skipping load")
                return
            }
            
            if (!isOnline()) {
                showOffline()
                return
            }
            hideOffline()
            val urlToLoad = dynamicUrl ?: targetUrl
            Log.d(fragmentTag, "Loading URL: $urlToLoad")
            if (::webView.isInitialized && ::binding.isInitialized) {
                // استفاده از post برای اطمینان از اجرا در UI thread
                view?.post {
                    if (!isDetached && isAdded && ::webView.isInitialized) {
                        try {
                            webView.loadUrl(urlToLoad)
                        } catch (e: Exception) {
                            Log.e(fragmentTag, "Error loading URL in post", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error loading URL", e)
        }
    }
    
    /**
     * بارگذاری مجدد با URL جدید (برای تغییر داینامیک منو)
     * open است تا بتوان در Fragmentهای فرزند override کرد
     */
    open fun reloadWithUrl(url: String) {
        try {
            // بررسی lifecycle و view state
            if (isDetached || view == null || !isAdded || context == null) {
                Log.w(fragmentTag, "Fragment not attached, skipping reload")
                return
            }
            
            dynamicUrl = url
            if (::webView.isInitialized && ::binding.isInitialized) {
                if (!isOnline()) {
                    showOffline()
                    return
                }
                hideOffline()
                Log.d(fragmentTag, "Reloading with new URL: $url")
                // استفاده از post برای اطمینان از اجرا در UI thread
                view?.post {
                    if (!isDetached && isAdded && ::webView.isInitialized) {
                        try {
                            webView.loadUrl(url)
                        } catch (e: Exception) {
                            Log.e(fragmentTag, "Error loading URL in post", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error reloading URL: $url", e)
        }
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
        try {
            if (!isDetached && view != null && ::binding.isInitialized) {
                binding.loaderContainer.visibility = View.VISIBLE
                binding.skeletonView.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error showing loader", e)
        }
    }

    /**
     * مخفی کردن Loader
     */
    private fun hideLoader() {
        try {
            if (!isDetached && view != null && ::binding.isInitialized) {
                binding.loaderContainer.visibility = View.GONE
                binding.skeletonView.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error hiding loader", e)
        }
    }

    /**
     * نمایش صفحه Offline
     */
    private fun showOffline() {
        try {
            if (!isDetached && view != null && ::binding.isInitialized) {
                binding.offlineContainer.visibility = View.VISIBLE
                if (::webView.isInitialized) {
                    binding.webView.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error showing offline", e)
        }
    }

    /**
     * مخفی کردن صفحه Offline
     */
    private fun hideOffline() {
        try {
            if (!isDetached && view != null && ::binding.isInitialized) {
                binding.offlineContainer.visibility = View.GONE
                if (::webView.isInitialized) {
                    binding.webView.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error hiding offline", e)
        }
    }

    /**
     * Retry برای بارگذاری مجدد
     */
    fun retryLoad() {
        loadUrl()
    }

    /**
     * مخفی کردن منوی فوتر سایت با JavaScript
     * منوی فوتر با ID bottomNavMenu و کلاس fixed inset-x-0 bottom-0 است
     */
    private fun hideFooterMenu() {
        if (!::webView.isInitialized || webView == null || view == null || isDetached) return
        
        // JavaScript برای مخفی کردن منوی فوتر
        val hideFooterScript = """
            (function() {
                // Selectorهای مختلف برای منوی فوتر
                var selectors = [
                    '#bottomNavMenu',                    // منوی اصلی فوتر
                    'nav#bottomNavMenu',                // nav با ID bottomNavMenu
                    '.fixed.inset-x-0.bottom-0',        // کلاس‌های منوی فوتر
                    'footer nav',
                    'footer .menu',
                    'footer .footer-menu',
                    '.footer-menu',
                    '.bottom-nav',
                    '#footer-menu',
                    'nav.footer-menu',
                    '.app-footer',
                    'footer .bottom-navigation',
                    '[class*="footer"] [class*="menu"]',
                    '[class*="footer"] nav',
                    'footer [class*="nav"]'
                ];
                
                function hideElements() {
                    selectors.forEach(function(selector) {
                        try {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                if (el) {
                                    el.style.display = 'none';
                                    el.style.visibility = 'hidden';
                                    el.style.height = '0';
                                    el.style.overflow = 'hidden';
                                    el.style.margin = '0';
                                    el.style.padding = '0';
                                }
                            });
                        } catch(e) {}
                    });
                }
                
                // اجرای فوری
                hideElements();
                
                // اجرای مجدد بعد از لود کامل DOM
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', hideElements);
                }
                
                // اجرای مجدد بعد از لود کامل صفحه
                window.addEventListener('load', hideElements);
                
                // اجرای مجدد بعد از تغییرات DOM (برای SPA)
                setTimeout(hideElements, 100);
                setTimeout(hideElements, 500);
                setTimeout(hideElements, 1000);
                setTimeout(hideElements, 2000);
                
                // Observer برای تغییرات DOM (برای SPA)
                if (window.MutationObserver) {
                    var observer = new MutationObserver(function(mutations) {
                        hideElements();
                    });
                    observer.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                }
            })();
        """.trimIndent()
        
        try {
            webView.evaluateJavascript(hideFooterScript, null)
            Log.d(fragmentTag, "Footer menu hidden")
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error hiding footer menu", e)
        }
    }

    /**
     * فعال کردن تم تاریک در صفحه وب با JavaScript
     */
    private fun enableDarkMode() {
        if (!::webView.isInitialized || webView == null || view == null || isDetached) return
        
        // JavaScript برای فعال کردن تم تاریک
        val darkModeScript = """
            (function() {
                // اضافه کردن کلاس dark به html element
                if (document.documentElement) {
                    document.documentElement.classList.add('dark');
                }
                
                // اضافه کردن attribute data-theme
                if (document.documentElement) {
                    document.documentElement.setAttribute('data-theme', 'dark');
                }
                
                // اضافه کردن meta tag برای تم تاریک
                var metaTheme = document.querySelector('meta[name="color-scheme"]');
                if (!metaTheme) {
                    metaTheme = document.createElement('meta');
                    metaTheme.name = 'color-scheme';
                    metaTheme.content = 'dark';
                    document.head.appendChild(metaTheme);
                } else {
                    metaTheme.content = 'dark';
                }
                
                // اضافه کردن style برای اطمینان از تم تاریک
                var styleId = 'vinor-dark-mode-style';
                if (!document.getElementById(styleId)) {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = `
                        html { color-scheme: dark !important; }
                        body { background-color: #111827 !important; color: #F9FAFB !important; }
                    `;
                    document.head.appendChild(style);
                }
            })();
        """.trimIndent()
        
        try {
            webView.evaluateJavascript(darkModeScript, null)
            Log.d(fragmentTag, "Dark mode enabled")
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error enabling dark mode", e)
        }
    }

    /**
     * بررسی اینکه آیا WebView می‌تواند به عقب برگردد
     */
    fun canGoBackInWebView(): Boolean {
        return try {
            if (::webView.isInitialized && webView != null && view != null && !isDetached) {
                webView.canGoBack()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error checking canGoBack", e)
            false
        }
    }

    /**
     * برگشت در WebView history
     */
    fun goBackInWebView(): Boolean {
        return try {
            if (::webView.isInitialized && webView != null && view != null && !isDetached && webView.canGoBack()) {
                webView.goBack()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error going back in WebView", e)
            false
        }
    }

    /**
     * بررسی اینکه آیا در ریشه تب هستیم (URL اصلی)
     */
    fun isAtRoot(): Boolean {
        return try {
            if (!::webView.isInitialized || webView == null || view == null || isDetached) {
                return true
            }
            
            val currentUrl = webView.url ?: ""
            if (currentUrl.isEmpty()) return true
            
            // بررسی اینکه آیا URL فعلی همان targetUrl است یا به آن ختم می‌شود
            val cleanTarget = targetUrl.replace("https://", "").replace("http://", "")
            val cleanCurrent = currentUrl.replace("https://", "").replace("http://", "")
            
            currentUrl == targetUrl || 
            currentUrl.endsWith(targetUrl) ||
            cleanCurrent == cleanTarget ||
            cleanCurrent.startsWith(cleanTarget + "/")
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error checking isAtRoot", e)
            true
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (::webView.isInitialized && webView != null && view != null) {
                webView.onPause()
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error in onPause", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (::webView.isInitialized && webView != null && view != null) {
                webView.onResume()
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error in onResume", e)
        }
    }

    override fun onDestroyView() {
        try {
            if (::webView.isInitialized && webView != null) {
                // توقف بارگذاری
                webView.stopLoading()
                // حذف WebView از parent برای جلوگیری از memory leaks
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                // destroy WebView (WebViewClient و WebChromeClient به صورت خودکار پاک می‌شوند)
                webView.destroy()
            }
        } catch (e: Exception) {
            Log.e(fragmentTag, "Error in onDestroyView", e)
        }
        super.onDestroyView()
    }
}

