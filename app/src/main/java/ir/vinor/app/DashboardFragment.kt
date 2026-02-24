package ir.vinor.app

/**
 * تب وینور (صفحه اصلی) - WebView.
 * فقط صفحه داشبورد وب لود می‌شود؛ هر لینک دیگر (ورود، جزئیات فایل، ...) در مرورگر باز می‌شود.
 */
class DashboardFragment : BaseWebViewFragment() {

    override val targetUrl: String = "https://vinor.ir/express/partner/dashboard"
    override val fragmentTag: String = "DashboardFragment"

    /** همیشه فقط همین صفحه وینور لود شود (بدون جایگزینی با منو). */
    override fun getUrlToLoad(): String = targetUrl

    /** هر آدرسی غیر از صفحه وینور در مرورگر باز شود تا تب فقط داشبورد را نشان دهد. */
    override fun shouldOverrideUrlLoadingForFragment(url: String): Boolean? =
        if (url.startsWith("https://vinor.ir/express/partner/dashboard")) null else true

    /** تغییر آدرس از منو اعمال نشود؛ همیشه داشبورد بماند. */
    override fun reloadWithUrl(url: String) {
        if (::webView.isInitialized && isAdded) {
            webView.loadUrl(targetUrl)
        }
    }
}
