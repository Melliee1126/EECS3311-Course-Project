import { useEffect, useRef, useState } from 'react'
import { searchRestaurants } from '../places'
import type { Review, ReviewDraft } from '../reviews'
import type { Restaurant } from '../restaurants'
import type { LatLng } from '../userLocation'
import { PhotoFramer, type Placement } from './PhotoFramer'
import { PhotoGrid } from './PhotoGrid'
import { fitPhotoShapes, photoFrame, photoShape, type PhotoShape } from '../photoLayout'

type Props = {
  reviews: Review[]
  notice: string | null
  bias: LatLng
  onPublish: (draft: ReviewDraft) => Promise<string | null>
  onUpdate: (id: string, draft: ReviewDraft) => Promise<string | null>
  onDelete: (id: string) => Promise<string | null>
  onSignIn: () => void
  signedIn: boolean
}

export function ReviewPanel({ reviews, notice, bias, onPublish, onUpdate, onDelete, onSignIn, signedIn }: Props) {
  const [showAll, setShowAll] = useState(false)
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [restaurantQuery, setRestaurantQuery] = useState('')
  const [matches, setMatches] = useState<Restaurant[]>([])
  const [rating, setRating] = useState(0)
  const [tags, setTags] = useState<string[]>([])
  const [tagDraft, setTagDraft] = useState('')
  const [body, setBody] = useState('')
  const [photos, setPhotos] = useState<DraftPhoto[]>([])
  const [framer, setFramer] = useState<FrameDraft | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)
  const photoInput = useRef<HTMLInputElement>(null)
  const replaceId = useRef<string | null>(null)
  const photoEpoch = useRef(0)
  const biasRef = useRef(bias)

  useEffect(() => {
    biasRef.current = bias
  }, [bias])

  useEffect(() => {
    const trimmed = restaurantQuery.trim()
    if (!pickerOpen || trimmed.length < 2) return
    const gate = { ok: true }
    const handle = window.setTimeout(() => {
      searchRestaurants(trimmed, biasRef.current)
        .then((found) => {
          if (gate.ok) setMatches(found)
        })
        .catch(() => {
          if (gate.ok) setMatches([])
        })
    }, 400)
    return () => {
      gate.ok = false
      window.clearTimeout(handle)
    }
  }, [pickerOpen, restaurantQuery])

  const preview = showAll ? reviews : reviews.slice(0, 2)

  function addTag() {
    const next = tagDraft.trim()
    if (!next || tags.includes(next) || tags.length >= 8 || next.length > 24) {
      setTagDraft('')
      return
    }
    setTags([...tags, next])
    setTagDraft('')
  }

  function clearFields() {
    photoEpoch.current += 1
    setRestaurant(null)
    setRating(0)
    setTags([])
    setTagDraft('')
    setBody('')
    setPhotos([])
    setFramer(null)
    setPickerOpen(false)
    setRestaurantQuery('')
    setMatches([])
    setEditingId(null)
  }

  function beginEdit(review: Review) {
    photoEpoch.current += 1
    setEditingId(review.id)
    setConfirmDeleteId(null)
    setRestaurant({
      restId: review.restId,
      name: review.name,
      cuisine: '',
      address: review.address,
      latitude: review.latitude,
      longitude: review.longitude,
      rating: 0,
      priceRange: '',
      status: 'discovered',
      tags: [],
    })
    setRating(review.rating)
    setTags(review.tags)
    setTagDraft('')
    setBody(review.body)
    const shots = review.photos?.length ? review.photos : review.photo ? [review.photo] : []
    const shape = photoShape(shots.length)
    setPhotos(shots.map((src) => ({ id: photoId(), source: src, framed: src, zoom: 1, x: 0, y: 0, shape })))
    setFramer(null)
    setPickerOpen(false)
    setRestaurantQuery('')
    setMatches([])
    setError(null)
  }

  function remove(id: string) {
    setPending(true)
    void onDelete(id).then((message) => {
      setPending(false)
      setError(message)
      if (!message) {
        setConfirmDeleteId(null)
        if (editingId === id) clearFields()
      }
    })
  }

  function publish() {
    if (!signedIn) {
      onSignIn()
      return
    }
    if (!restaurant) {
      setError('Add a restaurant first.')
      return
    }
    if (rating < 1) {
      setError('Choose a rating.')
      return
    }
    const framed = photos.map((item) => item.framed)
    const draft: ReviewDraft = {
      restId: restaurant.restId,
      name: restaurant.name,
      address: restaurant.address,
      latitude: restaurant.latitude,
      longitude: restaurant.longitude,
      rating,
      body,
      tags,
      photo: framed[0] ?? '',
      photos: framed,
    }
    setPending(true)
    const request = editingId ? onUpdate(editingId, draft) : onPublish(draft)
    void request.then((message) => {
      setPending(false)
      setError(message)
      if (!message) clearFields()
    })
  }

  function framingFor(id: string | null) {
    return photoFrame(id == null ? photos.length + 1 : photos.length)
  }

  function choosePhoto(id: string | null) {
    if (id == null && photos.length >= 4) {
      setError('You can add 4 photos.')
      return
    }
    replaceId.current = id
    photoInput.current?.click()
  }

  function applyPhotos(next: DraftPhoto[]) {
    const epoch = ++photoEpoch.current
    void fitPhotoShapes(next, photoShape(next.length)).then((fitted) => {
      if (photoEpoch.current === epoch) setPhotos(fitted)
    })
  }

  function removePhoto(id: string) {
    applyPhotos(photos.filter((photo) => photo.id !== id))
    setFramer((current) => (current?.id === id ? null : current))
    setError(null)
  }

  return (
    <section className="review-panel" aria-label="Create review">
      <div className="review-latest-head">
        <h2>Latest reviews</h2>
        <button type="button" className="review-see-all" onClick={() => setShowAll((open) => !open)}>
          See all →
        </button>
      </div>
      {notice && <p className="library-error">{notice}</p>}
      {preview.length === 0 ? (
        <p className="review-empty">No reviews yet.</p>
      ) : (
        <ul className="review-cards">
          {preview.map((review) => (
            <li key={review.id}>
              <article className={editingId === review.id ? 'review-card is-editing' : 'review-card'}>
                <ReviewShots photos={review.photos?.length ? review.photos : review.photo ? [review.photo] : []} />
                <p className="review-card-name">{review.name}</p>
                <p className="review-card-body">{review.body}</p>
                <p className="review-card-meta">
                  <PinIcon />
                  <span className="review-card-stars" aria-label={`${review.rating} star rating`}>
                    {[1, 2, 3, 4, 5].map((score) => (
                      <StarIcon key={score} filled={review.rating >= score} />
                    ))}
                  </span>
                </p>
                <div className="review-card-actions">
                  <button type="button" onClick={() => beginEdit(review)}>
                    Edit
                  </button>
                  {confirmDeleteId === review.id ? (
                    <>
                      <button type="button" disabled={pending} onClick={() => remove(review.id)}>
                        Delete
                      </button>
                      <button type="button" onClick={() => setConfirmDeleteId(null)}>
                        Keep
                      </button>
                    </>
                  ) : (
                    <button type="button" onClick={() => setConfirmDeleteId(review.id)}>
                      Delete
                    </button>
                  )}
                </div>
              </article>
            </li>
          ))}
        </ul>
      )}
      <hr className="review-rule" />
      <h2>{editingId ? 'Edit review' : 'Create review'}</h2>
      <div className="review-place">
        <button type="button" className="review-place-open" onClick={() => setPickerOpen((open) => !open)}>
          <PinIcon />
          <span className="review-place-copy">
            <span>{restaurant ? restaurant.name : 'Add Restaurant'}</span>
            {restaurant?.address && <span className="review-place-address">{restaurant.address}</span>}
          </span>
        </button>
        {restaurant ? (
          <button
            type="button"
            className="review-clear"
            aria-label="Clear restaurant"
            onClick={() => {
              setRestaurant(null)
              setRestaurantQuery('')
              setMatches([])
              setError(null)
            }}
          >
            ×
          </button>
        ) : (
          <ChevronIcon />
        )}
      </div>
      {pickerOpen && (
        <div className="review-picker">
          <div className="review-search">
            <input
              value={restaurantQuery}
              placeholder="Search restaurants"
              aria-label="Search restaurants"
              onChange={(event) => {
                const next = event.target.value
                setRestaurantQuery(next)
                if (next.trim().length < 2) setMatches([])
              }}
            />
            {restaurantQuery && (
              <button
                type="button"
                className="review-clear"
                aria-label="Clear search"
                onClick={() => {
                  setRestaurantQuery('')
                  setMatches([])
                }}
              >
                ×
              </button>
            )}
          </div>
          <ul>
            {matches.map((match) => (
              <li key={match.restId}>
                <button
                  type="button"
                  onClick={() => {
                    setRestaurant(match)
                    setPickerOpen(false)
                    setRestaurantQuery('')
                    setMatches([])
                    setError(null)
                  }}
                >
                  <span className="review-match-name">{match.name}</span>
                  <span className="review-match-address">{match.address || 'No address'}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
      <div className="review-score">
        <span>Your rating:</span>
        <span className="review-stars">
          {[1, 2, 3, 4, 5].map((score) => (
            <button
              key={score}
              type="button"
              aria-label={`${score} star${score === 1 ? '' : 's'}`}
              aria-pressed={rating >= score}
              onClick={() => {
                setRating(score)
                setError(null)
              }}
            >
              <StarIcon filled={rating >= score} />
            </button>
          ))}
        </span>
      </div>
      <div className="review-tags">
        {tags.map((tag) => (
          <button
            key={tag}
            type="button"
            className="review-tag"
            onClick={() => setTags(tags.filter((item) => item !== tag))}
          >
            {tag} ×
          </button>
        ))}
        <input
          className="review-tag-input"
          value={tagDraft}
          placeholder="Tag"
          aria-label="Add a tag"
          maxLength={24}
          onChange={(event) => setTagDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              addTag()
            }
          }}
          onBlur={addTag}
        />
      </div>
      <div className="review-write">
        <textarea
          value={body}
          maxLength={500}
          placeholder="Write something..."
          aria-label="Write something"
          onChange={(event) => {
            setBody(event.target.value)
            setError(null)
          }}
        />
        <div className="review-write-bar">
          {body && (
            <button
              type="button"
              className="review-clear"
              aria-label="Clear description"
              onClick={() => {
                setBody('')
                setError(null)
              }}
            >
              ×
            </button>
          )}
          <button
            type="button"
            className="review-camera"
            aria-label="Add photo"
            onClick={() => choosePhoto(null)}
          >
            <CameraIcon />
          </button>
        </div>
        <input
          ref={photoInput}
          type="file"
          accept="image/*"
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0]
            const replacing = replaceId.current
            replaceId.current = null
            event.target.value = ''
            if (!file) return
            if (replacing == null && photos.length >= 4) {
              setError('You can add 4 photos.')
              return
            }
            void readPhoto(file).then((source) => {
              if (!source) {
                setError('That photo could not be opened.')
                return
              }
              setFramer({ id: replacing, source, zoom: 1, x: 0, y: 0 })
              setError(null)
            })
          }}
        />
      </div>
      {photos.length > 0 && (
        <ul className={`review-draft-photos photo-grid photo-grid-${photoFrame(photos.length).layout}`}>
          {photos.map((item) => (
            <li key={item.id}>
              <img src={item.framed} alt="" />
              <div className="review-draft-actions">
                <button type="button" onClick={() => setFramer({ id: item.id, source: item.source, zoom: item.zoom, x: item.x, y: item.y })}>
                  Reframe
                </button>
                <button type="button" onClick={() => choosePhoto(item.id)}>
                  Reselect
                </button>
                <button type="button" onClick={() => removePhoto(item.id)}>
                  Remove
                </button>
              </div>
              <button
                type="button"
                className="review-clear"
                aria-label="Remove photo"
                onClick={() => removePhoto(item.id)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {framer && (
        <PhotoFramer
          key={`${framer.source}-${framingFor(framer.id).width}-${framingFor(framer.id).height}`}
          source={framer.source}
          width={framingFor(framer.id).width}
          height={framingFor(framer.id).height}
          initial={{ zoom: framer.zoom, x: framer.x, y: framer.y }}
          onCancel={() => setFramer(null)}
          onReselect={() => choosePhoto(framer.id)}
          onUse={(framed, placement) => {
            const shape = photoShape(framer.id == null ? photos.length + 1 : photos.length)
            const next = framer.id
              ? photos.map((item) =>
                  item.id === framer.id ? { ...item, source: framer.source, framed, ...placement, shape } : item,
                )
              : [...photos, { id: photoId(), source: framer.source, framed, ...placement, shape }].slice(0, 4)
            applyPhotos(next)
            setFramer(null)
            setError(null)
          }}
        />
      )}
      {error && <p className="library-error">{error}</p>}
      <div className="review-actions">
        {editingId && (
          <button type="button" className="review-cancel" disabled={pending} onClick={() => {
            clearFields()
            setError(null)
          }}>
            Cancel
          </button>
        )}
        <button type="button" className="review-publish" disabled={pending} onClick={publish}>
          {editingId ? 'Save' : 'Publish'}
        </button>
      </div>
    </section>
  )
}

function ReviewShots({ photos }: { photos: string[] }) {
  if (photos.length === 0) return <div className="review-photo" />
  return <PhotoGrid photos={photos} />
}

type DraftPhoto = Placement & { id: string; source: string; framed: string; shape: PhotoShape }
type FrameDraft = Placement & { id: string | null; source: string }

function photoId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function readPhoto(file: File): Promise<string | null> {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : null)
    reader.onerror = () => resolve(null)
    reader.readAsDataURL(file)
  })
}

function PinIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 21s6-5.4 6-10a6 6 0 1 0-12 0c0 4.6 6 10 6 10z" />
      <circle cx="12" cy="11" r="2" />
    </svg>
  )
}

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M9 6l6 6-6 6" />
    </svg>
  )
}

function StarIcon({ filled = false }: { filled?: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={filled ? 'is-filled' : undefined}>
      <path d="m12 3.6 2.1 4.7 5.1.6-3.8 3.4.9 5-4.3-2.5L7.7 17.3l.9-5L4.8 8.9l5.1-.6L12 3.6z" />
    </svg>
  )
}

function CameraIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M8 7.5 9.2 6h5.6L16 7.5H19a1 1 0 0 1 1 1V18a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1h3z" />
      <circle cx="12" cy="13" r="3" />
    </svg>
  )
}
