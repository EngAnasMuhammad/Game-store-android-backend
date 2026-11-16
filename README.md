# GameTrade Backend API

A production-ready RESTful e-commerce backend for **GameTrade**, a full-stack digital game key storefront built with **Kotlin**, **Ktor**, **JetBrains Exposed DSL**, **PostgreSQL (Neon)**, and **Supabase Storage**, containerized with **Docker** and deployed on **Render**.

Designed with strict ACID compliance, pessimistic row-level locking (`SELECT ... FOR UPDATE`) to prevent double-selling under concurrency, stateless JWT role-based authentication (email/password + Google Sign-In), and a server-side image optimization pipeline.

> **Demo Project Notice:** GameTrade is a portfolio showcase. All game activation keys and checkouts are simulated—no real payments or financial transactions take place.
>
> **Companion Client:** [GameTrade Android (Kotlin + Jetpack Compose)](https://github.com/EngAnasMuhammad/GameStoreAndroid)

---

## Tech Stack & Cloud Architecture

- **Language:** Kotlin
- **Server Framework:** Ktor (`ContentNegotiation`, `kotlinx.serialization`, `StatusPages`, `Authentication`)
- **Database & ORM:** Serverless PostgreSQL (**Neon**) + JetBrains Exposed DSL (`UUIDTable`)
- **Connection Pooling:** HikariCP with Coroutine-backed non-blocking transactions (`Dispatchers.IO`)
- **Object Storage & Media:** **Supabase Storage** (`game-covers` bucket) + server-side image resizing/JPEG compression (`ImageIO` / `Thumbnailator`)
- **Security & Auth:**
  - Google ID Token verification (for Android Credential Manager)
  - BCrypt salted password hashing (128-bit salt, cost factor 12)
  - Stateless HMAC-SHA256 JWT Authentication with `CUSTOMER` and `ADMIN` role guards
- **Deployment & Uptime:** Multi-stage **Docker** container hosted on **Render**, paired with a lightweight `/health` endpoint for external keep-alive monitoring

### Project Structure
```text
├── Dockerfile                       # Multi-stage JVM build & slim runtime image
└── src/main/kotlin/com/anasdev/
    ├── data/
    │   ├── local/
    │   │   ├── DatabaseFactory.kt   # HikariCP pool & suspended transaction wrapper
    │   │   └── Tables.kt            # Normalized PostgreSQL schema definitions
    │   └── repository/
    │       ├── GameRepository.kt    # Catalog queries, dynamic stock count & batch key insertion
    │       ├── OrderRepository.kt   # Row-level cart locking, lock release, atomic checkout & history
    │       └── UserRepository.kt    # User registration, Google auth lookup & credentials
    ├── domain/
    │   └── model/                   # @Serializable Request/Response DTOs
    │       ├── AdminDto.kt
    │       ├── AuthDto.kt
    │       ├── GameResponse.kt
    │       └── OrderDto.kt
    ├── representation/
    │   └── routes/
    │       ├── AdminRoutes.kt       # Role-guarded (ADMIN) catalog, key upload & cover image pipeline
    │       ├── AuthRoutes.kt        # Email/Password & Google Sign-In JWT issuance
    │       └── OrderRoutes.kt       # Protected cart lock/unlock, checkout & order history
    ├── Application.kt / main.kt     # Application entry point & module wiring
    ├── Routing.kt                   # Central route registration & /health endpoint
    ├── Security.kt                  # JWT verifier & authentication configuration
    ├── Serialization.kt             # kotlinx.serialization JSON setup
    └── StatusPages.kt               # Global exception handling
```

---

## Core Engineering Highlights

1. **Dynamic Real-Time Inventory (Zero Desync):**
   Instead of maintaining a fragile `stock_count` integer column that can drift out of sync during crashes, available stock is calculated dynamically in SQL via a `LEFT JOIN` counting keys where `is_sold = false` and `(locked_until IS NULL OR locked_until < NOW())`.
2. **Concurrency Protection & Explicit Lock Release:**
   - Adding a game to the cart (`POST /cart/lock`) acquires a PostgreSQL row-level lock (`SELECT ... FOR UPDATE`) on a single available key and stamps a 15-minute reservation (`locked_until`), preventing two concurrent users from reserving the same key.
   - Removing an item (`DELETE /cart/{id}`) verifies key ownership (`locked_by_user_id == userId`) and ensures `is_sold = false` before clearing the reservation, immediately returning the key to live stock.
3. **Atomic Multi-Table Checkout:**
   `POST /cart/checkout` runs inside a single ACID transaction (`dbQuery`): it validates active cart reservations, computes totals server-side, creates the `orders` receipt header, inserts `order_items` with frozen historical pricing (`price_at_purchase`), marks keys as permanently sold (`is_sold = true`), and returns the revealed activation keys.
4. **Server-Side Cover Optimization & Cloud Storage:**
   Uploaded game cover artwork is decoded, resized, and compressed to JPEG on the server before being pushed to Supabase Storage, keeping mobile payload sizes small and decoupling media storage from the stateless Ktor container.
5. **Scale-to-Zero Friendly Health Check:**
   `GET /health` responds directly from Ktor without executing a database query, allowing external cron pings to keep the Render container warm while Neon PostgreSQL sleeps automatically between real user traffic.

---

## Database Schema (PostgreSQL)

- **`users`**: `id (UUID PK)`, `email (UNIQUE)`, `password_hash`, `role (CUSTOMER | ADMIN)`, `created_at`
- **`games`**: `id (UUID PK)`, `title`, `description`, `image_path`, `release_date`, `genre`, `current_price`, `is_active`
- **`game_keys`**: `id (UUID PK)`, `game_id (FK -> games)`, `key_code (UNIQUE)`, `is_sold`, `locked_until`, `locked_by_user_id (FK -> users)`, `created_at`
- **`orders`**: `id (UUID PK)`, `user_id (FK -> users)`, `total_amount`, `created_at`
- **`order_items`**: `id (UUID PK)`, `order_id (FK -> orders)`, `key_id (FK -> game_keys)`, `price_at_purchase`

---

## API Endpoints

### Health, Public Storefront & Auth
| Method | Endpoint | Access | Description |
|:-------|:---------|:-------|:------------|
| `GET` | `/health` | Public | Lightweight liveness check (`200 OK`, no DB hit) |
| `GET` | `/games` | Public | List all active games with real-time `availableStock` |
| `POST` | `/auth/register` | Public | Register a new customer account & return a signed JWT |
| `POST` | `/auth/login` | Public | Authenticate email/password & return a signed JWT + role |
| `POST` | `/auth/google` | Public | Verify a Google ID token & return a signed JWT + role |

### Customer Cart & Orders (`Authorization: Bearer <token>`)
| Method | Endpoint | Access | Description |
|:-------|:---------|:-------|:------------|
| `GET` | `/cart` | Authenticated | View all keys currently reserved in the user's cart |
| `POST` | `/cart/lock` | Authenticated | Reserve 1 available key for a game for 15 minutes |
| `DELETE` | `/cart/{id}` | Authenticated | Release a locked key back to available stock |
| `POST` | `/cart/checkout` | Authenticated | Atomically purchase all reserved cart items & reveal keys |
| `GET` | `/orders` | Authenticated | Retrieve historical order receipts and purchased key codes |

### Admin Management (`Authorization: Bearer <token>` with `role == "ADMIN"`)
| Method | Endpoint | Access | Description |
|:-------|:---------|:-------|:------------|
| `POST` | `/admin/games` | Admin Only (`403` otherwise) | Add a new game to the store catalog |
| `POST` | `/admin/games/{id}/keys` | Admin Only (`403` otherwise) | Batch-insert activation keys for a specific game |
| `POST` | `/admin/upload-image` | Admin Only (`403` otherwise) | Compress & upload cover artwork to Supabase Storage |

---

## Local Setup & Deployment

### 1. Prerequisites
- JDK 17+
- PostgreSQL 15+ (local instance or a free Neon database)
- Supabase project with a public Storage bucket (`game-covers`)
- Google Cloud OAuth 2.0 Web Client ID (for Google Sign-In verification)

### 2. Environment Variables
Configure these environment variables in your IntelliJ Run Configuration (or Render dashboard):

```env
DB_URL=jdbc:postgresql://localhost:5432/gamestore_db
DB_USER=postgres
DB_PASSWORD=your_db_password
JWT_SECRET=your_hmac_sha256_secret_key
GOOGLE_WEB_CLIENT_ID=your_google_web_client_id.apps.googleusercontent.com
SUPABASE_URL=[https://your-project-id.supabase.co](https://your-project-id.supabase.co)
SUPABASE_SERVICE_KEY=your_supabase_service_role_key
```

### 3. Run Locally
```bash
./gradlew run
```
The server starts on `http://0.0.0.0:8080`.

### 4. Run with Docker
```bash
docker build -t gametrade-backend .
docker run -p 8080:8080 --env-file .env gametrade-backend
```
