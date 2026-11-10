import { useState, type FormEvent } from 'react'
import { placeCollectionIds, type Collection } from '../collections'
import type { Restaurant } from '../restaurants'
import { CollectionMenu } from './CollectionMenu'
import { PhotoGrid } from './PhotoGrid'

export type Friend = {
  id: string
  name: string
  avatar: string
}

export type FriendPost = {
  id: string
  author: string
  avatar: string
  when: string
  restaurant: string
  restId: string
  address: string
  latitude: number | null
  longitude: number | null
  rating: number
  tags: string[]
  body: string
  photo: string
  photos: string[]
}

export type FoundFriend = {
  id: string
  name: string
  handle: string
  reviewCount: number
  status: 'none' | 'pending' | 'incoming' | 'added'
  added: boolean
  avatar: string
}

export type FriendRequest = {
  id: string
  name: string
  handle: string
  avatar: string
}

export type FriendLookup = { friend: FoundFriend } | { error: string }

type Props = {
  friends?: Friend[]
  posts?: FriendPost[]
  incoming?: FriendRequest[]
  outgoing?: FriendRequest[]
  signedIn: boolean
  collections: Collection[]
  onSignIn: () => void
  onToggleCollection: (collectionId: string, place: Restaurant) => void
  onRequestCreate: (place: Restaurant) => void
  onLookup: (email: string) => Promise<FriendLookup>
  onAdd: (id: string) => Promise<FriendLookup>
  onRespond: (id: string, accept: boolean) => Promise<string | null>
  onClear: (id: string) => Promise<string | null>
}

const PREVIEW = 4

export function FriendsPanel({
  friends = [],
  posts = [],
  incoming = [],
  outgoing = [],
  signedIn,
  collections,
  onSignIn,
  onToggleCollection,
  onRequestCreate,
  onLookup,
  onAdd,
  onRespond,
  onClear,
}: Props) {
  const [adding, setAdding] = useState(false)
  const openAdd = () => setAdding(true)
  const requests = incoming.length + outgoing.length

  return (
    <section className="friends-feed" aria-label={adding ? 'Add friends' : 'Friends feed'}>
      {adding ? (
        <AddFriends
          signedIn={signedIn}
          onSignIn={onSignIn}
          onLookup={onLookup}
          onAdd={onAdd}
          onBack={() => setAdding(false)}
        />
      ) : friends.length === 0 && requests === 0 ? (
        <EmptyCircle onAdd={openAdd} />
      ) : (
        <Feed
          friends={friends}
          posts={posts}
          incoming={incoming}
          outgoing={outgoing}
          collections={collections}
          signedIn={signedIn}
          onSignIn={onSignIn}
          onToggleCollection={onToggleCollection}
          onRequestCreate={onRequestCreate}
          onAdd={openAdd}
          onRespond={onRespond}
          onClear={onClear}
        />
      )}
    </section>
  )
}

function EmptyCircle({ onAdd }: { onAdd: () => void }) {
  return (
    <div className="friends-empty">
      <div className="friends-empty-card">
        <CafeArt />
        <h2>Your food circle starts here</h2>
        <p>Follow your friends to see their restaurant discoveries, food photos, and reviews.</p>
        <button type="button" className="friends-primary" onClick={onAdd}>
          Find friends
        </button>
        <button type="button" className="friends-quiet" onClick={onAdd}>
          Invite friends
        </button>
      </div>
    </div>
  )
}

function AddFriends({
  signedIn,
  onSignIn,
  onLookup,
  onAdd,
  onBack,
}: {
  signedIn: boolean
  onSignIn: () => void
  onLookup: (email: string) => Promise<FriendLookup>
  onAdd: (id: string) => Promise<FriendLookup>
  onBack: () => void
}) {
  const [email, setEmail] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const [found, setFound] = useState<FoundFriend | null>(null)
  const [pending, setPending] = useState(false)
  const [saving, setSaving] = useState(false)

  function search(event: FormEvent) {
    event.preventDefault()
    if (!signedIn) {
      onSignIn()
      return
    }
    setPending(true)
    setFound(null)
    setNotice(null)
    void onLookup(email).then((result) => {
      setPending(false)
      if ('friend' in result) {
        setFound(result.friend)
        return
      }
      setNotice(result.error)
    })
  }

  function add() {
    if (!found || found.status === 'added' || found.status === 'pending' || saving) return
    setSaving(true)
    setNotice(null)
    void onAdd(found.id).then((result) => {
      setSaving(false)
      if ('friend' in result) {
        setFound(result.friend)
        return
      }
      setNotice(result.error)
    })
  }

  function invite() {
    const trimmed = email.trim()
    const at = trimmed.indexOf('@')
    const dot = trimmed.lastIndexOf('.')
    if (at <= 0 || dot < at + 2 || dot >= trimmed.length - 1) {
      setFound(null)
      setNotice('Enter an email.')
      return
    }
    const subject = encodeURIComponent('Join me on Food Assistant')
    const body = encodeURIComponent('I am using Food Assistant to find restaurants. Join me.')
    window.location.href = `mailto:${trimmed}?subject=${subject}&body=${body}`
  }

  return (
    <div className="friends-empty">
      <div className="friends-add-card">
        <button type="button" className="friends-add-back" onClick={onBack}>
          <BackIcon />
          Add Friends
        </button>
        <span className="friends-mail">
          <MailPlusIcon />
        </span>
        <h2>Find your friends</h2>
        <p>Enter your friend&apos;s email address to find their account and connect.</p>
        <form onSubmit={search}>
          <label htmlFor="friend-email">Email address</label>
          <input
            id="friend-email"
            type="email"
            autoComplete="email"
            placeholder="example@email.com"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
              setFound(null)
              setNotice(null)
            }}
          />
          {notice && <p className="friends-add-note">{notice}</p>}
          <button type="submit" className="friends-search" disabled={pending}>
            {pending ? 'Searching…' : 'Search account'}
          </button>
        </form>
        {found && (
          <div className="friend-result">
            <span className="friend-result-avatar">
              {found.avatar ? <img src={found.avatar} alt="" /> : <PersonGlyph />}
            </span>
            <div className="friend-result-copy">
              <strong>{found.name}</strong>
              <span>
                @{found.handle} · {found.reviewCount} public review{found.reviewCount === 1 ? '' : 's'}
              </span>
            </div>
            <button
              type="button"
              className={
                found.status === 'added' || found.status === 'pending'
                  ? 'friend-add-toggle is-added'
                  : 'friend-add-toggle'
              }
              disabled={found.status === 'added' || found.status === 'pending' || saving}
              aria-pressed={found.status === 'added' || found.status === 'pending'}
              onClick={add}
            >
              {found.status === 'added'
                ? 'ADDED'
                : found.status === 'pending'
                  ? 'REQUESTED'
                  : found.status === 'incoming'
                    ? 'ACCEPT'
                    : 'ADD +'}
            </button>
          </div>
        )}
        <hr />
        <p className="friends-invite-copy">Or invite a friend who doesn&apos;t have an account yet.</p>
        <button type="button" className="friends-invite" onClick={invite}>
          <PlaneIcon />
          Invite by email
        </button>
      </div>
    </div>
  )
}

function Feed({
  friends,
  posts,
  incoming,
  outgoing,
  collections,
  signedIn,
  onSignIn,
  onToggleCollection,
  onRequestCreate,
  onAdd,
  onRespond,
  onClear,
}: {
  friends: Friend[]
  posts: FriendPost[]
  incoming: FriendRequest[]
  outgoing: FriendRequest[]
  collections: Collection[]
  signedIn: boolean
  onSignIn: () => void
  onToggleCollection: (collectionId: string, place: Restaurant) => void
  onRequestCreate: (place: Restaurant) => void
  onAdd: () => void
  onRespond: (id: string, accept: boolean) => Promise<string | null>
  onClear: (id: string) => Promise<string | null>
}) {
  const [expanded, setExpanded] = useState(false)
  const [tab, setTab] = useState<'feed' | 'requests'>(friends.length === 0 ? 'requests' : 'feed')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const canFold = friends.length > PREVIEW
  const folded = canFold && !expanded
  const shown = folded ? friends.slice(0, PREVIEW - 1) : friends
  const requestCount = incoming.length + outgoing.length

  function respond(id: string, accept: boolean) {
    if (busyId) return
    setBusyId(id)
    setNotice(null)
    void onRespond(id, accept).then((error) => {
      setBusyId(null)
      setNotice(error)
    })
  }

  function clear(id: string) {
    if (busyId) return
    setBusyId(id)
    setNotice(null)
    void onClear(id).then((error) => {
      setBusyId(null)
      setNotice(error)
    })
  }

  return (
    <div className="friends-board">
      <div className="friends-head">
        <h2>Friends</h2>
        <button type="button" className="friends-add" aria-label="Add friend" onClick={onAdd}>
          <PlusIcon />
        </button>
      </div>
      <div className="friends-tabs" role="tablist" aria-label="Friend lists">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'feed'}
          className={tab === 'feed' ? 'friends-tab is-on' : 'friends-tab'}
          onClick={() => setTab('feed')}
        >
          Feed
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'requests'}
          className={tab === 'requests' ? 'friends-tab is-on' : 'friends-tab'}
          onClick={() => setTab('requests')}
        >
          Requests
          {requestCount > 0 && <span className="friends-tab-count">{requestCount}</span>}
        </button>
      </div>
      {tab === 'requests' ? (
        <RequestList
          incoming={incoming}
          outgoing={outgoing}
          busyId={busyId}
          notice={notice}
          onRespond={respond}
          onClear={clear}
        />
      ) : (
        <>
          <div className="friends-people">
            <ul>
              {shown.map((friend) => (
                <li key={friend.id}>
                  <Person name={friend.name} avatar={friend.avatar} />
                </li>
              ))}
              {folded && (
                <li>
                  <Person name="…" />
                </li>
              )}
            </ul>
            {canFold && (
              <button type="button" className="friends-see-all" onClick={() => setExpanded((open) => !open)}>
                {expanded ? 'show less' : 'see all'}
                <ChevronIcon />
              </button>
            )}
          </div>
          <hr className="friends-rule" />
          <h3>Recent posts</h3>
          {posts.length === 0 ? (
            <p className="friends-quiet-note">Your friends haven&apos;t shared any food memories yet.</p>
          ) : (
            <ul className="friends-posts">
              {posts.map((post) => (
                <li key={post.id}>
                  <PostCard
                    post={post}
                    collections={collections}
                    signedIn={signedIn}
                    onSignIn={onSignIn}
                    onToggleCollection={onToggleCollection}
                    onRequestCreate={onRequestCreate}
                  />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}

function RequestList({
  incoming,
  outgoing,
  busyId,
  notice,
  onRespond,
  onClear,
}: {
  incoming: FriendRequest[]
  outgoing: FriendRequest[]
  busyId: string | null
  notice: string | null
  onRespond: (id: string, accept: boolean) => void
  onClear: (id: string) => void
}) {
  if (incoming.length === 0 && outgoing.length === 0) {
    return <p className="friends-quiet-note">No friend requests.</p>
  }

  return (
    <div className="friends-requests">
      {notice && <p className="friends-add-note">{notice}</p>}
      {incoming.length > 0 && (
        <section>
          <h3>Received</h3>
          <ul>
            {incoming.map((request) => (
              <li key={request.id} className="friends-request">
                <span className="friends-avatar">
                  {request.avatar ? <img src={request.avatar} alt="" /> : <PersonGlyph />}
                </span>
                <div className="friends-request-copy">
                  <strong>{request.name}</strong>
                  <span>@{request.handle}</span>
                </div>
                <span className="friends-request-actions">
                  <button
                    type="button"
                    className="friends-accept"
                    disabled={busyId === request.id}
                    onClick={() => onRespond(request.id, true)}
                  >
                    Accept
                  </button>
                  <button
                    type="button"
                    className="friends-decline"
                    disabled={busyId === request.id}
                    onClick={() => onRespond(request.id, false)}
                  >
                    Decline
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
      {outgoing.length > 0 && (
        <section>
          <h3>Sent</h3>
          <ul>
            {outgoing.map((request) => (
              <li key={request.id} className="friends-request">
                <span className="friends-avatar">
                  {request.avatar ? <img src={request.avatar} alt="" /> : <PersonGlyph />}
                </span>
                <div className="friends-request-copy">
                  <strong>{request.name}</strong>
                  <span>@{request.handle}</span>
                </div>
                <span className="friends-request-actions">
                  <span className="friends-pending-label">Pending</span>
                  <button
                    type="button"
                    className="friends-decline"
                    disabled={busyId === request.id}
                    onClick={() => onClear(request.id)}
                  >
                    Clear
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

function Person({ name, avatar = '' }: { name: string; avatar?: string }) {
  return (
    <div className="friends-person">
      <span className="friends-avatar">
        {avatar ? <img src={avatar} alt="" /> : <PersonGlyph />}
      </span>
      <span>{name}</span>
    </div>
  )
}

function PostCard({
  post,
  collections,
  signedIn,
  onSignIn,
  onToggleCollection,
  onRequestCreate,
}: {
  post: FriendPost
  collections: Collection[]
  signedIn: boolean
  onSignIn: () => void
  onToggleCollection: (collectionId: string, place: Restaurant) => void
  onRequestCreate: (place: Restaurant) => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const place = postRestaurant(post)
  const memberIds = place.restId ? placeCollectionIds(collections, place.restId) : []
  const score = Math.max(0, Math.min(5, Math.round(post.rating)))

  return (
    <article className="friends-post">
      <div className="friends-post-top">
        <span className="friends-post-avatar">
          {post.avatar ? <img src={post.avatar} alt="" /> : <PersonGlyph />}
        </span>
        <div className="friends-post-copy">
          <span className="friends-author">{post.author}</span>
          {post.when && <span className="friends-when">{post.when}</span>}
          <div className="friends-place-row">
            <span className="friends-restaurant">
              <PinIcon />
              <span>{post.restaurant}</span>
            </span>
            <span className="friends-stars" aria-label={`${score} out of 5 stars`}>
              {[1, 2, 3, 4, 5].map((value) => (
                <StarIcon key={value} filled={value <= score} />
              ))}
            </span>
          </div>
          {post.tags.length > 0 && (
            <div className="friends-tags">
              {post.tags.map((tag) => (
                <span key={tag} className="friends-tag">
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>
        <div className="friends-bookmark-wrap">
          <button
            type="button"
            className="friends-bookmark"
            aria-label="Add to collection"
            aria-pressed={memberIds.length > 0}
            aria-expanded={menuOpen}
            onClick={() => {
              if (!signedIn) {
                onSignIn()
                return
              }
              setMenuOpen((open) => !open)
            }}
          >
            <BookmarkIcon />
          </button>
          {menuOpen && (
            <CollectionMenu
              collections={collections}
              memberIds={memberIds}
              onToggle={(collectionId) => onToggleCollection(collectionId, place)}
              onRequestCreate={() => onRequestCreate(place)}
              onClose={() => setMenuOpen(false)}
            />
          )}
        </div>
      </div>
      {post.body && <p className="friends-post-body">{post.body}</p>}
      <PhotoGrid
        className="friends-photo-grid"
        photos={post.photos?.length ? post.photos : post.photo ? [post.photo] : []}
      />
    </article>
  )
}

function postRestaurant(post: FriendPost): Restaurant {
  return {
    restId: post.restId,
    name: post.restaurant,
    cuisine: '',
    address: post.address,
    latitude: post.latitude,
    longitude: post.longitude,
    rating: post.rating,
    priceRange: '',
    status: 'discovered',
    tags: post.tags,
  }
}

function CafeArt() {
  return (
    <svg className="friends-art" viewBox="0 0 280 190" aria-hidden="true">
      <rect x="78" y="24" width="30" height="24" rx="3" fill="#f6d5dc" />
      <rect x="118" y="18" width="36" height="28" rx="3" fill="#f0c4ce" />
      <rect x="92" y="118" width="108" height="9" rx="3" fill="#efd6cf" />
      <rect x="106" y="127" width="6" height="26" rx="2" fill="#e4c8c0" />
      <rect x="178" y="127" width="6" height="26" rx="2" fill="#e4c8c0" />
      <rect x="124" y="107" width="12" height="11" rx="2" fill="#fff" stroke="#e7b7c2" />
      <rect x="150" y="107" width="12" height="11" rx="2" fill="#fff" stroke="#e7b7c2" />
      <rect x="40" y="128" width="36" height="6" rx="2" fill="#d9c6e4" />
      <rect x="55" y="134" width="5" height="24" rx="2" fill="#cbb6da" />
      <rect x="204" y="128" width="36" height="6" rx="2" fill="#e7b4c4" />
      <rect x="219" y="134" width="5" height="24" rx="2" fill="#dba8b8" />
      <circle cx="58" cy="78" r="14" fill="#f3c2ac" />
      <path d="M44 74c3-16 26-18 30-3-4-8-22-10-30 3z" fill="#5c4338" />
      <circle cx="53" cy="78" r="1.2" fill="#6b4a40" />
      <circle cx="62" cy="78" r="1.2" fill="#6b4a40" />
      <rect x="43" y="92" width="32" height="32" rx="12" fill="#cbb6ea" />
      <rect x="46" y="118" width="11" height="16" rx="4" fill="#6a5878" />
      <rect x="62" y="118" width="11" height="16" rx="4" fill="#6a5878" />
      <path d="M74 102c14 4 24 8 32 6" fill="none" stroke="#f3c2ac" strokeWidth="4" strokeLinecap="round" />
      <circle cx="222" cy="78" r="14" fill="#f3c2ac" />
      <path d="M208 70c6-16 28-12 28 2-8-10-20-12-28-2z" fill="#6b4038" />
      <circle cx="217" cy="78" r="1.2" fill="#6b4a40" />
      <circle cx="226" cy="78" r="1.2" fill="#6b4a40" />
      <rect x="206" y="92" width="32" height="32" rx="12" fill="#e7a4b6" />
      <rect x="208" y="118" width="11" height="16" rx="4" fill="#c47890" />
      <rect x="224" y="118" width="11" height="16" rx="4" fill="#c47890" />
      <path d="M206 102c-14 4-24 8-32 6" fill="none" stroke="#f3c2ac" strokeWidth="4" strokeLinecap="round" />
      <ellipse cx="248" cy="100" rx="11" ry="16" fill="#8eaa86" />
      <ellipse cx="238" cy="108" rx="8" ry="12" fill="#a3bc98" />
      <ellipse cx="258" cy="110" rx="7" ry="11" fill="#7d9e78" />
      <rect x="238" y="116" width="20" height="16" rx="3" fill="#e7b7a8" />
    </svg>
  )
}

function PersonGlyph() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8.5" r="3.1" />
      <path d="M6.4 18.2c.8-2.6 2.9-4 5.6-4s4.8 1.4 5.6 4" />
    </svg>
  )
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M14.5 6.5 9 12l5.5 5.5" />
    </svg>
  )
}

function MailPlusIcon() {
  return (
    <svg viewBox="0 0 48 48" aria-hidden="true">
      <rect x="8" y="14" width="26" height="18" rx="2.5" />
      <path d="M9 16.5 21 26l13-9.5" />
      <path d="M33.5 27.5v8M29.5 31.5h8" />
    </svg>
  )
}

function PlaneIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3.2 11.2 20.5 4.2l-6.4 15.2-2.3-6.1z" />
      <path d="m11.8 13.3 8.7-9.1" />
    </svg>
  )
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 6v12M6 12h12" />
    </svg>
  )
}

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M9 5l7 7-7 7" />
    </svg>
  )
}

function PinIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 21s6-5.2 6-10a6 6 0 1 0-12 0c0 4.8 6 10 6 10z" />
      <circle cx="12" cy="11" r="2" />
    </svg>
  )
}

function StarIcon({ filled }: { filled: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className={filled ? 'is-filled' : undefined} aria-hidden="true">
      <path d="m12 3.4 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.8 6.8 19.5l1-5.8L3.5 9.6l5.9-.9z" />
    </svg>
  )
}

function BookmarkIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 4.5h10a1 1 0 0 1 1 1V20l-6-3.2L6 20V5.5a1 1 0 0 1 1-1z" />
    </svg>
  )
}
