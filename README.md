# EECS3311-Course-Project

## Website

The desktop site lives in `web/`. A live Google Map fills the background. The cream sidebar, search bar, and chat card sit on top of it. The chat card asks the Java server for restaurants that fit what you typed and your taste profile. Friends shows reviews from people you have added. Add `OPENAI_API_KEY` to `supabase.local` when you want a language model to write the reply. Without that key, the same agent still picks the places and writes a short reply itself.

Search sends the text to the Java server, which asks Places API (New), biased to your location or downtown Toronto. Matches show under “More on Google” and as amber pins. Choosing a result closes the list, centers that pin, and opens a detail card at the bottom of the map. The bookmark adds that place to a collection. **Saved restaurants** is the default collection for the person who is signed in. The plus button opens Create review. A signed-in person picks a restaurant, a star rating, tags, and a note, then publishes it. Profile signs in or creates an account. The Java program uses the Supabase project named in `supabase.local` for sign-in, collections, reviews, friends, and profile photos. Before the first run, open the Supabase SQL editor and run `supabase/schema.sql`. Run that file again if friends were stored before the `friendships` and `avatars` tables existed, and again so taste profiles can be stored in Supabase. Until that table exists, the server keeps them in `data/taste-profiles.json`.

### Run

1. In Google Cloud Console, enable **Maps JavaScript API** for a browser key. If the key has an HTTP referrer restriction, allow the Vite address (usually `http://localhost:5173/*`).
2. Copy `web/.env.example` to `web/.env.local` and set `VITE_GOOGLE_MAPS_API_KEY`.
3. Enable **Places API (New)** for a second key that is not restricted to a website address. Add `PLACES_API_KEY=that-key` to `supabase.local`. The browser key cannot be used for search.
4. From the project root, start the Java backend:

```
javac -encoding UTF-8 -d out src/main/java/foodassistant/model/*.java src/main/java/foodassistant/collection/*.java src/main/java/foodassistant/review/*.java src/main/java/foodassistant/account/*.java src/main/java/foodassistant/places/*.java src/main/java/foodassistant/map/*.java src/main/java/foodassistant/feedback/*.java src/main/java/foodassistant/taste/*.java src/main/java/foodassistant/llm/*.java src/main/java/foodassistant/agent/*.java src/main/java/foodassistant/server/*.java src/main/java/foodassistant/cli/*.java
java -cp out foodassistant.server.CollectionServer
```

The same server answers restaurant search. With it running:

```
java -cp out foodassistant.cli.FoodAssistantCli --query ramen
```

5. From `web/`:

```
npm install
npm run dev
```

Open the URL Vite prints. Without a key, the sidebar and chat still show, and the map area asks for the key. With a key, pan and zoom the map. If location is allowed, a pin is placed on you and the map centers once. Later GPS updates move the pin only. If location is denied, the map stays on Toronto and no pin is shown.
