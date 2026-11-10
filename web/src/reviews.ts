import { useEffect, useState } from 'react'

export type Review = {
  id: string
  restId: string
  name: string
  address: string
  latitude: number | null
  longitude: number | null
  rating: number
  body: string
  tags: string[]
  photo: string
  photos: string[]
}

export type ReviewDraft = {
  restId: string
  name: string
  address: string
  latitude: number | null
  longitude: number | null
  rating: number
  body: string
  tags: string[]
  photo: string
  photos: string[]
}

export function useReviews(token: string | null) {
  const [reviews, setReviews] = useState<Review[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      return
    }
    const gate = { ok: true }
    fetch('/api/reviews', { headers: { Authorization: `Bearer ${token}` } })
      .then(async (response) => {
        const payload = (await response.json().catch(() => null)) as { reviews?: Review[]; error?: string } | null
        if (!gate.ok) return
        if (response.ok && payload?.reviews) {
          setReviews(payload.reviews.map(withPhotos))
          setError(null)
        } else if (response.status !== 401) {
          setError(payload?.error ?? 'The Java backend is not running.')
        }
      })
      .catch(() => {
        if (gate.ok) setError('The Java backend is not running.')
      })
    return () => {
      gate.ok = false
    }
  }, [token])

  const visible = token ? reviews : []

  async function save(method: string, path: string, draft?: ReviewDraft): Promise<string | null> {
    if (!token) return 'Sign in to publish a review.'
    try {
      const response = await fetch(path, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          ...(draft ? { 'Content-Type': 'application/json' } : {}),
        },
        body: draft ? JSON.stringify(draft) : undefined,
      })
      const payload = (await response.json().catch(() => null)) as { reviews?: Review[]; error?: string } | null
      if (!response.ok || !payload?.reviews) {
        return payload?.error ?? 'The Java backend is not running.'
      }
      setReviews(payload.reviews.map(withPhotos))
      setError(null)
      return null
    } catch {
      setError('The Java backend is not running.')
      return 'The Java backend is not running.'
    }
  }

  return {
    reviews: visible,
    error: token ? error : null,
    publish(draft: ReviewDraft) {
      return save('POST', '/api/reviews', draft)
    },
    update(id: string, draft: ReviewDraft) {
      return save('PATCH', `/api/reviews/${encodeURIComponent(id)}`, draft)
    },
    remove(id: string) {
      return save('DELETE', `/api/reviews/${encodeURIComponent(id)}`)
    },
  }
}

function withPhotos(review: Review): Review {
  const photos = review.photos?.length ? review.photos : review.photo ? [review.photo] : []
  return { ...review, photo: photos[0] ?? '', photos }
}
