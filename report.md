# Project report

**Project:** Personal food assistant (EECS3311)  
**Draft:** 14 November 2026. This section describes the program in the repository on that date.

---

## 1. Project overview and implementation status

The application is a personal food assistant. A signed-in person looks for restaurants near their current location, sees them on a map, saves them into collections, writes reviews, follows friends, and asks a chat assistant for places that fit what they want and what the account has already shown it likes.

The people it is built for are individual diners deciding where to eat, especially in and around Toronto. The map uses the browser’s location when the person allows it, and downtown Toronto when they do not. It is a personal tool: each account has its own collections, reviews, taste profile, friends, and chat. It is not a tool for restaurant owners, and it does not take reservations or orders.

The website in `web/` is the full interface. A Google Map fills the screen, and the sidebar, search bar, and chat card sit on top of it. The Java server `foodassistant.server.CollectionServer` (port 8080) is the only program that talks to Google Places and to Supabase. The website calls that server for search, accounts, collections, reviews, friends, photo posts, taste, and chat. Passwords go through Supabase Auth. Collections, reviews, friendships, profile photos, taste profiles, and instant photos are rows in the Supabase database described in `supabase/schema.sql`. The command-line program `foodassistant.cli.FoodAssistantCli` calls the same `/api/search` route and prints the restaurants through `MapService`.

### AI and language-model integration

Recommendations are produced by `FoodAgent`. The website and the server both call that one class. For a signed-in request it extracts what the person asked for, loads their taste, searches Google Places near their coordinates, filters and ranks the hits, and returns up to five places with a short reply. Those places are drawn as pins on the map.

The language model has two jobs, and both sit behind the `LLMEngine` interface. `IntentExtractor` asks the model to read the latest message, together with a few earlier turns, and return a small JSON object: dish, cuisine, budget, location, and whether the person wants a search or a clarifying question. `ResponseGenerator` then asks the model to write the chat reply. The prompt gives the model only places, scores, and taste facts that the Java program has already checked, and it tells the model not to invent names, addresses, ratings, prices, or menus. Search, filtering, and ranking stay in Java. `TasteBasedRanking` prefers places that match the account’s interests, likes, and usual price. `DistanceBasedRanking` is used when the person asks for somewhere nearby and did not name a food. If the model is unavailable, both steps fall back to code in the repository: word rules in `ConstraintExtractor` and `RequestSense` for the request, and `LocalLLMEngine` for the reply.

Which model runs is read from `supabase.local` by `LlmSettings` and built by `LLMFactory`. With `OPENAI_API_KEY` set, and with `LLM_PROVIDER` left unset or set to anything other than `local`, the factory builds `OpenAILLMEngine`. That engine calls OpenAI chat completions through `ExternalLLMClient`, using `gpt-4o-mini` unless `OPENAI_MODEL` names another model. `LLMProviderAdapter` is the only class that sees that client. With no key, or with `LLM_PROVIDER=local`, the factory builds `LocalLLMEngine`, which writes a short reply from the same facts. A failed cloud call uses that local reply as well, so a recommendation can still be returned.

Taste is stored per account and is updated without asking the model to invent a profile. `TasteProfileUpdater` scores foods from three signals: phrases the person states in chat (likes, dislikes, diets, and foods to avoid), the ratings, tags, and text of their reviews, and restaurants they have saved. Publishing, editing, or deleting a review notifies that updater through `FeedbackPublisher`. Chat can also record a stated preference before the search runs. **For you** searches from that profile when the person does not type a request. Category tags on a restaurant come from Google place types and the place name in `PlaceTags`. They are assigned by rules, and the person does not type them.

### What works today

- The map shows the person’s location, or downtown Toronto if location is denied. Search results, the open collection, or the latest chat recommendations appear as pins. Selecting a pin opens a detail card.
- Restaurant search on the website and on the command line both call `/api/search`. The server queries Places API (New), biased to the person or to Toronto. A missing or failing Places call is shown as an error. `--unavailable` on the command line prints a list when the map display is down.
- Sign-in, registration, email confirmation, password recovery, profile edits, and a profile photo.
- Collections for the signed-in account, including the default list **Saved restaurants**. A place can be added from its detail card or from a friend’s review. Duplicate places are refused, and the default list cannot be deleted.
- Reviews with a rating from 1 to 5, a note of up to 500 characters, up to eight tags, and up to four photos. Empty notes and ratings outside that range are rejected. A new or edited review updates the taste profile.
- Friend requests by email, and a feed of friends’ reviews in time order. The feed explains itself when there are no posts.
- An instant photo: the person takes or uploads one picture, may write a short caption onto it, and sends it to chosen friends. Friends see those photos in the instant inbox.
- Chat for a signed-in person. The assistant answers a typed request or **For you**, shows the reply and the recommended places, and pins those places when no search is active. The last 20 chats for that account are stored in Supabase and can be reopened on another browser, including the places from each chat.
- A taste profile on the server: explicit interests and a short description, plus likes, dislikes, diet, foods to avoid, and price comfort learned from chat, reviews, and saved places.

### Incomplete features and limitations

- The command line searches and prints restaurants. Accounts, chat, reviews, collections, friends, and taste are available on the website.
- Chat history keeps the latest 20 sessions for the account. A chat that was saved only in this browser is uploaded the next time that account signs in here. Opening the history list loads the sessions stored for the account.
- There is no separate visited list. The profile can count places whose status is `visited`, and nothing in the program sets that status, so the count stays at zero.
- Reviews have no private or public choice. Every stored review can appear on a friend’s feed.
- An instant stores the picture and the recipients. The caption is drawn onto the image in the browser. An instant is not tied to a restaurant, and it does not update taste.
- Restaurant tags are rule labels from Google place types and the name. Review tags are still typed by the person. `generateTags` on the language-model interface matches known food words. It does not call the model.
- The assistant has no menus and no opening hours. Diet and allergy handling keeps avoided foods out of the search text and out of places whose name or cuisine matches them. It cannot claim that a dish is safe. Words about atmosphere, such as a stylish room, are treated as a mood and then approximated with price and rating.
- A budget of `$$$$` does not send a price filter to Places. Lower bands do. If a filtered search returns fewer than three places, the agent searches again without the price filter and says so in the reply.
- Cloud replies depend on a key in `supabase.local` and on OpenAI answering. The local writer is a template over the same facts. It is less specific than the cloud reply.
- There is no automated test suite. The checks so far are manual runs of the website and the command line.

---

## 2. Feature-to-implementation mapping

Each entry is one Stage 1 feature. The classes and methods are the ones in the repository on 14 November 2026. The video row is left for later.

### F01 — Personal Food Map Visualization

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F01 — Personal Food Map Visualization |
| Stage 1 feature/reference | F01 |
| Status or change | Modified. The map runs. Search results, the open collection, and the latest chat recommendations can appear as pins. There is no visited layer, and only one of those sets is shown at a time. |
| Implemented classes/files and responsibilities | `web/src/components/MapBackground.tsx` draws the Google Map, the user pin, and restaurant pins. `web/src/App.tsx` chooses the pins: search results while a query is active, otherwise the latest recommendations, otherwise the open collection. `web/src/components/PlaceDetail.tsx` is the detail card. `web/src/userLocation.ts` reads the browser location. On the command line, `MapService` shows the same search results. `MapProvider` is the display interface, `MapProviderAdapter` turns each `Restaurant` into a pin label, and `ExternalMapClient` prints it. |
| Key methods | `MapService.show()`, `MapProvider.isAvailable()`, `MapProvider.showMarkers()`, `MapProviderAdapter.showMarkers()`, `ExternalMapClient.renderPins()`. |
| Design patterns | Adapter, on the command-line display only. The website map calls Google Maps in the browser and does not use this adapter. |

The page asks the browser for a position. `MapBackground` centers on that position, or on downtown Toronto, and places a pin on the person only when location is allowed. A later GPS update moves the pin and does not recenter the map. A restaurant with no coordinates stays in the list and is not pinned. On the command line, `FoodAssistantCli` loads the search results and `MapService.show()` displays them. If the program is started with `--unavailable`, the adapter reports the map as down and `MapService` prints a list.

### F02 — Personal Reviewing and Logging

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F02 — Personal Reviewing and Logging |
| Stage 1 feature/reference | F02 |
| Status or change | Modified. A signed-in person can create, edit, and delete a review, and that change updates the taste profile. The review cannot be marked private. |
| Implemented classes/files and responsibilities | `ReviewPanel.tsx` and `PhotoFramer.tsx` collect the rating, note, tags, and photos. `web/src/reviews.ts` calls `/api/reviews`. `ReviewBoard` checks the input and stores the review. `Review` holds one review. `ReviewPhotos` packs up to four images into the stored photo field. `SupabaseDatabase` reads and writes the `reviews` table. After a successful write, `ReviewBoard` notifies `FeedbackPublisher`. |
| Key methods | `ReviewBoard.publish()`, `ReviewBoard.update()`, `ReviewBoard.delete()`, `ReviewBoard.latest()`, `ReviewPhotos.encode()`, `ReviewPhotos.decode()`, `FeedbackPublisher.notifyObservers()`. |
| Design patterns | Observer. `ReviewBoard` is the source of the notice. `TasteProfileUpdater` is the observer that rebuilds taste. |

The person opens Create, picks a restaurant, sets one to five stars, writes a note, and may add tags and photos. `ReviewBoard` rejects a missing restaurant, a rating outside 1–5, an empty note, a note over 500 characters, more than eight tags, or a photo that is too large. A valid review is stored for that account. Editing loads the same record and sends it back through `update`. Deleting removes that row. Each of those three actions notifies the taste observer. There is no visibility field, so `publicReviews()` can return every review of that account to a friend.

### F03 — Conversational AI Recommendation Assistant

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F03 — Conversational AI Recommendation Assistant |
| Stage 1 feature/reference | F03 |
| Status or change | Implemented. A signed-in person can type a request or press For you and receive a reply plus up to five places, which are pinned on the map. |
| Implemented classes/files and responsibilities | `ChatCard.tsx` is the chat card. `web/src/assistant.ts` calls `/api/chat`. `FoodAgent` runs one recommendation: extract, remember taste, search, filter, rank, and explain. `IntentExtractor` reads the sentence. `ConstraintExtractor` and `RequestSense` supply the word-rule fallback. `CandidatesFilter` applies hard limits. `RankingStrategy` is implemented by `TasteBasedRanking` and `DistanceBasedRanking`. `ResponseGenerator` writes the reply. `PlacesSearch` supplies the restaurants. `MemoryService` loads the account’s taste. |
| Key methods | `FoodAgent.processRequest()`, `FoodAgent.getRecommendations()`, `FoodAgent.rankRestaurants()`, `IntentExtractor.extract()`, `ConstraintExtractor.extract()`, `CandidatesFilter.filter()`, `TasteBasedRanking.rank()`, `DistanceBasedRanking.rank()`, `ResponseGenerator.generate()`. |
| Design patterns | Facade (`FoodAgent`), Strategy (`RankingStrategy`), Factory Method (`LLMFactory`, `OpenAILLMFactory`, `LocalLLMFactory`), and Adapter (`LLMProviderAdapter` in front of `ExternalLLMClient`). |

The person must be signed in. The card sends the message, any chips, the last few turns, and the map location to `/api/chat`. `FoodAgent` asks the model for dish, cuisine, budget, location, and whether this is a search or a question. If that call fails, the word rules decide. A vague message gets a follow-up and no restaurant names. A search calls Places near the person, drops places that break the constraints or match a disliked or avoided food, ranks the rest, and keeps five. The model then writes the reply from those verified places. For you searches from the stored taste when the person does not name a food. Distance ranking is used when the person asks for somewhere nearby and does not name a food.

### F04 — Personalized Taste Profile

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F04 — Personalized Taste Profile |
| Stage 1 feature/reference | F04 |
| Status or change | Modified. The profile is stored per account, updated from use, and used by the assistant. The update is a score over foods, reviews, and saved places. A model does not invent the profile. |
| Implemented classes/files and responsibilities | `AccountPanel.tsx` edits interests and a short description. `web/src/assistant.ts` calls `/api/taste`. `MemoryService` loads and stores the profile in the Supabase `taste_profiles` table. `UserPreferences` holds interests, likes, dislikes, diet, avoids, price comfort, description, stated scores, and affinity. `TasteProfileUpdater` rebuilds likes and dislikes. `ChatPreference` reads a stated like, dislike, diet, or food to avoid from a chat sentence. `FoodWords` matches food words. |
| Key methods | `MemoryService.remember()`, `MemoryService.rebuild()`, `MemoryService.saveExplicit()`, `TasteProfileUpdater.onFeedback()`, `TasteProfileUpdater.learn()`, `TasteProfileUpdater.apply()`, `ChatPreference.read()`. |
| Design patterns | Observer. `TasteProfileUpdater` implements `FeedbackObserver`. A review or a collection change notifies it through `FeedbackPublisher`. A preference stated in chat is applied inside `FoodAgent` and does not go through the publisher. |

On sign-in the website loads the profile. Interests and the description can be edited and are saved with `saveExplicit`. A review, or adding and removing a saved place, calls `rebuild`, which scores foods from review ratings, tags, and text, and from saved restaurants. A 5-star review raises those foods. A 1-star review lowers them. A diet or an allergy stated in chat is kept until the person says it changed. The assistant uses the resulting likes, dislikes, diet, and avoids when it searches and ranks. Friends’ reviews are not part of this profile.

### F05 — Quick Photo Experience Sharing

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F05 — Quick Photo Experience Sharing |
| Stage 1 feature/reference | F05 |
| Status or change | Modified. A signed-in person can take or upload one photo, write a short caption onto it, and send it to chosen friends. The post is not attached to a restaurant, and it does not update taste. |
| Implemented classes/files and responsibilities | `InstantPanel.tsx` takes or uploads the photo, draws the caption onto the image, and lets the person pick friends. It calls `/api/instants`. `InstantBoard` checks the photo and the recipient list and stores the post. `SupabaseDatabase` writes the `instants` and `instant_receipts` tables. `AccountBook.friendIds()` limits recipients to accepted friends. |
| Key methods | `InstantBoard.share()`, `InstantBoard.inboxResponse()`. |
| Design patterns | N/A |

The person opens Instant, adds one photo, optionally writes a caption, and selects at least one friend. The caption is drawn onto the picture in the browser. The server stores that picture and one receipt per recipient. The inbox lists photos the person sent and photos friends sent back, with the author and the date. A missing friend, an empty recipient list, or a photo over the size limit is rejected. The post has no restaurant, no star rating, and no separate note field.

### F06 — Friends’ Activity Feed

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F06 — Friends’ Activity Feed |
| Stage 1 feature/reference | F06 |
| Status or change | Modified. Friend requests and the review feed work, and a restaurant on a friend’s post can be saved into one of the person’s collections. The feed does not change later recommendations. |
| Implemented classes/files and responsibilities | `FriendsPanel.tsx` is the feed and the add-friend screen. `AccountBook` looks up an account by email, sends and answers requests, and builds the feed. `FriendStore` keeps requests in the Supabase `friendships` table. `AvatarStore` keeps profile photos in the `avatars` table. `SupabaseDatabase.publicReviews()` loads each friend’s reviews. The bookmark opens `CollectionMenu`, which calls the collection routes. |
| Key methods | `AccountBook.lookupFriend()`, `AccountBook.requestFriend()`, `AccountBook.respond()`, `AccountBook.cancelRequest()`, `AccountBook.circle()`, `FriendStore.request()`, `FriendStore.accept()`, `FriendStore.reject()`, `FriendStore.cancel()`, `FriendStore.friendIds()`. |
| Design patterns | N/A |

A signed-in person searches by email. `lookupFriend` finds the other account and `requestFriend` records a pending request. If the other person had already requested this account, `FriendStore.request` accepts that request instead. The other person accepts or declines through `respond`. `circle()` then loads each friend’s reviews, sorts them by time, and the panel shows the author, stars, tags, note, and photos. An empty circle explains that no friend has shared a review yet. The bookmark adds or removes that restaurant in a chosen collection, including Saved restaurants.

### F07 — Restaurant Discovery and Search

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F07 — Restaurant Discovery and Search |
| Stage 1 feature/reference | F07 |
| Status or change | Implemented. The website and the command line both search through `/api/search`. |
| Implemented classes/files and responsibilities | `SearchBar.tsx` takes the query. `web/src/places.ts` calls `/api/search`. `PlacesSearch` calls Places API (New), biased to the person or to downtown Toronto, and maps each place into a `Restaurant`. `PlaceTags.assign()` adds category labels from the Google place types and the name. `RestaurantCard.tsx` renders a result. `FoodAssistantCli` calls the same route, and `MapService` prints it. |
| Key methods | `searchRestaurants()` in `places.ts`, `PlacesSearch.search()`, `PlacesSearch.photo()`, `PlaceTags.assign()`, `MapService.show()`. |
| Design patterns | Adapter, on the command-line display path. The website asks the Java server and does not call Places itself. |

After two characters, the website waits briefly and calls `/api/search` with the text and the person’s location, or downtown Toronto. The server asks Places and returns the matches. They appear in a list and as amber pins. Choosing one closes the list, centers that pin, and opens the detail card. If Places fails, the list shows the server’s error. The command line uses the same route: `java -cp out foodassistant.cli.FoodAssistantCli --query ramen --cuisine Japanese`. Category tags on these results are assigned by rules in `PlaceTags`. The person does not type them.

### F08 — Restaurant Recommendation History

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F08 — Restaurant Recommendation History |
| Stage 1 feature/reference | F08 |
| Status or change | Modified. Past chats and the places recommended in them are stored for the account and can be reopened on another browser. The latest 20 sessions are kept. No observer records them. |
| Implemented classes/files and responsibilities | `ChatCard.tsx` shows the history drawer and can start a new chat or reopen an old one. `web/src/chatHistory.ts` calls `/api/chat/history`. `ChatHistory` checks each session and keeps the latest 20. `SupabaseDatabase` reads and writes the `chat_sessions` table. `web/src/App.tsx` saves the open thread after each turn and, when a past chat is opened, can show its places on the map. There is no `HistoryRecorder` class. |
| Key methods | `ChatHistory.list()`, `ChatHistory.replace()`, `loadAccountHistory()`, `persistChatHistory()`, `sessionPicks()`. |
| Design patterns | N/A. Stage 1 planned an Observer so that a recommendation would be recorded after feedback. The only observer subscribed today is `TasteProfileUpdater`. It updates taste and does not store chat history. |

A signed-in chat is saved when its thread changes. Each session stores the title, the time, the messages, and the recommended places in `chat_sessions`, under that account. Opening the history list loads those sessions again, so another browser signed into the same account sees them. Opening one restores the thread, and its places can be shown as pins when no search is active. A chat left in this browser from before that table existed is uploaded the next time this account signs in here. The server then drops sessions beyond the latest 20.

### F09 — Automatic Category Tagging

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F09 — Automatic Category Tagging |
| Stage 1 feature/reference | F09 |
| Status or change | Modified. A restaurant receives category tags from its Google place types and its name. The tags are not proposed by a model, and the person does not confirm them. Tags on a review are still typed by hand. |
| Implemented classes/files and responsibilities | `PlaceTags` turns a Google type such as `hamburger_restaurant` into a label such as Burgers, and it also reads food words from the restaurant name. `PlacesSearch` calls it while building a search hit. `CollectionLibrary` and `SupabaseDatabase` call `PlaceTags.keep()` when a stored place is loaded, so an older raw type is shown as the same kind of label. `LLMEngine.generateTags()` exists on the model interface and is implemented by matching `FoodWords`. Nothing in the recommendation path calls it. |
| Key methods | `PlaceTags.assign()`, `PlaceTags.keep()`, `FoodWords.mentioned()`. |
| Design patterns | N/A |

Search results carry up to six labels. Those labels are stored with the place when it is saved into a collection, and they are shown on the place. The person can still type up to eight tags on a review. Those review tags are checked for length by `ReviewBoard` and are not suggested. Because the model is not asked to tag a place, this feature stays available when `OPENAI_API_KEY` is absent.

### F10 — Save and Custom Food Collections

| Mapping field | Entry |
| --- | --- |
| Feature ID and name | F10 — Save and Custom Food Collections |
| Stage 1 feature/reference | F10 |
| Status or change | Implemented. A signed-in person can keep Saved restaurants, Want to Visit, and named lists. Opening one list is what puts its places on the map. The lists are not separate layers that can be shown together. |
| Implemented classes/files and responsibilities | `CollectionsPanel.tsx` and `CollectionMenu.tsx` are the screens. `web/src/collections.ts` calls `/api/collections`. `CollectionLibrary` creates, fills, and deletes lists. `Collection` is one list. `CollectionStore` loads the library for the signed-in account. Rows live in the Supabase `collections` and `places` tables. Adding, removing, creating, and deleting a list also notifies `FeedbackPublisher`, so the taste profile is rebuilt. |
| Key methods | `CollectionLibrary.create()`, `CollectionLibrary.addPlace()`, `CollectionLibrary.removePlace()`, `CollectionLibrary.delete()`, `CollectionLibrary.all()`, `Collection.add()`, `Collection.remove()`, `Collection.saved()`, `Collection.want()`. |
| Design patterns | N/A. The taste notice uses the Observer already described under F02 and F04. The collection classes themselves are not a pattern. |

After sign-in, the server loads that account’s collections. If Saved restaurants or Want to Visit is missing, it creates that list. `delete` refuses to remove either default list. The bookmark on a place detail, and the bookmark on a friend’s post, opens `CollectionMenu`. Choosing a list calls `addPlace` or `removePlace`. A duplicate restaurant is not added twice. Creating a list asks for a name and rejects a blank or repeated name. Deleting a custom list removes that list. The restaurant can still sit in Saved restaurants, in Want to Visit, or in another list. An empty custom list is allowed. The assistant reads saved places as one of the taste signals. It does not browse the collection names as a separate search.
