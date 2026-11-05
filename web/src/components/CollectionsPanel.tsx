import { useEffect, useRef, useState } from 'react'
import { SAVED_COLLECTION_ID, collectionLabel, type Collection } from '../collections'

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
  const active = collections.find((collection) => collection.id === activeId) ?? null
  if (active) {
    return (
      <section className="library" aria-label={collectionLabel(active)}>
        <button type="button" className="library-back" onClick={onBack}>
          Collections
        </button>
        <div className="library-heading">
          <h2>{collectionLabel(active)}</h2>
          {active.id !== SAVED_COLLECTION_ID && (
            <DeleteCollection onDelete={() => onDelete(active.id)} />
          )}
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
    <section className="library" aria-label="Collections">
      <h2>Collections</h2>
      {notice && <p className="library-error">{notice}</p>}
      <ul className="library-list">
        {collections.map((collection) => (
          <li key={collection.id}>
            <button type="button" className="library-item" onClick={() => onOpen(collection.id)}>
              <BookmarkIcon />
              <span>{collectionLabel(collection)}</span>
            </button>
          </li>
        ))}
      </ul>
      <button type="button" className="library-new" onClick={onRequestCreate}>
        + Create new collection
      </button>
    </section>
  )
}

function BookmarkIcon() {
  return (
    <svg className="library-mark" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 3.8h10a1 1 0 0 1 1 1V20l-6-3.3L6 20V4.8a1 1 0 0 1 1-1z" />
    </svg>
  )
}

function DeleteCollection({ onDelete }: { onDelete: () => void }) {
  const [confirming, setConfirming] = useState(false)
  if (!confirming) {
    return (
      <button type="button" className="library-delete" onClick={() => setConfirming(true)}>
        Delete collection
      </button>
    )
  }
  return (
    <span className="library-confirm">
      <button type="button" onClick={onDelete}>
        Delete
      </button>
      <button type="button" onClick={() => setConfirming(false)}>
        Cancel
      </button>
    </span>
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
