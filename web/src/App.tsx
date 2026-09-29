const pins = [
  { className: "pin pin-start", label: "A" },
  { className: "pin pin-end", label: "B" },
];

export default function App() {
  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/route" aria-label="OSRS Travel home">
          <span className="brand-mark" aria-hidden="true">✦</span>
          <span>OSRS Travel</span>
        </a>
        <div className="searches" aria-label="Route endpoints">
          <label><span>From</span><input type="search" placeholder="Choose a starting place" /></label>
          <span className="arrow" aria-hidden="true">→</span>
          <label><span>To</span><input type="search" placeholder="Choose a destination" /></label>
        </div>
        <button className="account" type="button">Account: Mid <span aria-hidden="true">⌄</span></button>
      </header>

      <main className="workspace">
        <section className="map" aria-label="OSRS route map">
          <div className="map-grid" />
          <div className="river" />
          <div className="route-line" />
          {pins.map(pin => <span key={pin.label} className={pin.className}>{pin.label}</span>)}
          <p className="map-message">Choose two places to plan a route</p>
        </section>

        <aside className="itinerary" aria-labelledby="itinerary-title">
          <p className="eyebrow">Route planner</p>
          <h1 id="itinerary-title">Where are you heading?</h1>
          <p className="intro">Search above or choose points on the map. Your account build will determine the available journey.</p>
          <div className="empty-step">
            <span aria-hidden="true">⌖</span>
            <p><strong>No route yet</strong><br />Start and destination details will appear here.</p>
          </div>
        </aside>
      </main>
    </div>
  );
}
