import { useEffect, useState } from 'react'

export type LatLng = { lat: number; lng: number }

export const TORONTO: LatLng = { lat: 43.7, lng: -79.38 }

const EARTH_RADIUS_KM = 6371

export function distanceKm(from: LatLng, to: LatLng): number {
  const dLat = radians(to.lat - from.lat)
  const dLng = radians(to.lng - from.lng)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(radians(from.lat)) * Math.cos(radians(to.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

function radians(degrees: number) {
  return (degrees * Math.PI) / 180
}

export function useUserLocation(): LatLng | null {
  const [position, setPosition] = useState<LatLng | null>(null)

  useEffect(() => {
    if (!navigator.geolocation) return

    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        setPosition({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
        })
      },
      () => {
        // Denied or unavailable: callers keep the Toronto view.
      },
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 10000 },
    )

    return () => navigator.geolocation.clearWatch(watchId)
  }, [])

  return position
}
