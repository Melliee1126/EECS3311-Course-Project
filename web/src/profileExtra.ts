import { useSyncExternalStore } from 'react'

export type ProfileExtra = {
  description: string
  interests: string[]
  avatar: string
}

const EMPTY: ProfileExtra = { description: '', interests: [], avatar: '' }

const listeners = new Set<() => void>()
let snapshotId: string | null = null
let snapshotRaw: string | null = null
let snapshot: ProfileExtra = EMPTY

function storageKey(accountId: string) {
  return `food-assistant-profile:${accountId}`
}

function parse(raw: string | null): ProfileExtra {
  if (!raw) return EMPTY
  try {
    const parsed = JSON.parse(raw) as Partial<ProfileExtra>
    return {
      description: typeof parsed.description === 'string' ? parsed.description : '',
      interests: Array.isArray(parsed.interests)
        ? parsed.interests.filter((tag): tag is string => typeof tag === 'string').slice(0, 8)
        : [],
      avatar: typeof parsed.avatar === 'string' ? parsed.avatar : '',
    }
  } catch {
    return EMPTY
  }
}

export function readProfileExtra(accountId: string | null): ProfileExtra {
  if (!accountId) return EMPTY
  const raw = localStorage.getItem(storageKey(accountId))
  if (snapshotId === accountId && snapshotRaw === raw) return snapshot
  snapshotId = accountId
  snapshotRaw = raw
  snapshot = parse(raw)
  return snapshot
}

export function writeProfileExtra(accountId: string, next: ProfileExtra) {
  const raw = JSON.stringify(next)
  localStorage.setItem(storageKey(accountId), raw)
  snapshotId = accountId
  snapshotRaw = raw
  snapshot = next
  listeners.forEach((listener) => listener())
}

export function useProfileExtra(accountId: string | null) {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => readProfileExtra(accountId),
    () => EMPTY,
  )
}

export function readAvatarFile(file: File): Promise<string | null> {
  return new Promise((resolve) => {
    const image = new Image()
    const url = URL.createObjectURL(file)
    image.onload = () => {
      const scale = Math.min(1, 256 / Math.max(image.width, image.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(image.width * scale))
      canvas.height = Math.max(1, Math.round(image.height * scale))
      canvas.getContext('2d')?.drawImage(image, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      const data = canvas.toDataURL('image/jpeg', 0.8)
      resolve(data.length > 120_000 ? null : data)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    image.src = url
  })
}
