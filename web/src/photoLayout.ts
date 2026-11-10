export type PhotoShape = 'single' | 'tile'

export type PhotoFrame = {
  width: number
  height: number
  layout: 'single' | 'pair' | 'grid'
}

/** One photo stays wide. Two or more share a square frame so they can sit in a row or a grid. */
export function photoFrame(count: number): PhotoFrame {
  if (count <= 1) return { width: 960, height: 540, layout: 'single' }
  if (count === 2) return { width: 800, height: 800, layout: 'pair' }
  return { width: 800, height: 800, layout: 'grid' }
}

export function photoShape(count: number): PhotoShape {
  return count <= 1 ? 'single' : 'tile'
}

export function coverCrop(source: string, width: number, height: number): Promise<string> {
  return new Promise((resolve) => {
    const image = new Image()
    image.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const context = canvas.getContext('2d')
      if (!context) {
        resolve(source)
        return
      }
      const scale = Math.max(width / image.width, height / image.height)
      const dw = image.width * scale
      const dh = image.height * scale
      context.drawImage(image, (width - dw) / 2, (height - dh) / 2, dw, dh)
      let framed = canvas.toDataURL('image/jpeg', 0.82)
      if (framed.length > 180_000) framed = canvas.toDataURL('image/jpeg', 0.6)
      resolve(framed)
    }
    image.onerror = () => resolve(source)
    image.src = source
  })
}

export async function fitPhotoShapes<
  T extends { source: string; framed: string; shape: PhotoShape; zoom: number; x: number; y: number },
>(photos: T[], shape: PhotoShape): Promise<T[]> {
  const frame = photoFrame(shape === 'single' ? 1 : 2)
  return Promise.all(
    photos.map(async (item) => {
      if (item.shape === shape) return item
      const framed = await coverCrop(item.source, frame.width, frame.height)
      return { ...item, framed, shape, zoom: 1, x: 0, y: 0 }
    }),
  )
}
