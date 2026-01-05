package ir.vinor.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import ir.vinor.app.databinding.ActivityMainBinding

/**
 * MainActivity با Bottom Navigation - 5 تب همکار اکسپرس:
 * خانه، پورسانت، اکسپلور، روتین، من
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastBackPressTime: Long = 0
    private val backPressThreshold = 2000 // 2 seconds
    private var currentMenuItems: List<MenuManager.MenuItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        // اجباری کردن تم تاریک در تمام اپلیکیشن
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupBackPressHandler()
        loadMenuFromAPI()
        
        // Connection Test - لاگ برای تست
        Log.d("MainActivity", "App started - Connection Test")
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        // اتصال Bottom Navigation به NavController
        binding.bottomNavigation.setupWithNavController(navController)

        // لاگ تغییر تب و به‌روزرسانی URL از منوی API
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("MainActivity", "Tab changed to: ${destination.label}")
            updateFragmentUrlFromMenu(destination.id)
            logCurrentTab()
        }
    }

    /**
     * بارگذاری منو از API سایت
     */
    private fun loadMenuFromAPI() {
        MenuManager.fetchMenu(
            scope = lifecycleScope,
            onSuccess = { menuItems ->
                currentMenuItems = menuItems
                updateBottomNavigationMenu(menuItems)
                updateFragmentUrls(menuItems)
                Log.d("MainActivity", "Menu loaded: ${menuItems.size} items")
            },
            onError = { error ->
                Log.e("MainActivity", "Error loading menu: $error")
                // استفاده از منوی پیش‌فرض
                val defaultMenu = MenuManager.getDefaultMenu()
                currentMenuItems = defaultMenu
                updateBottomNavigationMenu(defaultMenu)
                updateFragmentUrls(defaultMenu)
            }
        )
    }
    
    /**
     * به‌روزرسانی URLهای Fragmentها بر اساس منوی API
     */
    private fun updateFragmentUrls(menuItems: List<MenuManager.MenuItem>) {
        // ذخیره mapping برای استفاده بعدی
        menuItems.forEach { menuItem ->
            val fullUrl = if (menuItem.url.startsWith("http")) {
                menuItem.url
            } else {
                "https://vinor.ir${menuItem.url}"
            }
            Log.d("MainActivity", "Menu item: ${menuItem.key} -> $fullUrl (Fragment ID: ${menuItem.fragmentId})")
        }
        
        // به‌روزرسانی Fragment فعلی
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as? NavHostFragment
        val currentDestinationId = navHostFragment?.navController?.currentDestination?.id
        if (currentDestinationId != null) {
            updateFragmentUrlFromMenu(currentDestinationId)
        }
    }
    
    /**
     * به‌روزرسانی URL Fragment بر اساس منوی API
     */
    private fun updateFragmentUrlFromMenu(fragmentId: Int) {
        val menuItem = currentMenuItems.find { it.fragmentId == fragmentId }
        if (menuItem != null) {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.navHostFragment) as? NavHostFragment
            val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
            
            if (currentFragment is BaseWebViewFragment) {
                val fullUrl = if (menuItem.url.startsWith("http")) {
                    menuItem.url
                } else {
                    "https://vinor.ir${menuItem.url}"
                }
                val currentUrl = currentFragment.getCurrentUrl()
                
                // برای Dashboard: اگر در همان تب هستیم و URL یکسان است، refresh کن
                if (fragmentId == R.id.dashboardFragment && currentFragment is DashboardFragment) {
                    val isSameUrl = currentUrl != null && (
                        currentUrl == fullUrl || 
                        currentUrl.contains("/express/partner/dashboard")
                    )
                    if (isSameUrl) {
                        // اگر در همان تب و همان URL هستیم، refresh کن
                        Log.d("MainActivity", "Dashboard tab selected - refreshing")
                        currentFragment.reloadWithUrl(fullUrl)
                    } else {
                        // اگر URL متفاوت است، load کن
                        currentFragment.reloadWithUrl(fullUrl)
                    }
                } else {
                    // برای سایر تب‌ها: فقط اگر URL متفاوت است، reload کن
                    if (currentUrl != fullUrl) {
                        currentFragment.reloadWithUrl(fullUrl)
                    }
                }
            }
        }
    }

    /**
     * به‌روزرسانی منوی Bottom Navigation
     */
    private fun updateBottomNavigationMenu(menuItems: List<MenuManager.MenuItem>) {
        val menu = binding.bottomNavigation.menu
        menu.clear()

        menuItems.forEachIndexed { index, item ->
            val menuItem = menu.add(0, item.fragmentId, index, item.label)
            val iconRes = MenuManager.getIconDrawable(item.icon)
            menuItem.icon = getDrawable(iconRes) ?: getDrawable(R.drawable.ic_home)
        }

        // اتصال مجدد به NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as? NavHostFragment
        navHostFragment?.navController?.let { navController ->
            binding.bottomNavigation.setupWithNavController(navController)
        }
    }

    private fun setupBackPressHandler() {
        // Back handling هوشمند
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.navHostFragment) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

                when {
                    // اگر Fragment یک BaseWebViewFragment است و WebView history دارد
                    currentFragment is BaseWebViewFragment && currentFragment.canGoBackInWebView() -> {
                        currentFragment.goBackInWebView()
                        Log.d("MainActivity", "WebView goBack")
                    }
                    
                    // اگر در ریشه تب هستیم (نه خانه) => به خانه برگرد
                    currentFragment is BaseWebViewFragment && 
                    currentFragment.isAtRoot() -> {
                        val homeFragmentId = currentMenuItems.firstOrNull()?.fragmentId ?: R.id.dashboardFragment
                        if (navHostFragment?.navController?.currentDestination?.id != homeFragmentId) {
                            navHostFragment?.navController?.navigate(homeFragmentId)
                            Log.d("MainActivity", "Navigated to Home tab")
                        } else {
                            handleExitConfirmation()
                        }
                    }
                    
                    // اگر در خانه و ریشه هستیم => Confirm خروج
                    else -> {
                        val homeFragmentId = currentMenuItems.firstOrNull()?.fragmentId ?: R.id.dashboardFragment
                        if (navHostFragment?.navController?.currentDestination?.id == homeFragmentId) {
                            handleExitConfirmation()
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun handleExitConfirmation() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastBackPressTime < backPressThreshold) {
            // خروج از اپ
            finish()
        } else {
            // نمایش پیام
            lastBackPressTime = currentTime
            Toast.makeText(
                this,
                "برای خروج دوباره دکمه بازگشت را فشار دهید",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * لاگ برای Connection Test - نمایش URL فعلی هر تب
     */
    private fun logCurrentTab() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
        
        if (currentFragment is BaseWebViewFragment) {
            Log.d("MainActivity", "Current Tab: ${currentFragment.fragmentTag}")
            Log.d("MainActivity", "Target URL: ${currentFragment.targetUrl}")
            val currentUrl = try {
                currentFragment.getCurrentUrl() ?: "Not loaded"
            } catch (e: Exception) {
                "Error getting URL: ${e.message}"
            }
            Log.d("MainActivity", "Current WebView URL: $currentUrl")
        }
    }
}
