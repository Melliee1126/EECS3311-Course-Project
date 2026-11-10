const NAV_ITEMS = [
  { src: '/icons/instant.png', label: 'For you' },
  { src: '/icons/feed.png', label: 'Friends' },
  { src: '/icons/bookmark.png', label: 'Saved' },
  { src: '/icons/create.png', label: 'Create' },
]

type Props = {
  libraryOpen: boolean
  accountOpen: boolean
  reviewOpen: boolean
  friendsOpen: boolean
  forYouActive: boolean
  avatar: string
  onForYou: () => void
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
  forYouActive,
  avatar,
  onForYou,
  onSaved,
  onCreate,
  onFriends,
  onProfile,
}: Props) {
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
              aria-pressed={
                item.label === 'For you'
                  ? forYouActive
                  : item.label === 'Saved'
                  ? libraryOpen
                  : item.label === 'Create'
                    ? reviewOpen
                    : item.label === 'Friends'
                      ? friendsOpen
                      : undefined
              }
              onClick={
                item.label === 'For you'
                  ? onForYou
                  : item.label === 'Saved'
                  ? onSaved
                  : item.label === 'Create'
                    ? onCreate
                    : item.label === 'Friends'
                      ? onFriends
                      : undefined
              }
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
