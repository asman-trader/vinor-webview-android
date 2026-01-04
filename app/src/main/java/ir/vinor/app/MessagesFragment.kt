package ir.vinor.app

/**
 * تب پیام‌ها/مشاوره - اعلان‌های همکار اکسپرس
 * URL: /express/partner/notifications
 * نیاز به لاگین دارد - اگر لاگین نباشد به /express/partner/login ریدایرکت می‌شود
 */
class MessagesFragment : BaseWebViewFragment() {
    override val targetUrl = "https://vinor.ir/express/partner/notifications"
    override val fragmentTag = "MessagesFragment"
}

