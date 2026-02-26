package ir.vinor.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ir.vinor.app.databinding.FragmentLoginStep2Binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.webkit.CookieManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper

/**
 * ورود نیتیو - مرحله ۲: وارد کردن کد OTP و تأیید.
 */
class LoginStep2Fragment : Fragment() {

    private var _binding: FragmentLoginStep2Binding? = null
    private val binding get() = _binding!!

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    companion object {
        private const val TAG = "LoginStep2"
        private const val BASE = "https://vinor.ir"
        private const val API_VERIFY = "/express/partner/api/verify"
        private const val API_LOGIN_REQUEST = "/express/partner/api/login-request"
        private const val API_OTP_RESEND = "/express/partner/otp/resend"
        private const val RESEND_COOLDOWN_SEC = 60
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginStep2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val phone = arguments?.getString("phone")?.trim() ?: ""
        if (phone.length != 11 || !phone.startsWith("09")) {
            findNavController().navigateUp()
            return
        }
        binding.loginStep2PhoneLabel.text = "کد ۵ رقمی ارسال‌شده به $phone را وارد کنید"
        startResendCountdown()

        binding.loginStep2Back.setOnClickListener { findNavController().navigateUp() }
        binding.loginStep2Submit.setOnClickListener { submitCode(phone) }
        binding.loginStep2Resend.isEnabled = false
        binding.loginStep2Resend.setOnClickListener { resendCode(phone) }
        binding.loginStep2Code.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                submitCode(phone)
                true
            } else false
        }
    }

    override fun onDestroyView() {
        resendCountdownJob?.let { handler.removeCallbacks(it) }
        resendCountdownJob = null
        job.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun cookieHeader(): String = CookieManager.getInstance().getCookie(BASE) ?: ""

    private fun submitCode(phone: String) {
        val raw = binding.loginStep2Code.text?.toString()?.trim() ?: ""
        val code = raw.filter { it.isDigit() }.take(5)
        binding.loginStep2Error.visibility = View.GONE
        if (code.length < 5) {
            binding.loginStep2Error.text = "لطفاً کد ۵ رقمی را کامل وارد کنید."
            binding.loginStep2Error.visibility = View.VISIBLE
            return
        }
        val cookie = cookieHeader()
        if (cookie.isBlank()) {
            binding.loginStep2Error.text = "نشست منقضی شده. لطفاً به مرحله قبل برگردید و دوباره درخواست کد دهید."
            binding.loginStep2Error.visibility = View.VISIBLE
            Log.w(TAG, "verify: no session cookie")
            return
        }

        binding.loginStep2Progress.visibility = View.VISIBLE
        binding.loginStep2Submit.isEnabled = false

        scope.launch {
            try {
                val json = JSONObject().put("code", code).put("phone", phone).toString()
                val req = Request.Builder()
                    .url("$BASE$API_VERIFY")
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Cookie", cookie)
                    .build()
                val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                saveCookiesFromResponse(resp)
                val body = resp.body?.string() ?: ""
                val obj = try { JSONObject(body) } catch (_: Exception) { null }
                val success = obj?.optBoolean("success", false) == true

                withContext(Dispatchers.Main) {
                    binding.loginStep2Progress.visibility = View.GONE
                    binding.loginStep2Submit.isEnabled = true
                    if (_binding == null) return@withContext
                    if (success) {
                        if (isAdded) Toast.makeText(requireContext(), "ورود با موفقیت انجام شد.", Toast.LENGTH_SHORT).show()
                        val nav = findNavController()
                        try {
                            nav.popBackStack(R.id.loginStep2Fragment, true)
                            nav.popBackStack(R.id.loginStep1Fragment, true)
                        } catch (_: Exception) {
                            nav.popBackStack()
                            nav.popBackStack()
                        }
                    } else {
                        binding.loginStep2Error.text = obj?.optString("error", "کد تأیید نادرست است.")
                        binding.loginStep2Error.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "verify error", e)
                withContext(Dispatchers.Main) {
                    binding.loginStep2Progress.visibility = View.GONE
                    binding.loginStep2Submit.isEnabled = true
                    if (_binding != null) {
                        binding.loginStep2Error.text = "خطا در ارتباط. دوباره تلاش کنید."
                        binding.loginStep2Error.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var resendCountdownJob: Runnable? = null

    private fun startResendCountdown() {
        resendCountdownJob?.let { handler.removeCallbacks(it) }
        binding.loginStep2Resend.isEnabled = false
        var secLeft = RESEND_COOLDOWN_SEC
        fun updateUi() {
            if (_binding == null) return
            if (secLeft <= 0) {
                binding.loginStep2Timer.text = "هنوز کد را دریافت نکردید؟"
                binding.loginStep2Resend.isEnabled = true
                return
            }
            binding.loginStep2Timer.text = "ارسال مجدد کد تا %d ثانیه دیگر".format(secLeft)
            secLeft--
            resendCountdownJob = Runnable { updateUi() }
            handler.postDelayed(resendCountdownJob!!, 1000L)
        }
        updateUi()
    }

    private var resendCooldownUntil = 0L
    private fun resendCode(phone: String) {
        if (phone.length != 11) return
        val now = System.currentTimeMillis()
        if (now < resendCooldownUntil) {
            val sec = ((resendCooldownUntil - now) / 1000).toInt()
            if (_binding != null && isAdded) {
                Toast.makeText(requireContext(), "لطفاً %d ثانیه صبر کنید.".format(sec), Toast.LENGTH_SHORT).show()
            }
            return
        }
        binding.loginStep2Error.visibility = View.GONE
        binding.loginStep2Resend.isEnabled = false
        resendCooldownUntil = now + RESEND_COOLDOWN_SEC * 1000L
        scope.launch {
            try {
                val json = JSONObject().put("phone", phone).toString()
                val req = Request.Builder()
                    .url("$BASE$API_OTP_RESEND")
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Cookie", cookieHeader())
                    .build()
                val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                saveCookiesFromResponse(resp)
                val body = resp.body?.string() ?: ""
                val obj = try { JSONObject(body) } catch (_: Exception) { null }
                val ok = obj?.optBoolean("ok", false) == true
                val msg = obj?.optString("message", null) ?: obj?.optString("error", if (ok) "کد تأیید مجدد ارسال شد." else "خطا در ارسال مجدد.")
                withContext(Dispatchers.Main) {
                    if (_binding != null && isAdded) {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                    if (_binding != null && ok) startResendCountdown()
                    else if (_binding != null) binding.loginStep2Resend.isEnabled = true
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.loginStep2Resend.isEnabled = true
                        if (isAdded) Toast.makeText(requireContext(), "خطا در ارتباط. دوباره تلاش کنید.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveCookiesFromResponse(resp: okhttp3.Response) {
        try {
            resp.headers("Set-Cookie").forEach { value ->
                CookieManager.getInstance().setCookie(BASE, value)
            }
            CookieManager.getInstance().flush()
        } catch (_: Exception) { }
    }
}
