# GameStore Backend API

A production-grade RESTful e-commerce backend for a digital game key store, built with **Kotlin**, **Ktor**, **JetBrains Exposed DSL**, and **PostgreSQL**.

Designed with strict ACID compliance, pessimistic row-level locking (`SELECT ... FOR UPDATE`) to prevent double-selling under high concurrency, and stateless JWT role-based authentication.

---

## Tech Stack & Architecture

* **Language:** Kotlin
* **Server Framework:** Ktor (`Netty` / `CIO`, `ContentNegotiation`, `kotlinx.serialization`, `StatusPages`)
* **Database & ORM:** PostgreSQL + JetBrains Exposed DSL (`UUIDTable`)
* **Connection Pooling:** HikariCP with Coroutine-backed non-blocking transactions (`Dispatchers.IO`)
* **Security:** BCrypt salted password hashing (128-bit salt, cost factor 12) & Stateless HMAC-SHA256 JWT Authentication (`CUSTOMER` and `ADMIN` roles)

### Project Structure
```text
src/main/kotlin/com/anasdev/
├── data/
│   ├── local/
│   │   ├── DatabaseFactory.kt   # HikariCP pool & suspended transaction wrapper
│   │   └── Tables.kt            # Normalized PostgreSQL schema definitions
│   └── repository/
│       ├── GameRepository.kt    # Catalog queries, dynamic stock count & batch key insertion
│       ├── OrderRepository.kt   # Row-level cart locking, atomic checkout & receipt history
│       └── UserRepository.kt    # User registration & credential lookup
├── domain/
│   └── model/                   # @Serializable Request/Response DTOs
│       ├── AdminDto.kt
│       ├── AuthDto.kt
│       ├── GameResponse.kt
│       └── OrderDto.kt
├── representation/
│   └── routes/
│       ├── AdminRoutes.kt       # Role-guarded (ADMIN) catalog & key management
│       ├── AuthRoutes.kt        # Registration & Login JWT issuance
│       └── OrderRoutes.kt       # Protected cart reservation, checkout & order history
├── main.kt                      # Application entry point & module wiring
├── Routing.kt                   # Central route registration
├── Security.kt                  # JWT verifier & authentication configuration
├── Serialization.kt             # kotlinx.serialization JSON setup
└── StatusPages.kt               # Global exception handling
```

---

## Core Engineering Highlights

1. **Dynamic Real-Time Inventory (Zero Desync):**
   Instead of maintaining a fragile `stock_count` integer column that can drift out of sync during crashes, available stock is calculated dynamically in SQL by counting keys where `is_sold = false` and `(locked_until IS NULL OR locked_until < NOW())`.
2. **Concurrency & Race-Condition Protection (`SELECT ... FOR UPDATE`):**
   When a user adds a game to their cart (`POST /cart/lock`), the transaction acquires a PostgreSQL row-level lock on a single available key and stamps a 15-minute reservation (`locked_until`). Concurrent requests at the exact same millisecond are queued by PostgreSQL, preventing two users from reserving the same key.
3. **Atomic Multi-Table Checkout:**
   `POST /cart/checkout` executes inside a single ACID database transaction (`dbQuery`): it validates active cart reservations, calculates the total server-side, creates the `orders` receipt header, inserts `order_items` with frozen historical pricing (`price_at_purchase`), marks the keys as permanently sold (`is_sold = true`), and returns the revealed activation keys.

---

## Database Schema (PostgreSQL)

* **`users`**: `id (UUID PK)`, `email (UNIQUE)`, `password_hash`, `role (CUSTOMER | ADMIN)`, `created_at`
* **`games`**: `id (UUID PK)`, `title`, `description`, `image_path`, `release_date`, `genre`, `current_price`, `is_active`
* **`game_keys`**: `id (UUID PK)`, `game_id (FK -> games)`, `key_code (UNIQUE)`, `is_sold`, `locked_until`, `locked_by_user_id (FK -> users)`, `created_at`
* **`orders`**: `id (UUID PK)`, `user_id (FK -> users)`, `total_amount`, `created_at`
* **`order_items`**: `id (UUID PK)`, `order_id (FK -> orders)`, `key_id (FK -> game_keys)`, `price_at_purchase`

---

## API Endpoints

### Public Storefront & Auth
| Method | Endpoint         | Access | Description                                           |
|:-------|:-----------------|:-------|:------------------------------------------------------|
| `GET`  | `/games`         | Public | List all active games with real-time `availableStock` |
| `POST` | `/auth/register` | Public | Register a new customer account & return a signed JWT |
| `POST` | `/auth/login`    | Public | Authenticate credentials & return a signed JWT + role |

### Customer Cart & Orders (`Authorization: Bearer <token>`)
| Method | Endpoint         | Access        | Description                                                |
|:-------|:-----------------|:--------------|:-----------------------------------------------------------|
| `GET`  | `/cart`          | Authenticated | View all keys currently reserved in the user's cart        |
| `POST` | `/cart/lock`     | Authenticated | Reserve 1 available key for a game for 15 minutes          |
| `POST` | `/cart/checkout` | Authenticated | Atomically purchase all reserved cart items & reveal keys  |
| `GET`  | `/orders`        | Authenticated | Retrieve historical order receipts and purchased key codes |

### Admin Management (`Authorization: Bearer <token>` with `role == "ADMIN"`)
| Method | Endpoint                 | Access                       | Description                                      |
|:-------|:-------------------------|:-----------------------------|:-------------------------------------------------|
| `POST` | `/admin/games`           | Admin Only (`403` otherwise) | Add a new game to the store catalog              |
| `POST` | `/admin/games/{id}/keys` | Admin Only (`403` otherwise) | Batch-insert activation keys for a specific game |

---

## Local Setup & Environment Variables

### 1. Prerequisites
* JDK 17+
* PostgreSQL 15+ (with a database named `gamestore_db`)

### 2. Environment Variables
Configure the following environment variables in your IDE Run Configuration before starting the server:

```env
DB_URL=jdbc:postgresql://localhost:5432/gamestore_db
DB_USER=postgres
DB_PASSWORD=your_db_password
JWT_SECRET=your_hmac_sha256_secret_key
```

### 3. Run the Server
```bash
./gradlew run
```
The server starts on `http://0.0.0.0:8080`.