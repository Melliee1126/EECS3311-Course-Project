import type { SuggestedRestaurant } from '../assistant'
import { readableCuisine, visibleTags } from '../restaurants'
import { TagList } from './TagList'

type Props = {
  places: SuggestedRestaurant[]
  selectedId: string | null
  docked?: boolean
  onSelect: (restId: string) => void
}

export function RecommendationRow({ places, selectedId, docked = false, onSelect }: Props) {
  if (places.length === 0) return null

  return (
    <div className={docked ? 'recommend-row docked' : 'recommend-row'} aria-label="Recommended restaurants">
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
            <span className="recommend-meta">{[readableCuisine(place.cuisine), place.priceRange].filter(Boolean).join(' · ')}</span>
            <TagList tags={visibleTags(place, true)} />
            {place.explanation && <span className="recommend-why">{place.explanation}</span>}
          </span>
        </button>
      ))}
    </div>
  )
}
