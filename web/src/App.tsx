import { ChatCard } from './components/ChatCard'
import { MapBackground } from './components/MapBackground'
import { SearchBar } from './components/SearchBar'
import { Sidebar } from './components/Sidebar'

export default function App() {
  return (
    <div className="app">
      <MapBackground />
      <Sidebar />
      <SearchBar />
      <ChatCard />
    </div>
  )
}
