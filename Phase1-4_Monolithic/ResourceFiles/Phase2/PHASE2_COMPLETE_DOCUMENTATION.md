# Phase 2 Documentation - Authentication & Wallet Operations ✅

**Status:** COMPLETED ✅  
**Completion Date:** [Current Date]

---

## Table of Contents
1. [Phase 2 Overview](#phase-2-overview)
2. [Three Flows Completed](#three-flows-completed)
3. [Admin API](#admin-api)
4. [Security Implementation](#security-implementation)
5. [Database Schema](#database-schema)
6. [DTOs (Data Transfer Objects)](#dtos)
7. [API Endpoints Summary](#api-endpoints-summary)
8. [Error Handling](#error-handling)
9. [Testing Guide](#testing-guide)
10. [Files Modified](#files-modified)

---

## Phase 2 Overview

### What is Phase 2?
Phase 2 implements **user authentication** and **wallet management** with role-based access control.

### Objectives Completed ✅
- ✅ User registration with automatic wallet creation
- ✅ User login with JWT token generation (access + refresh)
- ✅ JWT token validation on protected endpoints
- ✅ Wallet operations (get, deposit, withdraw) with ownership verification
- ✅ Admin panel for viewing all wallets
- ✅ Role-based access control (CUSTOMER, ADMIN)

### Key Features
| Feature | Details |
|---------|---------|
| **JWT Authentication** | 15-min access token, 7-day refresh token |
| **Password Security** | BCrypt hashing |
| **Token Validation** | JwtAuthenticationFilter on every request |
| **Ownership Verification** | Users can only access their own wallets |
| **Admin Access** | Admins can view all wallets (bypass ownership check) |
| **Role-Based Control** | CUSTOMER vs ADMIN roles |

---

## Three Flows Completed

---

## **FLOW A: User Registration** 📝

### Endpoint
```
POST /api/users/register
```

### Request
```json
{
  "fullName": "Rahul Mulik",
  "email": "rahul@gmail.com",
  "password": "password123"
}
```

### Response (201 Created)
```json
{
  "id": 1,
  "fullName": "Rahul Mulik",
  "email": "rahul@gmail.com",
  "createdAt": "2024-01-15T10:30:45.123"
}
```

### What Happens Internally
```
1. UserController receives request
   ↓
2. Validates input (@Valid)
   ↓
3. UserService.register() called
   ↓
4. Check: Does email already exist?
   - YES → Throw DuplicateEmailException (409)
   - NO → Continue
   ↓
5. Hash password using BCrypt
   ↓
6. Create User entity with role=CUSTOMER
   ↓
7. Create Wallet for user (balance=0, currency=INR)
   ↓
8. Save to database
   ↓
9. Convert to UserResponse DTO
   ↓
10. Return 201 CREATED
```

### Status Codes
| Code | Meaning | Example |
|------|---------|---------|
| **201** | User created | Success ✅ |
| **400** | Bad request | Invalid input, missing field |
| **409** | Conflict | Email already registered |

### Validation Rules
- fullName: Not empty, max 100 chars
- email: Valid email format
- password: Min 6 chars

### DTOs Used
```java
RegisterRequest (input):
  - fullName: String
  - email: String
  - password: String

UserResponse (output):
  - id: Long
  - fullName: String
  - email: String
  - createdAt: LocalDateTime
```

### Database Changes
```
Users table: NEW ROW
  id: 1, fullName: "Rahul", email: "rahul@gmail.com", 
  password: "bcrypt_hash", role: "CUSTOMER", createdAt: now

Wallets table: NEW ROW
  id: 1, userId: 1, balance: 0, currency: "INR", 
  createdAt: now, updatedAt: now
```

### Example (cURL)
```bash
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Rahul Mulik",
    "email": "rahul@gmail.com",
    "password": "password123"
  }'
```

---

## **FLOW B: User Login** 🔑

### Endpoint
```
POST /api/users/login
```

### Request
```json
{
  "email": "rahul@gmail.com",
  "password": "password123"
}
```

### Response (200 OK)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyYWhvbEBnbWFpbC5jb20iLCJpYXQiOjE2ODc2NDM0NTIsImV4cCI6MTY4NzY0NDM1Mn0.r_GkDNR_ptWSzDTgZd0Uxp46VpPYg_9pI4t6w6Z_34Y",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyYWhvbEBnbWFpbC5jb20iLCJpYXQiOjE2ODc2NDM0NTIsImV4cCI6MTY4ODI0ODI1Mn0.hmzp-uBo8jwLXxyU_eiVWf7RwWNM8QpqMHc26VcvC4A",
  "tokenType": "Bearer"
}
```

### What Happens Internally
```
1. UserController receives email + password
   ↓
2. UserService.login() called
   ↓
3. Find user by email
   - NOT FOUND → Throw ResourceNotFoundException (404)
   ↓
4. Compare passwords using PasswordEncoder.matches()
   - MISMATCH → Throw ResourceNotFoundException (401)
   ↓
5. JwtTokenProvider generates:
   - accessToken (15 minutes validity)
   - refreshToken (7 days validity)
   ↓
6. Return LoginResponse with tokens
```

### JWT Token Structure
```
Header:
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload:
{
  "sub": "rahul@gmail.com",
  "role": "CUSTOMER",
  "iat": 1687643452,    (issued at)
  "exp": 1687644352     (expires)
}

Signature: HMAC-SHA256(secret_key)
```

### Token Details
| Token | Validity | Purpose |
|-------|----------|---------|
| **accessToken** | 15 minutes | Use for API requests |
| **refreshToken** | 7 days | Get new accessToken when expired |

### Status Codes
| Code | Meaning |
|------|---------|
| **200** | Login successful, tokens returned ✅ |
| **401** | Invalid credentials (wrong password) ❌ |
| **404** | User not found ❌ |
| **400** | Bad request (missing email/password) ❌ |

### DTOs Used
```java
LoginRequest (input):
  - email: String
  - password: String

LoginResponse (output):
  - accessToken: String
  - refreshToken: String
  - tokenType: String ("Bearer")
```

### Example (cURL)
```bash
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "rahul@gmail.com",
    "password": "password123"
  }'
```

### Using Token in Requests
```bash
# Copy accessToken from login response, then:
curl -X GET http://localhost:8080/api/wallets/1 \
  -H "Authorization: Bearer <accessToken>"
```

---

## **FLOW C: Wallet Operations** 💰

### Prerequisites
- User must be logged in
- Must have valid JWT accessToken
- Token passed in `Authorization: Bearer <token>` header

### **C1. Get Wallet**

#### Endpoint
```
GET /api/wallets/{walletId}
Header: Authorization: Bearer <accessToken>
```

#### Response (200 OK)
```json
{
  "walletId": 1,
  "userId": 1,
  "balance": 5000,
  "currency": "INR"
}
```

#### What Happens Internally
```
1. Request arrives with Authorization header
   ↓
2. JwtAuthenticationFilter intercepts:
   - Extract token from "Bearer <token>"
   - Validate signature + expiry
   - Extract email from token
   - Set email in SecurityContext
   ↓
3. WalletController.getWallet() executed
   ↓
4. Get wallet from database
   ↓
5. verifyOwnership(wallet):
   - Get logged-in user email from SecurityContext
   - Get wallet owner email from database
   - Compare:
     * ADMIN user? → Allow (bypass check)
     * Same email? → Allow ✅
     * Different email? → Throw 403 ❌
   ↓
6. Convert to WalletResponse DTO
   ↓
7. Return 200 OK
```

#### Ownership Check Details
```java
private void verifyOwnership(Wallet wallet) {
    // Get logged-in user (set by JwtAuthenticationFilter)
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    
    // Check if user has ADMIN role
    boolean isAdmin = authentication.getAuthorities()
                    .stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    
    if (isAdmin) {
        return;  // Admins can access any wallet
    }
    
    // For regular users, check ownership
    String currentEmail = securityUtils.getCurrentUserEmail();
    
    if (!currentEmail.equals(wallet.getUser().getEmail())) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, 
            "Access denied: wallet does not belong to you"
        );
    }
}
```

#### Status Codes
| Code | Meaning |
|------|---------|
| **200** | Wallet returned ✅ |
| **401** | No token / Invalid token ❌ |
| **403** | Wallet doesn't belong to you ❌ |
| **404** | Wallet not found ❌ |

---

### **C2. Deposit Money**

#### Endpoint
```
POST /api/wallets/{walletId}/deposit
Header: Authorization: Bearer <accessToken>
```

#### Request
```json
{
  "amount": 5000
}
```

#### Response (200 OK)
```json
{
  "walletId": 1,
  "userId": 1,
  "balance": 5000,
  "currency": "INR"
}
```

#### What Happens
```
1. JWT validation (same as Get Wallet)
2. Ownership verification
3. WalletService.deposit(walletId, amount)
   - Get wallet from DB
   - Add amount to balance
   - Save to DB
4. Return updated wallet
```

#### Example (cURL)
```bash
curl -X POST http://localhost:8080/api/wallets/1/deposit \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000}'
```

---

### **C3. Withdraw Money**

#### Endpoint
```
POST /api/wallets/{walletId}/withdraw
Header: Authorization: Bearer <accessToken>
```

#### Request
```json
{
  "amount": 1000
}
```

#### Response (200 OK)
```json
{
  "walletId": 1,
  "userId": 1,
  "balance": 4000,
  "currency": "INR"
}
```

#### What Happens
```
1. JWT validation (same as Get Wallet)
2. Ownership verification
3. WalletService.withdraw(walletId, amount)
   - Get wallet from DB
   - Check if balance >= amount
     * NO → Throw InsufficientBalanceException (409)
   - Subtract amount from balance
   - Save to DB
4. Return updated wallet
```

#### Status Codes
| Code | Meaning |
|------|---------|
| **200** | Withdrawal successful ✅ |
| **409** | Insufficient balance ❌ |
| **403** | Wallet doesn't belong to you ❌ |

#### Example (cURL)
```bash
curl -X POST http://localhost:8080/api/wallets/1/withdraw \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 1000}'
```

---

## Admin API

### Overview
Admin endpoints allow administrators to:
- View all wallets in the system
- Bypass wallet ownership checks
- Monitor system usage

### Admin Role Requirements
```
User must have role = "ADMIN"
Token must be valid
Must pass .hasRole("ADMIN") check in SecurityConfig
```

---

### **Admin: Get All Wallets**

#### Endpoint
```
GET /api/admin/wallets
Header: Authorization: Bearer <adminAccessToken>
```

#### Response (200 OK)
```json
[
  {
    "walletId": 1,
    "userId": 1,
    "balance": 5000,
    "currency": "INR"
  },
  {
    "walletId": 2,
    "userId": 2,
    "balance": 3100,
    "currency": "INR"
  },
  {
    "walletId": 3,
    "userId": 3,
    "balance": 2700,
    "currency": "INR"
  }
]
```

#### What Happens
```
1. Request arrives with Authorization header
   ↓
2. JwtAuthenticationFilter validates token
   ↓
3. Extract admin's email and role
   ↓
4. Spring checks: Does token have ROLE_ADMIN?
   - YES → Continue ✅
   - NO → Return 403 Forbidden ❌
   ↓
5. AdminController.getAllWallets() executes
   ↓
6. WalletService.getAllWallets() returns all wallets
   ↓
7. Convert each to WalletResponse DTO
   ↓
8. Return 200 OK with list
```

#### Status Codes
| Code | Meaning |
|------|---------|
| **200** | List returned ✅ |
| **401** | No token / Invalid token ❌ |
| **403** | User is not ADMIN ❌ |

#### Example (cURL)
```bash
curl -X GET http://localhost:8080/api/admin/wallets \
  -H "Authorization: Bearer <adminAccessToken>"
```

---

## What Happens When Non-Admin Tries to Access Admin API?

### Scenario: Regular User (CUSTOMER) Accesses `/api/admin/wallets`

```
Regular User: rahul@gmail.com (role: CUSTOMER)
Action: GET /api/admin/wallets
Token: Valid JWT with role=CUSTOMER
```

### Step-by-Step Process

**Step 1: Request Arrives**
```
GET /api/admin/wallets
Authorization: Bearer eyJ...(valid token)
```

**Step 2: JwtAuthenticationFilter Validates**
```
✅ Token signature valid
✅ Token not expired
✅ Email extracted: rahul@gmail.com
✅ Role extracted: CUSTOMER
✅ Set in SecurityContext with ROLE_CUSTOMER
```

**Step 3: Spring Security Checks Authorization**
```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
                                  ↓
SecurityContext has authorities: [ROLE_CUSTOMER]
                                  ↓
Spring checks: Does ROLE_CUSTOMER == ROLE_ADMIN?
                                  ↓
NO ❌ → Access Denied
```

**Step 4: Exception Handler Catches It**
```java
.exceptionHandling(exception -> exception
    .accessDeniedHandler((request, response, accessDeniedException) -> {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);  // 403
        response.setContentType("application/json");
        response.getWriter().write("""
        {
            "status": 403,
            "message": "Access denied. Admin role required."
        }
        """);
    })
)
```

**Step 5: Response Sent to Client**

### Response (403 Forbidden)
```json
{
    "status": 403,
    "message": "Access denied. Admin role required."
}
```

---

### Different Scenarios

#### **Scenario 1: Valid Admin Token** ✅
```
User: admin@gmail.com (role: ADMIN)
Token: Valid JWT with role=ADMIN
Request: GET /api/admin/wallets

Result: ✅ 200 OK - Returns all wallets
```

#### **Scenario 2: Valid Customer Token** ❌
```
User: rahul@gmail.com (role: CUSTOMER)
Token: Valid JWT with role=CUSTOMER
Request: GET /api/admin/wallets

Result: ❌ 403 Forbidden - Access denied
```

#### **Scenario 3: No Token** ❌
```
Request: GET /api/admin/wallets
No Authorization header

Result: ❌ 401 Unauthorized - Missing token
```

#### **Scenario 4: Invalid/Expired Token** ❌
```
Request: GET /api/admin/wallets
Token: eyJ...(expired or invalid)

Result: ❌ 401 Unauthorized - Invalid token
```

#### **Scenario 5: Customer Tries Own Wallet** ✅
```
User: rahul@gmail.com (role: CUSTOMER)
Request: GET /api/wallets/1 (their own wallet)

Result: ✅ 200 OK - Ownership check passes
```

#### **Scenario 6: Customer Tries Other's Wallet** ❌
```
User: rahul@gmail.com (role: CUSTOMER)
Request: GET /api/wallets/2 (amit's wallet)

Result: ❌ 403 Forbidden - Ownership check fails
```

#### **Scenario 7: Admin Accesses Customer's Wallet** ✅
```
User: admin@gmail.com (role: ADMIN)
Request: GET /api/wallets/1 (customer's wallet)

Result: ✅ 200 OK - Admin can access any wallet
```

---

## Security Implementation

### 1. JwtAuthenticationFilter
```
What: Validates JWT tokens on every request
When: Before reaching the controller
How:
  1. Extract token from "Authorization: Bearer <token>"
  2. Validate token signature using secret key
  3. Validate token expiry
  4. Check token blacklist (logout)
  5. Extract email and role from token
  6. Set in Spring Security context
```

### 2. SecurityConfig
```java
Public endpoints (no token needed):
  - POST /api/users/register
  - POST /api/users/login

Protected endpoints (token needed):
  - All other endpoints

Admin endpoints (token + ADMIN role needed):
  - GET /api/admin/wallets

User endpoints (token needed, ownership check):
  - GET /api/wallets/{walletId}
  - POST /api/wallets/{walletId}/deposit
  - POST /api/wallets/{walletId}/withdraw
```

### 3. Ownership Verification
```
For regular users:
  - Can only access/modify their own wallet
  - verifyOwnership() method checks email match

For admin users:
  - Can access any wallet
  - verifyOwnership() returns early for admins
```

### 4. Password Security
```
Hashing: BCrypt
Rounds: 12 (default)
Safe Against: Rainbow tables, brute force

Never stored: Plaintext passwords
Always compared: Using PasswordEncoder.matches()
```

---

## Database Schema

### Users Table
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'CUSTOMER',  -- CUSTOMER, ADMIN
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Wallets Table
```sql
CREATE TABLE wallets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(19, 2) DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'INR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

### Relationships
```
One User → One Wallet (1:1)
  User.id = Wallet.user_id
```

### Test Data
```sql
Users:
1. Rahul (rahul@gmail.com) - CUSTOMER
2. Amit (amit@gmail.com) - CUSTOMER
3. Priya (priya@gmail.com) - CUSTOMER
4. Admin (admin@gmail.com) - ADMIN

Wallets:
1. Rahul's wallet - balance: 3500
2. Amit's wallet - balance: 3100
3. Priya's wallet - balance: 2700
(Admin has no wallet - operators, not customers)
```

---

## DTOs

### Input DTOs (Request)

#### RegisterRequest
```java
{
  "fullName": "string",    // Not empty, max 100
  "email": "string",       // Valid email format
  "password": "string"     // Min 6 chars
}
```

#### LoginRequest
```java
{
  "email": "string",       // Valid email
  "password": "string"     // User's password
}
```

#### AmountRequest
```java
{
  "amount": number         // Positive number, <= balance
}
```

### Output DTOs (Response)

#### UserResponse
```java
{
  "id": number,
  "fullName": "string",
  "email": "string",
  "createdAt": "datetime"
}
```

#### LoginResponse
```java
{
  "accessToken": "string",    // 15-min JWT
  "refreshToken": "string",   // 7-day JWT
  "tokenType": "Bearer"
}
```

#### WalletResponse
```java
{
  "walletId": number,
  "userId": number,
  "balance": number,
  "currency": "string"
}
```

---

## API Endpoints Summary

### Customer Endpoints

| Method | Endpoint | Header | Status | Purpose |
|--------|----------|--------|--------|---------|
| POST | `/api/users/register` | - | 201 | Create account |
| POST | `/api/users/login` | - | 200 | Get tokens |
| GET | `/api/wallets/{id}` | Bearer token | 200 | View wallet |
| POST | `/api/wallets/{id}/deposit` | Bearer token | 200 | Add money |
| POST | `/api/wallets/{id}/withdraw` | Bearer token | 200 | Withdraw money |

### Admin Endpoints

| Method | Endpoint | Header | Role | Status | Purpose |
|--------|----------|--------|------|--------|---------|
| GET | `/api/admin/wallets` | Bearer token | ADMIN | 200 | View all wallets |

---

## Error Handling

### HTTP Status Codes

| Code | Name | Meaning | Example |
|------|------|---------|---------|
| **200** | OK | Request successful | Wallet retrieved ✅ |
| **201** | Created | Resource created | User registered ✅ |
| **400** | Bad Request | Invalid input | Missing field ❌ |
| **401** | Unauthorized | No/invalid token | No Authorization header ❌ |
| **403** | Forbidden | Access denied | Not wallet owner ❌ |
| **404** | Not Found | Resource missing | Wallet/User not found ❌ |
| **409** | Conflict | Resource conflict | Email already exists ❌ |

### Error Response Format

```json
{
    "timestamp": "2024-01-15T10:30:45.123",
    "status": 409,
    "error": "Conflict",
    "message": "Email already registered: rahul@gmail.com"
}
```

### Common Errors

#### 1. Email Already Exists
```
Status: 409 Conflict
Cause: POST /api/users/register with existing email
Solution: Use different email
```

#### 2. Invalid Credentials
```
Status: 401 Unauthorized
Cause: POST /api/users/login with wrong password
Solution: Use correct password
```

#### 3. No Token
```
Status: 401 Unauthorized
Cause: GET /api/wallets/1 without Authorization header
Solution: Add "Authorization: Bearer <token>" header
```

#### 4. Invalid Token
```
Status: 401 Unauthorized
Cause: Expired token or wrong token format
Solution: Login again to get new token
```

#### 5. Not Wallet Owner
```
Status: 403 Forbidden
Cause: Accessing other user's wallet
Solution: Only access your own wallet (or be admin)
```

#### 6. Not Admin
```
Status: 403 Forbidden
Cause: CUSTOMER role accessing /api/admin/**
Solution: Only admins can access admin endpoints
```

#### 7. Insufficient Balance
```
Status: 409 Conflict
Cause: Trying to withdraw more than balance
Solution: Withdraw less amount
```

#### 8. User Not Found
```
Status: 404 Not Found
Cause: User doesn't exist in database
Solution: Check email/register if new user
```

---

## Testing Guide

### Test Data
```
Email: rahul@gmail.com
Password: password123
Role: CUSTOMER
Wallet ID: 1

Admin Email: admin@gmail.com
Password: password123
Role: ADMIN
```

### Test Scenarios

#### **Test 1: User Registration**
```
✅ Register with new email → 201 Created
❌ Register with existing email → 409 Conflict
❌ Register with missing field → 400 Bad Request
```

#### **Test 2: User Login**
```
✅ Login with correct credentials → 200 OK + tokens
❌ Login with wrong password → 401 Unauthorized
❌ Login with non-existent email → 404 Not Found
```

#### **Test 3: Get Own Wallet**
```
✅ Customer gets own wallet → 200 OK
✅ Admin gets any wallet → 200 OK
❌ No token → 401 Unauthorized
❌ Invalid token → 401 Unauthorized
```

#### **Test 4: Access Other's Wallet**
```
❌ Customer accesses other wallet → 403 Forbidden
✅ Admin accesses other wallet → 200 OK
```

#### **Test 5: Deposit Money**
```
✅ Deposit to own wallet → 200 OK, balance increases
✅ Admin deposits to any wallet → 200 OK
❌ Deposit to other's wallet → 403 Forbidden
```

#### **Test 6: Withdraw Money**
```
✅ Withdraw from own wallet (sufficient balance) → 200 OK
❌ Withdraw more than balance → 409 Conflict
❌ Withdraw from other's wallet → 403 Forbidden
```

#### **Test 7: Admin API**
```
✅ Admin views all wallets → 200 OK, list returned
❌ Customer views all wallets → 403 Forbidden
❌ No token for admin endpoint → 401 Unauthorized
```

---

## Files Modified

### New Files
```
JwtAuthenticationFilter.java
  - Validates JWT on every request
  - Sets user in SecurityContext
```

### Modified Files

#### UserController.java
```
Added:
  - POST /api/users/register endpoint
  - POST /api/users/login endpoint
  - toResponse() and toWalletSummary() methods
```

#### WalletController.java
```
Added:
  - GET /api/wallets/{walletId} endpoint
  - POST /api/wallets/{walletId}/deposit endpoint
  - POST /api/wallets/{walletId}/withdraw endpoint
  - verifyOwnership() method
```

#### AdminController.java
```
New:
  - GET /api/admin/wallets endpoint
  - getAllWallets() method
```

#### SecurityConfig.java
```
Modified:
  - Added JwtAuthenticationFilter field
  - Added @RequiredArgsConstructor
  - Registered filter: .addFilterBefore()
  - Added admin role check: .hasRole("ADMIN")
  - Added custom 403 handler
```

#### UserService.java
```
Modified:
  - Added register() method (creates user + wallet)
  - Added login() method (generates tokens)
  - Password hashing with BCrypt
```

#### WalletService.java
```
Modified:
  - Added getWallet() method
  - Added deposit() method
  - Added withdraw() method
  - Added getAllWallets() method (for admin)
  - Added balance validation
```

---

## JWT Token Flow Diagram

```
Registration/Login:
Client → Controller → Service → JwtTokenProvider → Response (tokens)

Protected Request:
Client + Token → JwtAuthenticationFilter → SecurityContext (email set)
                      ↓
            WalletController → verifyOwnership() → Service → DB → Response
```

---

## Phase 2 Completion Checklist ✅

- [x] User registration
- [x] User login with JWT
- [x] JWT token validation
- [x] Wallet get, deposit, withdraw
- [x] Ownership verification
- [x] Admin role support
- [x] Admin can view all wallets
- [x] Error handling
- [x] Security implementation
- [x] Role-based access control

---

## What's Next (Phase 3)

Phase 3 will implement:
- Transfer money between wallets
- Transaction history
- Refresh token endpoint
- Transaction status tracking
- Audit logging

---

## Postman Collection

Import the provided **DigitalWalletAPI_postman_collection.json** to test all endpoints.

Variables to configure:
- `baseUrl`: http://localhost:8080
- `accessToken`: (Auto-filled from login response)
- `walletId`: 1 (or any wallet ID)

---

**Phase 2 Documentation Complete!** ✅
