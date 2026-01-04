package ir.vinor.app

/**
 * تب جستجو - لیست فایل‌های اکسپرس (همان صفحه خانه)
 * URL: /public
 */
class SearchFragment : BaseWebViewFragment() {
    override val targetUrl: String = "https://vinor.ir/public"
    override val fragmentTag: String = "SearchFragment"
}

