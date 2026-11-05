import { useState, type CSSProperties } from 'react'
import { placeCollectionIds, type Collection } from '../collections'
import type { Restaurant } from '../restaurants'
import { hasLocation } from '../restaurants'
import { distanceKm, type LatLng } from '../userLocation'
import { CollectionMenu } from './CollectionMenu'

type Props = {
  restaurant: Restaurant
  userPosition: LatLng | null
  collections: Collection[]
  signedIn: boolean
  onToggleCollection: (collectionId: string) => void
  onRequestCreate: () => void
  onSignIn: () => void
}

export function PlaceDetail({
  restaurant,
  userPosition,
  collections,
  signedIn,
  onToggleCollection,
  onRequestCreate,
  onSignIn,
}: Props) {
  const [menuOpen, setMenuOpen] = useState(false)
  const distance =
    userPosition && hasLocation(restaurant)
      ? distanceKm(userPosition, { lat: restaurant.latitude, lng: restaurant.longitude })
      : null
  const score = Math.max(0, Math.min(5, restaurant.rating))
  const memberIds = placeCollectionIds(collections, restaurant.restId)

  return (
    <section className="place-detail" aria-label={restaurant.name}>
      <div className="place-photo" />
      <div className="place-copy">
        <h2 className="place-name">{restaurant.name}</h2>
        {restaurant.address && <p className="place-address">{restaurant.address}</p>}
        <div className="place-meta">
          {distance != null && (
            <p className="place-distance">
              <PinIcon />
              <span>{formatDistance(distance)}</span>
            </p>
          )}
          <Stars score={score} />
        </div>
      </div>
      <button
        type="button"
        className="place-bookmark"
        aria-label="Collections"
        aria-pressed={memberIds.length > 0}
        aria-expanded={menuOpen}
        onClick={() => {
          if (!signedIn) {
            onSignIn()
            return
          }
          setMenuOpen((open) => !open)
        }}
      >
        <BookmarkIcon />
      </button>
      {menuOpen && (
        <CollectionMenu
          collections={collections}
          memberIds={memberIds}
          onToggle={onToggleCollection}
          onRequestCreate={onRequestCreate}
          onClose={() => setMenuOpen(false)}
        />
      )}
    </section>
  )
}

function formatDistance(km: number) {
  if (km < 0.1) return '< 0.1 km'
  return `${km.toFixed(1)} km`
}

function Stars({ score }: { score: number }) {
  const label = score > 0 ? `${score.toFixed(1)} out of 5` : 'Unrated'
  return (
    <div
      className="place-stars"
      style={{ '--score': `${(score / 5) * 100}%` } as CSSProperties}
      aria-label={label}
    >
      <StarRow />
      <span className="place-stars-fill">
        <StarRow />
      </span>
    </div>
  )
}

function StarRow() {
  return (
    <span className="star-row" aria-hidden="true">
      {Array.from({ length: 5 }, (_, index) => (
        <StarIcon key={index} />
      ))}
    </span>
  )
}

function StarIcon() {
  return (
    <svg viewBox="2.6 2.2 19 18.2">
      <path d="M12 3.4 14.6 8.7l5.8.8-4.2 4.1 1 5.8L12 16.6 6.8 19.4l1-5.8L3.6 9.5l5.8-.8L12 3.4z" />
    </svg>
  )
}

function PinIcon() {
  return (
    <svg className="place-pin" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 21s6-6.1 6-10.2A6 6 0 0 0 6 10.8C6 14.9 12 21 12 21z" />
      <circle cx="12" cy="10.5" r="2.1" />
    </svg>
  )
}

function BookmarkIcon() {
  return (
    <svg className="place-bookmark" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 3.8h10a1 1 0 0 1 1 1V20l-6-3.3L6 20V4.8a1 1 0 0 1 1-1z" />
    </svg>
  )
}
