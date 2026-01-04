package ir.vinor.app

/**
 * تب خانه - صفحه اصلی همکاران اکسپرس
 * فقط صفحه /express/partner/dashboard نمایش داده می‌شود
 */
class DashboardFragment : BaseWebViewFragment() {
    override val targetUrl: String = "https://vinor.ir/express/partner/dashboard"
    override val fragmentTag: String = "DashboardFragment"
    
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
        // فقط اگر URL همان dashboard است، reload کن
        val dashboardUrl = "https://vinor.ir/express/partner/dashboard"
        if (url.contains("/express/partner/dashboard") || url == dashboardUrl) {
            super.reloadWithUrl(dashboardUrl)
        } else {
            // اگر URL متفاوت است، به dashboard برگرد
            android.util.Log.d(fragmentTag, "Redirecting to dashboard: $url -> $dashboardUrl")
            super.reloadWithUrl(dashboardUrl)
        }
    }
}

