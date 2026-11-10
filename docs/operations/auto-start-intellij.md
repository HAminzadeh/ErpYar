# راه‌اندازی خودکار ErpYar از IntelliJ IDEA

## هدف و نقطه ورود

در IntelliJ پس از **Reload All Maven Projects**، از منوی Run کانفیگ **`ErpYar - Start All Services`** را انتخاب و Run کنید. کلاس مرکزی:

`tools/erp-launcher/src/main/java/ir/erp/launcher/ErpYarLauncher.java`

این راه‌انداز از Java 21 استاندارد استفاده می‌کند و به Spring Boot یا وابستگی خارجی برای اجرای اولیه وابسته نیست. باز شدن صرف پروژه در IntelliJ عمداً باعث اجرای خودکار برنامه یا تغییر سیستم نمی‌شود: آغاز عملیات فقط با فشردن Run است. مدیریت سرویس‌ها به Docker Compose واگذار شده و سرویس‌ها به‌صورت مستقل در کانتینر راه‌اندازی می‌شوند؛ **AuditApplication را برای شروع کل سیستم Run نکنید**.

## رفتار راه‌انداز به ترتیب

1. پیدا کردن مسیر اصلی پروژه با `pom.xml` و `compose.yaml` (امکان `--repo` نیز وجود دارد).
2. اطمینان از اجرای روی JDK 21 و وجود Docker CLI، Compose v2 و Docker Engine؛ اگر Docker Desktop روی ویندوز نصب اما خاموش باشد، تلاش برای اجرای آن انجام می‌شود. ابزارهای Git، Maven و Python برای شروع سرویس‌های Docker اجباری نیستند. راه‌انداز نرم‌افزار جدیدی را از اینترنت دانلود/اجرا نمی‌کند و نصب سراسری JDK یا Docker را بدون مجوز انجام نمی‌دهد.
3. ایجاد امن `.env` در ریشه پروژه اگر وجود ندارد؛ تولید `JWT_SECRET`، `DB_PASSWORD` و `BOOTSTRAP_ADMIN_PASSWORD` تصادفی. اگر `.env` موجود باشد فقط کلیدهای کاملاً غایب افزوده می‌شوند؛ مقادیر موجود، از جمله رمزها، هرگز جایگزین نمی‌شوند.
4. اعتبارسنجی Compose (`docker compose config --quiet`) و راه‌اندازی PostgreSQL. پورت مخصوص دسترسی محلی PostgreSQL، به‌صورت پیش‌فرض فقط روی `127.0.0.1:15432` منتشر می‌شود. دسترسی DB از سرویس‌های Docker همچنان `postgres:5432` است.
5. بررسی تطابق رمز `.env` با محیط کانتینر و سپس **آزمون احراز هویت واقعی PostgreSQL**. رمز در لاگ نمایش داده نمی‌شود. رمز کاربران موجود به‌طور خودکار تغییر نمی‌کند.
6. جست‌وجو و ایجاد **فقط دیتابیس‌های منطقی غایب**: `identity_db`, `organization_db`, `master_data_db`, `workflow_db`, `notification_db`, `audit_db`؛ دیتابیس‌های موجود دست‌نخورده‌اند. Migrationهای Flyway توسط خود سرویس‌ها اجرا می‌شوند.
7. اجرای `docker compose up -d --build` برای هفت سرویس به‌همراه PostgreSQL؛ کنترل وضعیت هشت کانتینر و پاسخ سلامت Gateway؛ همچنین درخواست بی‌توکن به شش مسیر Backend باید کد 401 (به معنی فعال بودن مسیر و امنیت سرویس) برگرداند. 401 در این تست موفق است؛ خطاهای 404/502/503 موفق تلقی نمی‌شوند.
8. نمایش آدرس Gateway و خاتمه موفق. بدون `--smoke` داده آزمایشی ایجاد نمی‌شود.

## پروفایل اجرای مستقیم در IntelliJ (برای Debug)

**اول راه‌انداز مرکزی را اجرا کنید** تا دیتابیس و کانتینرها آماده شوند. اگر قصد Debug سرویس Java محلی را دارید، از پروفایل `ide` استفاده کنید؛ پروفایل پیش‌فرض سرویس‌های Docker همچنان دست‌نخورده است.

کانفیگ اشتراکی آماده: **`ErpYar - Audit IDE Debug`**؛ Main class برابر `ir.erp.audit.AuditApplication`، Working directory برابر `$PROJECT_DIR$` و متغیر `SPRING_PROFILES_ACTIVE=ide` است. سرویس Audit در این حالت به `127.0.0.1:${ERP_DB_HOST_PORT:15432}/audit_db` متصل می‌شود و HTTP خود را روی پورت `18006` اجرا می‌کند؛ لذا با Gateway روی `8080` تداخل ندارد. تمام شش سرویس داده‌ای پروفایل `ide` دارند (به‌ترتیب پورت‌های `18001` تا `18006`). Gateway نیز می‌تواند با پروفایل ide روی `18000` اجرا شود، مشروط به اینکه بقیه سرویس‌ها به‌صورت محلی در حال اجرا باشند.

در فایل‌های `application.yml` سرویس‌های داده‌ای `spring.config.import: optional:file:./.env[.properties]` افزوده شده است. این مسیر نسبت به Working directory حل می‌شود؛ در IntelliJ آن را `$PROJECT_DIR$` قرار دهید. این گزینه داخل Docker نیز امن است، چون فایل `.env` داخل Image کپی نمی‌شود و مقادیر از environment کانتینر دریافت می‌شوند.

**اگر نام ماژول کانفیگ در IntelliJ شناسایی نشد:** از Maven پنجره Reload بزنید یا روی `ErpYarLauncher.main` راست‌کلیک و Run کنید، سپس Working directory را ریشه پروژه قرار دهید. هیچ پلاگین مخصوص IntelliJ لازم نیست.

## رفع خطای SQL State 28P01 / password authentication failed

علت مشاهده‌شده: اجرای مستقیم `AuditApplication` در IntelliJ بدون بارگذاری `.env` و با آدرس/پورت نادرست PostgreSQL؛ یا رمز دیتابیس موجود با `.env` یکسان نیست. راه‌انداز هر دو وضعیت را قبل از Start سرویس‌ها کنترل می‌کند.

- ابتدا با کانفیگ `ErpYar - Start All Services` PostgreSQL و سرویس‌ها را آماده کنید، سپس `ErpYar - Audit IDE Debug` را برای Debug استفاده کنید.
- مطمئن شوید `127.0.0.1:15432` برای دیتابیس پروژه آزاد است؛ پیش‌فرض `5432` ممکن است به PostgreSQL **دیگری** در ویندوز متصل شود.
- اگر **همان** دیتابیس Docker با رمز موجود کار نمی‌کند، احتمالاً Volume قبلاً با رمز دیگری ایجاد شده است. تغییر `POSTGRES_PASSWORD` پس از ایجاد Volume، رمز role موجود را به‌تنهایی عوض نمی‌کند. برنامه برای حفاظت از داده‌ها، خودکار رمز role را Reset نمی‌کند. پس از تهیه بکاپ و تأیید مالک دیتابیس، با روش مدیریتی کنترل‌شده رمز role را با `.env` همگام کنید. رمز را در تیکت یا پیام عمومی نفرستید.
- هرگز برای این خطا از `docker compose down -v` یا حذف Volume استفاده نکنید؛ تمام اطلاعات دیتابیس از بین می‌روند.

## دستورات جایگزین و حالت‌های خطا

```powershell
Set-Location 'D:\ChatGPT_Projects\ErpYar'
# کد Java21 را از IntelliJ اجرا کنید؛ یا بعد از Maven compile:
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --check
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher
# تصاویر موجود، بدون بازسازی:
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --skip-build
# تست Smoke با ایجاد ردیف آزمایشی (فقط با تصمیم آگاهانه):
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --smoke
```

برای محیطی که Docker Desktop نصب نشده یا مجوز اجرای Engine ندارد، راه‌انداز با توضیح پیش‌نیاز موردنیاز توقف می‌کند. در محیط‌هایی که Maven CLI فقط از Nexus سازمانی استفاده می‌کند، ساخت Image توسط Docker از Maven داخل Image انجام می‌شود و به تنظیمات Maven روی میزبان وابسته نیست.

## تضمین‌ها و محدودیت‌ها

- هیچ دستور `down -v`، `reset --hard`، `git clean`، `DROP DATABASE` یا Commit/Push خودکار وجود ندارد.
- اسرار فقط در `.env` محلی/متغیرهای container نگه‌داری می‌شوند؛ `.env` در `.gitignore` است. وجود بدافزار یا دسترسی مدیریتی روی سیستم خارج از محدوده این کنترل است.
- سلامتی Gateway و 401 سرویس‌ها، تضمین کامل عملیاتی ERP یا تست مجوز cross-company نیست. تست‌های Python/Java و Smoke جداگانه اجرا شوند.
- تست Smoke اختیاری است و در دیتابیس جاری رکورد Demo اضافه می‌کند؛ در دیتابیس Production اجرا نشود.
- راه‌انداز فعلاً فقط برای محیط توسعه محلی با Docker Compose و PostgreSQL مشترک طراحی شده است، نه Provisioning سرور Production.
- به‌علت امنیت داده، برنامه رمز موجود پایگاه‌داده را خودکار نمی‌چرخاند یا نرم‌افزار سیستمی را بی‌اجازه نصب نمی‌کند؛ این‌ها نیازمند اقدام آگاهانه اپراتور هستند.
