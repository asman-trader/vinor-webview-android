package ir.vinor.app

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object UrlUtils {
    private const val VINOR_HOST = "vinor.ir"
    private val HOME_PATHS = setOf("", "/", "/home", "/search", "/profile")

    fun normalize(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            var uri = URI(trimmed)
            // Force https and drop fragments/queries
            val scheme = "https"
            var host = uri.host ?: ""
            host = host.lowercase(Locale.US).removePrefix("www.")
            val path = (uri.path ?: "").removeSuffix("/")
            val normPath = if (path.isEmpty()) "/" else path
            URI(scheme, uri.userInfo, host, -1, normPath, null, null).toString()
        } catch (_: URISyntaxException) {
            trimmed
        }
    }

    fun isVinorDomain(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = (uri.host ?: "").lowercase(Locale.US).removePrefix("www.")
            host == VINOR_HOST
        } catch (_: Exception) {
            false
        }
    }

    fun isHomePage(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = (uri.host ?: "").lowercase(Locale.US).removePrefix("www.")
            if (host != VINOR_HOST) return false
            val path = (uri.path ?: "").removeSuffix("/")
            val normPath = if (path.isEmpty()) "/" else path
            HOME_PATHS.contains(normPath)
        } catch (_: Exception) {
            false
        }
    }
}

