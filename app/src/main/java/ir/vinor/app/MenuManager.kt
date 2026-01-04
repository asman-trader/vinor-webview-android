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
     * فقط 5 تب همکار اکسپرس: خانه، پورسانت، اکسپلور، روتین، من
     */
    private val keyToFragmentId = mapOf(
        "dashboard" to R.id.dashboardFragment,      // خانه
        "commissions" to R.id.commissionsFragment,  // پورسانت
        "express" to R.id.expressFragment,          // اکسپلور
        "routine" to R.id.routineFragment,          // روتین
        "profile" to R.id.profileFragment           // من
    )
    
    
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
     * منوی پیش‌فرض - 5 تب همکار اکسپرس
     */
    fun getDefaultMenu(): List<MenuItem> {
        return listOf(
            MenuItem("dashboard", "https://vinor.ir/express/partner/dashboard", "fa-home", "خانه", R.id.dashboardFragment),
            MenuItem("commissions", "https://vinor.ir/express/partner/commissions", "fa-chart-line", "پورسانت", R.id.commissionsFragment),
            MenuItem("express", "https://vinor.ir/express/partner/explore", "fa-magnifying-glass", "اکسپلور", R.id.expressFragment),
            MenuItem("routine", "https://vinor.ir/express/partner/routine", "fa-list-check", "روتین", R.id.routineFragment),
            MenuItem("profile", "https://vinor.ir/express/partner/profile", "fa-user", "من", R.id.profileFragment)
        )
    }
}

