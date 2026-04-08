import encoding from 'k6/encoding';
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8088';
const BASIC_USER = __ENV.BASIC_USER || 'demo';
const BASIC_PASSWORD = __ENV.BASIC_PASSWORD || 'demo123';

const JDS_VUS = Number(__ENV.JDS_VUS || 5);
const CANDIDATES_VUS = Number(__ENV.CANDIDATES_VUS || 5);
const SEARCH_MISS_VUS = Number(__ENV.SEARCH_MISS_VUS || 3);
const SEARCH_HIT_VUS = Number(__ENV.SEARCH_HIT_VUS || 3);

const JDS_DURATION = __ENV.JDS_DURATION || '30s';
const CANDIDATES_DURATION = __ENV.CANDIDATES_DURATION || '30s';
const SEARCH_MISS_DURATION = __ENV.SEARCH_MISS_DURATION || '30s';
const SEARCH_HIT_DURATION = __ENV.SEARCH_HIT_DURATION || '30s';

const JDS_DURATION_METRIC = new Trend('jds_duration', true);
const CANDIDATES_DURATION_METRIC = new Trend('candidates_duration', true);
const SEARCH_MISS_DURATION_METRIC = new Trend('search_miss_duration', true);
const SEARCH_HIT_DURATION_METRIC = new Trend('search_hit_duration', true);

const JDS_REQUESTS = new Counter('jds_requests');
const CANDIDATES_REQUESTS = new Counter('candidates_requests');
const SEARCH_MISS_REQUESTS = new Counter('search_miss_requests');
const SEARCH_HIT_REQUESTS = new Counter('search_hit_requests');

const JDS_FAILURES = new Rate('jds_failures');
const CANDIDATES_FAILURES = new Rate('candidates_failures');
const SEARCH_MISS_FAILURES = new Rate('search_miss_failures');
const SEARCH_HIT_FAILURES = new Rate('search_hit_failures');

function buildScenarios() {
  const scenarios = {};
  if (JDS_VUS > 0) {
    scenarios.jds_read = {
      executor: 'constant-vus',
      exec: 'jdsRead',
      vus: JDS_VUS,
      duration: JDS_DURATION,
    };
  }
  if (CANDIDATES_VUS > 0) {
    scenarios.candidates_read = {
      executor: 'constant-vus',
      exec: 'candidatesRead',
      vus: CANDIDATES_VUS,
      duration: CANDIDATES_DURATION,
      startTime: '2s',
    };
  }
  if (SEARCH_MISS_VUS > 0) {
    scenarios.search_miss = {
      executor: 'constant-vus',
      exec: 'searchMiss',
      vus: SEARCH_MISS_VUS,
      duration: SEARCH_MISS_DURATION,
      startTime: '4s',
    };
  }
  if (SEARCH_HIT_VUS > 0) {
    scenarios.search_hit = {
      executor: 'constant-vus',
      exec: 'searchHit',
      vus: SEARCH_HIT_VUS,
      duration: SEARCH_HIT_DURATION,
      startTime: '6s',
    };
  }
  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: {
    checks: ['rate>0.99'],
    jds_duration: ['p(95)<100', 'p(99)<200'],
    candidates_duration: ['p(95)<500', 'p(99)<1000'],
    search_miss_duration: ['p(95)<3000', 'p(99)<10000'],
    search_hit_duration: ['p(95)<100', 'p(99)<300'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

function authHeaders() {
  return {
    Authorization: `Basic ${encoding.b64encode(`${BASIC_USER}:${BASIC_PASSWORD}`)}`,
    'Content-Type': 'application/json',
  };
}

function getJson(url, tags) {
  return http.get(url, {
    headers: authHeaders(),
    tags,
  });
}

function postJson(url, body, tags) {
  return http.post(url, JSON.stringify(body), {
    headers: authHeaders(),
    tags,
  });
}

function expectOk(response, label) {
  const ok = check(response, {
    [`${label} status is 200`]: (res) => res.status === 200,
  });
  if (!ok) {
    fail(`${label} failed with status=${response.status} body=${String(response.body).slice(0, 200)}`);
  }
}

export function setup() {
  const jdsResponse = getJson(`${BASE_URL}/api/jds`, { endpoint: 'jds', phase: 'setup' });
  expectOk(jdsResponse, 'setup_jds');
  const jds = jdsResponse.json();
  if (!Array.isArray(jds) || jds.length === 0) {
    fail('No JD data found; cannot run performance baseline.');
  }

  const jd = jds
    .slice()
    .sort((left, right) => (right.candidateCount || 0) - (left.candidateCount || 0))[0];

  const detailResponse = getJson(`${BASE_URL}/api/jds/${jd.id}`, { endpoint: 'jd_detail', phase: 'setup' });
  expectOk(detailResponse, 'setup_jd_detail');
  const detail = detailResponse.json();
  const jobDescription = (detail && detail.content) || jd.content || jd.title || 'CVect performance probe';

  const warmPayload = {
    jobDescription,
    topK: 20,
    filterByExperience: true,
    filterBySkill: true,
    experienceWeight: 0.5,
    skillWeight: 0.5,
    onlyVectorReadyCandidates: false,
  };

  const warmResponse = postJson(`${BASE_URL}/api/search`, warmPayload, {
    endpoint: 'search',
    phase: 'setup',
    mode: 'warm-hit',
  });
  expectOk(warmResponse, 'setup_search_warm');

  return {
    jdId: jd.id,
    jobDescription,
    warmPayload,
  };
}

export function jdsRead() {
  const response = getJson(`${BASE_URL}/api/jds`, { endpoint: 'jds' });
  JDS_REQUESTS.add(1);
  JDS_DURATION_METRIC.add(response.timings.duration);
  JDS_FAILURES.add(response.status !== 200);
  expectOk(response, 'jds_read');
}

export function candidatesRead(data) {
  const response = getJson(`${BASE_URL}/api/candidates?jdId=${data.jdId}`, {
    endpoint: 'candidates',
  });
  CANDIDATES_REQUESTS.add(1);
  CANDIDATES_DURATION_METRIC.add(response.timings.duration);
  CANDIDATES_FAILURES.add(response.status !== 200);
  expectOk(response, 'candidates_read');
}

export function searchMiss(data) {
  const payload = {
    jobDescription: `${data.jobDescription}\n# miss-probe-${__VU}-${__ITER}`,
    topK: data.warmPayload.topK,
    filterByExperience: data.warmPayload.filterByExperience,
    filterBySkill: data.warmPayload.filterBySkill,
    experienceWeight: data.warmPayload.experienceWeight,
    skillWeight: data.warmPayload.skillWeight,
    onlyVectorReadyCandidates: data.warmPayload.onlyVectorReadyCandidates,
  };
  const response = postJson(`${BASE_URL}/api/search`, payload, {
    endpoint: 'search',
    mode: 'miss',
  });
  SEARCH_MISS_REQUESTS.add(1);
  SEARCH_MISS_DURATION_METRIC.add(response.timings.duration);
  SEARCH_MISS_FAILURES.add(response.status !== 200);
  expectOk(response, 'search_miss');
}

export function searchHit(data) {
  const response = postJson(`${BASE_URL}/api/search`, data.warmPayload, {
    endpoint: 'search',
    mode: 'hit',
  });
  SEARCH_HIT_REQUESTS.add(1);
  SEARCH_HIT_DURATION_METRIC.add(response.timings.duration);
  SEARCH_HIT_FAILURES.add(response.status !== 200);
  expectOk(response, 'search_hit');
}
