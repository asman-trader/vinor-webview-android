package ir.vinor.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import ir.vinor.app.databinding.FragmentRoutineBinding
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
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import android.webkit.CookieManager
import java.util.concurrent.TimeUnit

/**
 * تب روتین - کاملاً نیتیو (بدون WebView)
 * از API سایت برای داده‌های تقویم و مراحل استفاده می‌کند.
 */
class RoutineFragment : Fragment() {

    private var _binding: FragmentRoutineBinding? = null
    private val binding get() = _binding!!

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var currentMonth: Calendar = Calendar.getInstance(Locale.US)
    private var completedDays: List<String> = emptyList()
    private var stepsDetail: List<String> = emptyList()
    private var todayStr: String = ""

    companion object {
        private const val TAG = "RoutineFragment"
        private const val BASE = "https://vinor.ir"
        private const val ROUTINE_DATA = "/express/partner/routine/data"
        private const val ROUTINE_STEPS_DETAIL = "/express/partner/routine/steps/detail"
        private const val ROUTINE_STEPS = "/express/partner/routine/steps"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        todayStr = todayDateString()
        setupSteps()
        setupCalendarNav()
        loadTodayDetail()
        loadMonthData()
        binding.cardTraining.setOnClickListener {
            openTraining()
        }
    }

    override fun onDestroyView() {
        job.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun cookieHeader(): String {
        val cookie = CookieManager.getInstance().getCookie(BASE)
        return cookie ?: ""
    }

    private fun todayDateString(): String {
        val c = Calendar.getInstance(Locale.US)
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun setupSteps() {
        val step1 = binding.step1
        val step2 = binding.step2
        val step3 = binding.step3
        val progressBar = binding.routineProgressBar
        val progressText = binding.routineProgressText

        fun updateProgress() {
            var n = 0
            if (step1.isChecked) n++
            if (step2.isChecked) n++
            if (step3.isChecked) n++
            progressBar.progress = n
            progressText.text = "$n / ۳"
        }

        val listener: (Boolean) -> Unit = { _ -> updateProgress(); saveSteps() }
        step1.setOnCheckedChangeListener { _, _ -> updateProgress(); saveSteps() }
        step2.setOnCheckedChangeListener { _, _ -> updateProgress(); saveSteps() }
        step3.setOnCheckedChangeListener { _, _ -> updateProgress(); saveSteps() }
        updateProgress()
    }

    private fun saveSteps() {
        val detail = mutableListOf<String>()
        if (binding.step1.isChecked) detail.add("1")
        if (binding.step2.isChecked) detail.add("2")
        if (binding.step3.isChecked) detail.add("3")
        scope.launch {
            try {
                val json = JSONObject()
                    .put("date", todayStr)
                    .put("detail", org.json.JSONArray(detail))
                    .put("count", detail.size)
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$BASE$ROUTINE_STEPS")
                    .post(body)
                    .addHeader("Cookie", cookieHeader())
                    .addHeader("Content-Type", "application/json")
                    .build()
                withContext(Dispatchers.IO) {
                    client.newCall(req).execute()
                }.use { resp ->
                    if (resp.isSuccessful) {
                        loadMonthData()
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "خطا در ذخیره", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "saveSteps error", e)
                withContext(Dispatchers.Main) {
                    if (_binding != null) Toast.makeText(requireContext(), "خطا در ارتباط", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTodayDetail() {
        scope.launch {
            try {
                val url = "$BASE$ROUTINE_STEPS_DETAIL?date=${URLEncoder.encode(todayStr, "UTF-8")}"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookieHeader())
                    .build()
                val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                if (!resp.isSuccessful) return@launch
                val body = resp.body?.string() ?: return@launch
                val obj = JSONObject(body)
                val arr = obj.optJSONArray("detail")
                stepsDetail = mutableListOf<String>().apply {
                    if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i, ""))
                }
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.step1.isChecked = stepsDetail.contains("1")
                    binding.step2.isChecked = stepsDetail.contains("2")
                    binding.step3.isChecked = stepsDetail.contains("3")
                    binding.routineProgressBar.progress = stepsDetail.size
                    binding.routineProgressText.text = "${stepsDetail.size} / ۳"
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadTodayDetail error", e)
            }
        }
    }

    private fun loadMonthData() {
        val month = String.format(Locale.US, "%04d-%02d", currentMonth.get(Calendar.YEAR), currentMonth.get(Calendar.MONTH) + 1)
        scope.launch {
            try {
                val url = "$BASE$ROUTINE_DATA?month=${URLEncoder.encode(month, "UTF-8")}"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookieHeader())
                    .build()
                val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                if (!resp.isSuccessful) return@launch
                val body = resp.body?.string() ?: return@launch
                val obj = JSONObject(body)
                val daysArr = obj.optJSONArray("days")
                completedDays = mutableListOf<String>().apply {
                    if (daysArr != null) for (i in 0 until daysArr.length()) add(daysArr.optString(i, ""))
                }
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    updateMonthLabel()
                    fillCalendar()
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMonthData error", e)
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    completedDays = emptyList()
                    updateMonthLabel()
                    fillCalendar()
                }
            }
        }
    }

    private fun updateMonthLabel() {
        val monthName = try {
            java.text.SimpleDateFormat("MMMM yyyy", Locale("fa")).format(currentMonth.time)
        } catch (_: Exception) {
            String.format(Locale.US, "%04d-%02d", currentMonth.get(Calendar.YEAR), currentMonth.get(Calendar.MONTH) + 1)
        }
        binding.routineMonthLabel.text = monthName
        val count = completedDays.size
        binding.routineStatus.text = if (count > 0) "$count روز انجام روتین در این ماه" else ""
    }

    private fun fillCalendar() {
        if (_binding == null) return
        val grid = binding.routineCalendar
        grid.removeAllViews()
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday -> 0-based col
        val numEmpty = firstDayOfWeek - 1
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
        val cellSize = (resources.displayMetrics.widthPixels - 32 * dp) / 7
        val cellH = 36 * dp

        var index = 0
        for (i in 0 until numEmpty) {
            val tv = TextView(requireContext())
            tv.text = ""
            val lp = GridLayout.LayoutParams(GridLayout.spec(index / 7), GridLayout.spec(index % 7))
            lp.width = cellSize
            lp.height = cellH
            lp.setMargins(2, 2, 2, 2)
            tv.layoutParams = lp
            grid.addView(tv)
            index++
        }
        for (day in 1..maxDay) {
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            val tv = TextView(requireContext())
            tv.text = day.toString()
            tv.gravity = android.view.Gravity.CENTER
            tv.setTextColor(if (completedDays.contains(dateStr)) 0xFF16a34a.toInt() else 0xFF9CA3AF.toInt())
            tv.textSize = 12f
            val lp = GridLayout.LayoutParams(GridLayout.spec(index / 7), GridLayout.spec(index % 7))
            lp.width = cellSize
            lp.height = cellH
            lp.setMargins(2, 2, 2, 2)
            tv.layoutParams = lp
            grid.addView(tv)
            index++
        }
    }

    private fun setupCalendarNav() {
        binding.btnPrevMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            loadMonthData()
        }
        binding.btnNextMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            loadMonthData()
        }
    }

    private fun openTraining() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$BASE/express/partner/training"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "خطا در باز کردن لینک", Toast.LENGTH_SHORT).show()
        }
    }
}
