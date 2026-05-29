import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  scenarios: {
    payment_load: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 600,
      stages: [
        { target: 50,  duration: '30s' },  // warm up
        { target: 100, duration: '30s' },  // ramp
        { target: 250, duration: '60s' },  // reach target
        { target: 250, duration: '3880s'}, // hold for ~1M txns
      ],
    },
  },
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<5000'],
  },
};

const BASE_URL = 'http://localhost:8080';
const USERS = ['user1', 'user2', 'user3', 'user4', 'user5'];

export default function () {
  // Generate unique transaction ID
  const transactionId = `TXN-${Date.now()}-${randomString(8)}`;

  // Select random sender and receiver
  const senderIndex = Math.floor(Math.random() * USERS.length);
  let receiverIndex = Math.floor(Math.random() * USERS.length);
  while (receiverIndex === senderIndex) {
    receiverIndex = Math.floor(Math.random() * USERS.length);
  }

  const sender = USERS[senderIndex];
  const receiver = USERS[receiverIndex];
  const amount = Math.floor(Math.random() * 500) + 10; // Random amount between 10-510

  // Initiate payment
  const paymentPayload = {
    transactionId: transactionId,
    senderId: sender,
    receiverId: receiver,
    amount: amount,
    currency: 'USD',
  };

  const paymentResponse = http.post(
    `${BASE_URL}/v1/payments`,
    JSON.stringify(paymentPayload),
    {
      headers: {
        'Content-Type': 'application/json',
        'timeout': '30s',
      },
    }
  );

  check(paymentResponse, {
    'Payment initiated successfully': (r) => r.status === 201,
    'Response contains transaction ID': (r) => r.body.includes(transactionId),
    'Response contains PENDING status': (r) => r.body != null && r.body.includes('PENDING'),
  });

  // Check payment status
  if (paymentResponse.status === 201) {
    sleep(0.1); // Small delay between operations

    const statusResponse = http.get(
      `${BASE_URL}/v1/payments/${transactionId}/status`
    );

    check(statusResponse, {
      'Get status successful': (r) => r.status === 200,
      'Status endpoint responds': (r) => r.body !== null && r.body !== undefined && r.body.length > 0,
    });
  }

  // Get transaction history
  //is load test required
  sleep(0.05);
  const historyResponse = http.get(
    `${BASE_URL}/v1/ledger/user/${sender}`
  );

  check(historyResponse, {
    'Get history successful': (r) => r.status === 200 || r.status === 404, // 404 if no history
    'History endpoint responds': (r) => r.body !== null && r.body !== undefined && r.body.length > 0,
  });

  sleep(0.1); // Delay between iterations
}

