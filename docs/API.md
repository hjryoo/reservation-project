# 콘서트 예약 서비스 API 명세서

## 개요
콘서트 예약 서비스의 REST API 명세서입니다. 대기열 시스템을 통해 안정적인 예약 서비스를 제공하며, 모든 API는 대기열 토큰 검증을 통과해야 사용 가능합니다.

## 공통 사항

### Base URL
```
https://api.concert-reservation.com/api/v1
```

### 공통 헤더
```
Content-Type: application/json
Authorization: Bearer {queue_token}
```

### 공통 응답 형식
```json
{
  "success": true,
  "data": {},
  "message": "성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

### 공통 에러 응답
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지",
    "details": "상세 에러 정보"
  },
  "timestamp": "2025-08-17T15:30:00Z"
}
```

### HTTP 상태 코드
- `200 OK`: 성공
- `201 Created`: 생성 성공
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 충돌 (중복 예약 등)
- `429 Too Many Requests`: 요청 제한 초과
- `500 Internal Server Error`: 서버 에러

## API 목록

### 1. 유저 대기열 토큰 발급 API

#### 1.1 대기열 토큰 발급
**요청**
```http
POST /queue/token
```

**Request Body**
```json
{
  "userId": "user123"
}
```

**Response (성공)**
```json
{
  "success": true,
  "data": {
    "queueToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "user123",
    "queuePosition": 150,
    "estimatedWaitTime": 900,
    "status": "WAITING",
    "expiresAt": "2025-08-17T16:30:00Z"
  },
  "message": "대기열 토큰이 발급되었습니다.",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**Error Cases**
- `400 Bad Request`: 필수 파라미터 누락
- `429 Too Many Requests`: 대기열 인원 초과

#### 1.2 대기열 상태 조회 (폴링)
**요청**
```http
GET /queue/status
```

**Headers**
```
Authorization: Bearer {queue_token}
```

**Response (대기 중)**
```json
{
  "success": true,
  "data": {
    "queuePosition": 45,
    "estimatedWaitTime": 270,
    "status": "WAITING",
    "totalWaiting": 1000,
    "activeUsers": 100
  },
  "message": "대기열 상태 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**Response (활성화)**
```json
{
  "success": true,
  "data": {
    "queuePosition": 0,
    "estimatedWaitTime": 0,
    "status": "ACTIVE",
    "activeUntil": "2025-08-17T16:00:00Z"
  },
  "message": "서비스 이용 가능",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**Error Cases**
- `401 Unauthorized`: 유효하지 않은 토큰
- `410 Gone`: 만료된 토큰

### 2. 예약 가능 날짜 / 좌석 API

#### 2.1 예약 가능 날짜 조회
**요청**
```http
GET /concerts/available-dates
```

**Query Parameters**
- `month` (optional): 조회할 월 (YYYY-MM)
- `limit` (optional): 조회 개수 (기본값: 30)

**Headers**
```
Authorization: Bearer {queue_token}
```

**Response**
```json
{
  "success": true,
  "data": {
    "availableDates": [
      {
        "date": "2025-08-20",
        "concertId": "concert_001",
        "title": "2025 Summer Concert",
        "venue": "올림픽체조경기장",
        "startTime": "19:00",
        "endTime": "21:30",
        "totalSeats": 50,
        "availableSeats": 25,
        "price": 150000
      },
      {
        "date": "2025-08-21",
        "concertId": "concert_002",
        "title": "Classic Night",
        "venue": "세종문화회관",
        "startTime": "19:30",
        "endTime": "22:00",
        "totalSeats": 50,
        "availableSeats": 50,
        "price": 120000
      }
    ]
  },
  "message": "예약 가능 날짜 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**Error Cases**
- `401 Unauthorized`: 유효하지 않은 대기열 토큰
- `403 Forbidden`: 대기열 순서가 아직 활성화되지 않음

#### 2.2 좌석 정보 조회
**요청**
```http
GET /concerts/{concertId}/seats
```

**Path Parameters**
- `concertId`: 콘서트 ID

**Query Parameters**
- `date`: 콘서트 날짜 (YYYY-MM-DD)

**Headers**
```
Authorization: Bearer {queue_token}
```

**Response**
```json
{
  "success": true,
  "data": {
    "concertInfo": {
      "concertId": "concert_001",
      "title": "2025 Summer Concert",
      "date": "2025-08-20",
      "venue": "올림픽체조경기장",
      "startTime": "19:00",
      "price": 150000
    },
    "seats": [
      {
        "seatNumber": 1,
        "status": "AVAILABLE",
        "position": "A1"
      },
      {
        "seatNumber": 2,
        "status": "RESERVED",
        "position": "A2",
        "reservedUntil": "2025-08-17T15:35:00Z"
      },
      {
        "seatNumber": 3,
        "status": "SOLD",
        "position": "A3"
      }
    ],
    "summary": {
      "totalSeats": 50,
      "availableSeats": 25,
      "reservedSeats": 15,
      "soldSeats": 10
    }
  },
  "message": "좌석 정보 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**좌석 상태 코드**
- `AVAILABLE`: 예약 가능
- `RESERVED`: 임시 예약 (5분간)
- `SOLD`: 결제 완료

**Error Cases**
- `401 Unauthorized`: 유효하지 않은 대기열 토큰
- `403 Forbidden`: 대기열 비활성화 상태
- `404 Not Found`: 존재하지 않는 콘서트

### 3. 좌석 예약 요청 API

#### 3.1 좌석 예약
**요청**
```http
POST /concerts/{concertId}/reservations
```

**Path Parameters**
- `concertId`: 콘서트 ID

**Headers**
```
Authorization: Bearer {queue_token}
```

**Request Body**
```json
{
  "date": "2025-08-20",
  "seatNumber": 1,
  "userId": "user123"
}
```

**Response (성공)**
```json
{
  "success": true,
  "data": {
    "reservationId": "reservation_001",
    "concertId": "concert_001",
    "seatNumber": 1,
    "userId": "user123",
    "status": "RESERVED",
    "reservedAt": "2025-08-17T15:30:00Z",
    "expiresAt": "2025-08-17T15:35:00Z",
    "price": 150000,
    "concertInfo": {
      "title": "2025 Summer Concert",
      "date": "2025-08-20",
      "venue": "올림픽체조경기장",
      "startTime": "19:00"
    }
  },
  "message": "좌석 예약이 완료되었습니다. 5분 내에 결제를 완료해주세요.",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**Error Cases**
- `400 Bad Request`: 필수 파라미터 누락 또는 잘못된 형식
- `401 Unauthorized`: 유효하지 않은 대기열 토큰
- `403 Forbidden`: 대기열 비활성화 상태
- `404 Not Found`: 존재하지 않는 콘서트 또는 좌석
- `409 Conflict`: 이미 예약된 좌석

#### 3.2 예약 상태 조회
**요청**
```http
GET /reservations/{reservationId}
```

**Headers**
```
Authorization: Bearer {queue_token}
```

**Response**
```json
{
  "success": true,
  "data": {
    "reservationId": "reservation_001",
    "status": "RESERVED",
    "expiresAt": "2025-08-17T15:35:00Z",
    "remainingTime": 240,
    "concertInfo": {
      "title": "2025 Summer Concert",
      "date": "2025-08-20",
      "seatNumber": 1,
      "price": 150000
    }
  },
  "message": "예약 상태 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

### 4. 잔액 충전 / 조회 API

#### 4.1 잔액 조회
**요청**
```http
GET /users/{userId}/balance
```

**Headers**
```
Authorization: Bearer {queue_token}
```

**Response**
```json
{
  "success": true,
  "data": {
    "userId": "user123",
    "balance": 500000,
    "currency": "KRW",
    "lastUpdated": "2025-08-17T14:30:00Z"
  },
  "message": "잔액 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**Error Cases**
- `401 Unauthorized`: 유효하지 않은 대기열 토큰
- `403 Forbidden`: 권한 없음 (다른 사용자 정보 조회)
- `404 Not Found`: 존재하지 않는 사용자

#### 4.2 잔액 충전
**요청**
```http
POST /users/{userId}/balance/charge
```

**Headers**
```
Authorization: Bearer {queue_token}
```

**Request Body**
```json
{
  "amount": 100000,
  "paymentMethod": "CREDIT_CARD",
  "paymentInfo": {
    "cardNumber": "****-****-****-1234",
    "cardType": "VISA"
  }
}
```

**Response**
```json
{
  "success": true,
  "data": {
    "chargeId": "charge_001",
    "userId": "user123",
    "amount": 100000,
    "previousBalance": 500000,
    "newBalance": 600000,
    "paymentMethod": "CREDIT_CARD",
    "chargedAt": "2025-08-17T15:30:00Z",
    "transactionId": "txn_12345"
  },
  "message": "잔액 충전이 완료되었습니다.",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**Error Cases**
- `400 Bad Request`: 잘못된 충전 금액 (최소: 1,000원, 최대: 1,000,000원)
- `401 Unauthorized`: 유효하지 않은 대기열 토큰
- `403 Forbidden`: 권한 없음
- `422 Unprocessable Entity`: 결제 처리 실패

#### 4.3 충전 내역 조회
**요청**
```http
GET /users/{userId}/balance/history
```

**Query Parameters**
- `page` (optional): 페이지 번호 (기본값: 1)
- `size` (optional): 페이지 크기 (기본값: 20)
- `type` (optional): 거래 유형 (CHARGE, PAYMENT)

**Response**
```json
{
  "success": true,
  "data": {
    "transactions": [
      {
        "transactionId": "txn_12345",
        "type": "CHARGE",
        "amount": 100000,
        "balance": 600000,
        "description": "카드 충전",
        "createdAt": "2025-08-17T15:30:00Z"
      },
      {
        "transactionId": "txn_12344",
        "type": "PAYMENT",
        "amount": -150000,
        "balance": 500000,
        "description": "콘서트 티켓 결제",
        "createdAt": "2025-08-17T14:30:00Z"
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "totalElements": 50,
      "totalPages": 3
    }
  },
  "message": "거래 내역 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

### 5. 결제 API

#### 5.1 결제 처리
**요청**
```http
POST /payments
```

**Headers**
```
Authorization: Bearer {queue_token}
```

**Request Body**
```json
{
  "reservationId": "reservation_001",
  "userId": "user123",
  "amount": 150000
}
```

**Response (성공)**
```json
{
  "success": true,
  "data": {
    "paymentId": "payment_001",
    "reservationId": "reservation_001",
    "userId": "user123",
    "amount": 150000,
    "status": "COMPLETED",
    "paidAt": "2025-08-17T15:30:00Z",
    "ticketInfo": {
      "ticketId": "ticket_001",
      "concertTitle": "2025 Summer Concert",
      "date": "2025-08-20",
      "seatNumber": 1,
      "venue": "올림픽체조경기장",
      "startTime": "19:00"
    },
    "balanceInfo": {
      "previousBalance": 600000,
      "newBalance": 450000
    }
  },
  "message": "결제가 완료되었습니다. 대기열 토큰이 만료됩니다.",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

**Error Cases**
- `400 Bad Request`: 필수 파라미터 누락
- `401 Unauthorized`: 유효하지 않은 대기열 토큰
- `403 Forbidden`: 권한 없음 또는 대기열 비활성화
- `404 Not Found`: 존재하지 않는 예약
- `409 Conflict`: 예약 만료 또는 이미 결제 완료
- `422 Unprocessable Entity`: 잔액 부족

#### 5.2 결제 내역 조회
**요청**
```http
GET /payments/{paymentId}
```

**Headers**
```
Authorization: Bearer {queue_token}
```

**Response**
```json
{
  "success": true,
  "data": {
    "paymentId": "payment_001",
    "reservationId": "reservation_001",
    "amount": 150000,
    "status": "COMPLETED",
    "paidAt": "2025-08-17T15:30:00Z",
    "ticketInfo": {
      "ticketId": "ticket_001",
      "concertTitle": "2025 Summer Concert",
      "date": "2025-08-20",
      "seatNumber": 1,
      "venue": "올림픽체조경기장"
    }
  },
  "message": "결제 내역 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

## 심화 기능 API

### 6. 대기열 관리 고도화 API

#### 6.1 대기열 설정 조회 (관리자)
**요청**
```http
GET /admin/queue/config
```

**Headers**
```
Authorization: Bearer {admin_token}
```

**Response**
```json
{
  "success": true,
  "data": {
    "maxActiveUsers": 100,
    "maxWaitingUsers": 10000,
    "sessionDuration": 1800,
    "batchProcessInterval": 30,
    "autoScaling": {
      "enabled": true,
      "minActiveUsers": 50,
      "maxActiveUsers": 500,
      "scalingFactor": 1.2
    }
  },
  "message": "대기열 설정 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

#### 6.2 대기열 설정 변경 (관리자)
**요청**
```http
PUT /admin/queue/config
```

**Request Body**
```json
{
  "maxActiveUsers": 150,
  "sessionDuration": 1800,
  "autoScaling": {
    "enabled": true,
    "scalingFactor": 1.5
  }
}
```

#### 6.3 실시간 대기열 현황 (관리자)
**요청**
```http
GET /admin/queue/stats
```

**Response**
```json
{
  "success": true,
  "data": {
    "currentStats": {
      "activeUsers": 85,
      "waitingUsers": 1234,
      "totalRequestsToday": 50000,
      "averageWaitTime": 450
    },
    "systemLoad": {
      "cpuUsage": 65.5,
      "memoryUsage": 78.2,
      "dbConnections": 45
    },
    "recommendations": {
      "suggestedMaxUsers": 120,
      "reasoning": "높은 시스템 부하로 인한 조정 권장"
    }
  },
  "message": "대기열 현황 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

#### 6.4 동적 대기열 조정
**요청**
```http
POST /admin/queue/scale
```

**Request Body**
```json
{
  "action": "SCALE_UP",
  "targetActiveUsers": 150,
  "reason": "높은 수요로 인한 확장"
}
```

### 7. 고급 예약 관리 API

#### 7.1 좌석 잠금 해제 (관리자)
**요청**
```http
DELETE /admin/reservations/{reservationId}/lock
```

#### 7.2 대량 좌석 상태 변경 (관리자)
**요청**
```http
PUT /admin/concerts/{concertId}/seats/bulk-update
```

**Request Body**
```json
{
  "seats": [1, 2, 3, 4, 5],
  "action": "RELEASE",
  "reason": "시스템 오류로 인한 복구"
}
```

#### 7.3 예약 통계 조회
**요청**
```http
GET /admin/reservations/stats
```

**Query Parameters**
- `startDate`: 시작 날짜
- `endDate`: 종료 날짜
- `concertId` (optional): 특정 콘서트

**Response**
```json
{
  "success": true,
  "data": {
    "period": {
      "startDate": "2025-08-01",
      "endDate": "2025-08-17"
    },
    "summary": {
      "totalReservations": 1500,
      "completedPayments": 1350,
      "expiredReservations": 150,
      "conversionRate": 90.0
    },
    "hourlyStats": [
      {
        "hour": "14:00",
        "reservations": 45,
        "payments": 42
      }
    ]
  },
  "message": "예약 통계 조회 성공",
  "timestamp": "2025-08-17T15:30:00Z"
}
```

## 웹소켓 API (실시간 알림)

### 연결
```
ws://api.concert-reservation.com/ws/queue
```

### 구독 메시지
```json
{
  "action": "SUBSCRIBE",
  "queueToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 서버 푸시 메시지 예시
```json
{
  "type": "QUEUE_UPDATE",
  "data": {
    "queuePosition": 23,
    "estimatedWaitTime": 138,
    "status": "WAITING"
  },
  "timestamp": "2025-08-17T15:30:00Z"
}
```

```json
{
  "type": "QUEUE_ACTIVATED",
  "data": {
    "status": "ACTIVE",
    "activeUntil": "2025-08-17T16:00:00Z"
  },
  "timestamp": "2025-08-17T15:30:00Z"
}
```

## Rate Limiting

### 일반 사용자
- 토큰 발급: 1회/분
- 상태 조회: 20회/분
- 예약 요청: 5회/분
- 기타 API: 60회/분

### 관리자
- 관리 API: 100회/분

## 보안 고려사항

### 토큰 보안
- JWT 토큰 사용
- 5분마다 토큰 갱신 권장
- 토큰 탈취 방지를 위한 IP 검증

### API 보안
- HTTPS 필수
- CORS 정책 적용
- SQL Injection 방지
- Rate Limiting 적용

### 데이터 보안
- 개인정보 암호화 저장
- 결제 정보 PCI DSS 준수
- 접근 로그 기록

## 모니터링 및 로깅

### 주요 메트릭
- API 응답 시간
- 에러율
- 대기열 처리량
- 동시 접속자 수

### 로그 레벨
- `INFO`: 정상 처리
- `WARN`: 주의 필요
- `ERROR`: 오류 발생
- `CRITICAL`: 즉시 대응 필요