# ErpYar Phase 01 — Java 21 Development Launcher

این ماژول نقطه ورود مرکزی IntelliJ است. فاز ۱ فقط **پیش‌نیازها، تنظیمات و قراردادهای توسعه** را پیاده می‌کند؛ اجرای واقعی زیرساخت در فاز ۲ و انتخاب/اجرای ماژول‌ها در فازهای ۳ و ۴ اضافه می‌شود.

## IntelliJ (Windows)

1. Project SDK و Maven Runner JRE را روی **Java 21** قرار دهید و `Reload All Maven Projects` بزنید.
2. Run Configuration **`ErpYar - Doctor`** را اجرا کنید: هیچ فایل یا Docker container ایجاد/تغییر نمی‌کند.
3. **`ErpYar - Prepare Environment`** را فقط یک بار برای ساخت `.env` در صورت نیاز اجرا کنید. اجرای مجدد آن مقادیر Secret را تغییر نمی‌دهد.
4. **`ErpYar - Start All`** در فاز ۱ فقط بررسی می‌کند و با وضعیت **DEFERRED_NOT_READY** (کد خروج ۳) پایان می‌یابد؛ عمداً هفت سرویس را اجرا نمی‌کند. پس از اجرای فاز ۴ این Run Configuration فعال خواهد شد.
5. کانفیگ قبلی **`ErpYar - Start All Services (legacy preview)`** فقط `--plan` را اجرا می‌کند؛ راه‌اندازی Docker تمام ماژول‌ها منسوخ شده است.

## CLI

در Windows پس از ساخت Maven (از IntelliJ یا `mvn -B -ntp verify`):

```powershell
cd 'D:\ChatGPT_Projects\ErpYar'
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --doctor
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --doctor --json
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --plan
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --env-only
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --start
```

مسیر اصلی یا هر زیرپوشه موجود را می‌توان با `--repo "D:\ChatGPT_Projects\ErpYar\tools\erp-launcher\src"` معرفی کرد. لینک نمادین به مسیر خارج مخزن، مسیر غیرموجود و ریشه غیرمعتبر رد می‌شود. `--json` بدون مقادیر رمز، خروجی ساختاریافته با نام مرحله، مدت، وضعیت، اقدام پیشنهادی و Exit Code می‌دهد.

| وضعیت | کد خروج | معنی |
|---|---:|---|
| Doctor/Plan/Env بدون خطای مسدودکننده | 0 | فقط بررسی یا ساخت تنظیمات تکمیل شد؛ **نه** اجرای ERP |
| پیش‌نیاز یا تنظیمات نامعتبر | 2 | مشکل باید اصلاح شود |
| Start در فاز ۱ | 3 | به دلیل ناتمام بودن فازهای ۲ تا ۴، برنامه هنوز READY نیست |

`--doctor`، `--check` و `--plan` همه **read-only** هستند؛ Docker Desktop را نیز اجرا نمی‌کنند. `--env-only` فقط `.env` را می‌سازد یا تکمیل می‌کند. `--start` می‌تواند Docker Desktop نصب‌شده را در Windows اجرا کند اما هیچ ERP Java container را اجرا نمی‌کند. `--smoke` تا تکمیل فاز ۵ غیرفعال است تا داده آزمایشی ناخواسته ساخته نشود.

## خطای ثبت‌شده: `ProjectRoot.find` و PostgreSQL 28P01

- تست قدیمی `findsProjectRootFromNestedModule` اکنون باید مسیر والد معتبر را برگرداند؛ هیچ الزامی نیست حتماً `--repo` به ریشه اشاره کند.
- خطای `password authentication failed for user erp` با حذف Volume یا تغییر مخفیانه رمز رفع نمی‌شود. دیتابیس فعلی با آدرس `127.0.0.1:${ERP_DB_HOST_PORT:-15432}` در پروفایل `ide` تعریف شده؛ بررسی اتصال واقعی و بازیابی کنترل‌شده به فاز ۲ تعلق دارد.
- `.env` به Git اضافه نشود. فایل‌های Application IDE قبلی حفظ شده‌اند و پورت‌های ۱۸۰۰۰ تا ۱۸۰۰۶ برای سرویس‌های محلی از قبل رزرو شده‌اند.

## تست و محدودیت‌ها

```powershell
mvn -B -ntp verify
py -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

Doctor تفاوت کمبود Maven CLI با تنظیم IntelliJ bundled Maven را گزارش می‌کند؛ وجود Parent POM در Cache را با تأیید کامل dependencyها اشتباه نمی‌گیرد. اگر `~/.m2/settings.xml` دارای Mirror داخلی باشد و Maven شکست بخورد، وضعیت Nexus را جدا از خطاهای Compile بررسی کنید. پشتیبانی از دانلود آفلاین Imageها، provisioning DB و آغاز واقعی ماژول‌های انتخابی در فازهای بعد خواهد بود.

## ایمنی

هیچ دستور `docker compose up -d --build` برای سرویس‌های Java، `down -v`، `DROP DATABASE`، `git reset`, `git clean`, تغییر خودکار رمز، نصب سراسری بدون تأیید یا Commit/Push خودکار در مسیر این Launcher وجود ندارد. این ماژول Production Provisioner نیست.
