# فاز ۰۲.۱ — فقط یک نقطه ورود دائمی (Unified Entry Point)

## هدف و محدوده

**یک گزینه رسمی اجرای پروژه در IntelliJ:** `ErpYar - Run`.

- Main class: `ir.erp.launcher.ErpYarLauncher`
- Module: `erp-launcher`
- Working directory: `$PROJECT_DIR$`
- Program arguments: `--start`
- Java 21، بدون نیاز به Plugin خاص برای Run

از این فاز به بعد تمام عملیات به همان Launcher سپرده می‌شود؛ Doctor، کش Image و آماده‌سازی دیتابیس
بخشی از فرایند شروع هستند. در عملیات عادی نیاز نیست دکمه جداگانه‌ای اجرا شود.

## مرز فنی بسیار مهم

این تغییر فقط **یکپارچه‌سازی نقطه ورود** است، نه تکمیل خودکار فازهای بعد.
فاز ۰۲ فقط زیرساخت PostgreSQL را از Docker بالا می‌آورد؛ هیچ Spring Boot ERP Service در
Docker اجرا نمی‌شود. ماژول‌های مشتری در فاز ۰۳ انتخاب و در فاز ۰۴ به‌صورت JVMهای مستقل
روی میزبان راه‌اندازی خواهند شد. تا آن زمان نتیجه صحیح `--start` بعد از آماده‌سازی زیرساخت:
`DEFERRED_NOT_READY`، کد خروج `3` است؛ `SUCCESS` کامل ERP **نیست**.

## مراحل معمول اجرا

1. IntelliJ: Reload All Maven Projects و JDK 21.
2. Run drop-down: **`ErpYar - Run`** را انتخاب کنید و Play بزنید.
3. سیستم Java، Docker/Compose، `.env`، کش Image و PostgreSQL را بررسی می‌کند.
4. در حضور Image در Docker یا کش معتبر، مجدداً آن را دانلود نمی‌کند؛ فقط در صورت نیاز
   و اتصال آنلاین، Image رسمی زیرساخت را Pull و آرشیو می‌کند.
5. در صورت مشکل (`SQLSTATE 28P01` یا نبود Image در حالت آفلاین)، پیام خطا می‌دهد و
   هیچ Volume، رمز یا رکوردی را بازنشانی/حذف نمی‌کند.

## چرا قبلاً گزینه‌های زیادی دیده می‌شد؟

۸ فایل قدیمی در `.run/` کنار هم ثبت شده بودند (Doctor، Export، Offline، Prepare،
Start Infrastructure، Audit Debug، Start All و legacy preview). این اصلاحیه **همان ۸ فایل
را از Git حذف** می‌کند و فقط یک فایل `.run/ErpYar - Run.run.xml` در Repo اضافه می‌کند.
نصب‌کننده تنها فایل‌های مربوط به Repo و تأییدشده را حذف می‌کند؛ اگر یکی از آن‌ها تغییر محلی
داشته باشد به‌جای حذف بی‌اجازه متوقف می‌شود.

**نکته IntelliJ:** بخش `All Configurations` همچنین از `.idea/workspace.xml` و تنظیمات
شخصی/موقت IDE تغذیه می‌شود؛ گاهی `AccountingApplication`, `AuditApplication` یا گزینه‌های
دیگر باقی می‌مانند. این فایل‌ها خارج از کنترل سورس اشتراکی هستند. برای خلوت کردنِ
منو، `Run > Edit Configurations...` را باز کنید و فقط تنظیمات قدیمی‌ای را که خودتان
دیگر نیاز ندارید حذف کنید. حذف خودکار `.idea` یا فایل‌های تنظیمات شخصی ممنوع است.
ممکن است پس از Reload یک‌بار انتخاب `ErpYar - Run` لازم باشد؛ سپس با Play همان اجرا می‌شود.

## ابزارهای تخصصی همچنان موجودند؛ بدون دکمه دوم

در ترمینال ریشه پروژه (بعد از کامپایل `erp-launcher`):

```powershell
$main = 'ir.erp.launcher.ErpYarLauncher'
$cp = 'tools/erp-launcher/target/classes'
java -cp $cp $main --doctor --json
java -cp $cp $main --infra-only --offline
java -cp $cp $main --cache-export
java -cp $cp $main --cache-import --offline
```

این فرمان‌ها برای عیب‌یابی/انتقال محیط هستند و مسیر اصلی روزمره محسوب نمی‌شوند.
`--doctor` فقط‌خواندنی است. داده‌های واقعی PostgreSQL و رمزهای فعلی تغییر نمی‌کنند.

## تست و معیار پذیرش

```powershell
mvn -B -ntp verify
py -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

تست قراردادی بررسی می‌کند فقط یک `.run/*.run.xml` وجود داشته باشد، Main class و Working
Directory صحیح باشند و منطق کش/Compose همچنان از `ErpYarLauncher` در دسترس باشد.
بدون Docker واقعی نمی‌توان مدعی تست راه‌اندازی PostgreSQL روی سیستم کاربر شد.

## نگهداری آینده

تمام فازهای بعد باید **همین کلاس و همین نام Run Configuration** را حفظ کنند و تنها
قابلیت داخلی `StartupOrchestrator` را کامل کنند. ایجاد Run Configuration اشتراکی
جدید برای تک‌تک ماژول‌ها یا مراحل تشخیصی، یک بازگشت ناخواسته در معماری محسوب می‌شود.
