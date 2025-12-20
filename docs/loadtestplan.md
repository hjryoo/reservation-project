부하 테스트 계획 및 콘서트 예약 시스템 검증 보고서
1. 개요 (Overview)

본 테스트는 분산락(Distributed Lock)을 통한 동시성 제어와 Kafka 기반의 비동기 이벤트 처리 구조가 고부하 상황에서도 데이터 정합성을 유지하며 안정적으로 동작하는지 검증하는 것을 목표로 한다.

특히, 일반적인 시스템 오류(500 Error)와 비즈니스 로직에 의한 거절(400/409 - 이미 예약된 좌석)을 명확히 구분하여 측정함으로써, 실제 유효 처리량(Effective Throughput) 과 동시성 제어의 정확성을 평가한다.
2. 테스트 환경 및 데이터 (Test Environment & Data)
   2.1 인프라 환경

실제 운영 환경과 유사한 기술 스택을 로컬 Docker 환경에 구성하여 테스트를 수행한다.

    Application Server: Spring Boot 3.4.1 (Java 17)

        JVM Options: -Xms512m -Xmx1024m (제한된 리소스 가정)

    Database: MySQL 8.0 (Docker)

        Connection Pool: HikariCP (Max: 20)

    Cache/Lock: Redis 7.2 (Docker) - Redisson 클라이언트 사용

    Message Queue: Apache Kafka 3.6 (Docker)

    Test Tool: k6 (Local Machine) with Custom Metrics

2.2 테스트 데이터 (Data Seeding)

스크립트 로직(TOTAL_SEATS = 1000)에 맞춰 고강도 경합을 유도하기 위해 데이터 범위를 제한적으로 설정한다.

    User: 10,000명 (userId: 1 ~ 10,000)

    Concert: 단일 콘서트 집중 테스트 (concertId: 1)

    Seat: 1,000석 (seatNumber: 1 ~ 1,000)

        전략: 좌석 수를 1,000개로 한정하고 다수의 유저가 무작위로 접근하게 하여 높은 충돌율(High Contention Rate) 을 의도적으로 발생시킴.

2.3 지연 시뮬레이션 (Latency Simulation)

    Kafka Consumer: 결제 완료 후 데이터 플랫폼 전송 로직에 500ms ~ 1,500ms의 인위적 지연을 주입.

    검증 목표: 백그라운드 작업의 지연이 메인 트랜잭션(결제 API 응답 속도)에 전파되지 않는지(Non-blocking) 검증.

3. 테스트 대상 및 시나리오 (Target & Scenarios)

k6 스크립트에 정의된 3가지 시나리오를 병렬 또는 개별적으로 수행한다.
3.1 대상 1: 콘서트 목록 조회 (Browsing Scenario)

트래픽의 진입점이자 가장 빈번하게 호출되는 단순 조회 API 성능을 측정한다.

    Endpoint: GET /concerts

    부하 패턴 (Ramping VUs):

        Stages: 0명 → 50명(30s) → 50명 유지(1m) → 0명(30s)

        Target VUs: 최대 50명

    검증 항목:

        단순 조회 시 응답 속도(latency < 200ms) 만족 여부.

3.2 대상 2: 좌석 예약 요청 (Reserving Scenario) - 핵심 테스트

수십 명의 유저가 한정된 1,000개의 좌석을 두고 경쟁하는 시나리오이다.

    Endpoint: POST /reservations

    부하 패턴 (Ramping VUs):

        Stages: 0명 → 50명(30s) → 50명 유지(1m) → 0명(30s)

        Target VUs: 최대 50명

    데이터 접근 패턴:

        1 ~ 1,000번 좌석 중 무작위 선택 (randomIntBetween)

    응답 처리 전략 (Custom Metrics):

        Success (200): successful_reservations 카운트 증가.

        Business Failure (400/409): "이미 예약된 좌석" 응답. 실패가 아닌 정상적인 경합 패배로 간주하여 business_failures 카운트 증가.

        System Error (500+): system_errors 카운트 증가 (테스트 실패 요인).

3.3 대상 3: 결제 요청 (Payment Scenario)

예약에 성공한 건에 대한 후속 처리 및 외부 연동 시뮬레이션이다.

    Endpoint: POST /payments

    부하 패턴 (Constant VUs):

        VUs: 5명 고정 (결제는 예약보다 트래픽이 적으므로 고정 부하로 설정)

        Duration: 2분

    검증 항목:

        Kafka Consumer 지연 환경에서도 HTTP 응답이 즉각적(p95 < 500ms)으로 오는지 확인.


4. 목표 성능 지표 (SLO - Service Level Objective)

| 지표 (Metric)        | 목표치 (Target / Threshold)           | 설명 (Description)                                                               |
| ------------------ | ---------------------------------- | ------------------------------------------------------------------------------ |
| System Reliability | System Errors < 1 (Zero Tolerance) | DB 커넥션 고갈, 데드락, NullPointer 등 500번대 서버 에러가 0건이어야 함.                            |
| Latency (p95)      | 500ms 이하                           | 모든 요청(성공 및 비즈니스 거절 포함)의 95%가 0.5초 이내에 응답해야 함.                                  |
| Concurrency        | Business Failures 허용               | 좌석 선점 실패(Sold Out)는 자연스러운 현상이므로 에러율에 포함하지 않음. 단, 중복 예약(Overbooking)은 발생하면 안 됨. |
| Throughput         | 시스템 한계 측정                          | 에러 없이 처리 가능한 최대 TPS를 확인하여 인프라 증설 기준 마련.                                        |