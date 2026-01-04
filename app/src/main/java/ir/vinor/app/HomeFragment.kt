package ir.vinor.app

/**
 * تب خانه - صفحه اصلی یا لیست فایل‌های اکسپرس
 * URL: / یا /public (بسته به منوی سایت)
 */
class HomeFragment : BaseWebViewFragment() {
    override val targetUrl: String = "https://vinor.ir/"
    override val fragmentTag: String = "HomeFragment"
}

