# EECS3311-Course-Project

## Website

The desktop site lives in `web/`. A live Google Map fills the background. The cream sidebar, search bar, and chat card sit on top of it. Search and chat are visual for now: nothing is sent, and the other screens are not wired up.

### Run

1. In Google Cloud Console, enable **Maps JavaScript API** for a browser key.
2. Copy `web/.env.example` to `web/.env.local` and set `VITE_GOOGLE_MAPS_API_KEY`.
3. From `web/`:

```
npm install
npm run dev
```

Open the URL Vite prints. Without a key, the sidebar and chat still show, and the map area asks for the key. With a key, pan and zoom the map. If location is allowed, a pin is placed on you and the map centers once. Later GPS updates move the pin only. If location is denied, the map stays on Toronto and no pin is shown.
