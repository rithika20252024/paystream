# PayStream — Distributed Payment & Double-Entry Ledger Engine

PayStream is an enterprise-grade payment processing gateway and double-entry financial ledger built with **Java 17**, **Maven**, and **Spring Boot 3.2.0**. 

It simulates high-volume payment infrastructure used at financial technology leaders such as **Visa, Stripe, and Fidelity Investments**. It guarantees **zero double-charging (Idempotency)** during network retries and enforces **double-entry accounting consistency** with database optimistic locking (`@Version`).

---

##  Interactive Web Dashboard

PayStream includes an interactive single-page web portal served directly by Spring Boot at `http://localhost:8080`.

- **Live Account Balances:** Displays real-time balances for seed accounts (*Alice, Bob, Charlie*) with optimistic lock tracking.
- **Payment & Idempotency Simulator:** Send payments or click **"Retry (Same Key)"** to send duplicate requests. Visually proves that duplicate requests with identical `Idempotency-Key` headers return the cached response instantly without deducting money twice!
- **Double-Entry Ledger Audit Trail:** Live table of all `DEBIT` and `CREDIT` transaction entries per account.

---

##  acheivements in project
- **Engineered PayStream, a high-reliability digital payment engine in Java 17 and Spring Boot**, simulating Visa/Stripe payment gateway architecture for double-entry financial transaction processing.
- **Architected an Idempotency Engine using `Idempotency-Key` headers**, storing request hashes and response payloads to guarantee zero duplicate charges during network retries or client double-clicking.
- **Implemented Double-Entry Accounting Ledger & State Machine**, enforcing matching `DEBIT` and `CREDIT` audit logs and atomic transaction state transitions (`INITIATED` $\rightarrow$ `PROCESSING` $\rightarrow$ `COMPLETED`).
- **Guaranteed concurrency safety using Optimistic Locking (`@Version`)**, preventing race conditions, lost updates, and balance corruption during simultaneous multi-threaded account transfers.
- **Built an interactive single-page web portal** (HTML5/TailwindCSS) with a real-time idempotency retry simulator and dynamic ledger audit statement viewer.
- **Developed a 10-thread concurrent integration stress test** using JUnit 5, Mockito, and `CountDownLatch` to empirically verify zero system balance leakage under high thread contention.

---

##  System Architecture & Payment Flow

```
                               ┌──────────────────────────────────┐
                               │  Incoming Payment Request (HTTP) │ (Header: Idempotency-Key)
                               └─────────────────┬────────────────┘
                                                 │
                                                 ▼
                               ┌──────────────────────────────────┐
                               │       PaymentController          │
                               └─────────────────┬────────────────┘
                                                 │
                                                 ▼
                               ┌──────────────────────────────────┐
                               │         PaymentService           │
                               └────────┬─────────────────┬───────┘
                                        │                 │
             ┌──────────────────────────┘                 └──────────────────────────┐
             ▼                                                                       ▼
┌───────────────────────────┐                                           ┌───────────────────────────┐
│    IdempotencyService     │ (Key Check)                               │      AccountRepository    │
└────────────┬──────────────┘                                           └────────────┬──────────────┘
             │                                                                       │
    ┌────────┴─────────┐                                                    ┌────────┴─────────┐
    │ Key Exists?      │                                                    │ Balance Check &  │
    ├─────────┬────────┤                                                    │ Optimistic Lock  │
    │ YES     │ NO     │                                                    └────────┬─────────┘
    ▼         ▼        ▼                                                             │
┌────────┐  ┌───────────────────────────┐                                            ▼
│Return  │  │ Execute Transfer:         │                               ┌───────────────────────────┐
│Cached  │  │ - Sender Balance - Amount │                               │       LedgerService       │
│DTO     │  │ - Receiver Balance + Amt  │                               │ - Create DEBIT entry      │
└────────┘  └─────────────┬─────────────┘                               │ - Create CREDIT entry     │
                          │                                             └────────────┬──────────────┘
                          ▼                                                          │
            ┌───────────────────────────┐                                            ▼
            │ Save Idempotency Record   │                               ┌───────────────────────────┐
            │ & Return 200 OK           │                               │   Transaction Repository  │
            └───────────────────────────┘                               └───────────────────────────┘
```

---

##  Key Financial Engineering Concepts

### 1. Idempotency (Zero Double Charges)
- **Problem:** In distributed payment systems, network timeouts or user double-clicks cause clients to retry payment requests. Without idempotency, users get charged twice.
- **PayStream Solution:** Clients supply a unique `Idempotency-Key` HTTP header (e.g. UUID). PayStream checks `IdempotencyRepository`. If the key exists, it deserializes the stored response JSON and returns it immediately. The ledger transfer is **NEVER executed twice**.

### 2. Double-Entry Accounting Ledger
- **Concept:** In banking systems, money cannot simply disappear or be created out of nowhere. Every movement of funds must balance.
- **PayStream Solution:** For every payment of amount $X$:
  - A `DEBIT` entry of $X$ is recorded against the sender's account.
  - A `CREDIT` entry of $X$ is recorded against the receiver's account.
  - Both entries record `balanceAfter` to maintain an immutable audit trail.

### 3. Concurrency & Optimistic Locking (`@Version`)
- **Problem:** If 10 concurrent threads try to transfer money out of the same account at the exact same millisecond, read-modify-write race conditions can cause lost updates or negative balances.
- **PayStream Solution:** JPA `@Version` annotation on `Account` entity detects concurrent modifications. If two threads read version 0 and try to update, Hibernate rejects the second thread with an `ObjectOptimisticLockingFailureException` (HTTP 409 Conflict), protecting database integrity.

---

##  Project Directory Structure

```
paystream/
├── pom.xml
├── Dockerfile
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/paystream/
│   │   │   ├── PayStreamApplication.java
│   │   │   ├── model/                         # JPA Entities & DTOs
│   │   │   │   ├── Account.java               (Optimistic Lock @Version)
│   │   │   │   ├── PaymentTransaction.java    (Transaction state machine)
│   │   │   │   ├── LedgerEntry.java           (Double-Entry DEBIT/CREDIT)
│   │   │   │   ├── IdempotencyRecord.java     (Idempotency response cache)
│   │   │   │   ├── PaymentRequestDTO.java
│   │   │   │   └── PaymentResponseDTO.java
│   │   │   ├── repository/                    # Spring Data Repositories
│   │   │   │   ├── AccountRepository.java
│   │   │   │   ├── TransactionRepository.java
│   │   │   │   ├── LedgerRepository.java
│   │   │   │   └── IdempotencyRepository.java
│   │   │   ├── service/                       # Business Logic
│   │   │   │   ├── PaymentService.java
│   │   │   │   ├── LedgerService.java
│   │   │   │   └── IdempotencyService.java
│   │   │   ├── exception/                     # Global Error Handling
│   │   │   │   ├── InsufficientBalanceException.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   └── controller/                    # REST Controllers
│   │   │       ├── PaymentController.java     (POST /api/v1/payments)
│   │   │       └── AccountController.java     (GET /api/v1/accounts)
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── data.sql                       # Seed Accounts (Alice, Bob, Charlie)
│   │       └── static/
│   │           └── index.html                 # Interactive Payment Portal
│   └── test/
│       └── java/com/paystream/
│           ├── service/
│           │   └── PaymentServiceTest.java    # Unit Tests
│           └── integration/
│               └── PaymentIntegrationTest.java# ⭐ Multi-threaded Concurrency Test
```

---

## REST API Documentation

### 1. Payment Endpoint (`POST /api/v1/payments`)
- **Headers:** `Idempotency-Key: IDEMP-UUID-12345` (Required)
- **Request Body:**
```json
{
  "senderAccountNumber": "ACC-1001",
  "receiverAccountNumber": "ACC-1002",
  "amount": 100.00,
  "currency": "USD"
}
```
- **Response (HTTP 200 OK):**
```json
{
  "transactionReference": "TX-4F9B8FBA",
  "idempotencyKey": "IDEMP-UUID-12345",
  "senderAccountNumber": "ACC-1001",
  "receiverAccountNumber": "ACC-1002",
  "amount": 100.00,
  "currency": "USD",
  "status": "COMPLETED",
  "message": "Payment executed successfully",
  "timestamp": "2026-09-04T12:00:00.000"
}
```

### 2. Account & Ledger Endpoints (`/api/v1/accounts`)
- `GET /api/v1/accounts` — Returns list of all bank accounts and balances.
- `GET /api/v1/accounts/{accountNumber}/statement` — Returns double-entry audit statement for account.

---

##  Testing & Concurrency Stress Test

Run full test suite:
```bash
mvn clean test
```

###  10-Thread Concurrency Test Explanation:
`PaymentIntegrationTest.shouldHandleConcurrentPaymentsSafely` releases 10 concurrent threads simultaneously using `CountDownLatch` to execute payments. The test verifies:
- `totalSystemBalance = senderBalance + receiverBalance` remains **exactly $1,500.00**, empirically proving zero balance corruption or money creation under thread contention.

---

##  How to Run Locally

### Run via Maven:
```bash
git cloe https://github.com/rithika20252024/paystream
cd paystream
mvn spring-boot:run
```
Access UI: **`http://localhost:8080`**  
Access H2 Console: **`http://localhost:8080/h2-console`**

---


