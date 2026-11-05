import { useEffect, useRef, useState } from 'react'
import type { Restaurant } from './restaurants'

export type Collection = {
  id: string
  name: string
  places: Restaurant[]
}

export const SAVED_COLLECTION_ID = 'saved'

export function collectionLabel(collection: Collection) {
  return collection.id === SAVED_COLLECTION_ID ? 'Saved restaurants' : collection.name
}

export function placeCollectionIds(collections: Collection[], restId: string) {
  return collections
    .filter((collection) => collection.places.some((place) => place.restId === restId))
    .map((collection) => collection.id)
}

type Result =
  | { ok: true; collections: Collection[]; id?: string }
  | { ok: false; error: string; signedOut?: boolean }

export function useCollections(token: string | null, onSignedOut: () => void) {
  const [owned, setOwned] = useState<{ token: string; collections: Collection[] } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const signedOutRef = useRef(onSignedOut)
  const collections = token && owned?.token === token ? owned.collections : []

  useEffect(() => {
    signedOutRef.current = onSignedOut
  }, [onSignedOut])

  useEffect(() => {
    if (!token) return
    const gate = { ok: true }
    request('/api/collections', 'GET', undefined, token).then((result) => {
      if (!gate.ok) return
      if (result.signedOut) {
        signedOutRef.current()
        return
      }
      if (result.ok) {
        setOwned({ token, collections: result.collections })
        setError(null)
      } else {
        setError(result.error)
      }
    })
    return () => {
      gate.ok = false
    }
  }, [token])

  async function apply(path: string, method: string, body?: unknown): Promise<Result> {
    if (!token) {
      return { ok: false, error: 'Sign in first.', signedOut: true }
    }
    const result = await request(path, method, body, token)
    if (result.signedOut) {
      signedOutRef.current()
      return result
    }
    if (result.ok) {
      setOwned({ token, collections: result.collections })
      setError(null)
    } else if (result.error === 'The Java backend is not running.') {
      setError(result.error)
    }
    return result
  }

  return {
    collections,
    error,
    addCollection(name: string, place?: Restaurant) {
      return apply('/api/collections', 'POST', { name, place: place ?? null })
    },
    toggleInCollection(collectionId: string, place: Restaurant) {
      const included = collections.some(
        (collection) =>
          collection.id === collectionId && collection.places.some((item) => item.restId === place.restId),
      )
      const id = encodeURIComponent(collectionId)
      if (included) {
        return apply(`/api/collections/${id}/places/${encodeURIComponent(place.restId)}`, 'DELETE')
      }
      return apply(`/api/collections/${id}/places`, 'POST', place)
    },
    removeFromCollection(collectionId: string, restId: string) {
      return apply(
        `/api/collections/${encodeURIComponent(collectionId)}/places/${encodeURIComponent(restId)}`,
        'DELETE',
      )
    },
    removeCollection(collectionId: string) {
      return apply(`/api/collections/${encodeURIComponent(collectionId)}`, 'DELETE')
    },
  }
}

async function request(path: string, method: string, body: unknown, token: string): Promise<Result> {
  try {
    const response = await fetch(path, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    if (response.status === 401) {
      const payload = (await response.json().catch(() => null)) as { error?: string } | null
      return { ok: false, error: payload?.error ?? 'Sign in first.', signedOut: true }
    }
    const payload = (await response.json().catch(() => null)) as {
      collections?: Collection[]
      id?: string
      error?: string
    } | null
    if (!response.ok || !payload?.collections) {
      return { ok: false, error: payload?.error ?? 'The Java backend is not running.' }
    }
    return { ok: true, collections: payload.collections, id: payload.id }
  } catch {
    return { ok: false, error: 'The Java backend is not running.' }
  }
}
