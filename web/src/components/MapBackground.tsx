import { useEffect, useRef } from 'react'
import { AdvancedMarker, Map, Pin, useMap } from '@vis.gl/react-google-maps'
import { hasLocation, type Restaurant } from '../restaurants'
import { TORONTO, type LatLng } from '../userLocation'

type Props = {
  restaurants: Restaurant[]
  selectedId: string | null
  fitToResults: boolean
  lockToResults: boolean
  userPosition: LatLng | null
  onSelect: (restId: string) => void
}

const TORONTO_ZOOM = 12
const USER_ZOOM = 14

const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim() ?? ''

function UserLocation({
  position,
  lockToResults,
}: {
  position: LatLng | null
  lockToResults: boolean
}) {
  const map = useMap()
  const centered = useRef(false)

  useEffect(() => {
    if (!map || !position || centered.current || lockToResults) return
    map.panTo(position)
    map.setZoom(USER_ZOOM)
    centered.current = true
  }, [map, position, lockToResults])

  if (!position) return null

  return (
    <AdvancedMarker position={position}>
      <Pin background="#d23b4a" borderColor="#9c1f32" glyphColor="#ffffff" />
    </AdvancedMarker>
  )
}

function ResultMarkers({
  restaurants,
  selectedId,
  fitToResults,
  onSelect,
}: Omit<Props, 'lockToResults' | 'userPosition'>) {
  const map = useMap()
  const located = restaurants.filter(hasLocation)

  useEffect(() => {
    if (!map || !fitToResults) return
    const pins = restaurants.filter(hasLocation)
    if (pins.length === 0) return
    if (pins.length === 1) {
      map.panTo({ lat: pins[0].latitude, lng: pins[0].longitude })
      map.setZoom(14)
      return
    }
    const bounds = new google.maps.LatLngBounds()
    for (const restaurant of pins) {
      bounds.extend({ lat: restaurant.latitude, lng: restaurant.longitude })
    }
    map.fitBounds(bounds, { top: 140, right: 48, bottom: 48, left: 480 })
  }, [map, fitToResults, restaurants])

  useEffect(() => {
    if (!map || !selectedId) return
    const chosen = restaurants.find((restaurant) => restaurant.restId === selectedId)
    if (!chosen || !hasLocation(chosen)) return
    map.panTo({ lat: chosen.latitude, lng: chosen.longitude })
    map.setZoom(15)
  }, [map, selectedId, restaurants])

  return (
    <>
      {located.map((restaurant) => {
        const selected = restaurant.restId === selectedId
        const colors = pinColors(restaurant, selected)
        return (
          <AdvancedMarker
            key={restaurant.restId}
            position={{ lat: restaurant.latitude, lng: restaurant.longitude }}
            title={restaurant.name}
            zIndex={selected ? 2 : 1}
            onClick={() => onSelect(restaurant.restId)}
          >
            <Pin
              background={colors.background}
              borderColor={colors.borderColor}
              glyphColor={colors.glyphColor}
            />
          </AdvancedMarker>
        )
      })}
    </>
  )
}

function pinColors(restaurant: Restaurant, selected: boolean) {
  if (selected) {
    return { background: '#1a1a1a', borderColor: '#1a1a1a', glyphColor: '#ffffff' }
  }
  if (restaurant.status === 'discovered') {
    return { background: '#c47b3a', borderColor: '#8a5424', glyphColor: '#ffffff' }
  }
  return { background: '#2f6f62', borderColor: '#1d4a42', glyphColor: '#ffffff' }
}

export function MapBackground({
  restaurants,
  selectedId,
  fitToResults,
  lockToResults,
  userPosition,
  onSelect,
}: Props) {
  if (!apiKey) {
    return (
      <div className="map-layer map-fallback" role="status">
        <p>
          Add <code>VITE_GOOGLE_MAPS_API_KEY</code> to <code>web/.env.local</code> and
          enable the Maps JavaScript API and Places API (New).
        </p>
      </div>
    )
  }

  return (
    <div className="map-layer">
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
        <UserLocation position={userPosition} lockToResults={lockToResults} />
        <ResultMarkers
          restaurants={restaurants}
          selectedId={selectedId}
          fitToResults={fitToResults}
          onSelect={onSelect}
        />
      </Map>
    </div>
  )
}
