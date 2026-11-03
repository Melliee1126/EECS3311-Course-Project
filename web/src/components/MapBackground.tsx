import { useEffect, useRef, useState } from 'react'
import {
  AdvancedMarker,
  APIProvider,
  Map,
  Pin,
  useMap,
} from '@vis.gl/react-google-maps'

const TORONTO = { lat: 43.7, lng: -79.38 }
const TORONTO_ZOOM = 12
const USER_ZOOM = 14

const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim() ?? ''

function UserLocation() {
  const map = useMap()
  const [position, setPosition] = useState<google.maps.LatLngLiteral | null>(null)
  const centered = useRef(false)

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
        // Denied or unavailable: keep the Toronto view and show no pin.
      },
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 10000 },
    )

    return () => navigator.geolocation.clearWatch(watchId)
  }, [])

  useEffect(() => {
    if (!map || !position || centered.current) return
    map.panTo(position)
    map.setZoom(USER_ZOOM)
    centered.current = true
  }, [map, position])

  if (!position) return null

  return (
    <AdvancedMarker position={position}>
      <Pin background="#d23b4a" borderColor="#9c1f32" glyphColor="#ffffff" />
    </AdvancedMarker>
  )
}

export function MapBackground() {
  if (!apiKey) {
    return (
      <div className="map-layer map-fallback" role="status">
        <p>
          Add <code>VITE_GOOGLE_MAPS_API_KEY</code> to <code>web/.env.local</code>{' '}
          and enable the Maps JavaScript API.
        </p>
      </div>
    )
  }

  return (
    <div className="map-layer">
      <APIProvider apiKey={apiKey}>
        <Map
          className="map-canvas"
          defaultCenter={TORONTO}
          defaultZoom={TORONTO_ZOOM}
          gestureHandling="greedy"
          disableDefaultUI
          mapId="DEMO_MAP_ID"
          colorScheme="LIGHT"
          reuseMaps
        >
          <UserLocation />
        </Map>
      </APIProvider>
    </div>
  )
}
