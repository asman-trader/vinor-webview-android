package ir.vinor.app

/**
 * تب وینور (صفحه اصلی) - WebView.
 * همان صفحه داشبورد وب (کارت‌ها، آمار، هشدارها، جستجو و ...) در اپ نمایش داده می‌شود.
 */
class DashboardFragment : BaseWebViewFragment() {

    override val targetUrl: String = "https://vinor.ir/express/partner/dashboard"
    override val fragmentTag: String = "DashboardFragment"
}
