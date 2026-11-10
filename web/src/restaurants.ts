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
  photo?: string
}

export function hasLocation(
  restaurant: Restaurant,
): restaurant is Restaurant & { latitude: number; longitude: number } {
  return restaurant.latitude != null && restaurant.longitude != null
}
