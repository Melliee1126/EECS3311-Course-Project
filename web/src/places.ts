import type { Restaurant } from './restaurants'
import type { LatLng } from './userLocation'

type PlaceResult = {
  id?: string
  displayName?: string | { text?: string }
  formattedAddress?: string
  location?: { lat?: number | (() => number); lng?: number | (() => number) } | null
  rating?: number | null
  priceLevel?: string | number | null
  primaryType?: string | null
}

type SearchablePlaces = {
  Place: {
    searchByText?: (request: {
      textQuery: string
      fields: string[]
      includedType: string
      useStrictTypeFiltering: boolean
      locationBias: LatLng
      maxResultCount: number
      language: string
      region: string
    }) => Promise<{ places?: PlaceResult[] }>
  }
}

const PRICE_LABELS: Record<string, string> = {
  '0': '$',
  '1': '$',
  '2': '$$',
  '3': '$$$',
  '4': '$$$$',
  FREE: '$',
  INEXPENSIVE: '$',
  MODERATE: '$$',
  EXPENSIVE: '$$$',
  VERY_EXPENSIVE: '$$$$',
  PRICE_LEVEL_FREE: '$',
  PRICE_LEVEL_INEXPENSIVE: '$',
  PRICE_LEVEL_MODERATE: '$$',
  PRICE_LEVEL_EXPENSIVE: '$$$',
  PRICE_LEVEL_VERY_EXPENSIVE: '$$$$',
}

export async function searchRestaurants(
  placesLibrary: google.maps.PlacesLibrary,
  query: string,
  bias: LatLng,
): Promise<Restaurant[]> {
  const searchable = placesLibrary as unknown as SearchablePlaces
  if (typeof searchable.Place?.searchByText !== 'function') {
    throw new Error('Places text search is unavailable')
  }

  const { places = [] } = await searchable.Place.searchByText({
    textQuery: query,
    fields: ['id', 'displayName', 'formattedAddress', 'location', 'rating', 'priceLevel', 'primaryType'],
    includedType: 'restaurant',
    useStrictTypeFiltering: false,
    locationBias: bias,
    maxResultCount: 20,
    language: 'en',
    region: 'ca',
  })

  return places.flatMap(toRestaurant)
}

export function placesFailureMessage(error: unknown): string {
  const detail = error instanceof Error ? error.message : String(error)
  if (/enabled|denied|api key|referer|referrer/i.test(detail)) {
    return 'Google Places is blocked for this key. Enable Places API (New) and allow this site on the key.'
  }
  return "Couldn't load restaurants from Google Places."
}

function toRestaurant(place: PlaceResult): Restaurant[] {
  const name = readText(place.displayName)
  if (!name) return []
  const address = readText(place.formattedAddress)
  const id = readText(place.id) || `${name}-${address}`
  return [
    {
      restId: `g:${id}`,
      name,
      cuisine: cuisineFromType(place.primaryType),
      address,
      latitude: coordinate(place.location, 'lat'),
      longitude: coordinate(place.location, 'lng'),
      rating: typeof place.rating === 'number' ? place.rating : 0,
      priceRange: priceRange(place.priceLevel),
      status: 'discovered',
      tags: place.primaryType ? [place.primaryType] : [],
    },
  ]
}

function cuisineFromType(primaryType: string | null | undefined): string {
  if (!primaryType) return 'Restaurant'
  const label = primaryType
    .split('_')
    .filter((word) => word !== 'restaurant')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
  return label || 'Restaurant'
}

function priceRange(level: string | number | null | undefined): string {
  if (level == null || level === '') return ''
  return PRICE_LABELS[String(level).toUpperCase()] ?? ''
}

function coordinate(
  location: PlaceResult['location'],
  axis: 'lat' | 'lng',
): number | null {
  const raw = location?.[axis]
  const value = typeof raw === 'function' ? raw() : raw
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function readText(value: string | { text?: string } | null | undefined): string {
  if (!value) return ''
  if (typeof value === 'string') return value
  return value.text ?? ''
}
