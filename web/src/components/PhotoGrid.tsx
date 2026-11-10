import { photoFrame } from '../photoLayout'

type Props = {
  photos: string[]
  className?: string
}

export function PhotoGrid({ photos, className = '' }: Props) {
  if (photos.length === 0) return null
  const { layout } = photoFrame(photos.length)
  return (
    <div className={`photo-grid photo-grid-${layout} ${className}`.trim()}>
      {photos.map((src, index) => (
        <img key={`${index}-${src.slice(0, 24)}`} src={src} alt="" />
      ))}
    </div>
  )
}
