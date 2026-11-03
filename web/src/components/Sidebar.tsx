const NAV_ITEMS = [
  { src: '/icons/instant.png', label: 'For you' },
  { src: '/icons/feed.png', label: 'Friends' },
  { src: '/icons/bookmark.png', label: 'Saved' },
  { src: '/icons/create.png', label: 'Create' },
]

export function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="nav-column">
        <img className="logo" src="/icons/APP_Logo.png" alt="" />
        <nav className="rail" aria-label="Main">
          {NAV_ITEMS.map((item) => (
            <button key={item.label} type="button" aria-label={item.label}>
              <img src={item.src} alt="" />
            </button>
          ))}
        </nav>
        <button type="button" className="avatar" aria-label="Profile">
          <PersonIcon />
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
