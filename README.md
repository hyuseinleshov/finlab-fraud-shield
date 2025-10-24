# FinLab Fraud Shield

Real-time invoice fraud detection system built with microservices architecture, stateful JWT authentication, and intelligent risk scoring.

**Developed for:** Fibank Hackathon - FinLab Challenge 2025

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Required-blue.svg)](https://www.docker.com/)

---

## Table of Contents

- [Quick Start](#quick-start)
- [Prerequisites](#prerequisites)
- [Build and Run Instructions](#build-and-run-instructions)
- [System Overview](#system-overview)
- [API Endpoints](#api-endpoints)
- [Testing](#testing)
- [Fraud Detection Rules](#fraud-detection-rules)
- [Configuration](#configuration)
- [Performance](#performance)
- [License](#license)

---

## Quick Start

Get the system running in **under 5 minutes**:

```bash
# 1. Clone the repository
git clone https://github.com/hyuseinleshov/finlab-fraud-shield.git
cd finlab-fraud-shield

# 2. Start all services (single command)
docker compose up --build -d

# 3. Wait for services to be ready (~60 seconds)
docker compose logs -f flyway  # Wait for "Successfully applied n migrations"

# 4. Access the application in your browser
# Navigate to: https://localhost/app/
```

**Default Credentials:**
- Username: `admin`
- Password: `password123`

---

## Prerequisites

Before starting, ensure you have the following installed:

- **Docker** (version 20.10 or higher)
- **Docker Compose** (version 2.0 or higher)
- **Git** (for cloning the repository)

**System Requirements:**
- 4 CPU cores (minimum 2)
- 8 GB RAM (minimum 4 GB)
- 5 GB free disk space

**Verify Prerequisites:**
```bash
docker --version          # Should be 20.10+
docker compose version    # Should be 2.0+
```

---

## Build and Run Instructions

### Standard Startup

The entire system can be started with a **single command**:

```bash
docker compose up --build -d
```

This command will:
1. Build multi-stage Docker images for Gateway and Accounts services
2. Start PostgreSQL database
3. Run Flyway migrations (schema creation + 1M IBAN generation)
4. Start Redis cache
5. Start Gateway and Accounts microservices
6. Start Nginx reverse proxy with TLS

### Verify Services

Check that all services are healthy:

```bash
docker compose ps
```

Expected output:
```
NAME                  STATUS              PORTS
finlab-postgres       Up (healthy)        5432/tcp
finlab-redis          Up (healthy)        6379/tcp
finlab-flyway         Exited (0)          -
finlab-gateway        Up (healthy)        0.0.0.0:8080->8080/tcp
finlab-accounts       Up (healthy)        0.0.0.0:8081->8081/tcp
finlab-nginx          Up                  0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
```

### View Logs

Monitor logs for specific services:

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f gateway
docker compose logs -f accounts
docker compose logs -f nginx

# Flyway migration (IBAN generation)
docker compose logs flyway
```

### Access the Application

Once all services are running:

- **Frontend (HTTPS):** https://localhost/app/
- **API Gateway (HTTPS):** https://localhost/api/
- **Health Check:** https://localhost/actuator/health

**Note:** You will see a browser warning for the self-signed TLS certificate. This is expected for local development. Click "Advanced" → "Proceed to localhost" to continue.

### Stop Services

```bash
# Stop all services (preserve data)
docker compose down

# Stop and remove all data (clean slate)
docker compose down -v
```

### Rebuild After Code Changes

```bash
# Rebuild and restart specific service
docker compose up --build -d gateway

# Rebuild all services
docker compose up --build -d
```

### Troubleshooting

**Issue: Port already in use**
```bash
# Check what's using the ports (platform-specific):
# Linux/macOS:
sudo lsof -i :443
sudo lsof -i :8080

# Windows (PowerShell):
netstat -ano | findstr :443
netstat -ano | findstr :8080

# Stop conflicting services:
# Linux (systemd): sudo systemctl stop nginx
# Windows: Stop service via Services panel or Task Manager
# macOS: brew services stop nginx (if installed via Homebrew)
```

**Issue: Services not starting**
```bash
# Check logs for errors
docker compose logs gateway
docker compose logs accounts

# Verify environment variables
cat .env

# Reset everything and start fresh
docker compose down -v
docker compose up --build -d
```

**Issue: Flyway migration fails**
```bash
# Check Flyway logs
docker compose logs flyway

# Manually run Flyway
docker compose up flyway

# Reset database (WARNING: deletes all data)
docker compose down -v
docker compose up --build -d
```

---

## System Overview

### Architecture

Architecture diagram will be added here.

### Technology Stack

**Backend:**
- **Java 21** (Eclipse Temurin)
- **Spring Boot 3.5.6** (Spring MVC, Spring Security 6, Spring Data JDBC)
- **JdbcTemplate** (NO JPA/Hibernate)
- **PostgreSQL 15** (RDBMS)
- **Redis 7** (Cache and session store)
- **Flyway 10** (Database migrations)

**Frontend:**
- **Vanilla JavaScript** (ES6+)
- **HTML5 + CSS3** (Responsive design)

**Infrastructure:**
- **Docker & Docker Compose** (Containerization)
- **Nginx 1.27** (Reverse proxy, TLS, HTTP/2, gzip)
- **Apache JMeter 5.6.3** (Load testing)

**Security:**
- **Stateful JWT** (Redis + PostgreSQL dual storage)
- **BCrypt** password hashing
- **X-API-KEY** authentication (service-to-service)
- **TLS 1.2/1.3** (Self-signed certificates)

### Key Features

**Architecture & Design**
- Microservices architecture with Gateway and Accounts services
- Dedicated Docker network for service isolation
- Multi-stage Docker builds for optimized container images
- Single-command deployment with Docker Compose

**Authentication & Security**
- Stateful JWT authentication with dual storage (Redis + PostgreSQL)
- In-memory token storage on frontend (no localStorage/sessionStorage)
- Service-to-service authentication via X-API-KEY
- TLS 1.2/1.3 encryption with HTTP/2 support
- BCrypt password hashing with Spring Security 6

**Fraud Detection**
- 5-rule scoring engine with point-based system (0-100)
- Three-tier decision framework: ALLOW / REVIEW / BLOCK
- Explainable risk factors for transparency
- Real-time validation with sub-200ms target latency

**Data Management**
- 1 million valid Bulgarian IBANs with MOD 97 validation
- PostgreSQL 15 with JdbcTemplate (no JPA/Hibernate)
- Flyway database migrations with automatic seeding
- Redis caching for high-performance lookups

**Performance & Testing**
- Connection pooling (HikariCP) and Redis optimization
- Apache JMeter load testing with normal and extreme scenarios
- Comprehensive unit and integration test coverage
- Audit logging for compliance and monitoring

---

## API Endpoints

### Authentication Endpoints (Gateway)

All authentication endpoints are accessible via the API Gateway.

#### 1. Login

Authenticate a user and receive JWT tokens.

**Endpoint:** `POST /api/auth/login`

**Request:**
```json
{
  "username": "admin",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

**cURL Example:**
```bash
curl -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}' \
  -k  # -k flag ignores self-signed certificate warning
```

---

#### 2. Logout

Invalidate the current JWT token.

**Endpoint:** `POST /api/auth/logout`

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "message": "Logout successful",
  "status": "success"
}
```

**cURL Example:**
```bash
curl -X POST https://localhost/api/auth/logout \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -k
```

---

#### 3. Refresh Token

Get a new access token using a refresh token.

**Endpoint:** `POST /api/auth/refresh`

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

---

### Fraud Detection Endpoint (Gateway → Accounts)

#### 4. Validate Invoice

Submit an invoice for fraud risk assessment.

**Endpoint:** `POST /api/v1/invoices/validate`

**Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request:**
```json
{
  "iban": "BG80BNBG96611020345678",
  "amount": 1500.00,
  "vendorId": 1001,
  "invoiceNumber": "INV-2025-001"
}
```

**Response (200 OK):**
```json
{
  "decision": "ALLOW",
  "fraudScore": 15,
  "riskFactors": [
    "Velocity: 3 transactions in 15 minutes for this IBAN"
  ]
}
```

**Possible Decisions:**
- `ALLOW` - Fraud score 0-30 (approve transaction)
- `REVIEW` - Fraud score 31-70 (manual review required)
- `BLOCK` - Fraud score 71-100 (reject transaction)

**cURL Example:**
```bash
# 1. Get token
TOKEN=$(curl -s -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}' \
  -k | jq -r .accessToken)

# 2. Validate invoice
curl -X POST https://localhost/api/v1/invoices/validate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "iban": "BG80BNBG96611020345678",
    "amount": 1500.00,
    "vendorId": 1001,
    "invoiceNumber": "INV-2025-001"
  }' \
  -k | jq
```

---

### Health Check Endpoint

#### 5. Health Check

Check service health status.

**Endpoint:** `GET /actuator/health`

**Response (200 OK):**
```json
{
  "status": "UP"
}
```

**cURL Example:**
```bash
curl -X GET https://localhost/actuator/health -k
```

---

## Testing

### Manual Testing via Frontend

1. Navigate to **https://localhost/app/**
2. Accept the self-signed certificate warning
3. Login with default credentials:
   - Username: `admin`
   - Password: `password123`
4. Use the **Test Scenario Dropdown** to select a prefilled test case:
   - **Legitimate Transaction** (ALLOW)
   - **Duplicate Invoice** (REVIEW/BLOCK)
   - **Invalid IBAN** (BLOCK)
   - **Amount Near Threshold** (REVIEW)
   - **Multiple Risk Factors** (BLOCK)
   - **Clean Transaction** (ALLOW)
5. Click **"Validate Invoice"** to see fraud detection results

---

### Load Testing with JMeter

The project includes two JMeter test plans for performance validation.

#### Normal Load Test

Simulates **100 concurrent users** over **5 minutes**.

**Run Test:**
```bash
docker compose --profile testing up --build jmeter
```

**Results Location:**
- JTL file: `/stress_tests/normal_load_results/results.jtl`
- HTML report: `/stress_tests/normal_load_results/html/index.html`

**View Results:**

Open `stress_tests/normal_load_results/html/index.html` in your browser.

**Test Configuration:**
- Users: 100
- Ramp-up: 30 seconds
- Duration: 5 minutes (300 seconds)
- Target: `POST /api/v1/invoices/validate`
- Assertions: Response time, status code

---

#### Extreme Load Test

Simulates **1000 concurrent users** over **10 minutes** to identify breaking points.

**Run Test:**
```bash
docker compose --profile testing-extreme up --build jmeter-extreme
```

**Results Location:**
- JTL file: `/stress_tests/extreme_load_results/results.jtl`
- HTML report: `/stress_tests/extreme_load_results/html/index.html`

**View Results:**

Open `stress_tests/extreme_load_results/html/index.html` in your browser.

**Test Configuration:**
- Users: 1000
- Ramp-up: 60 seconds
- Duration: 10 minutes (600 seconds)
- Target: `POST /api/v1/invoices/validate`

---

### Unit and Integration Tests

Run tests for Gateway and Accounts services:

```bash
# Gateway tests
cd gateway
./mvnw test

# Accounts tests
cd accounts
./mvnw test
```

**Test Coverage:**
- JWT authentication and validation
- Fraud scoring engine rules
- IBAN validation (MOD 97)
- API key authentication
- Repository operations (JdbcTemplate)
- Controller endpoints

---

## Fraud Detection Rules

The fraud detection engine uses a **point-based scoring system** with 5 independent rules:

### Rule 1: Duplicate Invoice Detection
**Points:** +50
**Description:** Checks if the same invoice number was submitted in the last 24 hours.
**Implementation:** Redis Set with 24-hour TTL using `SETIFABSENT` pattern.

### Rule 2: Invalid IBAN
**Points:** +50
**Description:** Validates IBAN using ISO 7064 MOD 97 checksum algorithm.
**Implementation:** Piece-wise MOD 97 calculation with 1-hour Redis cache.

### Rule 3: Risky IBAN
**Points:** +40
**Description:** Checks if the IBAN is flagged as risky in the database (~10% of 1M IBANs).
**Implementation:** PostgreSQL lookup with indexed query.

### Rule 4: Amount Manipulation
**Points:** +30
**Description:** Detects amounts just below common thresholds (e.g., 9,999.99 instead of 10,000).
**Implementation:** Pattern matching against threshold values (1000, 5000, 10000, 50000, 100000).

### Rule 5: Velocity Anomaly
**Points:** +15
**Description:** Detects abnormal transaction frequency (>5 for IBAN, >10 for vendor in 15 minutes).
**Implementation:** Redis ZSet with time-windowed counting using `ZCOUNT`.

---

### Decision Framework

| Fraud Score | Decision   | Action                            |
|-------------|------------|-----------------------------------|
| 0-30        | **ALLOW**  | Approve transaction automatically |
| 31-70       | **REVIEW** | Flag for manual review            |
| 71-100      | **BLOCK**  | Reject transaction automatically  |

**Example Scenarios:**

**Scenario 1: Legitimate Transaction**
- Valid IBAN, not risky, normal amount, first-time invoice, low velocity
- Score: **0-15**
- Decision: **ALLOW**

**Scenario 2: Suspicious Transaction**
- Valid IBAN, risky IBAN flagged, amount near threshold
- Score: **40 + 30 = 70**
- Decision: **REVIEW**

**Scenario 3: Fraudulent Transaction**
- Invalid IBAN + duplicate invoice
- Score: **50 + 50 = 100**
- Decision: **BLOCK**

---

## Configuration

### Environment Variables

All configuration is managed via `.env` file in the project root.

**Database Configuration:**
```env
DB_HOST=postgres
DB_PORT=5432
DB_NAME=finlab_fraud_shield
DB_USER=finlab_user
DB_PASSWORD=secure_db_password
```

**Redis Configuration:**
```env
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=redis_password
```

**JWT Configuration:**
```env
JWT_SECRET=your-256-bit-secret-key-change-this-in-production
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000
```

**API Key (Service-to-Service):**
```env
API_KEY=ZmluYmFuay1oYWNrYXRob24tYXBpLWtleS0yMDI1LWZpbmxhYi1mcmF1ZC1zaGllbGQ=
```

**Note:** The API key is Base64-encoded. To decode:
```bash
echo "ZmluYmFuay1oYWNrYXRob24tYXBpLWtleS0yMDI1LWZpbmxhYi1mcmF1ZC1zaGllbGQ=" | base64 -d
# Output: finbank-hackathon-api-key-2025-finlab-fraud-shield
```

---

### Application Configuration

Service-specific configuration is in `application.yml` files:

- `gateway/src/main/resources/application.yml`
- `accounts/src/main/resources/application.yml`

**Key Settings:**

**HikariCP Connection Pool:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Redis Connection Pool:**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 64
          max-idle: 16
          min-idle: 8
          max-wait: 100ms
```

**JWT Token Expiration:**
```yaml
jwt:
  secret: ${JWT_SECRET}
  access-expiration: ${JWT_ACCESS_EXPIRATION:900000}    # 15 minutes
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}  # 7 days
```

---

### Nginx Configuration

Nginx configuration is in `nginx/nginx.conf`:

**Key Features:**
- **TLS 1.2/1.3** with self-signed certificates
- **HTTP/2** for multiplexing
- **gzip compression** (level 6)
- **Rate limiting** (10 req/s for API, 5 req/s for auth)
- **Security headers** (HSTS, X-Frame-Options, etc.)
- **Upstream keepalive** (32 connections)

**Routes:**
- `/app/*` → Static files (frontend)
- `/api/*` → Gateway (JWT authentication)
- `/actuator/health` → Health checks (no auth)

---

## Performance

### Key Optimizations

The system implements several production-grade optimizations targeting <200ms P95 latency:

- **Parallelized fraud detection** - 5 concurrent checks with 150ms timeout (5x speedup)
- **Multi-layer caching** - Redis cache-first with >80% hit rate target
- **Connection pooling** - HikariCP with 30 connections per service
- **Async processing** - Non-blocking audit logging (saves 10-20ms per request)
- **JVM tuning** - G1GC with 100ms pause target, virtual threads enabled
- **HTTP/2 + gzip** - Network protocol optimization with upstream keepalive

### Status

The system implements production-grade optimizations including parallel fraud checks, async processing, multi-layer caching, and optimized connection pooling. Load test results are available in `/stress_tests/` after running the test commands above.

**Targets:** <200ms P95 latency, >50 req/s throughput

**Note:** Recent load testing shows performance regression compared to baseline results. Root cause investigation is needed to identify why implemented optimizations have not improved performance as expected.

---

## License

This project is licensed under the **Apache License 2.0**.

See the [LICENSE](./LICENSE) file for full details.

---

**Built with ❤️ for secure financial transactions**
