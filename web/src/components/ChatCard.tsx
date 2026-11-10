import { useEffect, useRef, useState } from 'react'
import type { SuggestedRestaurant } from '../assistant'

const CHIPS = ['Comfort food', 'Cafe & Brunch', 'Budget-friendly']

export type ChatMessage = {
  role: 'user' | 'assistant'
  text: string
  picks: SuggestedRestaurant[]
}

export type ChatSessionSummary = {
  id: string
  title: string
  updatedAt: number
}

type Props = {
  thread: ChatMessage[]
  busy: boolean
  signedIn: boolean
  sessions: ChatSessionSummary[]
  activeSessionId: string | null
  onSend: (message: string, chips: string[]) => void
  onOpenSession: (id: string) => void
  onNewChat: () => void
}

export function ChatCard({
  thread,
  busy,
  signedIn,
  sessions,
  activeSessionId,
  onSend,
  onOpenSession,
  onNewChat,
}: Props) {
  const [draft, setDraft] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [localError, setLocalError] = useState<string | null>(null)
  const [historyOpen, setHistoryOpen] = useState(false)
  const logRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const log = logRef.current
    if (!log) return
    log.scrollTop = log.scrollHeight
  }, [thread, busy])

  function toggleChip(chip: string) {
    setSelected((current) =>
      current.includes(chip) ? current.filter((item) => item !== chip) : [...current, chip],
    )
  }

  function send() {
    const message = draft.trim()
    if (!message && selected.length === 0) {
      setLocalError('Ask for a kind of food.')
      return
    }
    setLocalError(null)
    if (signedIn) setDraft('')
    onSend(message, selected)
  }

  const talking = thread.length > 0 || busy

  return (
    <section className={talking ? 'chat chat-open' : 'chat'} aria-label="Food assistant" aria-busy={busy}>
      <div className="chat-head">
        <div>
          <h1>Your Food Assistant</h1>
          <p className="chat-tagline">Personalized to your taste</p>
        </div>
        <button
          type="button"
          className="chat-history-tab"
          aria-expanded={historyOpen}
          onClick={() => setHistoryOpen((open) => !open)}
        >
          History
        </button>
      </div>
      {historyOpen && (
        <div className="chat-history">
          <button
            type="button"
            className="chat-history-new"
            onClick={() => {
              setHistoryOpen(false)
              onNewChat()
            }}
          >
            New chat
          </button>
          {sessions.length === 0 ? (
            <p className="chat-status">No earlier chats.</p>
          ) : (
            <ul>
              {sessions.map((session) => (
                <li key={session.id}>
                  <button
                    type="button"
                    aria-current={session.id === activeSessionId ? 'true' : undefined}
                    onClick={() => {
                      setHistoryOpen(false)
                      onOpenSession(session.id)
                    }}
                  >
                    <span>{session.title}</span>
                    <time dateTime={new Date(session.updatedAt).toISOString()}>{formatWhen(session.updatedAt)}</time>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {talking && (
        <div className="chat-log" ref={logRef}>
          {thread.map((turn, index) => (
            <div key={`${turn.role}-${index}`} className={turn.role === 'user' ? 'chat-turn mine' : 'chat-turn'}>
              <p className={turn.role === 'user' ? 'chat-user' : 'chat-reply'}>{turn.text}</p>
            </div>
          ))}
          {busy && <p className="chat-status">Looking for places that fit…</p>}
        </div>
      )}
      {localError && <p className="chat-error">{localError}</p>}
      <div className="chips">
        {CHIPS.map((chip) => {
          const isOn = selected.includes(chip)
          return (
            <button
              key={chip}
              type="button"
              className={isOn ? 'chip selected' : 'chip'}
              aria-pressed={isOn}
              onClick={() => toggleChip(chip)}
            >
              {chip}
            </button>
          )
        })}
      </div>
      <label className="composer">
        <span className="visually-hidden">Ask me anything about food</span>
        <textarea
          rows={2}
          value={draft}
          placeholder="Ask me anything about food..."
          onChange={(event) => {
            setDraft(event.target.value)
            setLocalError(null)
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              if (!busy) send()
            }
          }}
        />
      </label>
      <div className="composer-actions">
        <button type="button" aria-label="Add photo">
          <ImageIcon />
        </button>
        <button type="button" aria-label="Voice">
          <MicIcon />
        </button>
        <button type="button" className="send" aria-label="Send" disabled={busy} onClick={send}>
          <ArrowIcon />
        </button>
      </div>
    </section>
  )
}

function formatWhen(updatedAt: number) {
  const when = new Date(updatedAt)
  const now = new Date()
  const sameDay = when.toDateString() === now.toDateString()
  return sameDay
    ? when.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
    : when.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

function ImageIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="4" y="5" width="16" height="14" rx="2" />
      <circle cx="9" cy="10" r="1.4" />
      <path d="M5 16.5 9.2 12l3 3.2 2.2-2.2L19 17" />
    </svg>
  )
}

function MicIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="9" y="3.5" width="6" height="10" rx="3" />
      <path d="M7 11a5 5 0 0 0 10 0M12 16.2V20" />
    </svg>
  )
}

function ArrowIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 18V6M7 11l5-5 5 5" />
    </svg>
  )
}
