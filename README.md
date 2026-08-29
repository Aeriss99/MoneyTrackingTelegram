# Money Tracking Telegram Bot

Sebuah aplikasi pencatat keuangan (Money Tracker) sederhana yang sepenuhnya diakses melalui chat Telegram. Dibangun menggunakan Java 17 dan Spring Boot.

## 🚀 Fitur

- **Catat Pengeluaran/Pemasukan Cepat**: Cukup ketik `makan 25000` atau `gaji 5000000`.
- **Cek Saldo (`/saldo`)**: Menampilkan total pemasukan, pengeluaran, dan sisa saldo.
- **Riwayat (`/riwayat`)**: Menampilkan 10 riwayat transaksi terakhir.
- **Laporan Bulanan (`/laporan`)**: Menampilkan ringkasan keuangan untuk bulan ini.
- **Hapus Transaksi (`/hapus [ID]`)**: Menghapus transaksi yang salah ketik.
- **Isolasi Data User**: Data Anda aman dan tidak akan bercampur dengan data user Telegram lainnya.

## 🛠️ Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.2.x
- **Database:** H2 Database (Local Dev) & PostgreSQL (Production)
- **Bot API:** Telegram Bot API (`telegrambots-spring-boot-starter`)
- **Build Tool:** Maven

## 🏗️ Arsitektur Sederhana

```
USER (Telegram) ↔ Telegram Bot API ↔ Spring Boot (Business Logic) ↔ Database (H2/PostgreSQL)
```

## ⚙️ Cara Menjalankan di Local (Development)

1. Pastikan Anda sudah menginstall **Java 17**.
2. Buat bot baru di Telegram melalui **BotFather** dan dapatkan **Bot Token** serta **Username Bot**.
3. Clone repository ini.
4. Export environment variables untuk token bot (atau edit file `src/main/resources/application.properties` sementara, tapi *jangan di-commit*):
   
   ```bash
   export TELEGRAM_BOT_TOKEN="token_dari_botfather"
   export TELEGRAM_BOT_USERNAME="username_bot_anda"
   ```
5. Jalankan aplikasi menggunakan Maven:
   
   ```bash
   ./mvnw spring-boot:run
   ```
6. Aplikasi akan berjalan dan menggunakan database H2 (file-based) yang akan tersimpan di dalam folder `./data/money_tracking`. Buka Telegram Anda dan mulai chat dengan Bot tersebut!

## 🚀 Panduan Deployment (Railway - Production)

Untuk environment Production, sangat disarankan menggunakan **PostgreSQL** yang disediakan oleh Railway.

1. Buat akun di [Railway.app](https://railway.app/).
2. Buat project baru dan tambahkan **PostgreSQL** database.
3. Hubungkan repository GitHub ini ke Railway Project Anda.
4. Tambahkan Environment Variables di Railway:
   - `TELEGRAM_BOT_TOKEN` : (Token dari BotFather)
   - `TELEGRAM_BOT_USERNAME` : (Username bot tanpa @)
   - `SPRING_DATASOURCE_URL` : (Gunakan Database URL yang diberikan Railway PostgreSQL, pastikan prefix-nya adalah `jdbc:postgresql://...`)
   - `SPRING_DATASOURCE_DRIVER_CLASS_NAME` : `org.postgresql.Driver`
5. Deploy ulang (Re-deploy). Bot Anda kini live di server Railway!

---
*Project ini dibuat sebagai project pembelajaran Java Backend menggunakan Spring Boot dan Telegram API.*
