# Phase 01: Doctor, Environment, Plan — راهنمای عملیات و عیب‌یابی

## مرز فاز

این فاز تنها ۱۰ فعالیت تثبیت لانچر و بررسی پیش‌نیاز را انجام می‌دهد. فاز ۱ **هیچ سرویس ERP در Docker** اجرا نمی‌کند و برای اجرای تمام برنامه روی میزبان هنوز آماده نیست. `Start All` تا فاز ۴ با `DEFERRED_NOT_READY` و خروج ۳ خاتمه می‌یابد.

## مراحل پیشنهادی در IntelliJ

1. از مسیر `D:\ChatGPT_Projects\ErpYar` پروژه را باز و Maven Project را Reload کنید.
2. JDK = 21 و Maven Runner JRE = Project JDK 21 را انتخاب کنید. برای استفاده از Maven نصب‌شده توسط IntelliJ نیازی به تغییر Java سراسری Windows نیست.
3. گزینه `ErpYar - Doctor` را اجرا کنید. خروجی `PREFLIGHT` شامل Java, Maven, Docker CLI/Compose, Linux Engine, WSL2, RAM, CPU, disk, .env, پورت‌ها و دسترسی پوشه است. Doctor نه `.env` می‌سازد، نه Docker را اجرا می‌کند.
4. در صورت نیاز گزینه `ErpYar - Prepare Environment` را اجرا کنید. اگر `.env` موجود است، هیچ مقدار موجودی بازنویسی نمی‌شود؛ کلیدهای واقعاً مفقود تکمیل می‌شوند.
5. `ErpYar - Start All` فقط پیش‌نمایش قراردادهای فاز ۲/۳/۴ را نشان خواهد داد و باید خروجی کد ۳ داشته باشد. این وضعیت طبیعی Phase 01 است.

## نمونه خروجی

```text
ErpYar Phase 01 | DOCTOR | DIAGNOSTIC_WITH_ERRORS
[PASS] PREFLIGHT java Java 21 detected, expected Java 21
[WARNING] PREFLIGHT maven ...
[FAIL] PREFLIGHT docker-engine Docker Engine not responding
...
```

این خروجی نمونه است؛ مقادیر واقعی به سیستم وابسته‌اند.

## تحلیل خطاهای پرتکرار

| پیام | توضیح | اقدام ایمن |
|---|---|---|
| `Repository path is missing` | مسیر `--repo` وجود ندارد | مسیری که واقعاً دارای `pom.xml` و `compose.yaml` است وارد کنید |
| `Cannot locate ErpYar source root` | ریشه معتبر پیدا نشده | Working directory را `$PROJECT_DIR$` تنظیم کنید |
| Java 21 expected | IntelliJ از JVM دیگر اجرا می‌کند | Project SDK و Run JRE را به 21 تغییر دهید |
| Maven not detected | `mvn` در PATH نیست | Bundled Maven در IntelliJ یا Maven رسمی 3.9+ انتخاب کنید |
| Maven mirror settings | احتمال خطای Nexus/Artifactory سازمانی | اعتبار اتصال Mirror را بررسی کنید؛ خطا را بی‌دلیل به Source نسبت ندهید |
| Docker CLI missing | Docker Desktop یا CLI نصب نیست | نسخه رسمی متناسب با Windows/WSL را از Docker نصب کنید؛ نیازمند تأیید شماست |
| Docker Engine not responding | Docker نصب ولی Engine خاموش است | Docker Desktop و Linux Containers/WSL2 را بررسی کنید |
| WSL status unknown | WSL2 قابل تشخیص نیست | پشتیبانی WSL2/virtualization و ویژگی‌های Windows را بررسی کنید |
| localhost port listening | پورتی در اختیار فرایندی است | ابتدا PID/مالک فرایند را بشناسید؛ فرایند را کورکورانه نکشید |
| `.env invalid` | کلید خالی/تکراری یا رمز نامعتبر | دستی فایل را بازبینی کنید؛ رمز موجود را عوض نکنید |
| `SQLSTATE 28P01` (فاز ۲) | مغایرت رمز دیتابیس واقعی و `.env` | احراز هویت را بررسی و با روش کنترل‌شده تطبیق دهید؛ Volume را حذف نکنید |

## آزمون خودکار

```powershell
# در ریشه سورس
mvn -B -ntp verify
py -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

آزمون Java در `tools/erp-launcher/src/test/java/ir/erp/launcher` شامل سناریوهای root nested، space path، مسیر نامعتبر، symlink، contract نسخه‌ها، idempotency `.env` و ممنوعیت اجرای Compose قدیمی است. Python `test_phase01_launcher.py` راه‌انداز واقعی را با `javac/java` روی Repo آزمایشی کامپایل و اجرا می‌کند، بدون اتصال Docker. خروج ۲ Doctor بدون Docker به معنی موفقیت تست *تشخیص خطا* است؛ به معنی آماده‌بودن محیط نیست.

## بازیابی

هیچ‌وقت به‌خاطر این فاز از `docker compose down -v`، حذف Volume، Reset Git، `DROP DATABASE` یا تغییر خودکار رمز استفاده نکنید. نصب‌کننده ZIP برای فایل‌های مقصد موجود بکاپ می‌گیرد و از بازنویسی مقصد متعارض جلوگیری می‌کند؛ Git Commit/Push فقط توسط کاربر انجام می‌شود.
