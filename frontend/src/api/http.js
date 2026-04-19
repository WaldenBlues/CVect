const TOKEN_KEY = 'cvect.accessToken'

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

export const apiFetch = async (url, options = {}) => {
  const headers = new Headers(options.headers || {})
  const token = getAccessToken()
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(url, {
    ...options,
    headers,
    credentials: options.credentials || 'same-origin'
  })
  if (response.status === 401) {
    clearAccessToken()
    window.dispatchEvent(new CustomEvent('cvect-auth-expired'))
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
