package ir.vinor.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import ir.vinor.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val targetUrl = "https://vinor.ir"

    private val requiredPermissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.SEND_SMS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // No-op: on next tel: click we will try again or fall back to ACTION_DIAL
    }

    private val smsConsentResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        val message = data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return@registerForActivityResult
        // Try to extract a 4-8 digit code from the SMS text
        val code = Regex("""\b(\d{4,8})\b""").find(message)?.value
        // For now, just inject the code into the page if there is a handler (adjust selector if needed)
        code?.let { onOtpReceived(it) }
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

        // Ask call-related permissions early (optional)
        requestCallPermissionsIfNeeded()

        // Start SMS User Consent (no SMS permission required)
        SmsRetriever.getClient(this).startSmsUserConsent(null)
    }

    private var isSmsReceiverRegistered: Boolean = false
    private var pendingOtp: String? = null

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
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("tel:")) {
                    handleTelLink(url)
                    return true
                }
                if (url.startsWith("sms:") || url.startsWith("smsto:")) {
                    handleSmsLink(url)
                    return true
                }
                view?.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pendingOtp?.let { injectOtpToWebView(it) }
            }
        }
    }

    private fun handleSmsLink(url: String) {
        // Prefer ACTION_SENDTO to let user confirm sending; works without SEND_SMS
        val smsUri = if (url.startsWith("sms:")) {
            Uri.parse(url.replaceFirst("sms:", "smsto:"))
        } else Uri.parse(url)
        val intent = Intent(Intent.ACTION_SENDTO, smsUri)
        // Some sites pass ?body=...; Android reads it automatically for smsto:
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun onOtpReceived(code: String) {
        pendingOtp = code
        binding.webView.post { injectOtpToWebView(code) }
    }

    private fun injectOtpToWebView(code: String) {
        val escaped = code.replace("'", "\\'")
        val js = """
            (function(){
                var code = '$escaped';
                function setValue(el){
                    if(!el) return false;
                    try{
                        el.focus();
                        el.value = code;
                        el.setAttribute('value', code);
                        var evts=['input','change','keyup','keydown','blur'];
                        evts.forEach(function(t){ try{ el.dispatchEvent(new Event(t,{bubbles:true})); }catch(e){} });
                        return true;
                    }catch(e){ return false; }
                }
                var selectors = [
                    "input[autocomplete='one-time-code']",
                    "input[name*='otp' i]",
                    "input[name*='code' i]",
                    "input[id*='otp' i]",
                    "input[id*='code' i]",
                    "input[class*='otp' i]",
                    "input[class*='code' i]",
                    "input[type='tel']",
                    "input[type='number']"
                ];
                for (var i=0;i<selectors.length;i++){
                    var el = document.querySelector(selectors[i]);
                    if (setValue(el)) return 'ok';
                }
                return 'notfound';
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(js, ValueCallback { result ->
            if (result?.contains("ok") == true) {
                pendingOtp = null
            }
        })
    }

    private fun requestCallPermissionsIfNeeded() {
        val needs = requiredPermissions.any {
            ContextCompat.checkSelfPermission(this, it) != PermissionChecker.PERMISSION_GRANTED
        }
        if (needs) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun handleTelLink(url: String) {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE
        ) == PermissionChecker.PERMISSION_GRANTED
        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse(url))
        } else {
            // Fallback to dialer if no CALL_PHONE
            Intent(Intent.ACTION_DIAL, Uri.parse(url))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // No dialer present; ignore
        }
    }
}

