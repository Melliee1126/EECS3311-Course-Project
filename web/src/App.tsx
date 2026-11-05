import { useEffect, useRef, useState } from 'react'
import { APIProvider, useMapsLibrary } from '@vis.gl/react-google-maps'
import { useAccount } from './account'
import { AccountPanel } from './components/AccountPanel'
import { ChatCard } from './components/ChatCard'
import { CollectionsPanel, CreateCollectionDialog } from './components/CollectionsPanel'
import { MapBackground } from './components/MapBackground'
import { PlaceDetail } from './components/PlaceDetail'
import { RestaurantCard } from './components/RestaurantCard'
import { SearchBar } from './components/SearchBar'
import { Sidebar } from './components/Sidebar'
import { useCollections } from './collections'
import { ReviewPanel } from './components/ReviewPanel'
import { useReviews } from './reviews'
import { placesFailureMessage, searchRestaurants } from './places'
import type { Restaurant } from './restaurants'
import { TORONTO, useUserLocation, type LatLng } from './userLocation'

const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim() ?? ''
const NO_RESTAURANTS: Restaurant[] = []

type PlaceSearch = {
  query: string
  restaurants: Restaurant[]
  note: string | null
}

export default function App() {
  if (!apiKey) {
    return <FoodApp places={null} />
  }

  return (
    <APIProvider apiKey={apiKey}>
      <PlacesSearch />
    </APIProvider>
  )
}

function PlacesSearch() {
  const places = useMapsLibrary('places')
  return <FoodApp places={places} />
}

function FoodApp({ places }: { places: google.maps.PlacesLibrary | null }) {
  const userPosition = useUserLocation()
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Restaurant | null>(null)
  const [listOpen, setListOpen] = useState(false)
  const [placeSearch, setPlaceSearch] = useState<PlaceSearch | null>(null)
  const [libraryOpen, setLibraryOpen] = useState(false)
  const [activeCollectionId, setActiveCollectionId] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [attachPlace, setAttachPlace] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const [accountNotice, setAccountNotice] = useState<string | null>(null)
  const [reviewOpen, setReviewOpen] = useState(false)
  const accountApi = useAccount()
  const collectionsApi = useCollections(accountApi.token, accountApi.signOut)
  const reviewsApi = useReviews(accountApi.token)
  const hasUserPosition = userPosition != null
  const positionRef = useRef<LatLng | null>(null)
  const searchGeneration = useRef(0)

  useEffect(() => {
    positionRef.current = userPosition
  }, [userPosition])

  useEffect(() => {
    const trimmed = query.trim()
    if (!apiKey || trimmed.length < 2 || !places) return

    const generation = ++searchGeneration.current
    const handle = window.setTimeout(() => {
      const bias = positionRef.current ?? TORONTO
      searchRestaurants(places, trimmed, bias)
        .then((found) => {
          if (searchGeneration.current !== generation) return
          setPlaceSearch({ query: trimmed, restaurants: found, note: null })
        })
        .catch((searchError: unknown) => {
          if (searchGeneration.current !== generation) return
          console.error(searchError)
          setPlaceSearch({
            query: trimmed,
            restaurants: [],
            note: placesFailureMessage(searchError),
          })
        })
    }, 400)

    return () => {
      window.clearTimeout(handle)
    }
  }, [places, query, hasUserPosition])

  const trimmedQuery = query.trim()
  const placesActive = apiKey.length > 0 && trimmedQuery.length >= 2
  const searchMatches = placeSearch?.query === trimmedQuery
  const placesLoading = placesActive && (places == null || !searchMatches)
  const placesNote =
    !apiKey && trimmedQuery.length >= 2
      ? 'Add a Google Maps key to discover restaurants.'
      : placesActive && searchMatches
        ? placeSearch.note
        : null
  const results = placesActive && searchMatches ? placeSearch.restaurants : NO_RESTAURANTS
  const searching = trimmedQuery.length >= 2
  const activeCollection = collectionsApi.collections.find(
    (collection) => collection.id === activeCollectionId,
  )
  const viewed = activeCollection?.places ?? []
  const mapPlaces = searching ? results : viewed
  const pins =
    selected && !mapPlaces.some((place) => place.restId === selected.restId)
      ? [...mapPlaces, selected]
      : mapPlaces

  function changeQuery(next: string) {
    setQuery(next)
    setSelected(null)
    setListOpen(next.trim().length >= 2)
  }

  function chooseRestaurant(restId: string) {
    const restaurant =
      pins.find((place) => place.restId === restId) ??
      collectionsApi.collections
        .flatMap((collection) => collection.places)
        .find((place) => place.restId === restId)
    if (!restaurant) return
    setSelected(restaurant)
    setListOpen(false)
  }

  function openAccount(notice: string | null) {
    setAccountOpen(true)
    setAccountNotice(notice)
    setLibraryOpen(false)
    setCreateOpen(false)
    setReviewOpen(false)
  }

  function requireAccount() {
    if (!accountApi.ready || accountApi.account) return accountApi.account != null
    openAccount('Sign in to keep your own collections.')
    return false
  }

  function openSaved() {
    if (!requireAccount()) return
    setAccountOpen(false)
    setReviewOpen(false)
    if (libraryOpen && activeCollectionId == null) {
      setLibraryOpen(false)
      return
    }
    setLibraryOpen(true)
    setActiveCollectionId(null)
  }

  function toggleReview() {
    if (reviewOpen) {
      setReviewOpen(false)
      return
    }
    setReviewOpen(true)
    setLibraryOpen(false)
    setAccountOpen(false)
    setCreateOpen(false)
  }

  function toggleProfile() {
    if (accountOpen) {
      setAccountOpen(false)
      setAccountNotice(null)
      return
    }
    openAccount(null)
  }

  function closeCreate() {
    setCreateOpen(false)
    setAttachPlace(false)
  }

  return (
    <div className="app">
      <MapBackground
        restaurants={pins}
        selectedId={selected?.restId ?? null}
        fitToResults={(searching || viewed.length > 0) && selected == null}
        lockToResults={searching || viewed.length > 0 || selected != null}
        userPosition={userPosition}
        onSelect={chooseRestaurant}
      />
      <Sidebar
        libraryOpen={libraryOpen}
        accountOpen={accountOpen}
        reviewOpen={reviewOpen}
        onSaved={openSaved}
        onCreate={toggleReview}
        onProfile={toggleProfile}
      />
      {libraryOpen && (
        <CollectionsPanel
          collections={collectionsApi.collections}
          notice={collectionsApi.error}
          activeId={activeCollectionId}
          onOpen={setActiveCollectionId}
          onBack={() => setActiveCollectionId(null)}
          onRequestCreate={() => {
            setAttachPlace(false)
            setCreateOpen(true)
          }}
          onDelete={(collectionId) => {
            void collectionsApi.removeCollection(collectionId).then((result) => {
              if (result.ok) setActiveCollectionId(null)
            })
          }}
          onRemovePlace={collectionsApi.removeFromCollection}
          onSelect={chooseRestaurant}
        />
      )}
      <SearchBar query={query} onQueryChange={changeQuery} />
      <RestaurantCard
        restaurants={results}
        query={query}
        open={listOpen}
        placesLoading={placesLoading}
        placesNote={placesNote}
        onSelect={chooseRestaurant}
      />
      {selected && (
        <PlaceDetail
          key={selected.restId}
          restaurant={selected}
          userPosition={userPosition}
          collections={collectionsApi.collections}
          signedIn={accountApi.account != null}
          onToggleCollection={(collectionId) =>
            collectionsApi.toggleInCollection(collectionId, selected)
          }
          onRequestCreate={() => {
            setAttachPlace(true)
            setCreateOpen(true)
          }}
          onSignIn={() => openAccount('Sign in to keep your own collections.')}
        />
      )}
      {!libraryOpen && !accountOpen && !reviewOpen && <ChatCard />}
      {reviewOpen && (
        <ReviewPanel
          reviews={reviewsApi.reviews}
          notice={reviewsApi.error}
          places={places}
          bias={userPosition ?? TORONTO}
          signedIn={accountApi.account != null}
          onSignIn={() => openAccount('Sign in to publish a review.')}
          onPublish={reviewsApi.publish}
        />
      )}
      {accountOpen && (
        <AccountPanel
          account={accountApi.account}
          notice={accountNotice ?? accountApi.error}
          onSignIn={accountApi.signIn}
          onRegister={accountApi.register}
          onUpdate={accountApi.updateProfile}
          onChangePassword={accountApi.changePassword}
          onSignOut={accountApi.signOut}
        />
      )}
      {createOpen && (
        <CreateCollectionDialog
          onClose={closeCreate}
          onCreate={async (name) => {
            const result = await collectionsApi.addCollection(
              name,
              attachPlace ? selected ?? undefined : undefined,
            )
            if (!result.ok) return result.error
            closeCreate()
            return null
          }}
        />
      )}
    </div>
  )
}
