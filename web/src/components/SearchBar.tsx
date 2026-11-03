import { useState } from 'react'

export function SearchBar() {
  const [query, setQuery] = useState('')

  return (
    <form className="search" role="search" onSubmit={(event) => event.preventDefault()}>
      <PinIcon />
      <span className="search-divider" aria-hidden="true" />
      <input
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Search this area"
        aria-label="Search this area"
      />
      <button type="submit" aria-label="Search">
        <SearchIcon />
      </button>
    </form>
  )
}

function PinIcon() {
  return (
    <svg className="search-pin" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 21s6-6.1 6-10.2A6 6 0 0 0 6 10.8C6 14.9 12 21 12 21z" />
      <circle cx="12" cy="10.5" r="2.1" />
    </svg>
  )
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="5.5" />
      <path d="M15.5 15.5 20 20" />
    </svg>
  )
}
