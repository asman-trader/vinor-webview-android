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
        .build()

    companion object {
        private const val TAG = "LoginStep2"
        private const val BASE = "https://vinor.ir"
        private const val API_VERIFY = "/express/partner/api/verify"
        private const val API_LOGIN_REQUEST = "/express/partner/api/login-request"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginStep2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val phone = arguments?.getString("phone") ?: ""
        binding.loginStep2PhoneLabel.text = "کد ارسال‌شده به $phone را وارد کنید."

        binding.loginStep2Back.setOnClickListener { findNavController().navigateUp() }
        binding.loginStep2Submit.setOnClickListener { submitCode(phone) }
        binding.loginStep2Resend.setOnClickListener { resendCode(phone) }
    }

    override fun onDestroyView() {
        job.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun cookieHeader(): String = CookieManager.getInstance().getCookie(BASE) ?: ""

    private fun submitCode(phone: String) {
        val code = binding.loginStep2Code.text?.toString()?.trim() ?: ""
        binding.loginStep2Error.visibility = View.GONE
        if (code.length < 5) {
            binding.loginStep2Error.text = "کد تأیید را وارد کنید."
            binding.loginStep2Error.visibility = View.VISIBLE
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
                    .addHeader("Cookie", cookieHeader())
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
                        Toast.makeText(requireContext(), "ورود با موفقیت انجام شد.", Toast.LENGTH_SHORT).show()
                        val nav = findNavController()
                        if (nav.currentBackStackEntry?.destination?.id == R.id.loginStep2Fragment) {
                            nav.popBackStack()
                        }
                        nav.popBackStack()
                    } else {
                        binding.loginStep2Error.text = obj?.optString("error", "کد نادرست است.")
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

    private fun resendCode(phone: String) {
        binding.loginStep2Error.visibility = View.GONE
        scope.launch {
            try {
                val json = JSONObject().put("phone", phone).toString()
                val req = Request.Builder()
                    .url("$BASE$API_LOGIN_REQUEST")
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .addHeader("Accept", "application/json")
                    .addHeader("Cookie", cookieHeader())
                    .build()
                val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                saveCookiesFromResponse(resp)
                val body = resp.body?.string() ?: ""
                val success = (try { JSONObject(body) } catch (_: Exception) { null })?.optBoolean("success", false) == true
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        Toast.makeText(requireContext(), if (success) "کد مجدد ارسال شد." else "خطا در ارسال مجدد.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) { }
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
