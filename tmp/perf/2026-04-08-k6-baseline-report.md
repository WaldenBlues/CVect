# CVect k6 Performance Report

Date: 2026-04-08
Environment: local Docker stack, frontend entry `http://127.0.0.1:8088`
Auth: basic auth `demo/demo123`
Data shape at test time:
- 2 JD records
- ~2021 candidates
- ~1011 candidates under the busiest JD

Scripts:
- `scripts/perf/k6-web-baseline.js`
- `scripts/perf/run-k6-web-baseline.sh`

## Scenario A: Mixed Web Baseline

Summary file: `tmp/perf/k6-mixed-baseline-summary.json`

Load shape:
- `GET /api/jds`: 5 VUs, 30s
- `GET /api/candidates?jdId=...`: 5 VUs, 30s
- `POST /api/search` cache hit: 3 VUs, 30s
- `POST /api/search` cache miss: 1 VU, 30s

Results:

| Endpoint | Requests | QPS | Avg | P50 | P95 | P99 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `GET /api/jds` | 23294 | 577.61 | 7.25ms | 5.59ms | 11.29ms | 16.50ms | 4967.44ms |
| `GET /api/candidates?jdId=...` | 433 | 10.74 | 393.60ms | 352.37ms | 411.56ms | 2264.53ms | 5358.14ms |
| `POST /api/search` cache hit | 24718 | 612.92 | 3.94ms | 2.42ms | 6.06ms | 10.69ms | 4962.95ms |
| `POST /api/search` cache miss | 5 | 0.124 | 7117.41ms | 6964.37ms | 9156.35ms | 9573.53ms | 9677.82ms |

Notes:
- Read APIs and cache-hit search are fast enough for interactive use.
- Candidate list has acceptable median latency, but tail latency spikes under mixed load are visible.
- Cache-miss search is the dominant bottleneck by a large margin.

## Scenario B: Isolated Cache-Miss Search

Summary file: `tmp/perf/k6-search-miss-isolated-summary.json`

Load shape:
- `POST /api/search` cache miss: 1 VU, 30s

Results:

| Endpoint | Requests | QPS | Avg | P50 | P95 | P99 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `POST /api/search` cache miss | 14 | 0.352 | 2503.62ms | 861.26ms | 5681.41ms | 6837.07ms | 7125.99ms |

Notes:
- Even without read traffic interference, uncached search still shows large variance.
- Median uncached search is under 1 second, but the slow tail grows to 5-7 seconds.
- This points to the search/embedding path itself, not only mixed-load contention.

## Bottom Line

- `JD` list and cached search are strong.
- Candidate list is acceptable now, but its P99 tail should be watched as data grows.
- Uncached semantic search is the current performance ceiling.
- If search UX matters most, the next optimization target is cache-miss search latency and its variance.
