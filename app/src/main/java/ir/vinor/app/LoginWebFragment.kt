package ir.vinor.app

/**
 * صفحه ورود وبی داخل اپ (WebView داخلی).
 * آدرس: /express/partner/login با next به پروفایل.
 */
class LoginWebFragment : BaseWebViewFragment() {

    override val targetUrl: String =
        "https://vinor.ir/express/partner/login?next=/express/partner/profile"

    override val fragmentTag: String = "LoginWebFragment"
}

