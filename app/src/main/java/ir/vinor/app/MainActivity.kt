package ir.vinor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import ir.vinor.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val targetUrl = "https://vinor.ir"

    private val smsConsentResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        val message = data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return@registerForActivityResult
        // Try to extract a 4-8 digit code from the SMS text
        val code = Regex("""\b(\d{4,8})\b""").find(message)?.value
        // For now, just inject the code into the page if there is a handler (adjust selector if needed)
        code?.let {
            binding.webView.post {
                binding.webView.evaluateJavascript(
                    "(function(){var i=document.querySelector('input[type=\"tel\"],input[name*=\"code\"],input[name*=\"otp\"]'); if(i){ i.value='${'$'}it'; var e=new Event('input',{bubbles:true}); i.dispatchEvent(e);} return true;})();",
                    null
                )
            }
        }
    }

    private val smsBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION != intent?.action) return
            val extras = intent.extras ?: return
            val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return
            when (status.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                    consentIntent?.let { smsConsentResultLauncher.launch(it) }
                }
                CommonStatusCodes.TIMEOUT -> {
                    // Ignore; no SMS received within the timeout window
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureWebView()
        binding.webView.loadUrl(targetUrl)

        // Start SMS User Consent (no SMS permission required)
        SmsRetriever.getClient(this).startSmsUserConsent(null)
    }

    private var isSmsReceiverRegistered: Boolean = false

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(smsBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(smsBroadcastReceiver, filter)
        }
        isSmsReceiverRegistered = true
    }

    override fun onStop() {
        if (isSmsReceiverRegistered) {
            try {
                unregisterReceiver(smsBroadcastReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver already unregistered; ignore
            }
            isSmsReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun configureWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                request?.url?.toString()?.let { view?.loadUrl(it) }
                return true
            }
        }
    }
}

