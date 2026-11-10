import type { Restaurant } from './restaurants'
import type { LatLng } from './userLocation'

export type TasteSnapshot = {
  interests: string[]
  likes: string[]
  dislikes: string[]
  priceComfort: string
  description: string
}

export type SuggestedRestaurant = Restaurant & {
  explanation: string
  matchReasons: string[]
}

export type AssistantReply = {
  reply: string
  recommendations: SuggestedRestaurant[]
  taste: TasteSnapshot
}

const EMPTY_TASTE: TasteSnapshot = {
  interests: [],
  likes: [],
  dislikes: [],
  priceComfort: '',
  description: '',
}

export type ChatTurn = {
  role: 'user' | 'assistant'
  text: string
}

export async function askAssistant(
  token: string,
  message: string,
  chips: string[],
  history: ChatTurn[],
  bias: LatLng,
  forYou: boolean,
): Promise<AssistantReply> {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      message,
      chips,
      history,
      latitude: bias.lat,
      longitude: bias.lng,
      forYou,
    }),
  })
  const payload = (await response.json().catch(() => null)) as (Partial<AssistantReply> & { error?: string }) | null
  if (!response.ok || !payload) {
    throw new Error(payload?.error ?? 'The Java backend is not running.')
  }
  return {
    reply: typeof payload.reply === 'string' ? payload.reply : '',
    recommendations: Array.isArray(payload.recommendations)
      ? payload.recommendations.flatMap((item) => {
          const restaurant = restaurantOf(item)
          return restaurant ? [restaurant] : []
        })
      : [],
    taste: tasteOf(payload.taste),
  }
}

export async function readTaste(token: string): Promise<TasteSnapshot> {
  const response = await fetch('/api/taste', {
    headers: { Authorization: `Bearer ${token}` },
  })
  const payload = (await response.json().catch(() => null)) as (Partial<TasteSnapshot> & { error?: string }) | null
  if (!response.ok || !payload) {
    throw new Error(payload?.error ?? 'The Java backend is not running.')
  }
  return tasteOf(payload)
}

export async function saveTaste(token: string, interests: string[], description: string): Promise<TasteSnapshot> {
  const response = await fetch('/api/taste', {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ interests, description }),
  })
  const payload = (await response.json().catch(() => null)) as (Partial<TasteSnapshot> & { error?: string }) | null
  if (!response.ok || !payload) {
    throw new Error(payload?.error ?? 'The Java backend is not running.')
  }
  return tasteOf(payload)
}

function tasteOf(value: Partial<TasteSnapshot> | undefined): TasteSnapshot {
  if (!value) return EMPTY_TASTE
  return {
    interests: strings(value.interests).slice(0, 8),
    likes: strings(value.likes).slice(0, 12),
    dislikes: strings(value.dislikes).slice(0, 12),
    priceComfort: typeof value.priceComfort === 'string' ? value.priceComfort : '',
    description: typeof value.description === 'string' ? value.description : '',
  }
}

function strings(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
}

function restaurantOf(value: unknown): SuggestedRestaurant | null {
  if (!value || typeof value !== 'object') return null
  const raw = value as Record<string, unknown>
  if (typeof raw.restId !== 'string' || typeof raw.name !== 'string') return null
  const status = raw.status === 'saved' || raw.status === 'visited' || raw.status === 'discovered' ? raw.status : 'discovered'
  return {
    restId: raw.restId,
    name: raw.name,
    cuisine: typeof raw.cuisine === 'string' ? raw.cuisine : '',
    address: typeof raw.address === 'string' ? raw.address : '',
    latitude: typeof raw.latitude === 'number' ? raw.latitude : null,
    longitude: typeof raw.longitude === 'number' ? raw.longitude : null,
    rating: typeof raw.rating === 'number' ? raw.rating : 0,
    priceRange: typeof raw.priceRange === 'string' ? raw.priceRange : '',
    status,
    tags: strings(raw.tags),
    photo: typeof raw.photo === 'string' ? raw.photo : '',
    explanation: typeof raw.explanation === 'string' ? raw.explanation : '',
    matchReasons: strings(raw.matchReasons),
  }
}
