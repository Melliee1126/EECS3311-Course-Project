import { hasLocation, type Restaurant } from '../restaurants'

type Props = {
  restaurants: Restaurant[]
  query: string
  open: boolean
  placesLoading: boolean
  placesNote: string | null
  onSelect: (restId: string) => void
}

export function RestaurantCard({
  restaurants,
  query,
  open,
  placesLoading,
  placesNote,
  onSelect,
}: Props) {
  if (!open) return null

  const showEmpty = restaurants.length === 0 && !placesLoading && !placesNote

  return (
    <section className="results" aria-label="Restaurant results" aria-busy={placesLoading}>
      <h2 className="results-heading">More on Google</h2>
      {placesLoading && restaurants.length === 0 && (
        <p className="results-status">Searching restaurants near you…</p>
      )}
      {placesNote && <p className="results-status">{placesNote}</p>}
      {showEmpty && <p className="results-status">No restaurants match “{query.trim()}”.</p>}
      {restaurants.length > 0 && (
        <ul>
          {restaurants.map((restaurant) => (
            <li key={restaurant.restId}>
              <button type="button" className="result" onClick={() => onSelect(restaurant.restId)}>
                {restaurant.photo ? (
                  <img className="result-photo" src={restaurant.photo} alt="" />
                ) : (
                  <span className="result-photo" aria-hidden="true" />
                )}
                <span className="result-copy">
                  <span className="result-name">{restaurant.name}</span>
                  <span className="result-meta">
                    {[
                      restaurant.cuisine,
                      restaurant.priceRange,
                      restaurant.rating > 0 ? String(restaurant.rating) : 'Unrated',
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                  <span className="result-address">{restaurant.address || 'No address on file'}</span>
                  {!hasLocation(restaurant) && (
                    <span className="result-note">Shown without a map pin</span>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
