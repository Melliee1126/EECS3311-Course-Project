import type { Restaurant } from '../restaurants'

type Props = {
  restaurants: Restaurant[]
  offMap: Restaurant[]
  selected: Restaurant | null
  query: string
  onSelect: (restId: string) => void
}

export function RestaurantCard({ restaurants, offMap, selected, query, onSelect }: Props) {
  return (
    <aside className="place-card" aria-label="Restaurants">
      {restaurants.length === 0 ? (
        <p className="place-empty">No restaurants match the selected filters.</p>
      ) : selected ? (
        <PlaceDetail restaurant={selected} />
      ) : (
        <p className="place-count">
          {restaurants.length} restaurant{restaurants.length === 1 ? '' : 's'}
          {query.trim() ? ' match this search' : ' on your map'}
        </p>
      )}
      {offMap.length > 0 && (
        <div className="off-map">
          <p>Shown without a map pin</p>
          <ul>
            {offMap.map((restaurant) => (
              <li key={restaurant.restId}>
                <button type="button" onClick={() => onSelect(restaurant.restId)}>
                  {restaurant.name}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </aside>
  )
}

function PlaceDetail({ restaurant }: { restaurant: Restaurant }) {
  return (
    <>
      <p className="place-status">{restaurant.status}</p>
      <h2>{restaurant.name}</h2>
      <p>
        {restaurant.cuisine} · {restaurant.priceRange}
        {restaurant.rating > 0 ? ` · ${restaurant.rating}` : ''}
      </p>
      <p>{restaurant.address || 'No address on file'}</p>
      {restaurant.tags.length > 0 && <p className="place-tags">{restaurant.tags.join(' · ')}</p>}
    </>
  )
}
