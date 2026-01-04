# راه حل مشکل Build در CI/CD

## مشکل
Plugin Kotlin در CI/CD resolve نمی‌شود.

## تغییرات انجام شده

### 1. Version Kotlin
- تغییر از `1.9.24` به `1.9.20` (stable و سازگار با Gradle 8.7)

### 2. Repository Order
- `gradlePluginPortal()` به اول لیست منتقل شد برای بهتر CI compatibility

### 3. Network Timeout
- Timeout settings به `gradle.properties` اضافه شد

### 4. Buildscript Block
- `buildscript` block به root `build.gradle` اضافه شد برای اطمینان از دسترسی به repositories

## تست
```bash
./gradlew --no-daemon --stacktrace assembleDebug
```

## اگر هنوز مشکل دارید

1. Version Kotlin را به `1.9.0` تغییر دهید (stable‌تر)
2. یا به `2.0.0` تغییر دهید (برای Gradle 8.7 مناسب‌تر)

