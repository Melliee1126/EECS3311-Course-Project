import { collectionLabel, type Collection } from '../collections'

type Props = {
  collections: Collection[]
  memberIds: string[]
  onToggle: (collectionId: string) => void
  onRequestCreate: () => void
  onClose: () => void
}

export function CollectionMenu({ collections, memberIds, onToggle, onRequestCreate, onClose }: Props) {
  return (
    <>
      <button type="button" className="menu-backdrop" aria-label="Close collections" onClick={onClose} />
      <div className="collection-menu" role="dialog" aria-label="Add to collection">
        <p className="collection-menu-title">Add to collection</p>
        <ul>
          {collections.map((collection) => {
            const included = memberIds.includes(collection.id)
            return (
              <li key={collection.id}>
                <button type="button" aria-pressed={included} onClick={() => onToggle(collection.id)}>
                  <span>{collectionLabel(collection)}</span>
                  <span className="collection-check">{included ? 'Added' : 'Add'}</span>
                </button>
              </li>
            )
          })}
        </ul>
        <button
          type="button"
          className="library-new"
          onClick={() => {
            onClose()
            onRequestCreate()
          }}
        >
          + Create new collection
        </button>
      </div>
    </>
  )
}
