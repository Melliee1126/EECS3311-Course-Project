import { useEffect, useState } from 'react'

export type Account = {
  id: string
  name: string
  email: string
  birthday: string
}

const TOKEN_KEY = 'food-assistant-token'

export function useAccount() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [account, setAccount] = useState<Account | null>(null)
  const [ready, setReady] = useState(() => localStorage.getItem(TOKEN_KEY) == null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return
    const gate = { ok: true }
    fetch('/api/accounts/me', { headers: { Authorization: `Bearer ${token}` } })
      .then(async (response) => {
        const payload = (await response.json().catch(() => null)) as { account?: Account; error?: string } | null
        if (!gate.ok) return
        if (response.status === 401 || !payload?.account) {
          localStorage.removeItem(TOKEN_KEY)
          setToken(null)
          setAccount(null)
          setError(null)
        } else {
          setAccount({ ...payload.account, birthday: payload.account.birthday ?? '' })
          setError(null)
        }
        setReady(true)
      })
      .catch(() => {
        if (!gate.ok) return
        setAccount(null)
        setError('The Java backend is not running.')
        setReady(true)
      })
    return () => {
      gate.ok = false
    }
  }, [token])

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
        localStorage.removeItem(TOKEN_KEY)
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
      const payload = (await response.json().catch(() => null)) as {
        token?: string
        account?: Account
        error?: string
      } | null
      if (!response.ok || !payload?.token || !payload.account) {
        const message = payload?.error ?? 'The Java backend is not running.'
        setError(message === 'The Java backend is not running.' ? message : null)
        return message
      }
      localStorage.setItem(TOKEN_KEY, payload.token)
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
    signOut() {
      const current = token
      localStorage.removeItem(TOKEN_KEY)
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
