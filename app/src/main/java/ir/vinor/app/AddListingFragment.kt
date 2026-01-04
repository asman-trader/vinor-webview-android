package ir.vinor.app

/**
 * تب ثبت آگهی - داشبورد همکار اکسپرس
 * URL: /express/partner/dashboard
 * نیاز به لاگین دارد - اگر لاگین نباشد به /express/partner/login ریدایرکت می‌شود
 */
class AddListingFragment : BaseWebViewFragment() {
    override val targetUrl = "https://vinor.ir/express/partner/dashboard"
    override val fragmentTag = "AddListingFragment"
}

