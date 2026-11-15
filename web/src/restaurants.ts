export type Restaurant = {
  restId: string
  name: string
  cuisine: string
  address: string
  latitude: number | null
  longitude: number | null
  rating: number
  priceRange: string
  priceSpan?: string
  status: 'saved' | 'visited' | 'discovered'
  tags: string[]
  photo?: string
}

const TAG_LABELS: Record<string, string> = {
  hamburger_restaurant: 'Burgers',
  barbecue_restaurant: 'BBQ',
  barbeque_restaurant: 'BBQ',
  sandwich_shop: 'Sandwiches',
  meal_takeaway: 'Takeout',
  meal_delivery: 'Delivery',
  fast_food_restaurant: 'Fast food',
  fine_dining_restaurant: 'Fine dining',
  bar_and_grill: 'Bar and grill',
  wine_bar: 'Wine',
  irish_pub: 'Pub',
  pub: 'Pub',
  bar: 'Bar',
  cafe: 'Cafe',
  cat_cafe: 'Cafe',
  dog_cafe: 'Cafe',
  donut_shop: 'Donuts',
  bagel_shop: 'Bagels',
  juice_shop: 'Juice',
  acai_shop: 'Acai',
  chocolate_shop: 'Dessert',
  chocolate_factory: 'Dessert',
  confectionery: 'Dessert',
  candy_store: 'Dessert',
  deli: 'Deli',
  diner: 'Diner',
  buffet_restaurant: 'Buffet',
  cafeteria: 'Cafeteria',
  food_court: 'Food court',
}

const SKIPPED_TYPES = new Set([
  'restaurant',
  'food',
  'point_of_interest',
  'establishment',
  'store',
  'premise',
  'political',
  'geocode',
  'plus_code',
  'street_address',
  'route',
  'locality',
  'neighborhood',
  'finance',
  'health',
  'lodging',
])

/** Turns a Google place type such as japanese_restaurant into Japanese. A label already written for people is left as it is. */
export function readableTag(tag: string): string {
  const trimmed = tag.trim()
  if (!trimmed) return ''
  const key = trimmed.toLowerCase().replace(/-/g, '_')
  if (!key.includes('_') && !TAG_LABELS[key] && !SKIPPED_TYPES.has(key)) return trimmed
  if (SKIPPED_TYPES.has(key)) return ''
  if (TAG_LABELS[key]) return TAG_LABELS[key]
  let phrase = key
  for (const suffix of ['_restaurant', '_shop', '_store', '_house']) {
    if (phrase.endsWith(suffix) && phrase.length > suffix.length) {
      phrase = phrase.slice(0, -suffix.length)
      break
    }
  }
  phrase = phrase.replace(/_/g, ' ').trim()
  if (!phrase || SKIPPED_TYPES.has(phrase.replace(/ /g, '_'))) return ''
  return TAG_LABELS[phrase.replace(/ /g, '_')] ?? phrase.replace(/\b[a-z]/g, (letter) => letter.toUpperCase())
}

export function readableCuisine(cuisine: string): string {
  const trimmed = cuisine.trim()
  if (!trimmed) return ''
  const label = readableTag(trimmed)
  if (label) return label
  return trimmed.toLowerCase() === 'restaurant' ? 'Restaurant' : ''
}

export function readableTags(tags: string[]): string[] {
  const seen = new Set<string>()
  const labels: string[] = []
  for (const tag of tags) {
    const label = readableTag(tag)
    const key = label.toLowerCase()
    if (!label || seen.has(key)) continue
    seen.add(key)
    labels.push(label)
  }
  return labels
}

export function placeCategoryTags(restaurant: Pick<Restaurant, 'tags' | 'cuisine'>): string[] {
  return readableTags([...restaurant.tags, restaurant.cuisine])
    .filter((tag) => tag.toLowerCase() !== 'restaurant')
    .slice(0, 8)
}

export function visibleTags(restaurant: Pick<Restaurant, 'tags' | 'cuisine'>, hideCuisine = false) {
  const cuisine = readableCuisine(restaurant.cuisine).toLowerCase()
  return readableTags(restaurant.tags).filter((label) => {
    const key = label.toLowerCase()
    return !(hideCuisine && (key === cuisine || key === 'restaurant'))
  })
}

export function hasLocation(
  restaurant: Restaurant,
): restaurant is Restaurant & { latitude: number; longitude: number } {
  return restaurant.latitude != null && restaurant.longitude != null
}
