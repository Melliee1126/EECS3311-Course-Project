# EECS3311-Course-Project

## Website

The desktop site lives in `web/`. A live Google Map fills the background. The cream sidebar, search bar, and chat card sit on top of it. Chat, For you, and Friends are visual for now.

Search sends the text to Places API (New), biased to your location or downtown Toronto. Matches show under “More on Google” and as amber pins. Choosing a result closes the list, centers that pin, and opens a detail card at the bottom of the map. The bookmark adds that place to a collection. **Saved restaurants** is the default collection for the person who is signed in. The plus button opens Create review. A signed-in person picks a restaurant, a star rating, tags, and a note, then publishes it. Profile signs in or creates an account. The Java program stores each password as a hash in the Supabase project named in `supabase.local`, along with that person’s collections. Before the first run, open the Supabase SQL editor and run `supabase/schema.sql`.

### Run

1. In Google Cloud Console, enable **Maps JavaScript API** and **Places API (New)** for a browser key. If the key has an HTTP referrer restriction, allow the Vite address (usually `http://localhost:5173/*`).
2. Copy `web/.env.example` to `web/.env.local` and set `VITE_GOOGLE_MAPS_API_KEY`.
3. From the project root, start the Java backend:

```
javac -encoding UTF-8 -d out src/main/java/foodassistant/model/*.java src/main/java/foodassistant/collection/*.java src/main/java/foodassistant/account/*.java src/main/java/foodassistant/server/*.java
java -cp out foodassistant.server.CollectionServer
```

4. From `web/`:

```
npm install
npm run dev
```

Open the URL Vite prints. Without a key, the sidebar and chat still show, and the map area asks for the key. With a key, pan and zoom the map. If location is allowed, a pin is placed on you and the map centers once. Later GPS updates move the pin only. If location is denied, the map stays on Toronto and no pin is shown.
