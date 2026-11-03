import { useState } from 'react'

const CHIPS = ['Comfort food', 'Cafe & Brunch', 'Budget-friendly']

export function ChatCard() {
  const [draft, setDraft] = useState('')
  const [selected, setSelected] = useState<string[]>([])

  function toggleChip(chip: string) {
    setSelected((current) =>
      current.includes(chip) ? current.filter((item) => item !== chip) : [...current, chip],
    )
  }

  return (
    <section className="chat" aria-label="Food assistant">
      <h1>Your Food Assistant</h1>
      <p className="chat-tagline">Personalized to your taste</p>
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
          onChange={(event) => setDraft(event.target.value)}
        />
      </label>
      <div className="composer-actions">
        <button type="button" aria-label="Add photo">
          <ImageIcon />
        </button>
        <button type="button" aria-label="Voice">
          <MicIcon />
        </button>
        <button type="button" className="send" aria-label="Send">
          <ArrowIcon />
        </button>
      </div>
    </section>
  )
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
