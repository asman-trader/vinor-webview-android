package ir.vinor.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import ir.vinor.app.databinding.FragmentDashboardBinding
import ir.vinor.app.databinding.ItemLandCardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.webkit.CookieManager
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * تب وینور (صفحه اصلی) - کاملاً نیتیو.
 * داده از API /express/partner/dashboard/data با کوکی دریافت می‌شود.
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "DashboardFragment"
        private const val BASE = "https://vinor.ir"
        private const val DASHBOARD_DATA = "/express/partner/dashboard/data"
        private val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.dashboardSearchBtn.setOnClickListener { performSearch() }
        binding.dashboardSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }
        binding.dashboardTrainingCard.setOnClickListener { openTraining() }
        binding.dashboardEmptyLogin.setOnClickListener { openLogin() }
        loadData()
    }

    override fun onResume() {
        super.onResume()
        // هر بار بازگشت به تب وینور، داده‌ها را تازه می‌کنیم
        loadData()
    }

    override fun onDestroyView() {
        job.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun cookieHeader(): String {
        val cm = CookieManager.getInstance()
        // سعی می‌کنیم مثل وب، کوکی‌های مسیر /express/partner را هم بگیریم
        val candidates = listOf(
            "$BASE/express/partner/dashboard",
            "$BASE/express/partner/",
            BASE
        )
        for (u in candidates) {
            val c = cm.getCookie(u)
            if (!c.isNullOrBlank()) return c
        }
        return cm.getCookie(BASE) ?: ""
    }

    private fun toPersianDigits(s: String): String {
        return s.map { c -> if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c }.joinToString("")
    }

    private fun formatToman(value: Int): String {
        if (value == 0) return "۰"
        val s = value.toString().reversed().chunked(3).joinToString("،").reversed()
        return toPersianDigits(s)
    }

    private fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        val raw = iso.trim().replace("Z", "")
        val outFmt = SimpleDateFormat("yyyy/MM/dd", Locale("fa"))
        val parsers = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        for (parser in parsers) {
            try {
                val d = parser.parse(raw.take(19).trim())
                if (d != null) return outFmt.format(d)
            } catch (_: Exception) { }
        }
        return raw.take(10).replace("-", "/")
    }

    private fun performSearch() {
        val q = binding.dashboardSearch.text?.toString()?.trim() ?: ""
        val url = if (q.isEmpty()) "$BASE/public" else "$BASE/public?q=${Uri.encode(q)}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun openTraining() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$BASE/express/partner/training")))
    }

    private fun openLogin() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$BASE/express/partner/login")))
    }

    private fun loadData() {
        binding.dashboardProgress.visibility = View.VISIBLE
        binding.dashboardStats.visibility = View.GONE
        binding.dashboardLandList.visibility = View.GONE
        binding.dashboardEmpty.visibility = View.GONE
        binding.dashboardPendingAlert.visibility = View.GONE
        binding.dashboardExpiredAlert.visibility = View.GONE

        scope.launch {
            try {
                val req = Request.Builder()
                    .url("$BASE$DASHBOARD_DATA")
                    .addHeader("Cookie", cookieHeader())
                    .addHeader("Accept", "application/json")
                    .build()
                val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                if (!resp.isSuccessful) {
                    withContext(Dispatchers.Main) { showError() }
                    return@launch
                }
                val body = resp.body?.string() ?: ""
                if (body.isBlank()) {
                    Log.w(TAG, "dashboard/data empty body")
                    withContext(Dispatchers.Main) { showError() }
                    return@launch
                }
                val obj = try { JSONObject(body) } catch (e: Exception) {
                    Log.e(TAG, "dashboard/data not JSON: ${body.take(200)}", e)
                    withContext(Dispatchers.Main) { showError() }
                    return@launch
                }
                if (!obj.optBoolean("success", false)) {
                    withContext(Dispatchers.Main) { showError() }
                    return@launch
                }
                val profile = obj.optBoolean("profile", false)
                val isApproved = obj.optBoolean("is_approved", false)
                val hasPendingApp = obj.optBoolean("has_pending_app", false)
                val totalCommission = obj.optInt("total_commission", 0)
                val pendingCommission = obj.optInt("pending_commission", 0)
                val soldCount = obj.optInt("sold_count", 0)
                val expiredCount = obj.optInt("expired_count", 0)
                val landsArr = obj.optJSONArray("assigned_lands") ?: org.json.JSONArray()
                val assigned = mutableListOf<JSONObject>()
                for (i in 0 until landsArr.length()) {
                    try {
                        val item = landsArr.optJSONObject(i) ?: continue
                        assigned.add(item)
                    } catch (e: Exception) {
                        Log.w(TAG, "skip land item $i", e)
                    }
                }
                // برای مهمان: اگر فایل ارسالی نداشتیم، از لیست عمومی فایل‌ها استفاده می‌کنیم
                val publicArr = obj.optJSONArray("public_lands") ?: org.json.JSONArray()
                val publicLands = mutableListOf<JSONObject>()
                for (i in 0 until publicArr.length()) {
                    try {
                        publicArr.optJSONObject(i)?.let { publicLands.add(it) }
                    } catch (_: Exception) { }
                }
                val lands = if (assigned.isNotEmpty()) assigned else publicLands
                Log.d(TAG, "dashboard/data assigned=${assigned.size} public=${publicLands.size} show=${lands.size}")
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    bind(
                        profile = profile,
                        isApproved = isApproved,
                        hasPendingApp = hasPendingApp,
                        totalCommission = totalCommission,
                        pendingCommission = pendingCommission,
                        soldCount = soldCount,
                        expiredCount = expiredCount,
                        lands = lands,
                        isPublicList = assigned.isEmpty() && publicLands.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadData error", e)
                withContext(Dispatchers.Main) { showError() }
            }
        }
    }

    private fun showError() {
        if (_binding == null) return
        binding.dashboardProgress.visibility = View.GONE
        context?.let { Toast.makeText(it, "خطا در بارگذاری داشبورد", Toast.LENGTH_SHORT).show() }
        binding.dashboardStats.visibility = View.GONE
        binding.dashboardLandList.visibility = View.GONE
        binding.dashboardEmpty.visibility = View.VISIBLE
        binding.dashboardEmptyLogin.visibility = View.VISIBLE
    }

    private fun bind(
        profile: Boolean,
        isApproved: Boolean,
        hasPendingApp: Boolean,
        totalCommission: Int,
        pendingCommission: Int,
        soldCount: Int,
        expiredCount: Int,
        lands: List<JSONObject>,
        isPublicList: Boolean = false
    ) {
        if (_binding == null) return
        binding.dashboardProgress.visibility = View.GONE
        binding.dashboardStats.visibility = View.VISIBLE

        binding.dashboardTotalCommission.text = formatToman(totalCommission)
        binding.dashboardPendingCommission.text = formatToman(pendingCommission)
        binding.dashboardSoldCount.text = toPersianDigits(soldCount.toString())

        binding.dashboardPendingAlert.visibility =
            if (hasPendingApp && !isApproved) View.VISIBLE else View.GONE
        binding.dashboardExpiredAlert.visibility =
            if (expiredCount > 0) View.VISIBLE else View.GONE
        if (expiredCount > 0) {
            binding.dashboardExpiredText.text =
                if (expiredCount == 1) "اعتبار یک فایل به پایان رسیده."
                else "اعتبار ${toPersianDigits(expiredCount.toString())} فایل به پایان رسیده."
        }

        binding.dashboardEmptyLogin.visibility = if (profile) View.GONE else View.VISIBLE

        val list = binding.dashboardLandList
        list.removeAllViews()

        if (lands.isEmpty()) {
            list.visibility = View.GONE
            binding.dashboardEmpty.visibility = View.VISIBLE
            return
        }
        list.visibility = View.VISIBLE
        binding.dashboardEmpty.visibility = View.GONE
        // وقتی لیست عمومی است، باکس آمار را مخفی می‌کنیم (برای مهمان معنا ندارد)
        binding.dashboardStats.visibility = if (isPublicList) View.GONE else View.VISIBLE

        for (i in lands.indices) {
            try {
                val land = lands[i]
                val itemBinding = ItemLandCardBinding.inflate(layoutInflater, list, false)
                itemBinding.landCardTitle.text = land.optString("title", "—")
                val price = land.optInt("price_total", 0)
                itemBinding.landCardPrice.text = formatToman(price) + " تومان"
                val commissionAmount = land.optInt("commission_amount", 0)
                val commissionPct = land.optDouble("commission_pct", 0.0)
                if (commissionAmount > 0 || commissionPct > 0) {
                    itemBinding.landCardCommission.visibility = View.VISIBLE
                    itemBinding.landCardCommission.text =
                        "پورسانت: " + formatToman(commissionAmount) + " تومان" +
                            (if (commissionPct > 0) " (${commissionPct.toLong()}%)" else "")
                } else {
                    itemBinding.landCardCommission.visibility = View.GONE
                }
                val metaParts = listOfNotNull(
                    land.optString("size").takeIf { it.isNotBlank() }?.let { "$it متر" },
                    land.optString("location").takeIf { it.isNotBlank() },
                    land.optString("city").takeIf { it.isNotBlank() },
                    land.optString("category").takeIf { it.isNotBlank() }
                )
                itemBinding.landCardMeta.text = metaParts.joinToString(" • ").ifBlank { "—" }
                itemBinding.landCardDate.text = formatDate(land.optString("created_at"))

                val status = land.optString("assignment_status", "active").trim()
                val isExpired = land.optBoolean("is_expired", false)
                itemBinding.landCardStatusChip.text = when {
                    isPublicList -> "فایل عمومی"
                    isExpired -> "منقضی"
                    status == "sold" -> "فروخته شد"
                    else -> "فعال"
                }
                when {
                    isPublicList -> {
                        itemBinding.landCardStatusChip.setBackgroundResource(R.drawable.bg_routine_chip)
                        itemBinding.landCardStatusChip.setTextColor(0xFF9CA3AF.toInt())
                    }
                    isExpired -> {
                        itemBinding.landCardStatusChip.setBackgroundResource(R.drawable.bg_calendar_done2)
                        itemBinding.landCardStatusChip.setTextColor(0xFF9CA3AF.toInt())
                    }
                    status == "sold" -> {
                        itemBinding.landCardStatusChip.setBackgroundResource(R.drawable.bg_routine_chip_emerald)
                        itemBinding.landCardStatusChip.setTextColor(0xFF4ADE80.toInt())
                    }
                    else -> {
                        itemBinding.landCardStatusChip.setBackgroundResource(R.drawable.bg_routine_chip_blue)
                        itemBinding.landCardStatusChip.setTextColor(0xFF93C5FD.toInt())
                    }
                }

                val imageUrl = land.optString("image_url").takeIf { it.isNotBlank() }
                if (!imageUrl.isNullOrBlank()) {
                    val fullUrl = if (imageUrl.startsWith("http")) imageUrl else "$BASE$imageUrl"
                    itemBinding.landCardImage.visibility = View.GONE
                    itemBinding.landCardImagePlaceholder.visibility = View.VISIBLE
                    loadLandImage(fullUrl, itemBinding.landCardImage, itemBinding.landCardImagePlaceholder)
            } else {
                    itemBinding.landCardImage.visibility = View.GONE
                    itemBinding.landCardImagePlaceholder.visibility = View.VISIBLE
                }

                val detailUrl = land.optString("detail_url").takeIf { it.isNotBlank() }
                itemBinding.root.setOnClickListener {
                    if (!detailUrl.isNullOrBlank()) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(detailUrl)))
                    }
                }
                list.addView(itemBinding.root)
            } catch (e: Exception) {
                Log.e(TAG, "bind land card $i failed", e)
            }
        }
    }

    private fun loadLandImage(url: String, imageView: ImageView, placeholder: TextView) {
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("Cookie", cookieHeader())
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        val bytes = resp.body?.bytes() ?: return@withContext null
                        BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
                    }
                }
                if (_binding == null) return@launch
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                    placeholder.visibility = View.GONE
                } else {
                    imageView.visibility = View.GONE
                    placeholder.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadLandImage failed: $url", e)
                if (_binding == null) return@launch
                imageView.visibility = View.GONE
                placeholder.visibility = View.VISIBLE
            }
        }
    }
}
