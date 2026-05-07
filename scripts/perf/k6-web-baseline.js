import encoding from 'k6/encoding';
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8088';
const BASIC_USER = __ENV.BASIC_USER || __ENV.LOGIN_USERNAME || '';
const BASIC_PASSWORD = __ENV.BASIC_PASSWORD || __ENV.LOGIN_PASSWORD || '';
const LOGIN_TENANT_ID = __ENV.LOGIN_TENANT_ID || '';

const JDS_VUS = Number(__ENV.JDS_VUS || 5);
const JD_DETAIL_VUS = Number(__ENV.JD_DETAIL_VUS || 3);
const CANDIDATES_VUS = Number(__ENV.CANDIDATES_VUS || 5);
const CANDIDATE_DETAIL_VUS = Number(__ENV.CANDIDATE_DETAIL_VUS || 3);
const AUDIT_LOG_VUS = Number(__ENV.AUDIT_LOG_VUS || 3);
const RESUME_HEALTH_VUS = Number(__ENV.RESUME_HEALTH_VUS || 3);
const SEARCH_MISS_VUS = Number(__ENV.SEARCH_MISS_VUS || 3);
const SEARCH_HIT_VUS = Number(__ENV.SEARCH_HIT_VUS || 3);

const JDS_DURATION = __ENV.JDS_DURATION || '30s';
const JD_DETAIL_DURATION = __ENV.JD_DETAIL_DURATION || '30s';
const CANDIDATES_DURATION = __ENV.CANDIDATES_DURATION || '30s';
const CANDIDATE_DETAIL_DURATION = __ENV.CANDIDATE_DETAIL_DURATION || '30s';
const AUDIT_LOG_DURATION = __ENV.AUDIT_LOG_DURATION || '30s';
const RESUME_HEALTH_DURATION = __ENV.RESUME_HEALTH_DURATION || '30s';
const SEARCH_MISS_DURATION = __ENV.SEARCH_MISS_DURATION || '30s';
const SEARCH_HIT_DURATION = __ENV.SEARCH_HIT_DURATION || '30s';

const JDS_DURATION_METRIC = new Trend('jds_duration', true);
const JD_DETAIL_DURATION_METRIC = new Trend('jd_detail_duration', true);
const CANDIDATES_DURATION_METRIC = new Trend('candidates_duration', true);
const CANDIDATE_VECTOR_STATUS_DURATION_METRIC = new Trend('candidate_vector_status_duration', true);
const AUDIT_LOG_DURATION_METRIC = new Trend('audit_log_duration', true);
const RESUME_HEALTH_DURATION_METRIC = new Trend('resume_health_duration', true);
const SEARCH_MISS_DURATION_METRIC = new Trend('search_miss_duration', true);
const SEARCH_HIT_DURATION_METRIC = new Trend('search_hit_duration', true);

const JDS_REQUESTS = new Counter('jds_requests');
const JD_DETAIL_REQUESTS = new Counter('jd_detail_requests');
const CANDIDATES_REQUESTS = new Counter('candidates_requests');
const CANDIDATE_VECTOR_STATUS_REQUESTS = new Counter('candidate_vector_status_requests');
const AUDIT_LOG_REQUESTS = new Counter('audit_log_requests');
const RESUME_HEALTH_REQUESTS = new Counter('resume_health_requests');
const SEARCH_MISS_REQUESTS = new Counter('search_miss_requests');
const SEARCH_HIT_REQUESTS = new Counter('search_hit_requests');

const JDS_FAILURES = new Rate('jds_failures');
const JD_DETAIL_FAILURES = new Rate('jd_detail_failures');
const CANDIDATES_FAILURES = new Rate('candidates_failures');
const CANDIDATE_VECTOR_STATUS_FAILURES = new Rate('candidate_vector_status_failures');
const AUDIT_LOG_FAILURES = new Rate('audit_log_failures');
const RESUME_HEALTH_FAILURES = new Rate('resume_health_failures');
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
  if (JD_DETAIL_VUS > 0) {
    scenarios.jd_detail_read = {
      executor: 'constant-vus',
      exec: 'jdDetailRead',
      vus: JD_DETAIL_VUS,
      duration: JD_DETAIL_DURATION,
      startTime: '2s',
    };
  }
  if (CANDIDATES_VUS > 0) {
    scenarios.candidates_read = {
      executor: 'constant-vus',
      exec: 'candidatesRead',
      vus: CANDIDATES_VUS,
      duration: CANDIDATES_DURATION,
      startTime: '4s',
    };
  }
  if (CANDIDATE_DETAIL_VUS > 0) {
    scenarios.candidate_vector_status_read = {
      executor: 'constant-vus',
      exec: 'candidateVectorStatusRead',
      vus: CANDIDATE_DETAIL_VUS,
      duration: CANDIDATE_DETAIL_DURATION,
      startTime: '6s',
    };
  }
  if (AUDIT_LOG_VUS > 0) {
    scenarios.audit_logs_read = {
      executor: 'constant-vus',
      exec: 'auditLogsRead',
      vus: AUDIT_LOG_VUS,
      duration: AUDIT_LOG_DURATION,
      startTime: '8s',
    };
  }
  if (RESUME_HEALTH_VUS > 0) {
    scenarios.resume_health_read = {
      executor: 'constant-vus',
      exec: 'resumeHealthRead',
      vus: RESUME_HEALTH_VUS,
      duration: RESUME_HEALTH_DURATION,
      startTime: '10s',
    };
  }
  if (SEARCH_MISS_VUS > 0) {
    scenarios.search_miss = {
      executor: 'constant-vus',
      exec: 'searchMiss',
      vus: SEARCH_MISS_VUS,
      duration: SEARCH_MISS_DURATION,
      startTime: '12s',
    };
  }
  if (SEARCH_HIT_VUS > 0) {
    scenarios.search_hit = {
      executor: 'constant-vus',
      exec: 'searchHit',
      vus: SEARCH_HIT_VUS,
      duration: SEARCH_HIT_DURATION,
      startTime: '14s',
    };
  }
  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  setupTimeout: __ENV.SETUP_TIMEOUT || '10m',
  thresholds: {
    checks: ['rate>0.99'],
    jds_duration: ['p(95)<100', 'p(99)<200'],
    jd_detail_duration: ['p(95)<200', 'p(99)<500'],
    candidates_duration: ['p(95)<500', 'p(99)<1000'],
    candidate_vector_status_duration: ['p(95)<400', 'p(99)<800'],
    audit_log_duration: ['p(95)<200', 'p(99)<500'],
    resume_health_duration: ['p(95)<100', 'p(99)<200'],
    search_miss_duration: ['p(95)<3000', 'p(99)<10000'],
    search_hit_duration: ['p(95)<100', 'p(99)<300'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

function basicAuthHeaders() {
  if ((BASIC_USER === '' && BASIC_PASSWORD !== '') || (BASIC_USER !== '' && BASIC_PASSWORD === '')) {
    fail('BASIC_USER and BASIC_PASSWORD must both be set or both be empty.');
  }

  if (BASIC_USER === '' && BASIC_PASSWORD === '') {
    return {
      'Content-Type': 'application/json',
    };
  }

  return {
    Authorization: `Basic ${encoding.b64encode(`${BASIC_USER}:${BASIC_PASSWORD}`)}`,
    'Content-Type': 'application/json',
  };
}

function authHeaders(auth) {
  const headers = basicAuthHeaders();
  if (auth && auth.accessToken) {
    headers.Cookie = `CVECT_ACCESS_TOKEN=${auth.accessToken}`;
  }
  return headers;
}

function getJson(url, tags, auth) {
  return http.get(url, {
    headers: authHeaders(auth),
    tags,
  });
}

function postJson(url, body, tags, auth) {
  return http.post(url, JSON.stringify(body), {
    headers: authHeaders(auth),
    tags,
  });
}

function login() {
  if (!BASIC_USER || !BASIC_PASSWORD) {
    fail('BASIC_USER and BASIC_PASSWORD are required for authenticated performance runs.');
  }

  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({
      tenantId: LOGIN_TENANT_ID,
      username: BASIC_USER,
      password: BASIC_PASSWORD,
    }),
    {
      headers: basicAuthHeaders(),
      tags: { endpoint: 'auth_login', phase: 'setup' },
    },
  );

  const ok = check(response, {
    ['login status is 200']: (res) => res.status === 200,
  });
  if (!ok) {
    fail(`login failed with status=${response.status} body=${String(response.body).slice(0, 200)}`);
  }

  const data = response.json();
  if (!data || !data.accessToken) {
    fail('login response did not include accessToken');
  }

  return { accessToken: data.accessToken, user: data.user };
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
  const auth = login();

  const jdsResponse = getJson(`${BASE_URL}/api/jds`, { endpoint: 'jds', phase: 'setup' }, auth);
  expectOk(jdsResponse, 'setup_jds');
  const jds = jdsResponse.json();
  if (!Array.isArray(jds) || jds.length === 0) {
    fail('No JD data found; cannot run performance baseline.');
  }

  const jd = jds
    .slice()
    .sort((left, right) => (right.candidateCount || 0) - (left.candidateCount || 0))[0];

  const detailResponse = getJson(`${BASE_URL}/api/jds/${jd.id}`, { endpoint: 'jd_detail', phase: 'setup' }, auth);
  expectOk(detailResponse, 'setup_jd_detail');
  const detail = detailResponse.json();
  const jobDescription = (detail && detail.content) || jd.content || jd.title || 'CVect performance probe';

  const candidatesResponse = getJson(`${BASE_URL}/api/candidates?jdId=${jd.id}`, {
    endpoint: 'candidates',
    phase: 'setup',
  }, auth);
  expectOk(candidatesResponse, 'setup_candidates');
  const candidates = candidatesResponse.json();
  if (!Array.isArray(candidates) || candidates.length === 0) {
    fail('No candidate data found; cannot run performance baseline.');
  }
  const candidateIds = candidates
    .slice(0, 20)
    .map((candidate) => candidate.candidateId)
    .filter(Boolean);
  if (candidateIds.length === 0) {
    fail('No candidate IDs found; cannot run performance baseline.');
  }

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
  }, auth);
  expectOk(warmResponse, 'setup_search_warm');

  return {
    accessToken: auth.accessToken,
    jdId: jd.id,
    candidateId: candidateIds[0],
    candidateIds,
    jobDescription,
    warmPayload,
  };
}

export function jdsRead(data) {
  const response = getJson(`${BASE_URL}/api/jds`, { endpoint: 'jds' }, data);
  JDS_REQUESTS.add(1);
  JDS_DURATION_METRIC.add(response.timings.duration);
  JDS_FAILURES.add(response.status !== 200);
  expectOk(response, 'jds_read');
}

export function candidatesRead(data) {
  const response = getJson(`${BASE_URL}/api/candidates?jdId=${data.jdId}`, {
    endpoint: 'candidates',
  }, data);
  CANDIDATES_REQUESTS.add(1);
  CANDIDATES_DURATION_METRIC.add(response.timings.duration);
  CANDIDATES_FAILURES.add(response.status !== 200);
  expectOk(response, 'candidates_read');
}

export function jdDetailRead(data) {
  const response = getJson(`${BASE_URL}/api/jds/${data.jdId}`, {
    endpoint: 'jd_detail',
  }, data);
  JD_DETAIL_REQUESTS.add(1);
  JD_DETAIL_DURATION_METRIC.add(response.timings.duration);
  JD_DETAIL_FAILURES.add(response.status !== 200);
  expectOk(response, 'jd_detail_read');
}

export function candidateVectorStatusRead(data) {
  const query = data.candidateIds.map((candidateId) => `candidateId=${candidateId}`).join('&');
  const response = getJson(`${BASE_URL}/api/candidates/vector-status?${query}`, {
    endpoint: 'candidate_vector_status',
  }, data);
  CANDIDATE_VECTOR_STATUS_REQUESTS.add(1);
  CANDIDATE_VECTOR_STATUS_DURATION_METRIC.add(response.timings.duration);
  CANDIDATE_VECTOR_STATUS_FAILURES.add(response.status !== 200);
  expectOk(response, 'candidate_vector_status_read');
}

export function auditLogsRead(data) {
  const response = getJson(`${BASE_URL}/api/audit-logs?size=6`, {
    endpoint: 'audit_logs',
  }, data);
  AUDIT_LOG_REQUESTS.add(1);
  AUDIT_LOG_DURATION_METRIC.add(response.timings.duration);
  AUDIT_LOG_FAILURES.add(response.status !== 200);
  expectOk(response, 'audit_logs_read');
}

export function resumeHealthRead(data) {
  const response = getJson(`${BASE_URL}/api/resumes/health`, {
    endpoint: 'resume_health',
  }, data);
  RESUME_HEALTH_REQUESTS.add(1);
  RESUME_HEALTH_DURATION_METRIC.add(response.timings.duration);
  RESUME_HEALTH_FAILURES.add(response.status !== 200);
  expectOk(response, 'resume_health_read');
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
  }, data);
  SEARCH_MISS_REQUESTS.add(1);
  SEARCH_MISS_DURATION_METRIC.add(response.timings.duration);
  SEARCH_MISS_FAILURES.add(response.status !== 200);
  expectOk(response, 'search_miss');
}

export function searchHit(data) {
  const response = postJson(`${BASE_URL}/api/search`, data.warmPayload, {
    endpoint: 'search',
    mode: 'hit',
  }, data);
  SEARCH_HIT_REQUESTS.add(1);
  SEARCH_HIT_DURATION_METRIC.add(response.timings.duration);
  SEARCH_HIT_FAILURES.add(response.status !== 200);
  expectOk(response, 'search_hit');
}
