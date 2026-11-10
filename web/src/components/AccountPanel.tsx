import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Account } from '../account'
import type { Collection } from '../collections'
import { correctFoodInterest, suggestFoodInterests } from '../foodInterests'
import { readAvatarFile, useProfileExtra, writeProfileExtra } from '../profileExtra'
import type { Review } from '../reviews'

type Props = {
  account: Account | null
  notice: string | null
  collections: Collection[]
  reviews: Review[]
  resettingPassword: boolean
  onSignIn: (email: string, password: string) => Promise<string | null>
  onRegister: (name: string, email: string, password: string) => Promise<string | null>
  onRecover: (email: string) => Promise<string | null>
  onResetPassword: (password: string) => Promise<string | null>
  onResetDone: () => void
  onUpdate: (name: string, email: string, birthday: string) => Promise<string | null>
  onChangePassword: (currentPassword: string, password: string) => Promise<string | null>
  onSignOut: () => void
  learnedLikes: string[]
  learnedDislikes: string[]
  priceComfort: string
  onTaste: (interests: string[], description: string) => void
}

export function AccountPanel({
  account,
  notice,
  collections,
  reviews,
  resettingPassword,
  onSignIn,
  onRegister,
  onRecover,
  onResetPassword,
  onResetDone,
  onUpdate,
  onChangePassword,
  onSignOut,
  learnedLikes,
  learnedDislikes,
  priceComfort,
  onTaste,
}: Props) {
  const [mode, setMode] = useState<'sign-in' | 'register' | 'recover'>('sign-in')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const [pending, setPending] = useState(false)

  if (resettingPassword) {
    return <NewPassword onResetPassword={onResetPassword} onDone={onResetDone} />
  }

  if (account) {
    return (
      <Profile
        account={account}
        collections={collections}
        reviews={reviews}
        onUpdate={onUpdate}
        onChangePassword={onChangePassword}
        onSignOut={onSignOut}
        learnedLikes={learnedLikes}
        learnedDislikes={learnedDislikes}
        priceComfort={priceComfort}
        onTaste={onTaste}
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

  function sendReset(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    setSent(false)
    void onRecover(email).then((message) => {
      setPending(false)
      setError(message)
      setSent(!message)
    })
  }

  if (mode === 'recover') {
    return (
      <section className="account account-auth" aria-label="Reset password">
        <h2>Reset password</h2>
        <p className="account-note">Enter your email and Supabase will send a reset link.</p>
        <form onSubmit={sendReset}>
          <label htmlFor="account-email">Email</label>
          <input
            id="account-email"
            type="email"
            value={email}
            autoComplete="email"
            onChange={(event) => {
              setEmail(event.target.value)
              setError(null)
              setSent(false)
            }}
          />
          {error && <p className="library-error">{error}</p>}
          {sent && <p className="account-saved">Check your email for a reset link.</p>}
          <button type="submit" className="create-submit" disabled={pending}>
            Send reset link
          </button>
        </form>
        <button
          type="button"
          className="account-switch"
          onClick={() => {
            setMode('sign-in')
            setError(null)
            setSent(false)
          }}
        >
          Back to sign in
        </button>
      </section>
    )
  }

  return (
    <section className="account account-auth" aria-label={creating ? 'Create account' : 'Sign in'}>
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
      {!creating && (
        <button
          type="button"
          className="account-switch"
          onClick={() => {
            setMode('recover')
            setError(null)
          }}
        >
          Forgot password?
        </button>
      )}
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

function NewPassword({
  onResetPassword,
  onDone,
}: {
  onResetPassword: (password: string) => Promise<string | null>
  onDone: () => void
}) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  function save(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    void onResetPassword(password).then((message) => {
      setPending(false)
      setError(message)
      if (!message) onDone()
    })
  }

  return (
    <section className="account account-auth" aria-label="Choose a new password">
      <h2>Choose a new password</h2>
      <p className="account-note">This replaces the password on your account.</p>
      <form onSubmit={save}>
        <label htmlFor="reset-password">New password</label>
        <input
          id="reset-password"
          type="password"
          value={password}
          autoComplete="new-password"
          onChange={(event) => {
            setPassword(event.target.value)
            setError(null)
          }}
        />
        {error && <p className="library-error">{error}</p>}
        <button type="submit" className="create-submit" disabled={pending}>
          Save password
        </button>
      </form>
    </section>
  )
}

function Profile({
  account,
  collections,
  reviews,
  onUpdate,
  onChangePassword,
  onSignOut,
  learnedLikes,
  learnedDislikes,
  priceComfort,
  onTaste,
}: {
  account: Account
  collections: Collection[]
  reviews: Review[]
  onUpdate: (name: string, email: string, birthday: string) => Promise<string | null>
  onChangePassword: (currentPassword: string, password: string) => Promise<string | null>
  onSignOut: () => void
  learnedLikes: string[]
  learnedDislikes: string[]
  priceComfort: string
  onTaste: (interests: string[], description: string) => void
}) {
  const [view, setView] = useState<'home' | 'edit' | 'personal' | 'privacy'>('home')
  const extra = useProfileExtra(account.id)
  const [adding, setAdding] = useState(false)
  const [draft, setDraft] = useState('')
  const [interestError, setInterestError] = useState<string | null>(null)
  const [avatarError, setAvatarError] = useState<string | null>(null)
  const skipInterestBlur = useRef(false)
  const avatarInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setView('home')
    setAdding(false)
    setAvatarError(null)
  }, [account.id])

  const places = uniquePlaces(collections)
  const visited = places.filter((place) => place.status === 'visited').length
  const handle = handleOf(account)

  function persist(next: typeof extra) {
    writeProfileExtra(account.id, next)
    onTaste(next.interests, next.description)
  }

  const interestSuggestions = suggestFoodInterests(draft)

  function addInterest(explicit?: string) {
    const raw = (explicit ?? draft).trim().slice(0, 24)
    if (!raw) {
      setDraft('')
      setAdding(false)
      setInterestError(null)
      return
    }
    const corrected = explicit ?? correctFoodInterest(raw)
    if (!corrected) {
      setInterestError(interestSuggestions.length > 0 ? 'Pick a food from the list.' : 'That doesn’t look like a food interest.')
      return
    }
    setDraft('')
    setAdding(false)
    setInterestError(null)
    if (extra.interests.some((item) => item.toLowerCase() === corrected.toLowerCase())) return
    persist({ ...extra, interests: [...extra.interests, corrected].slice(0, 8) })
  }

  if (view === 'edit') {
    return (
      <EditProfile
        account={account}
        description={extra.description}
        onUpdate={onUpdate}
        onSaveDescription={(description) => persist({ ...extra, description })}
        onBack={() => setView('home')}
      />
    )
  }

  if (view === 'personal') {
    return <PersonalForm account={account} onUpdate={onUpdate} onBack={() => setView('home')} />
  }

  if (view === 'privacy') {
    return <PrivacyForm onChangePassword={onChangePassword} onBack={() => setView('home')} />
  }

  return (
    <section className="account profile" aria-label="My Profile">
      <h2 className="profile-title">My Profile</h2>
      <hr className="profile-rule" />
      <div className="profile-identity">
        <button
          type="button"
          className="profile-avatar"
          aria-label="Change profile photo"
          onClick={() => avatarInput.current?.click()}
        >
          {extra.avatar ? <img src={extra.avatar} alt="" /> : <PersonIcon />}
          <span className="profile-avatar-plus" aria-hidden="true">
            <PlusIcon />
          </span>
        </button>
        <input
          ref={avatarInput}
          type="file"
          accept="image/*"
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (!file) return
            void readAvatarFile(file).then((avatar) => {
              if (!avatar) {
                setAvatarError('That photo is too large.')
                return
              }
              persist({ ...extra, avatar })
              setAvatarError(null)
            })
          }}
        />
        {avatarError && <p className="library-error">{avatarError}</p>}
        <p className="profile-name">{account.name}</p>
        <p className="profile-handle">@{handle}</p>
        <p className="profile-bio">{extra.description || 'description (optional by user)'}</p>
        <button type="button" className="profile-edit" onClick={() => setView('edit')}>
          Edit profile
        </button>
      </div>
      <hr className="profile-rule" />
      <div className="profile-stats">
        <p>
          <strong>{visited}</strong>
          <span>Visited</span>
        </p>
        <p>
          <strong>{reviews.length}</strong>
          <span>reviews</span>
        </p>
        <p>
          <strong>{places.length}</strong>
          <span>Saved</span>
        </p>
      </div>
      <hr className="profile-rule" />
      <h3 className="profile-section">My food interests</h3>
      <div className="profile-tags">
        {extra.interests.map((tag) => (
          <span key={tag} className="profile-tag">
            {tag}
            <button
              type="button"
              className="profile-tag-clear"
              aria-label={`Remove ${tag}`}
              onClick={() => persist({ ...extra, interests: extra.interests.filter((item) => item !== tag) })}
            >
              ×
            </button>
          </span>
        ))}
        {adding ? (
          <input
            className="profile-interest-input"
            aria-label="Food interest"
            value={draft}
            maxLength={24}
            autoFocus
            spellCheck
            autoCorrect="on"
            onChange={(event) => {
              setDraft(event.target.value)
              setInterestError(null)
            }}
            onBlur={() => {
              if (skipInterestBlur.current) {
                skipInterestBlur.current = false
                return
              }
              addInterest()
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                skipInterestBlur.current = true
                addInterest()
              }
              if (event.key === 'Escape') {
                setDraft('')
                setAdding(false)
                setInterestError(null)
              }
            }}
          />
        ) : (
          <button type="button" className="profile-tag-add" aria-label="Add a food interest" onClick={() => setAdding(true)}>
            <PlusIcon />
          </button>
        )}
      </div>
      {adding && interestSuggestions.length > 0 && (
        <div className="profile-interest-suggestions" role="listbox" aria-label="Food suggestions">
          {interestSuggestions.map((item) => (
            <button
              key={item}
              type="button"
              role="option"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => addInterest(item)}
            >
              {item}
            </button>
          ))}
        </div>
      )}
      {interestError && <p className="library-error">{interestError}</p>}
      {(learnedLikes.length > 0 || learnedDislikes.length > 0 || priceComfort) && (
        <div className="profile-learned">
          {learnedLikes.length > 0 && <p>Learned from your reviews: {learnedLikes.join(', ')}</p>}
          {learnedDislikes.length > 0 && <p>Less of: {learnedDislikes.join(', ')}</p>}
          {priceComfort && <p>Usual budget: {priceComfort}</p>}
        </div>
      )}
      <hr className="profile-rule" />
      <h3 className="profile-section">Account</h3>
      <div className="profile-rows">
        <button type="button" className="profile-row" onClick={() => setView('personal')}>
          <PersonIcon />
          Personal information
        </button>
        <button type="button" className="profile-row" onClick={() => setView('privacy')}>
          <LockIcon />
          Privacy settings
        </button>
        <button type="button" className="profile-row" onClick={onSignOut}>
          <LogoutIcon />
          Log out
        </button>
      </div>
    </section>
  )
}

function EditProfile({
  account,
  description,
  onUpdate,
  onSaveDescription,
  onBack,
}: {
  account: Account
  description: string
  onUpdate: (name: string, email: string, birthday: string) => Promise<string | null>
  onSaveDescription: (description: string) => void
  onBack: () => void
}) {
  const [name, setName] = useState(account.name)
  const [bio, setBio] = useState(description)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  function save(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    void onUpdate(name, account.email, account.birthday).then((message) => {
      setPending(false)
      setError(message)
      if (!message) {
        onSaveDescription(bio.trim())
        onBack()
      }
    })
  }

  return (
    <section className="account profile" aria-label="Edit profile">
      <button type="button" className="library-back" onClick={onBack}>
        My Profile
      </button>
      <h2 className="profile-title">Edit profile</h2>
      <form onSubmit={save}>
        <label htmlFor="edit-name">Name</label>
        <input
          id="edit-name"
          value={name}
          maxLength={40}
          autoComplete="name"
          onChange={(event) => {
            setName(event.target.value)
            setError(null)
          }}
        />
        <label htmlFor="edit-description">Description</label>
        <textarea
          id="edit-description"
          value={bio}
          maxLength={140}
          onChange={(event) => {
            setBio(event.target.value)
            setError(null)
          }}
        />
        {error && <p className="library-error">{error}</p>}
        <button type="submit" className="profile-edit" disabled={pending}>
          Save
        </button>
      </form>
    </section>
  )
}

function PersonalForm({
  account,
  onUpdate,
  onBack,
}: {
  account: Account
  onUpdate: (name: string, email: string, birthday: string) => Promise<string | null>
  onBack: () => void
}) {
  const [name, setName] = useState(account.name)
  const [email, setEmail] = useState(account.email)
  const [birthday, setBirthday] = useState(account.birthday)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  function save(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    void onUpdate(name, email, birthday).then((message) => {
      setPending(false)
      setError(message)
      if (!message) onBack()
    })
  }

  return (
    <section className="account profile" aria-label="Personal information">
      <button type="button" className="library-back" onClick={onBack}>
        My Profile
      </button>
      <h2 className="profile-title">Personal information</h2>
      <form onSubmit={save}>
        <label htmlFor="profile-name">Name</label>
        <input
          id="profile-name"
          value={name}
          maxLength={40}
          autoComplete="name"
          onChange={(event) => {
            setName(event.target.value)
            setError(null)
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
          }}
        />
        {error && <p className="library-error">{error}</p>}
        <button type="submit" className="profile-edit" disabled={pending}>
          Save
        </button>
      </form>
    </section>
  )
}

function PrivacyForm({
  onChangePassword,
  onBack,
}: {
  onChangePassword: (currentPassword: string, password: string) => Promise<string | null>
  onBack: () => void
}) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  function save(event: FormEvent) {
    event.preventDefault()
    setPending(true)
    void onChangePassword(currentPassword, newPassword).then((message) => {
      setPending(false)
      setError(message)
      if (!message) onBack()
    })
  }

  return (
    <section className="account profile" aria-label="Privacy settings">
      <button type="button" className="library-back" onClick={onBack}>
        My Profile
      </button>
      <h2 className="profile-title">Privacy settings</h2>
      <form onSubmit={save}>
        <label htmlFor="profile-current-password">Current password</label>
        <input
          id="profile-current-password"
          type="password"
          value={currentPassword}
          autoComplete="current-password"
          onChange={(event) => {
            setCurrentPassword(event.target.value)
            setError(null)
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
          }}
        />
        {error && <p className="library-error">{error}</p>}
        <button type="submit" className="profile-edit" disabled={pending}>
          Change password
        </button>
      </form>
    </section>
  )
}

function uniquePlaces(collections: Collection[]) {
  const byId = new Map<string, Collection['places'][number]>()
  for (const collection of collections) {
    for (const place of collection.places) {
      const current = byId.get(place.restId)
      if (!current || place.status === 'visited') byId.set(place.restId, place)
    }
  }
  return [...byId.values()]
}

function handleOf(account: Account) {
  const local = account.email.split('@')[0] ?? ''
  const slug = local.replace(/[^a-zA-Z0-9._]/g, '')
  return slug || 'user'
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 6.5v11M6.5 12h11" />
    </svg>
  )
}

function PersonIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8" r="3.2" />
      <path d="M6.2 18.5c.8-2.6 3-4 5.8-4s5 1.4 5.8 4" />
    </svg>
  )
}

function LockIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="6" y="10.5" width="12" height="8.5" rx="1.6" />
      <path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5" />
    </svg>
  )
}

function LogoutIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M14 7V5.8A1.8 1.8 0 0 0 12.2 4H6A1.8 1.8 0 0 0 4.2 5.8v12.4A1.8 1.8 0 0 0 6 20h6.2A1.8 1.8 0 0 0 14 18.2V17" />
      <path d="M10.5 12H19.5M17 9.2 19.8 12 17 14.8" />
    </svg>
  )
}
