# 🤖 Lingz Finance Bot

Asisten pelacak keuangan pribadi yang dibangun langsung di dalam Telegram. Cepat, simpel, pintar dengan AI, dan selalu bisa diakses kapan saja.

## 🚀 Fitur Utama

- **Fast Local Parser**: Deteksi instan untuk pencatatan transaksi sehari-hari (contoh: "makan 25k", "gaji 5 juta") tanpa jeda.
- **Smart AI Assistant**: Terintegrasi dengan Google Gemini AI untuk menganalisis pertanyaan keuangan yang lebih kompleks dengan gaya bahasa yang santai dan humoris.
- **Kategorisasi Cerdas**: Kategori otomatis (Makanan, Transport, Belanja, dll.) untuk memahami kebiasaan pengeluaran Anda.
- **Cek Saldo & Laporan**: Cek saldo Anda seketika atau dapatkan ringkasan bulanan.
- **Riwayat Transaksi**: Lihat dan navigasi riwayat transaksi Anda dengan mudah.
- **Export PDF Profesional**: Hasilkan laporan PDF keuangan yang elegan layaknya mutasi bank dengan kolom saldo berjalan langsung di dalam chat.
- **Penghapusan Instan**: Hapus transaksi yang salah ketik hanya dengan 1 klik.
- **Privasi Data**: Arsitektur multi-user memastikan data keuangan Anda terisolasi dengan aman.

## 🛠️ Stack Teknologi

- **Core:** Java 17, Spring Boot 3
- **Data Persistence:** Spring Data JPA, Hibernate
- **Database:** PostgreSQL (Production) / H2 (Local Development)
- **AI Integration:** Google Gemini API (`gemini-3.6-flash`)
- **Document Generation:** iTextPDF
- **Integration:** Telegram Bot Java Library

## ⚙️ Setup Development Lokal

1. Kebutuhan Sistem: Java 17 terinstall.
2. Dapatkan Bot Token dan Username dari **@BotFather** di Telegram.
3. Dapatkan API Key Gemini dari **Google AI Studio**.
4. Clone repositori dan copy file environment contoh:
   ```bash
   cp .env.example .env
   ```
5. Isi file `.env` dengan kredensial bot dan API Anda:
   ```env
   TELEGRAM_BOT_TOKEN=token_anda
   TELEGRAM_BOT_USERNAME=username_bot_anda
   GEMINI_API_KEY=api_key_gemini_anda
   SPRING_PROFILES_ACTIVE=local
   ```
6. Jalankan aplikasi:
   ```bash
   ./mvnw spring-boot:run
   ```
   *(Profil lokal secara otomatis menggunakan database H2 tertanam yang terletak di folder `./data/`).*

## 🚀 Deployment Produksi

Aplikasi ini telah dioptimalkan untuk penyedia Platform-as-a-Service (PaaS) seperti Back4App Containers, Koyeb, atau Railway.

**Langkah Deployment (Contoh Back4App):**
1. Hubungkan repositori ini ke layanan Back4App Container Anda.
2. Atur Environment Variables berikut di dashboard penyedia:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `TELEGRAM_BOT_TOKEN=token_anda`
   - `TELEGRAM_BOT_USERNAME=username_bot_anda`
   - `GEMINI_API_KEY=api_key_gemini_anda`
   - `DATABASE_USERNAME=user_db_anda`
   - `DATABASE_PASSWORD=password_db_anda`
3. Tambahkan layanan ping (seperti UptimeRobot) ke `/api/health` jika menggunakan PaaS versi gratis agar bot tidak tertidur (sleep).
4. Platform akan menangani build dan deployment secara otomatis.

---
*Dibangun untuk kecepatan, kepraktisan, dan teman yang asik dalam mengelola keuangan Anda.*
