import { useEffect, useRef, useState, type ChangeEvent, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import type { Friend } from './FriendsPanel'
import { TORONTO, type LatLng } from '../userLocation'

type Kind = 'message' | 'rating' | 'location' | 'weather' | 'time'

type Sticker = {
  kind: Kind
  text: string
  rating: number
  x: number
  y: number
}

type Snap = {
  id: string
  authorId: string
  author: string
  avatar: string
  photo: string
  when: string
}

type Ghost = {
  source: 'chip' | 'sticker'
  kind: Kind
  label: string
  rating: number
  x: number
  y: number
}

type Props = {
  friends: Friend[]
  token: string | null
  signedIn: boolean
  position: LatLng | null
  onSignIn: () => void
}

const PHOTO_W = 1080
const PHOTO_H = 1080

export function InstantPanel({ friends, token, signedIn, position, onSignIn }: Props) {
  const previewRef = useRef<HTMLDivElement>(null)
  const trashRef = useRef<HTMLDivElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const messageRef = useRef<HTMLInputElement>(null)
  const dragRef = useRef<{ source: 'chip' | 'sticker'; kind: Kind } | null>(null)
  const dragStop = useRef<(() => void) | null>(null)
  const stickerRef = useRef<Sticker | null>(null)
  const extrasRef = useRef({ location: 'Toronto, ON', weather: 'Add weather', time: clock(), rating: 5 })

  const [photo, setPhoto] = useState<string | null>(null)
  const [sticker, setSticker] = useState<Sticker | null>(null)
  const [library, setLibrary] = useState(false)
  const [viewing, setViewing] = useState<Snap | null>(null)
  const [picked, setPicked] = useState<string | null>(null)
  const [sendOpen, setSendOpen] = useState(false)
  const [chosen, setChosen] = useState<string[]>([])
  const [search, setSearch] = useState('')
  const [sending, setSending] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [snaps, setSnaps] = useState<Snap[]>([])
  const [loadNote, setLoadNote] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [camera, setCamera] = useState<'starting' | 'ready' | 'off'>('starting')
  const [rating, setRating] = useState(5)
  const [location, setLocation] = useState('Toronto, ON')
  const [weather, setWeather] = useState('Add weather')
  const [time, setTime] = useState(clock)
  const [ghost, setGhost] = useState<Ghost | null>(null)

  const spot = position ?? TORONTO
  const lat = spot.lat.toFixed(2)
  const lng = spot.lng.toFixed(2)
  const cameraApi = typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia
  const cameraState = !cameraApi ? 'off' : camera
  const pickedFriend = friends.find((friend) => friend.id === picked) ?? null
  const visible = snaps.filter((snap) => !picked || snap.authorId === picked)
  const query = search.trim().toLowerCase()
  const listed = friends.filter((friend) => friend.name.toLowerCase().includes(query))
  const chosenLive = chosen.filter((id) => friends.some((friend) => friend.id === id))
  const allChosen = friends.length > 0 && chosenLive.length === friends.length

  useEffect(() => {
    stickerRef.current = sticker
    extrasRef.current = { location, weather, time, rating }
  }, [sticker, location, weather, time, rating])

  useEffect(() => () => dragStop.current?.(), [])

  useEffect(() => {
    if (sticker?.kind === 'message') messageRef.current?.focus()
  }, [sticker?.kind])

  useEffect(() => {
    const id = window.setInterval(() => setTime(clock()), 30_000)
    return () => window.clearInterval(id)
  }, [])

  useEffect(() => {
    let live = true
    const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lng}&current=temperature_2m,weather_code`
    const placeUrl = `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lng}&localityLanguage=en`
    void fetch(weatherUrl)
      .then((response) => response.json())
      .then((data: { current?: { temperature_2m?: number; weather_code?: number } }) => {
        if (!live || !data.current) return
        setWeather(weatherLabel(Number(data.current.weather_code), Number(data.current.temperature_2m)))
      })
      .catch(() => {
        if (live) setWeather('Weather unavailable')
      })
    void fetch(placeUrl)
      .then((response) => response.json())
      .then((data: PlaceLookup) => {
        if (live) setLocation(placeLabel(data))
      })
      .catch(() => {
        if (live) setLocation('Toronto, ON')
      })
    return () => {
      live = false
    }
  }, [lat, lng])

  useEffect(() => {
    if (photo || library || !navigator.mediaDevices?.getUserMedia) return
    let cancelled = false
    let stream: MediaStream | null = null
    setCamera('starting')
    void navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'user' }, audio: false })
      .then((next) => {
        if (cancelled) {
          next.getTracks().forEach((track) => track.stop())
          return
        }
        stream = next
        const video = videoRef.current
        if (video) {
          video.srcObject = next
          void video.play().catch(() => undefined)
        }
        setCamera('ready')
      })
      .catch(() => {
        if (!cancelled) setCamera('off')
      })
    return () => {
      cancelled = true
      stream?.getTracks().forEach((track) => track.stop())
    }
  }, [photo, library])

  useEffect(() => {
    if (!token) return
    let live = true
    void fetch('/api/instants', { headers: { Authorization: `Bearer ${token}` } })
      .then(async (response) => {
        const payload = (await response.json().catch(() => null)) as { instants?: unknown; error?: string } | null
        if (!live) return
        if (!response.ok || !payload || !Array.isArray(payload.instants)) {
          setLoadNote(payload?.error ?? 'Could not load instants.')
          setSnaps([])
          return
        }
        setLoadNote(null)
        setSnaps(payload.instants.map((item) => asSnap(item)))
      })
      .catch(() => {
        if (live) setLoadNote('The Java backend is not running.')
      })
    return () => {
      live = false
    }
  }, [token, reload])

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      if (viewing) {
        setViewing(null)
        return
      }
      if (sendOpen) setSendOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [viewing, sendOpen])

  function revealSend() {
    setChosen((current) => {
      if (current.length > 0) return current
      return picked && friends.some((friend) => friend.id === picked) ? [picked] : current
    })
    setSearch('')
    setSendOpen(true)
  }

  function acceptPhoto(still: string) {
    setPhoto(still)
    setLibrary(false)
    setViewing(null)
    setNotice(null)
    setSendOpen(false)
  }

  function shutter() {
    const video = videoRef.current
    if (!video || video.videoWidth === 0) return
    const still = frameFromVideo(video)
    if (!still) {
      setNotice('Could not take that photo.')
      return
    }
    acceptPhoto(still)
  }

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setNotice('Choose a photo.')
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      void coverPhoto(String(reader.result))
        .then(acceptPhoto)
        .catch(() => setNotice('Could not read that photo.'))
    }
    reader.readAsDataURL(file)
  }

  function retake() {
    setPhoto(null)
    setSendOpen(false)
    setNotice(null)
  }

  async function download() {
    if (!photo) return
    try {
      const file = await exportPhoto(photo, sticker)
      const link = document.createElement('a')
      link.href = file
      link.download = 'instant.jpg'
      link.click()
    } catch {
      setNotice('Could not download that photo.')
    }
  }

  async function sendSnap() {
    if (!photo || chosenLive.length === 0 || sending) return
    if (!token) {
      onSignIn()
      return
    }
    setSending(true)
    setNotice(null)
    try {
      const file = await exportPhoto(photo, sticker)
      if (file.length > 200_000) {
        setNotice('That photo is too large.')
        return
      }
      const response = await fetch('/api/instants', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ photo: file, friendIds: chosenLive }),
      })
      const payload = (await response.json().catch(() => null)) as { error?: string } | null
      if (!response.ok) {
        setNotice(payload?.error ?? 'Could not send that instant.')
        return
      }
      const count = chosenLive.length
      setNotice(count === friends.length ? 'Sent to all friends.' : `Sent to ${count} friend${count === 1 ? '' : 's'}.`)
      setPhoto(null)
      setSticker(null)
      setChosen([])
      setSendOpen(false)
      setReload((value) => value + 1)
    } catch {
      setNotice('The Java backend is not running.')
    } finally {
      setSending(false)
    }
  }

  function finishDrag(clientX: number, clientY: number) {
    const drag = dragRef.current
    dragRef.current = null
    setGhost(null)
    if (!drag) return
    const trash = trashRef.current?.getBoundingClientRect()
    if (trash && inside(trash, clientX, clientY)) {
      if (drag.source === 'sticker') setSticker(null)
      return
    }
    const box = previewRef.current?.getBoundingClientRect()
    if (!box || !inside(box, clientX, clientY)) return
    const x = clamp((clientX - box.left) / box.width, 0.14, 0.86)
    const y = clamp((clientY - box.top) / box.height, 0.12, 0.88)
    if (drag.source === 'sticker') {
      setSticker((current) => (current ? { ...current, x, y } : current))
      return
    }
    const extras = extrasRef.current
    setSticker((current) => {
      if (current) return current
      if (drag.kind === 'message') return { kind: 'message', text: '', rating: extras.rating, x, y }
      if (drag.kind === 'rating') return { kind: 'rating', text: '', rating: extras.rating, x, y }
      const text = drag.kind === 'location' ? extras.location : drag.kind === 'weather' ? extras.weather : extras.time
      return { kind: drag.kind, text, rating: extras.rating, x, y }
    })
  }

  function startDrag(source: 'chip' | 'sticker', kind: Kind, event: ReactPointerEvent<HTMLElement>) {
    if (source === 'chip' && stickerRef.current) return
    const target = event.target as HTMLElement
    if (target.closest('input, textarea')) return
    const starNode = target.closest('[data-star]')
    const star = starNode ? Number((starNode as HTMLElement).dataset.star) : 0
    if (!star) event.preventDefault()
    const startX = event.clientX
    const startY = event.clientY
    let moved = false
    const move = (pointer: PointerEvent) => {
      if (!moved && Math.hypot(pointer.clientX - startX, pointer.clientY - startY) < 6) return
      moved = true
      dragRef.current = { source, kind }
      const extras = extrasRef.current
      const current = stickerRef.current
      const label =
        source === 'sticker' && current
          ? current.kind === 'message'
            ? current.text || 'Message'
            : current.kind === 'rating'
              ? ''
              : current.text
          : kind === 'message'
            ? 'Add a Message'
            : kind === 'location'
              ? extras.location
              : kind === 'weather'
                ? extras.weather
                : kind === 'time'
                  ? extras.time
                  : ''
      const score = source === 'sticker' && current ? current.rating : extras.rating
      setGhost({ source, kind, label, rating: score, x: pointer.clientX, y: pointer.clientY })
    }
    const up = (pointer: PointerEvent) => {
      stop()
      if (!moved) {
        dragRef.current = null
        setGhost(null)
        if (star) {
          if (source === 'sticker') setSticker((current) => (current ? { ...current, rating: star } : current))
          else setRating(star)
        }
        return
      }
      finishDrag(pointer.clientX, pointer.clientY)
    }
    const stop = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
      window.removeEventListener('pointercancel', up)
      dragStop.current = null
    }
    dragStop.current?.()
    dragStop.current = stop
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
    window.addEventListener('pointercancel', up)
  }

  function toggleFriend(id: string) {
    setChosen((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]))
  }

  function toggleAll() {
    setChosen(allChosen ? [] : friends.map((friend) => friend.id))
  }

  function chooseFriend(id: string) {
    setPicked((current) => (current === id ? null : id))
    setViewing(null)
  }

  return (
    <section className={ghost ? 'instant-screen is-dragging' : 'instant-screen'} aria-label="Instant">
      <div className={library ? 'instant-stage is-library' : 'instant-stage'}>
        <div className="instant-main">
          {library ? (
            <div className="instant-library">
              {viewing ? (
                <div className="instant-viewer">
                  <button type="button" className="instant-back" onClick={() => setViewing(null)}>
                    Back
                  </button>
                  <img src={viewing.photo} alt="" />
                  <p>
                    <strong>{viewing.author}</strong>
                    {viewing.when ? ` · ${viewing.when}` : ''}
                  </p>
                </div>
              ) : (
                <div className="instant-grid">
                  {visible.length === 0 ? (
                    <div className="instant-empty">
                      <p>{emptyCopy(signedIn, loadNote, pickedFriend)}</p>
                      {!signedIn && (
                        <button type="button" className="instant-link" onClick={onSignIn}>
                          Sign in
                        </button>
                      )}
                    </div>
                  ) : (
                    visible.map((snap) => (
                      <button key={snap.id} type="button" aria-label={`Instant from ${snap.author}`} onClick={() => setViewing(snap)}>
                        <img src={snap.photo} alt="" />
                      </button>
                    ))
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="instant-view">
              <div ref={previewRef} className="instant-preview">
                {photo ? (
                  <img className="instant-still" src={photo} alt="Preview before sending" />
                ) : (
                  <video ref={videoRef} autoPlay muted playsInline />
                )}
                {!photo && cameraState !== 'ready' && (
                  <p className="instant-camera-note">
                    {cameraState === 'starting' ? 'Starting the camera…' : 'Camera is off. Upload a photo instead.'}
                  </p>
                )}
                {photo && (
                  <button type="button" className="instant-retake" onClick={retake}>
                    Retake
                  </button>
                )}
                {sticker && (
                  <div
                    className={ghost?.source === 'sticker' ? 'instant-sticker is-dragging' : 'instant-sticker'}
                    style={{ left: `${sticker.x * 100}%`, top: `${sticker.y * 100}%` }}
                    onPointerDown={(event) => startDrag('sticker', sticker.kind, event)}
                  >
                    {sticker.kind === 'message' ? (
                      <input
                        ref={messageRef}
                        value={sticker.text}
                        maxLength={80}
                        placeholder="Message"
                        aria-label="Message"
                        onChange={(event) => {
                          const text = event.target.value
                          setSticker((current) => (current ? { ...current, text } : current))
                        }}
                      />
                    ) : sticker.kind === 'rating' ? (
                      <Stars value={sticker.rating} />
                    ) : (
                      <span>{sticker.text}</span>
                    )}
                  </div>
                )}
                {ghost && (
                  <div ref={trashRef} className="instant-trash" aria-hidden="true">
                    <TrashIcon />
                  </div>
                )}
              </div>
            </div>
          )}
          <div className={library ? 'instant-controls is-library' : 'instant-controls'}>
            <button
              type="button"
              className="instant-tool"
              aria-label={library ? 'Back to camera' : 'Instants from friends'}
              aria-pressed={library}
              onClick={() => {
                setLibrary((open) => !open)
                setViewing(null)
                setSendOpen(false)
              }}
            >
              <GridIcon />
            </button>
            {!library &&
              (photo ? (
                <button type="button" className="instant-shutter is-send" aria-label="Choose friends" onClick={revealSend}>
                  <PlaneIcon />
                </button>
              ) : (
                <button
                  type="button"
                  className="instant-shutter"
                  aria-label="Take photo"
                  disabled={cameraState !== 'ready'}
                  onClick={shutter}
                />
              ))}
            {!library &&
              (photo ? (
                <button type="button" className="instant-tool" aria-label="Download photo" onClick={() => void download()}>
                  <DownloadIcon />
                </button>
              ) : (
                <button type="button" className="instant-tool" aria-label="Upload photo" onClick={() => fileRef.current?.click()}>
                  <ImageIcon />
                </button>
              ))}
          </div>
          <input ref={fileRef} className="visually-hidden" type="file" accept="image/*" onChange={onFile} />
        </div>
        {!library && (
          <div className="instant-addons">
            <h2>Choose 1 to add</h2>
            <Addon label="Add a message" locked={sticker != null} onPointerDown={(event) => startDrag('chip', 'message', event)}>
              Add a Message
            </Addon>
            <Addon label="Star rating" locked={sticker != null} onPointerDown={(event) => startDrag('chip', 'rating', event)}>
              <Stars value={rating} />
            </Addon>
            <Addon label="Location" locked={sticker != null} onPointerDown={(event) => startDrag('chip', 'location', event)}>
              <PinIcon />
              <span>{location}</span>
            </Addon>
            <Addon label="Weather" locked={sticker != null} onPointerDown={(event) => startDrag('chip', 'weather', event)}>
              <CloudIcon />
              <span>{weather}</span>
            </Addon>
            <Addon label="Time" locked={sticker != null} onPointerDown={(event) => startDrag('chip', 'time', event)}>
              <ClockIcon />
              <span>{time}</span>
            </Addon>
            <p className="instant-hint">{sticker ? 'Drag it to the bin to change.' : 'Drag one onto the photo.'}</p>
          </div>
        )}
        {notice && <p className="instant-banner">{notice}</p>}
      </div>
      <aside className="instant-circle" aria-label="Friends">
        <button
          type="button"
          className={picked ? 'instant-all' : 'instant-all is-on'}
          onClick={() => {
            setPicked(null)
            setViewing(null)
          }}
        >
          All Friends
        </button>
        {friends.length === 0 ? (
          <p className="instant-circle-empty">{signedIn ? 'Friends you add show up here.' : 'Sign in to see your friends.'}</p>
        ) : (
          <ul className="instant-people">
            {friends.map((friend) => (
              <li key={friend.id}>
                <button
                  type="button"
                  className={picked === friend.id ? 'instant-friend is-on' : 'instant-friend'}
                  aria-pressed={picked === friend.id}
                  onClick={() => chooseFriend(friend.id)}
                >
                  <Avatar avatar={friend.avatar} />
                  <span className="instant-friend-name">{friend.name}</span>
                  <ChevronIcon />
                </button>
              </li>
            ))}
          </ul>
        )}
      </aside>
      {sendOpen && !library && (
        <div className="instant-send" role="dialog" aria-label="Send to">
          <div className="instant-send-head">
            <h2>Send to...</h2>
            <span className="instant-count">
              {chosenLive.length} selected
            </span>
            <button type="button" className="instant-send-close" aria-label="Close" onClick={() => setSendOpen(false)}>
              <CloseIcon />
            </button>
          </div>
          {!signedIn ? (
            <div className="instant-send-empty">
              <p>Sign in to send this instant.</p>
              <button type="button" className="instant-link" onClick={onSignIn}>
                Sign in
              </button>
            </div>
          ) : friends.length === 0 ? (
            <p className="instant-send-empty">Add a friend first, then send the instant.</p>
          ) : (
            <>
              <label className="instant-search">
                <SearchIcon />
                <input
                  value={search}
                  placeholder="Search friends..."
                  onChange={(event) => setSearch(event.target.value)}
                />
              </label>
              <ul className="instant-send-list">
                <li>
                  <button type="button" className="instant-send-row" aria-pressed={allChosen} onClick={toggleAll}>
                    <span className="instant-friend-avatar instant-all-avatar">All</span>
                    <span className="instant-friend-name">All friends</span>
                    <span className={allChosen ? 'instant-radio is-on' : 'instant-radio'} />
                  </button>
                </li>
                {listed.map((friend) => {
                  const on = chosenLive.includes(friend.id)
                  return (
                    <li key={friend.id}>
                      <button type="button" className="instant-send-row" aria-pressed={on} onClick={() => toggleFriend(friend.id)}>
                        <Avatar avatar={friend.avatar} />
                        <span className="instant-friend-name">{friend.name}</span>
                        <span className={on ? 'instant-radio is-on' : 'instant-radio'} />
                      </button>
                    </li>
                  )
                })}
                {listed.length === 0 && <li className="instant-send-miss">No friend with that name.</li>}
              </ul>
            </>
          )}
          <button type="button" className="instant-send-go" disabled={!signedIn || chosenLive.length === 0 || sending} onClick={() => void sendSnap()}>
            {sending ? 'Sending…' : 'Send Snap'}
          </button>
        </div>
      )}
      {ghost && (
        <div className="instant-ghost" style={{ left: ghost.x, top: ghost.y }}>
          {ghost.kind === 'rating' ? <Stars value={ghost.rating} /> : ghost.label}
        </div>
      )}
    </section>
  )
}

function Addon({
  label,
  locked,
  onPointerDown,
  children,
}: {
  label: string
  locked: boolean
  onPointerDown: (event: ReactPointerEvent<HTMLDivElement>) => void
  children: ReactNode
}) {
  return (
    <div
      className={locked ? 'instant-chip is-locked' : 'instant-chip'}
      role="button"
      tabIndex={locked ? -1 : 0}
      aria-label={label}
      aria-disabled={locked}
      onPointerDown={onPointerDown}
    >
      {children}
    </div>
  )
}

function Avatar({ avatar }: { avatar: string }) {
  return (
    <span className="instant-friend-avatar" aria-hidden="true">
      {avatar ? <img src={avatar} alt="" /> : <PersonIcon />}
    </span>
  )
}

function Stars({ value }: { value: number }) {
  return (
    <span className="instant-stars" aria-label={`${value} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((score) => (
        <svg key={score} viewBox="0 0 24 24" data-star={score} className={score <= value ? 'is-on' : undefined}>
          <path d="m12 3.4 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.8 6.8 19.5l1-5.8L3.5 9.6l5.9-.9z" />
        </svg>
      ))}
    </span>
  )
}

function emptyCopy(signedIn: boolean, loadNote: string | null, friend: Friend | null) {
  if (!signedIn) return 'Sign in to see instants from friends.'
  if (loadNote) return loadNote
  if (friend) return `${friend.name} has not sent you an instant yet.`
  return 'Instants friends send you show up here.'
}

function asSnap(value: unknown): Snap {
  const raw = value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
  return {
    id: typeof raw.id === 'string' ? raw.id : '',
    authorId: typeof raw.authorId === 'string' ? raw.authorId : '',
    author: typeof raw.author === 'string' && raw.author ? raw.author : 'Friend',
    avatar: typeof raw.avatar === 'string' ? raw.avatar : '',
    photo: typeof raw.photo === 'string' ? raw.photo : '',
    when: typeof raw.when === 'string' ? raw.when : '',
  }
}

function clock() {
  return new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

type PlaceLookup = {
  city?: string
  locality?: string
  principalSubdivision?: string
  principalSubdivisionCode?: string
}

function placeLabel(data: PlaceLookup) {
  const city = data.city || data.locality || 'Toronto'
  let code = data.principalSubdivisionCode ?? ''
  const dash = code.lastIndexOf('-')
  if (dash >= 0) code = code.slice(dash + 1)
  if (code.length > 0 && code.length <= 3) return `${city}, ${code}`
  const region = data.principalSubdivision?.trim() ?? ''
  if (region && region.toLowerCase() !== city.toLowerCase()) return `${city}, ${region}`
  return city
}

function weatherLabel(code: number, temperature: number) {
  if (!Number.isFinite(code) || !Number.isFinite(temperature)) return 'Weather unavailable'
  const degrees = `${Math.round(temperature)}°`
  const name =
    code === 0
      ? 'Clear'
      : code <= 3
        ? 'Cloudy'
        : code <= 48
          ? 'Fog'
          : code <= 67
            ? 'Rain'
            : code <= 77
              ? 'Snow'
              : code <= 82
                ? 'Showers'
                : code <= 86
                  ? 'Snow'
                  : 'Storm'
  return `${degrees} ${name}`
}

function inside(box: DOMRect, x: number, y: number) {
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function frameFromVideo(video: HTMLVideoElement) {
  const canvas = document.createElement('canvas')
  canvas.width = PHOTO_W
  canvas.height = PHOTO_H
  const context = canvas.getContext('2d')
  if (!context || video.videoWidth === 0) return ''
  const box = coverRect(video.videoWidth, video.videoHeight, PHOTO_W, PHOTO_H)
  context.save()
  context.translate(PHOTO_W, 0)
  context.scale(-1, 1)
  context.drawImage(video, box.x, box.y, box.width, box.height)
  context.restore()
  return compress(canvas)
}

function coverPhoto(source: string) {
  return loadImage(source).then((image) => {
    const canvas = document.createElement('canvas')
    canvas.width = PHOTO_W
    canvas.height = PHOTO_H
    const context = canvas.getContext('2d')
    if (!context) throw new Error('Could not read that photo.')
    const box = coverRect(image.width, image.height, PHOTO_W, PHOTO_H)
    context.drawImage(image, box.x, box.y, box.width, box.height)
    return compress(canvas)
  })
}

function exportPhoto(source: string, sticker: Sticker | null) {
  return loadImage(source).then((image) => {
    const canvas = document.createElement('canvas')
    canvas.width = image.width
    canvas.height = image.height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('Could not read that photo.')
    context.drawImage(image, 0, 0)
    if (sticker) drawSticker(context, sticker, image.width, image.height)
    return compress(canvas)
  })
}

function drawSticker(context: CanvasRenderingContext2D, sticker: Sticker, width: number, height: number) {
  const caption =
    sticker.kind === 'rating'
      ? `${'★'.repeat(clamp(sticker.rating, 1, 5))}${'☆'.repeat(5 - clamp(sticker.rating, 1, 5))}`
      : sticker.text.trim()
  if (!caption) return
  let fontSize = Math.max(22, Math.round(width / 26))
  context.font = `600 ${fontSize}px Segoe UI, sans-serif`
  while (context.measureText(caption).width > width * 0.72 && fontSize > 18) {
    fontSize -= 2
    context.font = `600 ${fontSize}px Segoe UI, sans-serif`
  }
  const textWidth = context.measureText(caption).width
  const padX = fontSize * 0.8
  const padY = fontSize * 0.45
  const boxWidth = textWidth + padX * 2
  const boxHeight = fontSize + padY * 2
  const x = clamp(sticker.x, 0, 1) * width - boxWidth / 2
  const y = clamp(sticker.y, 0, 1) * height - boxHeight / 2
  context.fillStyle = 'rgba(247, 244, 238, 0.94)'
  roundRect(context, x, y, boxWidth, boxHeight, boxHeight / 2)
  context.fill()
  context.fillStyle = sticker.kind === 'rating' ? '#c47d8e' : '#1c1c1c'
  context.textBaseline = 'middle'
  context.fillText(caption, x + padX, y + boxHeight / 2)
}

function roundRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  const curve = Math.min(radius, height / 2, width / 2)
  context.beginPath()
  context.moveTo(x + curve, y)
  context.arcTo(x + width, y, x + width, y + height, curve)
  context.arcTo(x + width, y + height, x, y + height, curve)
  context.arcTo(x, y + height, x, y, curve)
  context.arcTo(x, y, x + width, y, curve)
  context.closePath()
}

function coverRect(sourceWidth: number, sourceHeight: number, width: number, height: number) {
  const scale = Math.max(width / sourceWidth, height / sourceHeight)
  const drawnWidth = sourceWidth * scale
  const drawnHeight = sourceHeight * scale
  return { x: (width - drawnWidth) / 2, y: (height - drawnHeight) / 2, width: drawnWidth, height: drawnHeight }
}

function loadImage(source: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('Could not read that photo.'))
    image.src = source
  })
}

function compress(canvas: HTMLCanvasElement, depth = 0): string {
  let quality = 0.84
  let url = canvas.toDataURL('image/jpeg', quality)
  while (url.length > 170_000 && quality > 0.42) {
    quality -= 0.1
    url = canvas.toDataURL('image/jpeg', quality)
  }
  if (url.length <= 170_000 || depth >= 3) return url
  const next = document.createElement('canvas')
  next.width = Math.max(640, Math.round(canvas.width * 0.72))
  next.height = Math.max(640, Math.round(canvas.height * 0.72))
  const context = next.getContext('2d')
  if (!context) return url
  context.drawImage(canvas, 0, 0, next.width, next.height)
  return compress(next, depth + 1)
}

function GridIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.6" />
    </svg>
  )
}

function ImageIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3.5" y="5" width="17" height="14" rx="2" />
      <circle cx="8.5" cy="10" r="1.4" />
      <path d="M4.5 16.5 8.8 12l3 3.2 2.3-2.2L19.5 17" />
    </svg>
  )
}

function DownloadIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 4.5v10" />
      <path d="m8 11 4 4 4-4" />
      <path d="M5 18.5h14" />
    </svg>
  )
}

function PlaneIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3.4 11.2 20.4 4.4l-6.2 15-2.4-6z" />
      <path d="m11.8 13.4 8.6-9" />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 7.5h14" />
      <path d="M9.5 7.4V5.8A1.3 1.3 0 0 1 10.8 4.5h2.4a1.3 1.3 0 0 1 1.3 1.3v1.6" />
      <path d="M7.2 7.5 8 18.2a1.4 1.4 0 0 0 1.4 1.3h5.2a1.4 1.4 0 0 0 1.4-1.3l.8-10.7" />
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

function CloudIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7.5 17.5h9a3.5 3.5 0 0 0 .4-7 4.5 4.5 0 0 0-8.6-1.2A3.2 3.2 0 0 0 7.5 17.5z" />
    </svg>
  )
}

function ClockIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="7.2" />
      <path d="M12 8.2V12l2.6 2" />
    </svg>
  )
}

function ChevronIcon() {
  return (
    <svg className="chevron" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M9 6.5 14.5 12 9 17.5" />
    </svg>
  )
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="5.5" />
      <path d="m15.2 15.2 3.4 3.4" />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 7l10 10M17 7 7 17" />
    </svg>
  )
}

function PersonIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8.5" r="3.1" />
      <path d="M6.4 18.2c.8-2.6 2.9-4 5.6-4s4.8 1.4 5.6 4" />
    </svg>
  )
}
