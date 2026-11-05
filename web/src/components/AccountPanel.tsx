import { useState, type FormEvent } from 'react'
import type { Account } from '../account'

type Props = {
  account: Account | null
  notice: string | null
  onSignIn: (email: string, password: string) => Promise<string | null>
  onRegister: (name: string, email: string, password: string) => Promise<string | null>
  onUpdate: (name: string, email: string, birthday: string) => Promise<string | null>
  onChangePassword: (currentPassword: string, password: string) => Promise<string | null>
  onSignOut: () => void
}

export function AccountPanel({
  account,
  notice,
  onSignIn,
  onRegister,
  onUpdate,
  onChangePassword,
  onSignOut,
}: Props) {
  const [mode, setMode] = useState<'sign-in' | 'register'>('sign-in')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  if (account) {
    return (
      <ProfileForm
        account={account}
        onUpdate={onUpdate}
        onChangePassword={onChangePassword}
        onSignOut={onSignOut}
      />
    )
  }

  const creating = mode === 'register'

  function submit(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    const action = creating ? onRegister(name, email, password) : onSignIn(email, password)
    void action.then((message) => {
      setPending(false)
      setError(message)
      if (!message) {
        setPassword('')
      }
    })
  }

  return (
    <section className="account" aria-label={creating ? 'Create account' : 'Sign in'}>
      <h2>{creating ? 'Create account' : 'Sign in'}</h2>
      {notice && <p className="library-error">{notice}</p>}
      <form onSubmit={submit}>
        {creating && (
          <>
            <label htmlFor="account-name">Name</label>
            <input
              id="account-name"
              value={name}
              maxLength={40}
              autoComplete="name"
              onChange={(event) => {
                setName(event.target.value)
                setError(null)
              }}
            />
          </>
        )}
        <label htmlFor="account-email">Email</label>
        <input
          id="account-email"
          type="email"
          value={email}
          autoComplete="email"
          onChange={(event) => {
            setEmail(event.target.value)
            setError(null)
          }}
        />
        <label htmlFor="account-password">Password</label>
        <input
          id="account-password"
          type="password"
          value={password}
          autoComplete={creating ? 'new-password' : 'current-password'}
          onChange={(event) => {
            setPassword(event.target.value)
            setError(null)
          }}
        />
        {error && <p className="library-error">{error}</p>}
        <button type="submit" className="create-submit" disabled={pending}>
          {creating ? 'Create account' : 'Sign in'}
        </button>
      </form>
      <button
        type="button"
        className="account-switch"
        onClick={() => {
          setMode(creating ? 'sign-in' : 'register')
          setError(null)
        }}
      >
        {creating ? 'Sign in' : 'Create an account'}
      </button>
    </section>
  )
}

function ProfileForm({
  account,
  onUpdate,
  onChangePassword,
  onSignOut,
}: {
  account: Account
  onUpdate: (name: string, email: string, birthday: string) => Promise<string | null>
  onChangePassword: (currentPassword: string, password: string) => Promise<string | null>
  onSignOut: () => void
}) {
  const [name, setName] = useState(account.name)
  const [email, setEmail] = useState(account.email)
  const [birthday, setBirthday] = useState(account.birthday)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  function saveProfile(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    void onUpdate(name, email, birthday).then((message) => {
      setPending(false)
      setError(message)
      setSaved(message ? null : 'Profile saved.')
    })
  }

  function savePassword(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    void onChangePassword(currentPassword, newPassword).then((message) => {
      setPending(false)
      setError(message)
      setSaved(message ? null : 'Password changed.')
      if (!message) {
        setCurrentPassword('')
        setNewPassword('')
      }
    })
  }

  return (
    <section className="account" aria-label="Profile">
      <h2>Profile</h2>
      <form onSubmit={saveProfile}>
        <label htmlFor="profile-name">Name</label>
        <input
          id="profile-name"
          value={name}
          maxLength={40}
          autoComplete="name"
          onChange={(event) => {
            setName(event.target.value)
            setError(null)
            setSaved(null)
          }}
        />
        <label htmlFor="profile-email">Email</label>
        <input
          id="profile-email"
          type="email"
          value={email}
          autoComplete="email"
          onChange={(event) => {
            setEmail(event.target.value)
            setError(null)
            setSaved(null)
          }}
        />
        <label htmlFor="profile-birthday">Birthday</label>
        <input
          id="profile-birthday"
          type="date"
          value={birthday}
          max="9999-12-31"
          onChange={(event) => {
            setBirthday(event.target.value)
            setError(null)
            setSaved(null)
          }}
        />
        <button type="submit" className="create-submit" disabled={pending}>
          Save
        </button>
      </form>
      <form onSubmit={savePassword}>
        <h3>Password</h3>
        <label htmlFor="profile-current-password">Current password</label>
        <input
          id="profile-current-password"
          type="password"
          value={currentPassword}
          autoComplete="current-password"
          onChange={(event) => {
            setCurrentPassword(event.target.value)
            setError(null)
            setSaved(null)
          }}
        />
        <label htmlFor="profile-new-password">New password</label>
        <input
          id="profile-new-password"
          type="password"
          value={newPassword}
          autoComplete="new-password"
          onChange={(event) => {
            setNewPassword(event.target.value)
            setError(null)
            setSaved(null)
          }}
        />
        <button type="submit" className="create-submit" disabled={pending}>
          Change password
        </button>
      </form>
      {error && <p className="library-error">{error}</p>}
      {saved && <p className="account-saved">{saved}</p>}
      <button type="button" className="account-switch" onClick={onSignOut}>
        Sign out
      </button>
    </section>
  )
}
