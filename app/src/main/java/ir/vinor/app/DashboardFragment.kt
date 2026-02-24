package ir.vinor.app

/**
 * تب وینور (صفحه اصلی) - WebView.
 * صفحه داشبورد وب و جزئیات فایل‌ها داخل خود اپ (WebView) باز می‌شوند.
 * لینک‌های دیگر (مثلاً لاگین عمومی، صفحات غیرمرتبط) در مرورگر خارج از اپ باز می‌شوند.
 */
class DashboardFragment : BaseWebViewFragment() {

    override val targetUrl: String = "https://vinor.ir/express/partner/dashboard"
    override val fragmentTag: String = "DashboardFragment"

    /** همیشه فقط همین صفحه وینور لود شود (بدون جایگزینی با منو). */
    override fun getUrlToLoad(): String = targetUrl

    /**
     * اجازه بده:
     * - خود داشبورد
     * - صفحه جزئیات فایل همکاران: /express/partner/lands/<code>
     * داخل همان WebView لود شوند؛ بقیه لینک‌ها در مرورگر باز می‌شوند.
     */
    override fun shouldOverrideUrlLoadingForFragment(url: String): Boolean? {
        val isDashboard = url.startsWith("https://vinor.ir/express/partner/dashboard")
        val isPartnerLandDetail = url.startsWith("https://vinor.ir/express/partner/lands/")

        // این دو نوع صفحه داخل خود اپ (WebView) باز شوند
        if (isDashboard || isPartnerLandDetail) return null

        // بقیه لینک‌ها در مرورگر سیستم باز شوند
        return true
    }

    /** تغییر آدرس از منو اعمال نشود؛ همیشه داشبورد بماند. */
    override fun reloadWithUrl(url: String) {
        if (isWebViewInitialized() && isAdded) {
            webView.loadUrl(targetUrl)
        }
    }
}
