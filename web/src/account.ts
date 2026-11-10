import { useEffect, useState } from 'react'

export type Account = {
  id: string
  name: string
  email: string
  birthday: string
}

const TOKEN_KEY = 'food-assistant-token'
const REFRESH_KEY = 'food-assistant-refresh'
const EXPIRES_KEY = 'food-assistant-expires'

function readAuthHash(): 'recovery' | 'session' | null {
  const params = new URLSearchParams(window.location.hash.startsWith('#') ? window.location.hash.slice(1) : '')
  const access = params.get('access_token')
  const type = params.get('type')
  if (!access) return null
  if (type !== 'recovery' && type !== 'signup' && type !== 'magiclink' && type !== 'invite' && type !== 'email_change') {
    return null
  }
  storeSession(access, params.get('refresh_token'), Number(params.get('expires_in') || '3600'))
  window.history.replaceState(null, '', window.location.pathname + window.location.search)
  return type === 'recovery' ? 'recovery' : 'session'
}

let passwordRecovery = false
if (typeof window !== 'undefined') {
  passwordRecovery = readAuthHash() === 'recovery'
}

export function takePasswordRecovery() {
  const pending = passwordRecovery
  passwordRecovery = false
  return pending
}

function storeSession(token: string, refresh: string | null | undefined, expiresIn: number | undefined) {
  localStorage.setItem(TOKEN_KEY, token)
  if (refresh) localStorage.setItem(REFRESH_KEY, refresh)
  const seconds = expiresIn && expiresIn > 0 ? expiresIn : 3600
  localStorage.setItem(EXPIRES_KEY, String(Date.now() + seconds * 1000))
}

function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(EXPIRES_KEY)
}

type SessionPayload = {
  token?: string
  refreshToken?: string
  expiresIn?: number
  account?: Account
  error?: string
  message?: string
}

export function useAccount() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [account, setAccount] = useState<Account | null>(null)
  const [ready, setReady] = useState(() => localStorage.getItem(TOKEN_KEY) == null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return
    const gate = { ok: true }
    void loadAccount(token, gate)
    return () => {
      gate.ok = false
    }
  }, [token])

  async function loadAccount(access: string, gate: { ok: boolean }) {
    let current = access
    const expires = Number(localStorage.getItem(EXPIRES_KEY) || '0')
    if (expires > 0 && expires < Date.now() + 15000) {
      const refreshed = await refreshTokens()
      if (!gate.ok) return
      if (!refreshed) {
        clearSession()
        setToken(null)
        setAccount(null)
        setReady(true)
        return
      }
      current = refreshed
      if (refreshed !== access) {
        setToken(refreshed)
        return
      }
    }
    try {
      const response = await fetch('/api/accounts/me', { headers: { Authorization: `Bearer ${current}` } })
      const payload = (await response.json().catch(() => null)) as { account?: Account; error?: string } | null
      if (!gate.ok) return
      if (response.status === 401) {
        const refreshed = await refreshTokens()
        if (!gate.ok) return
        if (!refreshed || refreshed === current) {
          clearSession()
          setToken(null)
          setAccount(null)
        } else {
          setToken(refreshed)
        }
      } else if (!response.ok || !payload?.account) {
        setAccount(null)
        setError(payload?.error ?? 'The Java backend is not running.')
      } else {
        setAccount({ ...payload.account, birthday: payload.account.birthday ?? '' })
        setError(null)
      }
    } catch {
      if (!gate.ok) return
      setAccount(null)
      setError('The Java backend is not running.')
    }
    setReady(true)
  }

  async function refreshTokens(): Promise<string | null> {
    const refresh = localStorage.getItem(REFRESH_KEY)
    if (!refresh) return null
    try {
      const response = await fetch('/api/sessions/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: refresh }),
      })
      const payload = (await response.json().catch(() => null)) as SessionPayload | null
      if (!response.ok || !payload?.token) return null
      storeSession(payload.token, payload.refreshToken, payload.expiresIn)
      if (payload.account) {
        setAccount({ ...payload.account, birthday: payload.account.birthday ?? '' })
      }
      return payload.token
    } catch {
      return null
    }
  }

  async function saveAccount(path: string, method: string, body: unknown): Promise<string | null> {
    if (!token) return 'Sign in first.'
    try {
      const response = await fetch(path, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body),
      })
      const payload = (await response.json().catch(() => null)) as { account?: Account; error?: string } | null
      if (response.status === 401) {
        clearSession()
        setToken(null)
        setAccount(null)
        return payload?.error ?? 'Sign in first.'
      }
      if (!response.ok || !payload?.account) {
        return payload?.error ?? 'The Java backend is not running.'
      }
      setAccount({ ...payload.account, birthday: payload.account.birthday ?? '' })
      setError(null)
      return null
    } catch {
      setError('The Java backend is not running.')
      return 'The Java backend is not running.'
    }
  }

  async function establish(path: string, body: unknown): Promise<string | null> {
    try {
      const response = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const payload = (await response.json().catch(() => null)) as SessionPayload | null
      if (response.ok && payload?.message && !payload.token) {
        setError(null)
        return payload.message
      }
      if (!response.ok || !payload?.token || !payload.account) {
        const message = payload?.error ?? payload?.message ?? 'The Java backend is not running.'
        setError(message === 'The Java backend is not running.' ? message : null)
        return message
      }
      storeSession(payload.token, payload.refreshToken, payload.expiresIn)
      setToken(payload.token)
      setAccount({ ...payload.account, birthday: payload.account.birthday ?? '' })
      setError(null)
      return null
    } catch {
      setError('The Java backend is not running.')
      return 'The Java backend is not running.'
    }
  }

  return {
    token,
    account,
    ready,
    error,
    signIn(email: string, password: string) {
      return establish('/api/sessions', { email, password })
    },
    register(name: string, email: string, password: string) {
      return establish('/api/accounts', { name, email, password })
    },
    updateProfile(name: string, email: string, birthday: string) {
      return saveAccount('/api/accounts/me', 'PATCH', { name, email, birthday })
    },
    changePassword(currentPassword: string, password: string) {
      return saveAccount('/api/accounts/me/password', 'POST', { currentPassword, password })
    },
    resetPassword(password: string) {
      return saveAccount('/api/accounts/me/password', 'POST', { password })
    },
    async recover(email: string) {
      try {
        const response = await fetch('/api/accounts/recover', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email }),
        })
        const payload = (await response.json().catch(() => null)) as { error?: string } | null
        if (!response.ok) return payload?.error ?? 'Could not send the reset email.'
        return null
      } catch {
        return 'The Java backend is not running.'
      }
    },
    signOut() {
      const current = token
      clearSession()
      setToken(null)
      setAccount(null)
      setError(null)
      if (current) {
        void fetch('/api/sessions', {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${current}` },
        })
      }
    },
  }
}
