import { useEffect, useRef, useState } from 'react'
import { searchRestaurants } from '../places'
import type { Review, ReviewDraft } from '../reviews'
import type { Restaurant } from '../restaurants'
import type { LatLng } from '../userLocation'

type Props = {
  reviews: Review[]
  notice: string | null
  places: google.maps.PlacesLibrary | null
  bias: LatLng
  onPublish: (draft: ReviewDraft) => Promise<string | null>
  onSignIn: () => void
  signedIn: boolean
}

export function ReviewPanel({ reviews, notice, places, bias, onPublish, onSignIn, signedIn }: Props) {
  const [showAll, setShowAll] = useState(false)
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [restaurantQuery, setRestaurantQuery] = useState('')
  const [matches, setMatches] = useState<Restaurant[]>([])
  const [rating, setRating] = useState(0)
  const [tags, setTags] = useState<string[]>([])
  const [tagDraft, setTagDraft] = useState('')
  const [body, setBody] = useState('')
  const [photo, setPhoto] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const photoInput = useRef<HTMLInputElement>(null)
  const biasRef = useRef(bias)

  useEffect(() => {
    biasRef.current = bias
  }, [bias])

  useEffect(() => {
    const trimmed = restaurantQuery.trim()
    if (!pickerOpen || !places || trimmed.length < 2) return
    const gate = { ok: true }
    const handle = window.setTimeout(() => {
      searchRestaurants(places, trimmed, biasRef.current)
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
  }, [pickerOpen, places, restaurantQuery])

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
    setPending(true)
    void onPublish({
      restId: restaurant.restId,
      name: restaurant.name,
      address: restaurant.address,
      latitude: restaurant.latitude,
      longitude: restaurant.longitude,
      rating,
      body,
      tags,
      photo,
    }).then((message) => {
      setPending(false)
      setError(message)
      if (!message) {
        setRestaurant(null)
        setRating(0)
        setTags([])
        setBody('')
        setPhoto('')
        setPickerOpen(false)
        setRestaurantQuery('')
      }
    })
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
              <article className="review-card">
                {review.photo ? (
                  <img className="review-photo" src={review.photo} alt="" />
                ) : (
                  <div className="review-photo" />
                )}
                <p className="review-card-name">{review.name}</p>
                <p className="review-card-meta">
                  <PinIcon />
                  <StarIcon filled />
                </p>
              </article>
            </li>
          ))}
        </ul>
      )}
      <hr className="review-rule" />
      <h2>Create review</h2>
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
      <div className={body ? 'review-write has-note' : 'review-write'}>
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
        {body && (
          <button
            type="button"
            className="review-clear review-clear-note"
            aria-label="Clear description"
            onClick={() => {
              setBody('')
              setError(null)
            }}
          >
            ×
          </button>
        )}
        <button type="button" className="review-camera" aria-label="Add photo" onClick={() => photoInput.current?.click()}>
          <CameraIcon />
        </button>
        <input
          ref={photoInput}
          type="file"
          accept="image/*"
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (!file) return
            void shrinkPhoto(file).then((next) => {
              if (!next) {
                setError('That photo is too large.')
                return
              }
              setPhoto(next)
              setError(null)
            })
          }}
        />
      </div>
      {photo && <img className="review-draft-photo" src={photo} alt="" />}
      {error && <p className="library-error">{error}</p>}
      <button type="button" className="review-publish" disabled={pending} onClick={publish}>
        Publish
      </button>
    </section>
  )
}

function shrinkPhoto(file: File): Promise<string | null> {
  return new Promise((resolve) => {
    const image = new Image()
    const url = URL.createObjectURL(file)
    image.onload = () => {
      const scale = Math.min(1, 480 / Math.max(image.width, image.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(image.width * scale))
      canvas.height = Math.max(1, Math.round(image.height * scale))
      canvas.getContext('2d')?.drawImage(image, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      const data = canvas.toDataURL('image/jpeg', 0.7)
      resolve(data.length > 150_000 ? null : data)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    image.src = url
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
