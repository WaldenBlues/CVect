const TOKEN_KEY = 'cvect.accessToken'
export const AUTH_EXPIRED_EVENT = 'cvect-auth-expired'

const AUTH_EXPIRY_SKEW_MS = 1000

export const getAccessToken = () => {
  try {
    return window.localStorage.getItem(TOKEN_KEY) || ''
  } catch (_) {
    return ''
  }
}

export const setAccessToken = (token) => {
  try {
    if (token) {
      window.localStorage.setItem(TOKEN_KEY, token)
    } else {
      window.localStorage.removeItem(TOKEN_KEY)
    }
  } catch (_) {
    // Ignore storage failures; the HttpOnly cookie still carries same-origin auth.
  }
}

export const clearAccessToken = () => {
  setAccessToken('')
}

const decodeBase64Url = (value) => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(normalized.length + ((4 - normalized.length % 4) % 4), '=')
  const binary = window.atob(padded)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

export const parseJwtPayload = (token) => {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) return null
  try {
    return JSON.parse(decodeBase64Url(parts[1]))
  } catch (_) {
    return null
  }
}

export const getAccessTokenExpiresAt = (token = getAccessToken()) => {
  const payload = parseJwtPayload(token)
  const exp = Number(payload?.exp)
  return Number.isFinite(exp) && exp > 0 ? exp * 1000 : 0
}

export const isAccessTokenExpired = (token = getAccessToken(), nowMs = Date.now()) => {
  if (!token) return false
  const expiresAt = getAccessTokenExpiresAt(token)
  return !expiresAt || expiresAt <= nowMs + AUTH_EXPIRY_SKEW_MS
}

const dispatchAuthExpired = () => {
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
}

const shouldAttachBearerToken = (url, token, headers) => {
  if (!token || headers.has('Authorization')) return false
  try {
    const location = window.location
    if (!location?.origin || !location?.href) return true
    const target = new URL(url, location.href)
    return target.origin !== location.origin
  } catch (_) {
    return false
  }
}

export const apiFetch = async (url, options = {}) => {
  const headers = new Headers(options.headers || {})
  const token = getAccessToken()
  if (isAccessTokenExpired(token)) {
    clearAccessToken()
    dispatchAuthExpired()
    return new Response(null, { status: 401, statusText: 'Unauthorized' })
  }
  if (shouldAttachBearerToken(url, token, headers)) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(url, {
    ...options,
    headers,
    credentials: options.credentials || 'same-origin'
  })
  if (response.status === 401) {
    clearAccessToken()
    dispatchAuthExpired()
  }
  return response
}

export const authLogin = async ({ tenantId, username, password }) => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify({ tenantId, username, password })
  })
  if (!response.ok) {
    throw new Error(response.status === 401 ? '用户名或密码错误' : '登录失败')
  }
  const data = await response.json()
  setAccessToken(data?.accessToken || '')
  return data?.user || null
}

export const authMe = async () => {
  const response = await apiFetch('/api/auth/me')
  if (!response.ok) {
    return null
  }
  return response.json()
}

export const authLogout = async () => {
  try {
    await apiFetch('/api/auth/logout', { method: 'POST' })
  } finally {
    clearAccessToken()
  }
}
