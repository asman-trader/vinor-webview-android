# ساختار اپلیکیشن وینور - App Skeleton

## Routeهای استخراج شده از پروژه وب

### تب‌های اصلی:
1. **خانه (Home)**: `https://vinor.ir/public` - لیست فایل‌های اکسپرس
2. **جستجو (Search)**: `https://vinor.ir/public` - همان لیست (با قابلیت جستجو)
3. **ثبت آگهی (Add Listing)**: `https://vinor.ir/express/partner/dashboard` - نیاز به لاگین
4. **پیام‌ها (Messages)**: `https://vinor.ir/express/partner/notifications` - نیاز به لاگین
5. **پروفایل (Profile)**: `https://vinor.ir/express/partner/profile` - نیاز به لاگین

### ورود:
- **Login**: `https://vinor.ir/express/partner/login` - اگر کاربر لاگین نباشد، صفحات نیازمند لاگین به این صفحه ریدایرکت می‌شوند

## ساختار پروژه

### MainActivity
- مدیریت Bottom Navigation
- Back handling هوشمند:
  - اگر WebView history دارد => goBack
  - اگر در ریشه تب هست (نه خانه) => به خانه برگرد
  - اگر در خانه و ریشه است => Confirm خروج
- Connection Test logging برای debug

### BaseWebViewFragment
- Base class برای تمام Fragmentها
- مدیریت WebView با تنظیمات امنیتی
- Loader/Skeleton نمایش
- Offline handling
- DeepLink handling (لینک‌های vinor.ir داخل WebView، بقیه با مرورگر)

### Fragmentها
- `HomeFragment`: تب خانه
- `SearchFragment`: تب جستجو
- `AddListingFragment`: تب ثبت آگهی
- `MessagesFragment`: تب پیام‌ها
- `ProfileFragment`: تب پروفایل

## ویژگی‌ها

### ✅ Back Navigation هوشمند
- WebView history support
- Navigation بین تب‌ها
- Exit confirmation

### ✅ Loader & Skeleton
- ProgressBar در بالای صفحه
- Skeleton placeholder در زمان بارگذاری

### ✅ Offline Handling
- تشخیص قطعی اینترنت
- صفحه Offline با دکمه Retry

### ✅ امنیت WebView
- JavaScript enabled
- DOM Storage enabled
- Cache enabled
- File access disabled
- Mixed content blocked

### ✅ DeepLink
- لینک‌های `vinor.ir` داخل اپ باز می‌شود
- لینک‌های خارجی با مرورگر سیستم

## تست

برای تست Connection:
- لاگ‌های `MainActivity` و هر Fragment را بررسی کنید
- URL فعلی هر تب در لاگ نمایش داده می‌شود

## Build

```bash
./gradlew assembleDebug
```

یا در Android Studio:
- Build > Make Project

## نکات مهم

1. **لاگین**: صفحات ثبت آگهی، پیام‌ها و پروفایل نیاز به لاگین دارند. اگر کاربر لاگین نباشد، سایت خودش به `/express/partner/login` ریدایرکت می‌کند.

2. **Cache**: WebView از cache استفاده می‌کند برای بهبود عملکرد.

3. **User Agent**: User Agent به `VinorApp/Android` تغییر یافته است.

4. **RTL Support**: اپ از RTL پشتیبانی می‌کند.

