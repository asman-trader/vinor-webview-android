package ir.vinor.app

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import ir.vinor.app.databinding.ActivityMainBinding

/**
 * MainActivity با Bottom Navigation و 5 تب
 * - خانه
 * - جستجو
 * - ثبت آگهی
 * - پیام‌ها
 * - پروفایل
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastBackPressTime: Long = 0
    private val backPressThreshold = 2000 // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupBackPressHandler()
        
        // Connection Test - لاگ برای تست
        Log.d("MainActivity", "App started - Connection Test")
        logCurrentTab()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        // اتصال Bottom Navigation به NavController
        binding.bottomNavigation.setupWithNavController(navController)

        // لاگ تغییر تب
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("MainActivity", "Tab changed to: ${destination.label}")
            logCurrentTab()
        }
    }

    private fun setupBackPressHandler() {
        // Back handling هوشمند
        onBackPressedDispatcher.addCallback(this) {
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
                currentFragment.isAtRoot() && 
                navHostFragment?.navController?.currentDestination?.id != R.id.homeFragment -> {
                    navHostFragment?.navController?.navigate(R.id.homeFragment)
                    Log.d("MainActivity", "Navigated to Home tab")
                }
                
                // اگر در خانه و ریشه هستیم => Confirm خروج
                navHostFragment?.navController?.currentDestination?.id == R.id.homeFragment -> {
                    handleExitConfirmation()
                }
                
                // در غیر این صورت، Navigation Component خودش handle می‌کند
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
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
                if (currentFragment::webView.isInitialized) {
                    currentFragment.webView.url ?: "Not loaded"
                } else {
                    "WebView not initialized"
                }
            } catch (e: Exception) {
                "Error getting URL: ${e.message}"
            }
            Log.d("MainActivity", "Current WebView URL: $currentUrl")
        }
    }
}
