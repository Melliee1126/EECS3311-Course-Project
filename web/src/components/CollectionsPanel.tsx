import { useEffect, useRef, useState } from 'react'
import { SAVED_COLLECTION_ID, isProtectedCollection, type Collection } from '../collections'

type Props = {
  collections: Collection[]
  notice: string | null
  activeId: string | null
  onOpen: (collectionId: string) => void
  onBack: () => void
  onRequestCreate: () => void
  onDelete: (collectionId: string) => void
  onRemovePlace: (collectionId: string, restId: string) => void
  onSelect: (restId: string) => void
}

export function CollectionsPanel({
  collections,
  notice,
  activeId,
  onOpen,
  onBack,
  onRequestCreate,
  onDelete,
  onRemovePlace,
  onSelect,
}: Props) {
  const [finder, setFinder] = useState('')
  const active = collections.find((collection) => collection.id === activeId) ?? null
  const placeCount = new Set(
    collections.flatMap((collection) => collection.places.map((place) => place.restId)),
  ).size
  const needle = finder.trim().toLowerCase()
  const shown = needle
    ? collections.filter((collection) => panelLabel(collection).toLowerCase().includes(needle))
    : collections
  if (active) {
    return (
      <section className="library library-detail" aria-label={panelLabel(active)}>
        <button type="button" className="library-back" onClick={onBack}>
          My Collection
        </button>
        <div className="library-heading">
          <h2 className={active.id === SAVED_COLLECTION_ID ? undefined : 'library-title'}>
            {panelLabel(active)}
            {!isProtectedCollection(active.id) && (
              <button
                type="button"
                className="library-clear"
                aria-label={`Remove ${panelLabel(active)}`}
                onClick={() => onDelete(active.id)}
              >
                ×
              </button>
            )}
          </h2>
        </div>
        {active.places.length === 0 ? (
          <p className="library-empty">No restaurants in this collection yet.</p>
        ) : (
          <ul className="library-places">
            {active.places.map((place) => (
              <li key={place.restId}>
                <button type="button" className="library-place" onClick={() => onSelect(place.restId)}>
                  <span className="library-place-name">{place.name}</span>
                  <span className="library-place-address">{place.address || 'No address'}</span>
                </button>
                <button
                  type="button"
                  className="library-remove"
                  onClick={() => onRemovePlace(active.id, place.restId)}
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    )
  }

  return (
    <section className="library library-home" aria-label="My Collection">
      <h2>My Collection</h2>
      <p className="library-places-count">
        {placeCount} {placeCount === 1 ? 'place' : 'places'}
      </p>
      <hr className="library-rule" />
      <label className="library-find">
        <input
          type="search"
          value={finder}
          placeholder="find a collection"
          aria-label="Find a collection"
          onChange={(event) => setFinder(event.target.value)}
        />
        <SearchIcon />
      </label>
      {notice && <p className="library-error">{notice}</p>}
      <ul className="library-list">
        {shown.map((collection) => (
          <li key={collection.id} className="library-row">
            <button type="button" className="library-item" onClick={() => onOpen(collection.id)}>
              <span className="library-tile" aria-hidden="true">
                <BookmarkIcon />
              </span>
              <span>{panelLabel(collection)}</span>
            </button>
            {!isProtectedCollection(collection.id) && (
              <button
                type="button"
                className="library-clear"
                aria-label={`Remove ${panelLabel(collection)}`}
                onClick={() => onDelete(collection.id)}
              >
                ×
              </button>
            )}
          </li>
        ))}
        {shown.length === 0 && <li className="library-empty">No matching collections.</li>}
      </ul>
      <button type="button" className="library-new" onClick={onRequestCreate}>
        <span className="library-tile" aria-hidden="true">
          <PlusIcon />
        </span>
        <span>Create a new Collection</span>
      </button>
    </section>
  )
}

function panelLabel(collection: Collection) {
  return collection.id === SAVED_COLLECTION_ID ? 'Saved Restaurants' : collection.name
}

function BookmarkIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 4.2h10a1 1 0 0 1 1 1V20l-6-3.2L6 20V5.2a1 1 0 0 1 1-1z" />
    </svg>
  )
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 6.5v11M6.5 12h11" />
    </svg>
  )
}

function SearchIcon() {
  return (
    <svg className="library-find-icon" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6" />
      <path d="M15.8 15.8 20 20" />
    </svg>
  )
}

type DialogProps = {
  onClose: () => void
  onCreate: (name: string) => Promise<string | null>
}

export function CreateCollectionDialog({ onClose, onCreate }: DialogProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  return (
    <>
      <button type="button" className="menu-backdrop create-backdrop" aria-label="Close" onClick={onClose} />
      <form
        className="create-dialog"
        onSubmit={(event) => {
          event.preventDefault()
          void onCreate(title).then((message) => {
            if (message) setError(message)
          })
        }}
      >
        <div className="create-dialog-header">
          <h2>Create new collection</h2>
          <button type="button" aria-label="Close" onClick={onClose}>
            ×
          </button>
        </div>
        <label htmlFor="collection-title">Title</label>
        <input
          id="collection-title"
          ref={inputRef}
          value={title}
          maxLength={80}
          onChange={(event) => {
            setTitle(event.target.value)
            setError(null)
          }}
        />
        <p className="create-count">{title.length}/80 characters</p>
        {error && <p className="library-error">{error}</p>}
        <button type="submit" className="create-submit">
          Create
        </button>
      </form>
    </>
  )
}
