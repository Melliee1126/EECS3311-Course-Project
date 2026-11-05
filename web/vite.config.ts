import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

const restaurantFile = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../data/restaurants.json',
)

function restaurantData(): Plugin {
  return {
    name: 'restaurant-data',
    configureServer(server) {
      server.middlewares.use('/restaurants.json', (_req, res) => {
        res.setHeader('Content-Type', 'application/json')
        res.end(readFileSync(restaurantFile))
      })
    },
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'restaurants.json',
        source: readFileSync(restaurantFile),
      })
    },
  }
}

export default defineConfig({
  plugins: [react(), restaurantData()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
