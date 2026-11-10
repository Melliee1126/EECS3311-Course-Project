import type { Restaurant } from './restaurants'
import type { LatLng } from './userLocation'

type SearchPayload = {
  restaurants?: Restaurant[]
  error?: string
}

export async function searchRestaurants(query: string, bias: LatLng): Promise<Restaurant[]> {
  const params = new URLSearchParams({
    q: query,
    lat: String(bias.lat),
    lng: String(bias.lng),
  })
  const response = await fetch(`/api/search?${params}`)
  const payload = (await response.json().catch(() => null)) as SearchPayload | null
  if (!response.ok || !payload?.restaurants) {
    throw new Error(payload?.error ?? "Couldn't load restaurants from Google Places.")
  }
  return payload.restaurants
}

export function placesFailureMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return "Couldn't load restaurants from Google Places."
}
