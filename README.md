# 🤖 FinTrack Bot

Personal finance tracking assistant built directly into Telegram. Fast, simple, and always accessible.

## 🚀 Features

- **Quick Entry**: Intuitive menu to record income and expenses on the go.
- **Categorization**: Built-in categories (Food, Transport, Salary, etc.) to understand your spending habits.
- **Balance & Reports**: Instantly check your balance or get monthly summaries (`/saldo`, `/laporan`).
- **Transaction History**: View and paginate through your past transactions.
- **PDF Export**: Generate professional PDF reports of your transaction history directly in chat.
- **Data Privacy**: Multi-user architecture ensures your financial data is strictly isolated.
- **Correction**: Easily delete mistaken entries.

## 🛠️ Technology Stack

- **Core:** Java 17, Spring Boot 3
- **Data Persistence:** Spring Data JPA, Hibernate
- **Database:** PostgreSQL (Production) / H2 (Local Development)
- **Document Generation:** iTextPDF
- **Containerization:** Docker
- **Integration:** Telegram Bot Java Library

## ⚙️ Local Development Setup

1. Requirements: Java 17 installed on your machine.
2. Obtain a Bot Token and Username from **@BotFather** on Telegram.
3. Clone the repository and copy the example environment file:
   ```bash
   cp .env.example .env
   ```
4. Fill in the `.env` file with your Bot credentials and set the profile to local:
   ```env
   TELEGRAM_BOT_TOKEN=your_token_here
   TELEGRAM_BOT_USERNAME=your_bot_username
   SPRING_PROFILES_ACTIVE=local
   ```
5. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```
   *(The local profile automatically uses an embedded H2 database located in the `./data/` folder).*

## 🚀 Production Deployment

This application is containerized and optimized for Platform-as-a-Service (PaaS) providers that support Docker deployments (e.g., Back4App Containers, Koyeb, Railway).

**Deployment Steps (Back4App Example):**
1. Connect this repository to your Back4App Container service.
2. Set the following Environment Variables in the provider's dashboard:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `TELEGRAM_BOT_TOKEN=your_token`
   - `TELEGRAM_BOT_USERNAME=your_bot_name`
   - `DATABASE_USERNAME=your_db_user`
   - `DATABASE_PASSWORD=your_db_password`
3. The platform will read the provided `Dockerfile` and handle the build and deployment automatically.

---
*Built with simplicity and speed in mind.*
