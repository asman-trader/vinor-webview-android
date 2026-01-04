package ir.vinor.app

/**
 * تب خانه - صفحه اصلی همکاران اکسپرس
 * فقط صفحه /express/partner/dashboard نمایش داده می‌شود
 */
class DashboardFragment : BaseWebViewFragment() {
    override val targetUrl: String = "https://vinor.ir/express/partner/dashboard"
    override val fragmentTag: String = "DashboardFragment"
    
    private var lastRefreshTime: Long = 0
    private val refreshThreshold = 1000 // 1 second - اگر کمتر از 1 ثانیه از آخرین refresh گذشته باشد، refresh نکن
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // اطمینان از اینکه فقط صفحه اصلی همکاران نمایش داده می‌شود
        // dynamicUrl را null می‌کنیم تا همیشه از targetUrl استفاده شود
        dynamicUrl = null
    }
    
    /**
     * Override برای اطمینان از اینکه URL dashboard تغییر نمی‌کند
     * همیشه صفحه اصلی همکاران (/express/partner/dashboard) نمایش داده می‌شود
     */
    override fun reloadWithUrl(url: String) {
        val dashboardUrl = "https://vinor.ir/express/partner/dashboard"
        val currentTime = System.currentTimeMillis()
        
        // بررسی اینکه آیا URL همان dashboard است
        val isDashboardUrl = url.contains("/express/partner/dashboard") || url == dashboardUrl
        
        if (isDashboardUrl) {
            // اگر URL همان dashboard است
            val currentUrl = getCurrentUrl()
            val isSameUrl = currentUrl != null && (
                currentUrl == dashboardUrl || 
                currentUrl.contains("/express/partner/dashboard")
            )
            
            // اگر URL یکسان است و کمتر از threshold گذشته باشد، refresh نکن (جلوگیری از refresh مکرر)
            if (isSameUrl && (currentTime - lastRefreshTime) < refreshThreshold) {
                android.util.Log.d(fragmentTag, "Skipping refresh - too soon after last refresh")
                return
            }
            
            // اگر URL یکسان است اما زمان کافی گذشته، reload کن (refresh)
            if (isSameUrl) {
                android.util.Log.d(fragmentTag, "Refreshing dashboard page")
                lastRefreshTime = currentTime
                if (::webView.isInitialized) {
                    webView.reload()
                }
            } else {
                // اگر URL متفاوت است، load کن
                android.util.Log.d(fragmentTag, "Loading dashboard: $url -> $dashboardUrl")
                lastRefreshTime = currentTime
                super.reloadWithUrl(dashboardUrl)
            }
        } else {
            // اگر URL متفاوت است، به dashboard برگرد
            android.util.Log.d(fragmentTag, "Redirecting to dashboard: $url -> $dashboardUrl")
            lastRefreshTime = currentTime
            super.reloadWithUrl(dashboardUrl)
        }
    }
    
    /**
     * وقتی Fragment دوباره visible می‌شود، اگر در dashboard هستیم، refresh کن
     */
    override fun onResume() {
        super.onResume()
        // اگر Fragment visible است و در dashboard هستیم، refresh کن
        if (isVisible && ::webView.isInitialized) {
            val currentUrl = getCurrentUrl()
            if (currentUrl != null && currentUrl.contains("/express/partner/dashboard")) {
                val currentTime = System.currentTimeMillis()
                // فقط اگر بیش از threshold گذشته باشد، refresh کن
                if ((currentTime - lastRefreshTime) >= refreshThreshold) {
                    android.util.Log.d(fragmentTag, "Fragment resumed - refreshing dashboard")
                    lastRefreshTime = currentTime
                    webView.reload()
                }
            }
        }
    }
}

