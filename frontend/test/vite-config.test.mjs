import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

const loadViteConfig = async (proxyTarget) => {
  const previous = process.env.VITE_API_PROXY_TARGET
  if (proxyTarget === undefined) {
    delete process.env.VITE_API_PROXY_TARGET
  } else {
    process.env.VITE_API_PROXY_TARGET = proxyTarget
  }

  try {
    const configUrl = new URL('../vite.config.js', import.meta.url)
    configUrl.searchParams.set('t', `${Date.now()}-${Math.random()}`)
    const module = await import(configUrl.href)
    return module.default
  } finally {
    if (previous === undefined) {
      delete process.env.VITE_API_PROXY_TARGET
    } else {
      process.env.VITE_API_PROXY_TARGET = previous
    }
  }
}

describe('vite config', () => {
  it('should proxy api requests to the configured backend target', async () => {
    const config = await loadViteConfig('http://localhost:18080')

    assert.equal(config.server.proxy['/api'].target, 'http://localhost:18080')
    assert.equal(config.server.proxy['/api'].changeOrigin, true)
  })
})
