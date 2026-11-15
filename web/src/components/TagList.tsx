type Props = {
  tags: string[]
}

export function TagList({ tags }: Props) {
  if (tags.length === 0) return null

  return (
    <span className="category-tags">
      {tags.map((tag) => (
        <span key={tag} className="category-tag">
          {tag}
        </span>
      ))}
    </span>
  )
}
