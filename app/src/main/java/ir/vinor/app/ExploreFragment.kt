package ir.vinor.app

/**
 * تب اکسپلور - برای کاربران عمومی و همکاران اکسپرس
 * URL: /public (عمومی) یا /express/partner/explore (همکار)
 */
class ExploreFragment : BaseWebViewFragment() {
    override val targetUrl: String = "https://vinor.ir/public"
    override val fragmentTag: String = "ExploreFragment"
}
