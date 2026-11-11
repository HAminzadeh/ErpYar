# ErpYar — راه‌اندازی یکپارچه از IntelliJ (فاز ۰۲.۱)

> این سند جایگزین دستورالعمل قدیمی «ابتدا Run کردن همه سرویس‌های Docker» است.
> راهنمای اصلی: [Phase 02.1 — Single Entry](phase-02-1-single-entry.md).

از شاخه اصلی پروژه تنها یک کانفیگ مشترک داریم: **`ErpYar - Run`**.
کلاس اصلی آن `ir.erp.launcher.ErpYarLauncher` در ماژول `erp-launcher` است.

در IntelliJ: Maven Reload، انتخاب JDK 21، انتخاب `ErpYar - Run` و سپس Run.
این مسیر پیش‌نیازها و PostgreSQL را در Docker آماده می‌کند؛ هیچ سرویس Java
داخل Docker اجرا نمی‌شود. تا زمان فاز ۰۴ اجرای سرویس‌های Java روی میزبان
پیاده نشده و نتیجه درستِ `--start` پس از موفقیت زیرساخت
`DEFERRED_NOT_READY` / exit code `3` است، نه ERP READY.

برای Doctor، کش آفلاین، Export، Import یا Infrastructure Only از **همان کلاس**
با پارامترهای CLI استفاده کنید؛ برای هر عملیات Run Configuration جداگانه نسازید.
مثال‌ها و تفاوت فایل‌های اشتراکی `.run` با کانفیگ‌های محلی IntelliJ در راهنمای اصلی هستند.

اگر `SQLSTATE 28P01` مشاهده شد، Volume را حذف نکنید؛ رمز ذخیره‌شده واقعی
PostgreSQL ممکن است با `.env` قدیمی متفاوت باشد. مستند تشخیص:
[PostgreSQL Phase 02](phase-02-offline-infrastructure.md).
