import { useEffect, useRef, useState } from 'react'
import { APIProvider } from '@vis.gl/react-google-maps'
import { useAccount, takePasswordRecovery } from './account'
import { askAssistant, readTaste, saveTaste, type SuggestedRestaurant, type TasteSnapshot } from './assistant'
import { AccountPanel } from './components/AccountPanel'
import { ChatCard, type ChatMessage } from './components/ChatCard'
import { RecommendationRow } from './components/RecommendationRow'
import { loadChatSessions, saveChatSessions, type ChatSession } from './chatHistory'
import { CollectionsPanel, CreateCollectionDialog } from './components/CollectionsPanel'
import { MapBackground } from './components/MapBackground'
import { PlaceDetail } from './components/PlaceDetail'
import { RestaurantCard } from './components/RestaurantCard'
import { SearchBar } from './components/SearchBar'
import { Sidebar } from './components/Sidebar'
import { useCollections } from './collections'
import { ReviewPanel } from './components/ReviewPanel'
import { FriendsPanel, type Friend, type FriendLookup, type FriendPost, type FriendRequest, type FoundFriend } from './components/FriendsPanel'
import { useProfileExtra, readProfileExtra, writeProfileExtra } from './profileExtra'
import { useReviews } from './reviews'
import { placesFailureMessage, searchRestaurants } from './places'
import type { Restaurant } from './restaurants'
import { TORONTO, useUserLocation, type LatLng } from './userLocation'

const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim() ?? ''

const NO_RESTAURANTS: Restaurant[] = []
const NO_SESSIONS: ChatSession[] = []
const NO_PICKS: SuggestedRestaurant[] = []
const EMPTY_TASTE: TasteSnapshot = {
  interests: [],
  likes: [],
  dislikes: [],
  priceComfort: '',
  description: '',
}

type PlaceSearch = {
  query: string
  restaurants: Restaurant[]
  note: string | null
}

function latestAssistantPicks(messages: ChatMessage[]): SuggestedRestaurant[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index].role === 'assistant') return messages[index].picks
  }
  return NO_PICKS
}

export default function App() {
  if (!apiKey) {
    return <FoodApp />
  }

  return (
    <APIProvider apiKey={apiKey}>
      <FoodApp />
    </APIProvider>
  )
}

function FoodApp() {
  const userPosition = useUserLocation()
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Restaurant | null>(null)
  const [listOpen, setListOpen] = useState(false)
  const [placeSearch, setPlaceSearch] = useState<PlaceSearch | null>(null)
  const [libraryOpen, setLibraryOpen] = useState(false)
  const [activeCollectionId, setActiveCollectionId] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [createPlace, setCreatePlace] = useState<Restaurant | null>(null)
  const [resettingPassword, setResettingPassword] = useState(takePasswordRecovery)
  const [accountOpen, setAccountOpen] = useState(resettingPassword)
  const [accountNotice, setAccountNotice] = useState<string | null>(null)
  const [reviewOpen, setReviewOpen] = useState(false)
  const [friendsOpen, setFriendsOpen] = useState(false)
  const [friends, setFriends] = useState<Friend[]>([])
  const [incoming, setIncoming] = useState<FriendRequest[]>([])
  const [outgoing, setOutgoing] = useState<FriendRequest[]>([])
  const [friendPosts, setFriendPosts] = useState<FriendPost[]>([])
  const [thread, setThread] = useState<ChatMessage[]>([])
  const [sessions, setSessions] = useState<ChatSession[]>(NO_SESSIONS)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [assistantBusy, setAssistantBusy] = useState(false)
  const [assistantMode, setAssistantMode] = useState<'chat' | 'for-you' | null>(null)
  const [learned, setLearned] = useState<TasteSnapshot>(EMPTY_TASTE)
  const accountApi = useAccount()
  const profileExtra = useProfileExtra(accountApi.account?.id ?? null)
  const collectionsApi = useCollections(accountApi.token, accountApi.signOut)
  const reviewsApi = useReviews(accountApi.token)
  const hasUserPosition = userPosition != null
  const positionRef = useRef<LatLng | null>(null)
  const searchGeneration = useRef(0)
  const askGeneration = useRef(0)
  const accountId = accountApi.account?.id ?? null
  const accountIdRef = useRef(accountId)
  accountIdRef.current = accountId

  useEffect(() => {
    positionRef.current = userPosition
  }, [userPosition])

  useEffect(() => {
    if (!accountApi.token || !profileExtra.avatar) return
    void fetch('/api/accounts/me/avatar', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accountApi.token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ avatar: profileExtra.avatar }),
    })
  }, [accountApi.token, profileExtra.avatar])

  useEffect(() => {
    setActiveId(null)
    setThread([])
    setAssistantMode(null)
    setSessions(accountId ? loadChatSessions(accountId) : NO_SESSIONS)
  }, [accountId])

  useEffect(() => {
    const id = accountIdRef.current
    if (!id || !activeId || thread.length === 0) return
    setSessions((current) => {
      const previous = current.find((session) => session.id === activeId)
      const title =
        previous?.title || thread.find((message) => message.role === 'user')?.text.slice(0, 48) || 'Chat'
      const next = [
        { id: activeId, title, updatedAt: Date.now(), messages: thread },
        ...current.filter((session) => session.id !== activeId),
      ].slice(0, 20)
      saveChatSessions(id, next)
      return next
    })
  }, [thread, activeId])

  useEffect(() => {
    if (!accountApi.token || !accountApi.account) {
      setLearned(EMPTY_TASTE)
      return
    }
    const accountId = accountApi.account.id
    const token = accountApi.token
    let cancelled = false
    void readTaste(token)
      .then((taste) => {
        if (cancelled) return
        setLearned(taste)
        const local = readProfileExtra(accountId)
        if (
          taste.interests.length === 0 &&
          taste.description === '' &&
          (local.interests.length > 0 || local.description)
        ) {
          void saveTaste(token, local.interests, local.description)
            .then((saved) => {
              if (!cancelled) setLearned(saved)
            })
            .catch(() => undefined)
          return
        }
        if (
          taste.interests.join('\n') !== local.interests.join('\n') ||
          taste.description !== local.description
        ) {
          writeProfileExtra(accountId, {
            ...local,
            interests: taste.interests,
            description: taste.description,
          })
        }
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [accountApi.token, accountApi.account?.id])

  useEffect(() => {
    const trimmed = query.trim()
    if (trimmed.length < 2) return

    const generation = ++searchGeneration.current
    const handle = window.setTimeout(() => {
      const bias = positionRef.current ?? TORONTO
      searchRestaurants(trimmed, bias)
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
  }, [query, hasUserPosition])

  const trimmedQuery = query.trim()
  const placesActive = trimmedQuery.length >= 2
  const searchMatches = placeSearch?.query === trimmedQuery
  const placesLoading = placesActive && !searchMatches
  const placesNote = placesActive && searchMatches ? placeSearch.note : null
  const results = placesActive && searchMatches ? placeSearch.restaurants : NO_RESTAURANTS
  const searching = trimmedQuery.length >= 2
  const activeCollection = collectionsApi.collections.find(
    (collection) => collection.id === activeCollectionId,
  )
  const viewed = activeCollection?.places ?? []
  const recommendationPlaces = latestAssistantPicks(thread)
  const recommending =
    !searching &&
    !libraryOpen &&
    !friendsOpen &&
    !accountOpen &&
    !reviewOpen &&
    recommendationPlaces.length > 0
  const mapPlaces = searching ? results : recommending ? recommendationPlaces : viewed
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
    setFriendsOpen(false)
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
    setFriendsOpen(false)
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
    setFriendsOpen(false)
  }

  useEffect(() => {
    if (!accountApi.token) {
      setFriends([])
      setIncoming([])
      setOutgoing([])
      setFriendPosts([])
      return
    }
    if (!friendsOpen) return
    const gate = { ok: true }
    void fetch('/api/friends', {
      headers: { Authorization: `Bearer ${accountApi.token}` },
    })
      .then((response) => response.json().catch(() => null))
      .then((payload: FriendPayload | null) => {
        if (!gate.ok || !payload) return
        applyCircle(payload, setFriends, setIncoming, setOutgoing, setFriendPosts)
      })
      .catch(() => {})
    return () => {
      gate.ok = false
    }
  }, [accountApi.token, friendsOpen])

  async function lookupFriend(email: string): Promise<FriendLookup> {
    return friendRequest('/api/accounts/lookup', accountApi.token, { email })
  }

  async function addFriend(id: string): Promise<FriendLookup> {
    const result = await friendRequest('/api/friends', accountApi.token, { id })
    if ('friend' in result && result.circle) {
      applyCircle(result.circle, setFriends, setIncoming, setOutgoing, setFriendPosts)
    }
    return result
  }

  async function respondFriend(id: string, accept: boolean): Promise<string | null> {
    if (!accountApi.token) return 'Sign in to find a friend.'
    try {
      const response = await fetch('/api/friends/respond', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accountApi.token}`,
        },
        body: JSON.stringify({ id, accept }),
      })
      const payload = (await response.json().catch(() => null)) as FriendPayload | null
      if (!response.ok || !payload) {
        return payload?.error ?? 'The Java backend is not running.'
      }
      applyCircle(payload, setFriends, setIncoming, setOutgoing, setFriendPosts)
      return null
    } catch {
      return 'The Java backend is not running.'
    }
  }

  async function clearFriend(id: string): Promise<string | null> {
    if (!accountApi.token) return 'Sign in to find a friend.'
    try {
      const response = await fetch('/api/friends/cancel', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accountApi.token}`,
        },
        body: JSON.stringify({ id }),
      })
      const payload = (await response.json().catch(() => null)) as FriendPayload | null
      if (!response.ok || !payload) {
        return payload?.error ?? 'The Java backend is not running.'
      }
      applyCircle(payload, setFriends, setIncoming, setOutgoing, setFriendPosts)
      return null
    } catch {
      return 'The Java backend is not running.'
    }
  }

  function toggleFriends() {
    if (friendsOpen) {
      setFriendsOpen(false)
      return
    }
    setFriendsOpen(true)
    setLibraryOpen(false)
    setAccountOpen(false)
    setAccountNotice(null)
    setReviewOpen(false)
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
    setCreatePlace(null)
  }

  function ask(message: string, chips: string[], forYou: boolean) {
    if (!accountApi.ready) return
    if (!accountApi.token || !accountApi.account) {
      openAccount('Sign in to ask for a recommendation.')
      return
    }
    if (assistantBusy) return
    const generation = ++askGeneration.current
    const token = accountApi.token
    const shown = forYou ? 'For you' : [message, ...chips].filter(Boolean).join(' · ')
    const history = thread.slice(-8).map((turn) => ({ role: turn.role, text: turn.text }))
    if (activeId == null) setActiveId(crypto.randomUUID())
    setThread((current) => [...current, { role: 'user', text: shown, picks: [] }])
    setAssistantBusy(true)
    const bias = positionRef.current ?? TORONTO
    void askAssistant(token, message, chips, history, bias, forYou)
      .then((answer) => {
        if (askGeneration.current !== generation) return
        setThread((current) => [...current, { role: 'assistant', text: answer.reply, picks: answer.recommendations }])
        setLearned(answer.taste)
        if (answer.recommendations.length > 0) {
          setAssistantMode(forYou ? 'for-you' : 'chat')
          setQuery('')
          setListOpen(false)
          setSelected(null)
        } else {
          setAssistantMode(null)
        }
      })
      .catch((askError: unknown) => {
        if (askGeneration.current !== generation) return
        const note = askError instanceof Error ? askError.message : 'Could not get a recommendation.'
        setThread((current) => [...current, { role: 'assistant', text: note, picks: [] }])
      })
      .finally(() => {
        if (askGeneration.current === generation) setAssistantBusy(false)
      })
  }

  function newChat() {
    setActiveId(null)
    setThread([])
    setAssistantMode(null)
    setSelected(null)
  }

  function openSession(id: string) {
    const session = sessions.find((item) => item.id === id)
    if (!session) return
    const picks = [...session.messages].reverse().find((message) => message.role === 'assistant')?.picks ?? []
    setActiveId(id)
    setThread(session.messages)
    setAssistantMode(picks.length > 0 ? 'chat' : null)
    setQuery('')
    setListOpen(false)
    setSelected(null)
  }

  function saveProfileTaste(interests: string[], description: string) {
    if (!accountApi.token) return
    void saveTaste(accountApi.token, interests, description)
      .then(setLearned)
      .catch(() => undefined)
  }

  return (
    <div className="app">
      <MapBackground
        restaurants={pins}
        selectedId={selected?.restId ?? null}
        fitToResults={(searching || viewed.length > 0 || recommending) && selected == null}
        lockToResults={searching || viewed.length > 0 || selected != null || recommending}
        userPosition={userPosition}
        onSelect={chooseRestaurant}
        covered={friendsOpen}
      />
      <Sidebar
        libraryOpen={libraryOpen}
        accountOpen={accountOpen}
        reviewOpen={reviewOpen}
        friendsOpen={friendsOpen}
        forYouActive={assistantMode === 'for-you' && recommending}
        avatar={profileExtra.avatar}
        onForYou={() => ask('', [], true)}
        onSaved={openSaved}
        onCreate={toggleReview}
        onFriends={toggleFriends}
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
            setCreatePlace(null)
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
      {!friendsOpen && <SearchBar query={query} onQueryChange={changeQuery} />}
      {!friendsOpen && (
        <RestaurantCard
          restaurants={results}
          query={query}
          open={listOpen}
          placesLoading={placesLoading}
          placesNote={placesNote}
          onSelect={chooseRestaurant}
        />
      )}
      {recommending && (
        <RecommendationRow
          places={recommendationPlaces}
          selectedId={selected?.restId ?? null}
          raised={selected != null}
          onSelect={chooseRestaurant}
        />
      )}
      {selected && !friendsOpen && (
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
            setCreatePlace(selected)
            setCreateOpen(true)
          }}
          onSignIn={() => openAccount('Sign in to keep your own collections.')}
        />
      )}
      {!libraryOpen && !accountOpen && !reviewOpen && !friendsOpen && (
        <ChatCard
          thread={thread}
          busy={assistantBusy}
          signedIn={accountApi.account != null}
          sessions={sessions}
          activeSessionId={activeId}
          onSend={(message, chips) => ask(message, chips, false)}
          onOpenSession={openSession}
          onNewChat={newChat}
        />
      )}
      {friendsOpen && (
        <FriendsPanel
          friends={friends}
          posts={friendPosts}
          incoming={incoming}
          outgoing={outgoing}
          signedIn={accountApi.account != null}
          collections={collectionsApi.collections}
          onSignIn={() => openAccount('Sign in to save a place.')}
          onToggleCollection={(collectionId, place) => {
            void collectionsApi.toggleInCollection(collectionId, place)
          }}
          onRequestCreate={(place) => {
            setCreatePlace(place)
            setCreateOpen(true)
          }}
          onLookup={lookupFriend}
          onAdd={addFriend}
          onRespond={respondFriend}
          onClear={clearFriend}
        />
      )}
      {reviewOpen && (
        <ReviewPanel
          reviews={reviewsApi.reviews}
          notice={reviewsApi.error}
          bias={userPosition ?? TORONTO}
          signedIn={accountApi.account != null}
          onSignIn={() => openAccount('Sign in to publish a review.')}
          onPublish={reviewsApi.publish}
          onUpdate={reviewsApi.update}
          onDelete={reviewsApi.remove}
        />
      )}
      {accountOpen && (
        <AccountPanel
          account={accountApi.account}
          notice={accountNotice ?? accountApi.error}
          collections={collectionsApi.collections}
          reviews={reviewsApi.reviews}
          onSignIn={accountApi.signIn}
          onRegister={accountApi.register}
          onRecover={accountApi.recover}
          onResetPassword={accountApi.resetPassword}
          onResetDone={() => setResettingPassword(false)}
          resettingPassword={resettingPassword}
          onUpdate={accountApi.updateProfile}
          onChangePassword={accountApi.changePassword}
          onSignOut={accountApi.signOut}
          learnedLikes={learned.likes}
          learnedDislikes={learned.dislikes}
          priceComfort={learned.priceComfort}
          onTaste={saveProfileTaste}
        />
      )}
      {createOpen && (
        <CreateCollectionDialog
          onClose={closeCreate}
          onCreate={async (name) => {
            const result = await collectionsApi.addCollection(name, createPlace ?? undefined)
            if (!result.ok) return result.error
            closeCreate()
            return null
          }}
        />
      )}
    </div>
  )
}

type RawFriend = { id?: string; name?: string; handle?: string; avatar?: string }

type RawPost = {
  id?: string
  author?: string
  avatar?: string
  when?: string
    restaurant?: string
    restId?: string
    address?: string
    latitude?: number | null
    longitude?: number | null
    rating?: number
  tags?: string[]
  body?: string
  photo?: string
  photos?: string[]
}

type FriendPayload = {
  account?: {
    id?: string
    name?: string
    handle?: string
    reviewCount?: number
    status?: string
    added?: boolean
    avatar?: string
  }
  friends?: RawFriend[]
  incoming?: RawFriend[]
  outgoing?: RawFriend[]
  posts?: RawPost[]
  error?: string
}

function asFriend(person: RawFriend): Friend {
  return { id: person.id ?? '', name: person.name ?? 'Friend', avatar: person.avatar ?? '' }
}

function asRequest(person: RawFriend): FriendRequest {
  return {
    id: person.id ?? '',
    name: person.name ?? 'Friend',
    handle: person.handle || 'user',
    avatar: person.avatar ?? '',
  }
}

function asPost(post: RawPost): FriendPost {
  const photos = post.photos?.length ? post.photos : post.photo ? [post.photo] : []
  return {
    id: post.id ?? '',
    author: post.author ?? 'Friend',
    avatar: post.avatar ?? '',
    when: post.when ?? '',
    restaurant: post.restaurant ?? '',
    restId: post.restId ?? '',
    address: post.address ?? '',
    latitude: typeof post.latitude === 'number' ? post.latitude : null,
    longitude: typeof post.longitude === 'number' ? post.longitude : null,
    rating: post.rating ?? 0,
    tags: post.tags ?? [],
    body: post.body ?? '',
    photo: photos[0] ?? '',
    photos,
  }
}

function applyCircle(
  payload: FriendPayload,
  setFriends: (friends: Friend[]) => void,
  setIncoming: (requests: FriendRequest[]) => void,
  setOutgoing: (requests: FriendRequest[]) => void,
  setPosts: (posts: FriendPost[]) => void,
) {
  setFriends((payload.friends ?? []).map(asFriend))
  setIncoming((payload.incoming ?? []).map(asRequest))
  setOutgoing((payload.outgoing ?? []).map(asRequest))
  setPosts((payload.posts ?? []).map(asPost))
}

function friendStatus(account: NonNullable<FriendPayload['account']>): FoundFriend['status'] {
  if (account.status === 'pending' || account.status === 'incoming' || account.status === 'added') {
    return account.status
  }
  return account.added ? 'added' : 'none'
}

async function friendRequest(
  path: string,
  token: string | null,
  body: object,
): Promise<FriendLookup & { circle?: FriendPayload }> {
  if (!token) return { error: 'Sign in to find a friend.' }
  try {
    const response = await fetch(path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(body),
    })
    const payload = (await response.json().catch(() => null)) as FriendPayload | null
    const account = payload?.account
    if (!response.ok || !account?.id || !account.name) {
      return { error: payload?.error ?? 'The Java backend is not running.' }
    }
    const status = friendStatus(account)
    const friend: FoundFriend = {
      id: account.id,
      name: account.name,
      handle: account.handle || 'user',
      reviewCount: account.reviewCount ?? 0,
      status,
      added: status === 'added',
      avatar: account.avatar || '',
    }
    return { friend, circle: payload ?? undefined }
  } catch {
    return { error: 'The Java backend is not running.' }
  }
}
