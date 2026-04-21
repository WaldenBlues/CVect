import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  apiFetch,
  clearAccessToken,
  getAccessTokenExpiresAt,
  isAccessTokenExpired,
  parseJwtPayload,
  setAccessToken
} from './http.js'

globalThis.window = {
  atob: globalThis.atob,
  dispatchEvent: () => true,
  location: {
    href: 'http://localhost:8088/',
    origin: 'http://localhost:8088'
  },
  localStorage: {
    store: new Map(),
    getItem(key) {
      return this.store.get(key) || null
    },
    setItem(key, value) {
      this.store.set(key, String(value))
    },
    removeItem(key) {
      this.store.delete(key)
    }
  }
}

const tokenWithPayload = (payload) => {
  const encode = (value) => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`
}

describe('auth token helpers', () => {
  it('parseJwtPayload should decode a JWT payload', () => {
    const token = tokenWithPayload({ username: 'demo', exp: 123 })

    assert.deepEqual(parseJwtPayload(token), {
      username: 'demo',
      exp: 123
    })
  })

  it('getAccessTokenExpiresAt should convert exp seconds to milliseconds', () => {
    const token = tokenWithPayload({ exp: 1_776_677_600 })

    assert.equal(getAccessTokenExpiresAt(token), 1_776_677_600_000)
  })

  it('isAccessTokenExpired should expire tokens before the next API request', () => {
    const token = tokenWithPayload({ exp: 10 })

    assert.equal(isAccessTokenExpired(token, 10_000), true)
    assert.equal(isAccessTokenExpired(token, 8_000), false)
  })

  it('apiFetch should not override same-origin Basic Auth with Bearer', async () => {
    const token = tokenWithPayload({ exp: Math.floor(Date.now() / 1000) + 60 })
    setAccessToken(token)
    let capturedHeaders
    const previousFetch = globalThis.fetch
    globalThis.fetch = async (_url, options) => {
      capturedHeaders = options.headers
      return new Response('{}', { status: 200 })
    }

    try {
      await apiFetch('/api/auth/me')
    } finally {
      globalThis.fetch = previousFetch
      clearAccessToken()
    }

    assert.equal(capturedHeaders.has('Authorization'), false)
  })

  it('apiFetch should keep Bearer for cross-origin API calls', async () => {
    const token = tokenWithPayload({ exp: Math.floor(Date.now() / 1000) + 60 })
    setAccessToken(token)
    let capturedHeaders
    const previousFetch = globalThis.fetch
    globalThis.fetch = async (_url, options) => {
      capturedHeaders = options.headers
      return new Response('{}', { status: 200 })
    }

    try {
      await apiFetch('http://backend:8080/api/auth/me')
    } finally {
      globalThis.fetch = previousFetch
      clearAccessToken()
    }

    assert.equal(capturedHeaders.get('Authorization'), `Bearer ${token}`)
  })
})
