package ir.vinor.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * مدیریت منوی Bottom Navigation - هماهنگ با API سایت
 */
object MenuManager {
    
    private const val MENU_API_URL = "https://vinor.ir/api/menu"
    private const val TAG = "MenuManager"
    
    /**
     * ساختار منوی آیتم
     */
    data class MenuItem(
        val key: String,
        val url: String,
        val icon: String,
        val label: String,
        val fragmentId: Int
    )
    
    /**
     * Mapping بین key منوی سایت و Fragment ID
     */
    private val keyToFragmentId = mapOf(
        // منوی عمومی
        "home" to R.id.homeFragment,
        "explore" to R.id.exploreFragment,
        "help" to R.id.helpFragment,
        "about" to R.id.aboutFragment,
        "login" to R.id.loginFragment,
        // منوی همکار
        "dashboard" to R.id.dashboardFragment,
        "commissions" to R.id.commissionsFragment,
        "express" to R.id.expressFragment,
        "routine" to R.id.routineFragment,
        "profile" to R.id.profileFragment
    )
    
    /**
     * Mapping بین icon سایت (FontAwesome) و drawable اندروید
     */
    private fun getIconDrawable(iconName: String): Int {
        return when (iconName) {
            "fa-home" -> R.drawable.ic_home
            "fa-magnifying-glass" -> R.drawable.ic_search
            "fa-question-circle" -> R.drawable.ic_help
            "fa-info-circle" -> R.drawable.ic_about
            "fa-user" -> R.drawable.ic_profile
            "fa-chart-line" -> R.drawable.ic_commissions
            "fa-list-check" -> R.drawable.ic_routine
            else -> R.drawable.ic_home // fallback
        }
    }
    
    /**
     * دریافت منو از API سایت
     */
    fun fetchMenu(
        scope: CoroutineScope,
        onSuccess: (List<MenuItem>) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val menuItems = withContext(Dispatchers.IO) {
                    fetchMenuFromAPI()
                }
                withContext(Dispatchers.Main) {
                    onSuccess(menuItems)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching menu: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // در صورت خطا، منوی پیش‌فرض را برگردان
                    onSuccess(getDefaultMenu())
                }
            }
        }
    }
    
    private fun fetchMenuFromAPI(): List<MenuItem> {
        val url = URL(MENU_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                if (json.getBoolean("success")) {
                    val menuArray = json.getJSONArray("menu")
                    val menuItems = mutableListOf<MenuItem>()
                    
                    for (i in 0 until menuArray.length()) {
                        val item = menuArray.getJSONObject(i)
                        val key = item.getString("key")
                        val url = item.getString("url")
                        val icon = item.getString("icon")
                        val label = item.getString("label")
                        
                        val fragmentId = keyToFragmentId[key] ?: continue
                        
                        menuItems.add(MenuItem(key, url, icon, label, fragmentId))
                    }
                    
                    Log.d(TAG, "Menu fetched successfully: ${menuItems.size} items")
                    menuItems
                } else {
                    Log.w(TAG, "API returned success=false")
                    getDefaultMenu()
                }
            } else {
                Log.w(TAG, "HTTP error: $responseCode")
                getDefaultMenu()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing menu response: ${e.message}", e)
            getDefaultMenu()
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * دریافت icon drawable از icon name
     */
    fun getIconDrawable(iconName: String): Int {
        return when (iconName) {
            "fa-home" -> R.drawable.ic_home
            "fa-magnifying-glass" -> R.drawable.ic_search
            "fa-question-circle" -> R.drawable.ic_help
            "fa-info-circle" -> R.drawable.ic_about
            "fa-user" -> R.drawable.ic_profile
            "fa-chart-line" -> R.drawable.ic_commissions
            "fa-list-check" -> R.drawable.ic_routine
            else -> R.drawable.ic_home // fallback
        }
    }
    
    /**
     * منوی پیش‌فرض (منوی عمومی)
     */
    fun getDefaultMenu(): List<MenuItem> {
        return listOf(
            MenuItem("home", "https://vinor.ir/", "fa-home", "خانه", R.id.homeFragment),
            MenuItem("explore", "https://vinor.ir/public", "fa-magnifying-glass", "اکسپلور", R.id.exploreFragment),
            MenuItem("help", "https://vinor.ir/help", "fa-question-circle", "راهنما", R.id.helpFragment),
            MenuItem("about", "https://vinor.ir/", "fa-info-circle", "درباره", R.id.aboutFragment),
            MenuItem("login", "https://vinor.ir/express/partner/login", "fa-user", "ورود", R.id.loginFragment)
        )
    }
}

