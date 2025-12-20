import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

const successfulReservations = new Counter('successful_reservations');
const businessFailures = new Counter('business_failures');
const systemErrors = new Counter('system_errors');

export const options = {
    summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'max'],

    scenarios: {
        browsing: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 }, // Ramp up
                { duration: '1m', target: 50 },  // Stay
                { duration: '30s', target: 0 },  // Ramp down
            ],
            gracefulRampDown: '30s',
            exec: 'browsingScenario',
        },
        payment: {
            executor: 'constant-vus',
            vus: 5,
            duration: '2m',
            gracefulStop: '30s',
            exec: 'paymentScenario',
        },
        reserving: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '1m', target: 50 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'reservingScenario',
        },
    },

    thresholds: {
        'http_req_duration': ['p(95)<500'],

        'system_errors': ['count<1'],
    },
};

const BASE_URL = 'http://localhost:8080';
const CONCERT_ID = 1;
const TOTAL_SEATS = 1000;

// 헤더 생성 헬퍼 함수
function getHeaders(userId) {
    return {
        'Content-Type': 'application/json',
        'X-User-Id': userId.toString(), // 인증 헤더 예시
    };
}

export function browsingScenario() {
    const userId = randomIntBetween(1, 10000);

    // 1. 콘서트 목록 조회 (단순 조회)
    const res = http.get(`${BASE_URL}/concerts`, {
        headers: getHeaders(userId),
    });

    check(res, {
        'Browsing: status is 200': (r) => r.status === 200,
        'Browsing: latency < 200ms': (r) => r.timings.duration < 200,
    });

    // 500 에러 발생 시 시스템 에러 카운트
    if (res.status >= 500) systemErrors.add(1);

    sleep(1);
}

// 시나리오 B: 좌석 예약 (Reserving) - 가장 중요한 부하 테스트 구간
export function reservingScenario() {
    const userId = randomIntBetween(1, 10000);
    const seatNumber = randomIntBetween(1, TOTAL_SEATS);

    const payload = JSON.stringify({
        concertId: CONCERT_ID,
        seatNumber: seatNumber,
        userId: userId
    });

    const res = http.post(`${BASE_URL}/reservations`, payload, {
        headers: getHeaders(userId),
    });

    // 응답 상태에 따른 분기 처리 및 메트릭 기록
    if (res.status === 200) {
        // 성공
        successfulReservations.add(1);
    } else if (res.status === 400 || res.status === 409) {
        // 정상적인 비즈니스 실패 (이미 예약됨, 유효하지 않은 좌석 등)
        businessFailures.add(1);
    } else if (res.status >= 500) {
        // 시스템 장애 (DB 커넥션 풀 고갈, 타임아웃, NullPointer 등)
        systemErrors.add(1);
        console.error(`[Reserving Critical Error] Status: ${res.status}, Body: ${res.body}`);
    }

    // 체크 로직 개선: 200, 400, 409 모두 '정상 처리'로 간주
    check(res, {
        'Reserving: valid response (200/409/400)': (r) =>
            r.status === 200 || r.status === 409 || r.status === 400
    });

    sleep(1);
}

// 시나리오 C: 결제 (Payment)
export function paymentScenario() {
    const userId = randomIntBetween(1, 10000);
    const reservationId = randomIntBetween(1, 5000); // 가상의 예약 ID

    const payload = JSON.stringify({
        userId: userId,
        reservationId: reservationId,
        amount: 10000
    });

    const res = http.post(`${BASE_URL}/payments`, payload, {
        headers: getHeaders(userId),
    });

    // 결제도 실패할 수 있음 (이미 결제됨, 잔액 부족 등)
    check(res, {
        'Payment: handled correctly': (r) => r.status === 200 || r.status === 400
    });

    if (res.status >= 500) {
        systemErrors.add(1);
        console.error(`[Payment Error] Status: ${res.status}`);
    }

    sleep(1);
}