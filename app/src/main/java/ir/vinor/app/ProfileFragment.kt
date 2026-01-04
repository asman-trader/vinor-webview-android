package ir.vinor.app

/**
 * تب پروفایل - پروفایل همکار اکسپرس
 * URL: /express/partner/profile
 * نیاز به لاگین دارد - اگر لاگین نباشد به /express/partner/login ریدایرکت می‌شود
 */
class ProfileFragment : BaseWebViewFragment() {
    override val targetUrl = "https://vinor.ir/express/partner/profile"
    override val fragmentTag = "ProfileFragment"
}

