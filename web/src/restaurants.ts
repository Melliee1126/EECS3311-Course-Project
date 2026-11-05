export type Restaurant = {
  restId: string
  name: string
  cuisine: string
  address: string
  latitude: number | null
  longitude: number | null
  rating: number
  priceRange: string
  status: 'saved' | 'visited' | 'discovered'
  tags: string[]
}

export function mergeRestaurants(saved: Restaurant[], discovered: Restaurant[]) {
  const names = new Set(saved.map((restaurant) => restaurant.name.trim().toLowerCase()))
  const extra = discovered.filter((restaurant) => !names.has(restaurant.name.trim().toLowerCase()))
  return [...saved, ...extra]
}

export function hasLocation(
  restaurant: Restaurant,
): restaurant is Restaurant & { latitude: number; longitude: number } {
  return restaurant.latitude != null && restaurant.longitude != null
}

export function matchesQuery(restaurant: Restaurant, query: string) {
  const needle = query.trim().toLowerCase()
  if (!needle) return true
  const haystack = [
    restaurant.name,
    restaurant.cuisine,
    restaurant.address,
    restaurant.status,
    restaurant.priceRange,
    ...restaurant.tags,
  ]
    .join(' ')
    .toLowerCase()
  return haystack.includes(needle)
}

export async function loadRestaurants(): Promise<Restaurant[]> {
  const response = await fetch('/restaurants.json')
  if (!response.ok) {
    throw new Error('Restaurant data is unavailable')
  }
  return response.json() as Promise<Restaurant[]>
}
