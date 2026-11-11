# ErpYar Unified Launcher — Phase 02.1

**تنها نقطه ورود رسمی IntelliJ:** `ErpYar - Run` → `ir.erp.launcher.ErpYarLauncher`.
در `.run/` فقط همین یک Run Configuration اشتراکی نگهداری می‌شود.

## روش اجرای روزمره

1. در IntelliJ، SDK و Maven Runner JRE را روی **Java 21** قرار دهید و Maven Reload انجام دهید.
2. از نوار Run فقط **`ErpYar - Run`** را انتخاب کنید؛ Working directory برابر `$PROJECT_DIR$` است.
3. Launcher خودش پیش‌نیازها را تشخیص می‌دهد؛ `.env` را بدون تغییر رمزهای فعلی تکمیل می‌کند؛
   Image رسمی زیرساخت PostgreSQL را در Docker/کش محلی بررسی می‌کند، از کش بازیابی می‌کند
   یا در صورت نیاز آنلاین دریافت و در `.local/docker-images/` ذخیره می‌کند؛ سپس PostgreSQL
   و دیتابیس‌های منطقی غایب را کنترل می‌کند.
4. تا تکمیل فاز ۰۴، اجرای JVM سرویس‌های ERP **عمداً انجام نمی‌شود** و خروجی صحیح
   `DEFERRED_NOT_READY` / کد خروج ۳ است. این به معنی خرابی زیرساخت نیست؛ به معنی
   آماده نبودن اجرای نهایی ERP در دامنه فعلی توسعه است.

برای جلوگیری از سردرگمی، کانفیگ‌های اشتراکی قدیمی Doctor، Prepare، Start Infrastructure،
Export، Offline و Audit Debug حذف شده‌اند؛ **قابلیت‌های آن‌ها از کد حذف نشده‌اند**.
فایل‌های شخصی `.idea/workspace.xml` یا Run Configurationهای محلی ممکن است هنوز آن
نام‌ها را نمایش دهند. از `Run > Edit Configurations` فقط موارد شخصی اضافی را با بررسی دستی حذف کنید.
نصب‌کننده سورس، فایل‌های شخصی IDE را دستکاری نمی‌کند.

## عیب‌یابی و کارهای تخصصی (همان Main Class، بدون Run Configuration دوم)

بعد از ساخت ماژول `erp-launcher`، از ریشه پروژه:

```powershell
Set-Location 'D:\ChatGPT_Projects\ErpYar'
$main = 'ir.erp.launcher.ErpYarLauncher'
$cp = 'tools/erp-launcher/target/classes'
java -cp $cp $main --doctor --json         # بررسی فقط‌خواندنی
java -cp $cp $main --plan                  # برنامه مراحل آینده
java -cp $cp $main --env-only              # فقط .env
java -cp $cp $main --infra-only            # فقط PostgreSQL
java -cp $cp $main --cache-export          # صدور TAR PostgreSQL موجود
java -cp $cp $main --cache-import --offline # بازیابی آفلاین از TAR
java -cp $cp $main --start --offline       # Bootstrap آفلاین
```

`--doctor` و `--plan` هیچ فایل یا Container را تغییر نمی‌دهند. `--offline` هیچ Pull اینترنتی
نمی‌زند و در نبود Image/Cache با خطای شفاف متوقف می‌شود. TAR واقعی فقط روی سیستم دارای
Docker ساخته می‌شود. هیچ Image مربوط به سرویس Java ذخیره یا اجرا نمی‌شود.

## تست و ایمنی

```powershell
mvn -B -ntp verify
py -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

بدون `docker compose down -v`، بازنشانی رمز PostgreSQL، Git reset، Commit یا Push خودکار.
مدیریت ماژول مشتری (Phase 03)، اجرای سرویس‌های منتخب JVM (Phase 04) و سلامت کامل سیستم
(Phase 05) هنوز در محدوده مراحل بعدی هستند.

راهنمای تکمیلی: `docs/operations/phase-02-1-single-entry.md` و
`docs/operations/phase-02-offline-infrastructure.md`.
