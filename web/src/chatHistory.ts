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

export function readLocalSessions(accountId: string): ChatSession[] {
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

function writeLocalSessions(accountId: string, sessions: ChatSession[]) {
  localStorage.setItem(storageKey(accountId), JSON.stringify(sessions.slice(0, LIMIT)))
}

function clearLocalSessions(accountId: string) {
  localStorage.removeItem(storageKey(accountId))
}

/** Every restaurant recommended in one chat, in the order they first appeared. */
export function sessionPicks(messages: ChatMessage[]): SuggestedRestaurant[] {
  const byId = new Map<string, SuggestedRestaurant>()
  const order: string[] = []
  for (const message of messages) {
    if (message.role !== 'assistant') continue
    for (const place of message.picks) {
      if (!byId.has(place.restId)) order.push(place.restId)
      byId.set(place.restId, place)
    }
  }
  return order.flatMap((id) => {
    const place = byId.get(id)
    return place ? [place] : []
  })
}

export function sessionFromThread(
  sessions: ChatSession[],
  id: string,
  messages: ChatMessage[],
  updatedAt = Date.now(),
): ChatSession {
  const previous = sessions.find((session) => session.id === id)
  const title =
    previous?.title || messages.find((message) => message.role === 'user')?.text.slice(0, 48) || 'Chat'
  return { id, title, updatedAt, messages }
}

export function withSession(sessions: ChatSession[], session: ChatSession): ChatSession[] {
  return [session, ...sessions.filter((item) => item.id !== session.id)]
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .slice(0, LIMIT)
}

/** Keeps the chat open on screen when a reload returns the saved copy. */
export function retainOpenThread(
  sessions: ChatSession[],
  id: string | null,
  messages: ChatMessage[],
): ChatSession[] {
  if (!id || messages.length === 0) return sessions
  const existing = sessions.find((session) => session.id === id)
  if (existing && sameThread(existing.messages, messages)) return sessions
  return withSession(sessions, sessionFromThread(sessions, id, messages))
}

export async function loadAccountHistory(
  token: string,
  accountId: string,
): Promise<{ sessions: ChatSession[]; error: string | null }> {
  const local = readLocalSessions(accountId)
  try {
    const remote = await requestHistory(token, 'GET')
    const merged = mergeSessions(remote, local)
    if (!sameList(merged, remote)) {
      const saved = await persistChatHistory(token, accountId, merged)
      if (saved.sessions) return { sessions: saved.sessions, error: null }
      return { sessions: merged, error: saved.error }
    }
    clearLocalSessions(accountId)
    return { sessions: remote, error: null }
  } catch (loadError: unknown) {
    return {
      sessions: local,
      error: loadError instanceof Error ? loadError.message : 'Could not load the chat history.',
    }
  }
}

export async function persistChatHistory(
  token: string,
  accountId: string,
  sessions: ChatSession[],
): Promise<{ sessions: ChatSession[] | null; error: string | null }> {
  try {
    const saved = await requestHistory(token, 'PUT', sessions)
    clearLocalSessions(accountId)
    return { sessions: saved, error: null }
  } catch (saveError: unknown) {
    writeLocalSessions(accountId, sessions)
    return {
      sessions: null,
      error: saveError instanceof Error ? saveError.message : 'Could not save the chat history.',
    }
  }
}

function mergeSessions(remote: ChatSession[], local: ChatSession[]): ChatSession[] {
  const byId = new Map<string, ChatSession>()
  for (const session of [...remote, ...local]) {
    const existing = byId.get(session.id)
    if (!existing || session.updatedAt >= existing.updatedAt) byId.set(session.id, session)
  }
  return [...byId.values()].sort((left, right) => right.updatedAt - left.updatedAt).slice(0, LIMIT)
}

function sameList(left: ChatSession[], right: ChatSession[]): boolean {
  if (left.length !== right.length) return false
  return left.every((session, index) => session.id === right[index].id && session.updatedAt === right[index].updatedAt)
}

function sameThread(left: ChatMessage[], right: ChatMessage[]): boolean {
  if (left.length !== right.length) return false
  return left.every((message, index) => {
    const other = right[index]
    if (message.role !== other.role || message.text !== other.text || message.picks.length !== other.picks.length) {
      return false
    }
    return message.picks.every((place, pick) => place.restId === other.picks[pick].restId)
  })
}

async function requestHistory(token: string, method: 'GET' | 'PUT', sessions?: ChatSession[]): Promise<ChatSession[]> {
  const response = await fetch('/api/chat/history', {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(method === 'PUT' ? { 'Content-Type': 'application/json' } : {}),
    },
    body: method === 'PUT' ? JSON.stringify({ sessions: sessions ?? [] }) : undefined,
  })
  const payload = (await response.json().catch(() => null)) as { sessions?: unknown; error?: string } | null
  if (!response.ok || !payload) {
    throw new Error(payload?.error ?? 'The Java backend is not running.')
  }
  if (!Array.isArray(payload.sessions)) return []
  return payload.sessions.flatMap((item) => sessionOf(item)).slice(0, LIMIT)
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
      priceSpan: typeof raw.priceSpan === 'string' ? raw.priceSpan : '',
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
