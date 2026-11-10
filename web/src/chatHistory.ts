import type { ChatMessage } from './components/ChatCard'
import type { SuggestedRestaurant } from './assistant'

export type ChatSession = {
  id: string
  title: string
  updatedAt: number
  messages: ChatMessage[]
}

const LIMIT = 20

function storageKey(accountId: string) {
  return `food-chat-history:${accountId}`
}

export function loadChatSessions(accountId: string): ChatSession[] {
  try {
    const raw = localStorage.getItem(storageKey(accountId))
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed.flatMap((item) => sessionOf(item)).slice(0, LIMIT)
  } catch {
    return []
  }
}

export function saveChatSessions(accountId: string, sessions: ChatSession[]) {
  localStorage.setItem(storageKey(accountId), JSON.stringify(sessions.slice(0, LIMIT)))
}

function sessionOf(value: unknown): ChatSession[] {
  if (!value || typeof value !== 'object') return []
  const item = value as Partial<ChatSession>
  if (typeof item.id !== 'string' || !Array.isArray(item.messages)) return []
  const messages = item.messages.flatMap((message) => messageOf(message))
  if (messages.length === 0) return []
  const title =
    typeof item.title === 'string' && item.title.trim()
      ? item.title.trim().slice(0, 48)
      : messages.find((message) => message.role === 'user')?.text.slice(0, 48) || 'Chat'
  return [
    {
      id: item.id,
      title,
      updatedAt: typeof item.updatedAt === 'number' ? item.updatedAt : Date.now(),
      messages,
    },
  ]
}

function messageOf(value: unknown): ChatMessage[] {
  if (!value || typeof value !== 'object') return []
  const item = value as Partial<ChatMessage>
  if ((item.role !== 'user' && item.role !== 'assistant') || typeof item.text !== 'string') return []
  const picks = Array.isArray(item.picks) ? item.picks.flatMap((place) => placeOf(place)) : []
  return [{ role: item.role, text: item.text, picks }]
}

function placeOf(value: unknown): SuggestedRestaurant[] {
  if (!value || typeof value !== 'object') return []
  const raw = value as Record<string, unknown>
  if (typeof raw.restId !== 'string' || typeof raw.name !== 'string') return []
  const status = raw.status === 'saved' || raw.status === 'visited' || raw.status === 'discovered' ? raw.status : 'discovered'
  return [
    {
      restId: raw.restId,
      name: raw.name,
      cuisine: typeof raw.cuisine === 'string' ? raw.cuisine : '',
      address: typeof raw.address === 'string' ? raw.address : '',
      latitude: typeof raw.latitude === 'number' ? raw.latitude : null,
      longitude: typeof raw.longitude === 'number' ? raw.longitude : null,
      rating: typeof raw.rating === 'number' ? raw.rating : 0,
      priceRange: typeof raw.priceRange === 'string' ? raw.priceRange : '',
      status,
      tags: Array.isArray(raw.tags) ? raw.tags.filter((tag): tag is string => typeof tag === 'string') : [],
      photo: typeof raw.photo === 'string' ? raw.photo : '',
      explanation: typeof raw.explanation === 'string' ? raw.explanation : '',
      matchReasons: Array.isArray(raw.matchReasons)
        ? raw.matchReasons.filter((reason): reason is string => typeof reason === 'string')
        : [],
    },
  ]
}
