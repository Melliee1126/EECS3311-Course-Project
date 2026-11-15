const NAV_ITEMS = [
  { src: '/icons/instant.png', label: 'Instant' },
  { src: '/icons/feed.png', label: 'Friends' },
  { src: '/icons/bookmark.png', label: 'Saved' },
  { src: '/icons/create.png', label: 'Create' },
] as const

type Props = {
  libraryOpen: boolean
  accountOpen: boolean
  reviewOpen: boolean
  friendsOpen: boolean
  instantOpen: boolean
  avatar: string
  onInstant: () => void
  onSaved: () => void
  onCreate: () => void
  onFriends: () => void
  onProfile: () => void
}

export function Sidebar({
  libraryOpen,
  accountOpen,
  reviewOpen,
  friendsOpen,
  instantOpen,
  avatar,
  onInstant,
  onSaved,
  onCreate,
  onFriends,
  onProfile,
}: Props) {
  function pressed(label: (typeof NAV_ITEMS)[number]['label']) {
    if (label === 'Instant') return instantOpen
    if (label === 'Saved') return libraryOpen
    if (label === 'Create') return reviewOpen
    return friendsOpen
  }

  function activate(label: (typeof NAV_ITEMS)[number]['label']) {
    if (label === 'Instant') onInstant()
    else if (label === 'Saved') onSaved()
    else if (label === 'Create') onCreate()
    else onFriends()
  }

  return (
    <aside className="sidebar">
      <div className="nav-column">
        <img className="logo" src="/icons/APP_Logo.png" alt="" />
        <nav className="rail" aria-label="Main">
          {NAV_ITEMS.map((item) => (
            <button
              key={item.label}
              type="button"
              aria-label={item.label}
              aria-pressed={pressed(item.label)}
              onClick={() => activate(item.label)}
            >
              <img src={item.src} alt="" />
            </button>
          ))}
        </nav>
        <button
          type="button"
          className="avatar"
          aria-label="Profile"
          aria-pressed={accountOpen}
          onClick={onProfile}
        >
          {avatar ? <img src={avatar} alt="" /> : <PersonIcon />}
        </button>
      </div>
    </aside>
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
