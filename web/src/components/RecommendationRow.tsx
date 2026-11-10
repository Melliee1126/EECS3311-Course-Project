import type { SuggestedRestaurant } from '../assistant'

type Props = {
  places: SuggestedRestaurant[]
  selectedId: string | null
  raised: boolean
  onSelect: (restId: string) => void
}

export function RecommendationRow({ places, selectedId, raised, onSelect }: Props) {
  if (places.length === 0) return null

  return (
    <div className={raised ? 'recommend-row raised' : 'recommend-row'} aria-label="Recommended restaurants">
      {places.map((place) => (
        <button
          key={place.restId}
          type="button"
          className={place.restId === selectedId ? 'recommend-card selected' : 'recommend-card'}
          aria-pressed={place.restId === selectedId}
          onClick={() => onSelect(place.restId)}
        >
          {place.photo ? <img src={place.photo} alt="" /> : <span className="recommend-photo" aria-hidden="true" />}
          <span className="recommend-copy">
            <span className="recommend-name">{place.name}</span>
            <span className="recommend-meta">{[place.cuisine, place.priceRange].filter(Boolean).join(' · ')}</span>
            {place.explanation && <span className="recommend-why">{place.explanation}</span>}
          </span>
        </button>
      ))}
    </div>
  )
}
