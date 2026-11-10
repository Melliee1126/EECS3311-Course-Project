import { useEffect, useRef, useState, type PointerEvent } from 'react'

export type Placement = { zoom: number; x: number; y: number }

type Props = {
  source: string
  width: number
  height: number
  initial?: Placement
  onCancel: () => void
  onReselect: () => void
  onUse: (framed: string, placement: Placement) => void
}

export function PhotoFramer({ source, width, height, initial, onCancel, onReselect, onUse }: Props) {
  const frameRef = useRef<HTMLDivElement>(null)
  const imageRef = useRef<HTMLImageElement | null>(null)
  const drag = useRef<{ x: number; y: number; ox: number; oy: number } | null>(null)
  const [ready, setReady] = useState(false)
  const [zoom, setZoom] = useState(initial?.zoom ?? 1)
  const [offset, setOffset] = useState({ x: initial?.x ?? 0, y: initial?.y ?? 0 })
  const [frame, setFrame] = useState({ w: 0, h: 0 })

  useEffect(() => {
    const image = new Image()
    image.onload = () => {
      imageRef.current = image
      setReady(true)
    }
    image.src = source
  }, [source])

  useEffect(() => {
    const node = frameRef.current
    if (!node) return
    const measure = () => setFrame({ w: node.clientWidth, h: node.clientHeight })
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  function coverScale() {
    const image = imageRef.current
    if (!image || frame.w === 0 || frame.h === 0) return 1
    return Math.max(frame.w / image.width, frame.h / image.height)
  }

  function clamp(nextZoom: number, next: { x: number; y: number }) {
    const image = imageRef.current
    if (!image || frame.w === 0) return next
    const scale = Math.max(frame.w / image.width, frame.h / image.height) * nextZoom
    const maxX = Math.max(0, (image.width * scale - frame.w) / 2)
    const maxY = Math.max(0, (image.height * scale - frame.h) / 2)
    return {
      x: Math.min(maxX, Math.max(-maxX, next.x)),
      y: Math.min(maxY, Math.max(-maxY, next.y)),
    }
  }

  function imageStyle() {
    const image = imageRef.current
    if (!image || frame.w === 0) return undefined
    const scale = coverScale() * zoom
    const dw = image.width * scale
    const dh = image.height * scale
    const x = (frame.w - dw) / 2 + offset.x
    const y = (frame.h - dh) / 2 + offset.y
    return { width: dw, height: dh, transform: `translate(${x}px, ${y}px)` }
  }

  function move(event: PointerEvent<HTMLDivElement>) {
    if (!drag.current) return
    setOffset(
      clamp(zoom, {
        x: drag.current.ox + event.clientX - drag.current.x,
        y: drag.current.oy + event.clientY - drag.current.y,
      }),
    )
  }

  function usePhoto() {
    const image = imageRef.current
    if (!image || frame.w === 0 || frame.h === 0) return
    const scale = Math.max(frame.w / image.width, frame.h / image.height) * zoom
    const dw = image.width * scale
    const dh = image.height * scale
    const x = (frame.w - dw) / 2 + offset.x
    const y = (frame.h - dh) / 2 + offset.y
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) return
    const ratio = width / frame.w
    context.drawImage(image, x * ratio, y * ratio, dw * ratio, dh * ratio)
    const framed = canvas.toDataURL('image/jpeg', 0.82)
    if (framed.length > 180_000) {
      onUse(canvas.toDataURL('image/jpeg', 0.6), { zoom, x: offset.x, y: offset.y })
      return
    }
    onUse(framed, { zoom, x: offset.x, y: offset.y })
  }

  return (
    <div className="photo-framer" role="dialog" aria-label="Reframe photo">
      <div className="photo-framer-card">
        <h2>Reframe photo</h2>
        <p>Drag the photo and use the slider. The feed shows this frame.</p>
        <div
          ref={frameRef}
          className="photo-framer-frame"
          style={{ ['--frame-w' as string]: width, ['--frame-h' as string]: height }}
          onPointerDown={(event) => {
            drag.current = { x: event.clientX, y: event.clientY, ox: offset.x, oy: offset.y }
            event.currentTarget.setPointerCapture(event.pointerId)
          }}
          onPointerMove={move}
          onPointerUp={() => {
            drag.current = null
          }}
          onPointerCancel={() => {
            drag.current = null
          }}
        >
          {ready && <img src={source} alt="" style={imageStyle()} draggable={false} />}
        </div>
        <label className="photo-framer-zoom">
          Zoom
          <input
            type="range"
            min={1}
            max={3}
            step={0.01}
            value={zoom}
            onChange={(event) => {
              const next = Number(event.target.value)
              setZoom(next)
              setOffset((current) => clamp(next, current))
            }}
          />
        </label>
        <div className="photo-framer-actions">
          <button type="button" className="photo-framer-reselect" onClick={onReselect}>
            Reselect
          </button>
          <button type="button" className="photo-framer-cancel" onClick={onCancel}>
            Cancel
          </button>
          <button type="button" className="photo-framer-use" disabled={!ready} onClick={usePhoto}>
            Use photo
          </button>
        </div>
      </div>
    </div>
  )
}
