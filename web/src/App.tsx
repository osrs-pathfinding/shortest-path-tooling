import { useQuery } from "@tanstack/react-query";
import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { loadPresets } from "./api/presets";
import { calculateRoute } from "./api/routes";
import { RouteMap } from "./components/RouteMap";
import { locationFromParam, locationParam, places } from "./data/places";
import type { AccountBuild, Location, RoutePolicy, WorldPoint } from "./domain/contracts";

const defaultPolicy: RoutePolicy = {
  avoidWilderness: true,
  banking: "allow",
  resources: "fastest",
  avoidedTransportTypes: [],
};

const customAccountKey = "osrs-travel.custom-account.v1";
const AccountEditor = lazy(() => import("./components/AccountEditor").then(module => ({ default: module.AccountEditor })));

function loadCustomAccount(): AccountBuild | undefined {
  try {
    const value = JSON.parse(localStorage.getItem(customAccountKey) || "null") as AccountBuild | null;
    return value?.schemaVersion === 1 && value.id === "custom" && value.levels && value.poh ? value : undefined;
  } catch {
    return undefined;
  }
}

function PlaceInput({ label, location, onSelect }: {
  label: string;
  location?: Location;
  onSelect(location?: Location): void;
}) {
  const [text, setText] = useState(location?.name || "");
  useEffect(() => setText(location?.name || ""), [location]);
  const choose = (value: string) => {
    setText(value);
    const place = places.find(candidate => candidate.name?.toLowerCase() === value.toLowerCase());
    if (place) onSelect(place);
    else if (!value) onSelect(undefined);
  };
  return <label>
    <span>{label}</span>
    <input type="search" list="places" value={text} placeholder={`Choose ${label.toLowerCase()}`}
      onChange={event => choose(event.target.value)} />
  </label>;
}

export default function App() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [start, setStart] = useState(() => locationFromParam(searchParams.get("from")));
  const [destination, setDestination] = useState(() => locationFromParam(searchParams.get("to")));
  const [customAccount, setCustomAccount] = useState(loadCustomAccount);
  const [accountId, setAccountId] = useState(() => {
    const requested = searchParams.get("account") || "mid";
    return requested === "custom" && !customAccount ? "mid" : requested;
  });
  const [editingAccount, setEditingAccount] = useState(false);
  const [policy, setPolicy] = useState(defaultPolicy);
  const presets = useQuery({ queryKey: ["account-presets"], queryFn: loadPresets });
  const account = accountId === "custom" ? customAccount
    : presets.data?.find(preset => preset.id === accountId) || presets.data?.[0];
  const quests = useMemo(() => Array.from(new Set([
    ...(presets.data?.flatMap(preset => preset.completedQuests) || []),
    ...(account?.completedQuests || []),
  ])).sort(), [account, presets.data]);
  const request = useMemo(() => account && start && destination
    ? { account, start, destination, policy } : undefined, [account, start, destination, policy]);
  const route = useQuery({
    queryKey: ["route", account, start?.coordinate, destination?.coordinate, policy],
    queryFn: ({ signal }) => calculateRoute(request!, signal),
    enabled: Boolean(request),
    retry: false,
  });

  const remember = (field: "from" | "to" | "account", value?: string) => {
    setSearchParams(current => {
      const next = new URLSearchParams(current);
      if (value) next.set(field, value); else next.delete(field);
      return next;
    }, { replace: true });
  };
  const chooseStart = (location?: Location) => {
    setStart(location);
    remember("from", location && locationParam(location));
  };
  const chooseDestination = (location?: Location) => {
    setDestination(location);
    remember("to", location && locationParam(location));
  };
  const pickMap = (coordinate: WorldPoint) => {
    const location = { name: `${coordinate.x}, ${coordinate.y}`, coordinate };
    if (!start || destination) {
      chooseStart(location);
      chooseDestination(undefined);
    } else chooseDestination(location);
  };

  return <div className="app-shell">
    <datalist id="places">{places.map(place => <option key={place.placeId} value={place.name} />)}</datalist>
    <header className="topbar">
      <a className="brand" href="/route" aria-label="OSRS Travel home">
        <span className="brand-mark" aria-hidden="true">✦</span>OSRS Travel
      </a>
      <div className="searches" aria-label="Route endpoints">
        <PlaceInput label="From" location={start} onSelect={chooseStart} />
        <span className="arrow" aria-hidden="true">→</span>
        <PlaceInput label="To" location={destination} onSelect={chooseDestination} />
      </div>
      <div className="account-controls">
        <label className="account">
          <span>Account</span>
          <select aria-label="Account" value={accountId} disabled={!presets.data && !customAccount}
            onChange={event => {
              setAccountId(event.target.value);
              remember("account", event.target.value);
            }}>
            {(presets.data || []).map(preset => <option key={preset.id} value={preset.id}>{preset.name}</option>)}
            {customAccount && <option value="custom">{customAccount.name}</option>}
          </select>
        </label>
        <button type="button" className="edit-account" disabled={!account} onClick={() => setEditingAccount(true)}>Edit</button>
      </div>
    </header>

    <main className="workspace">
      <section className="map" aria-label="OSRS route map">
        <RouteMap start={start} destination={destination} route={route.data} onPick={pickMap} />
        {!start || !destination ? <p className="map-message">Search or click the map to choose two places</p> : null}
      </section>

      <aside className="itinerary" aria-labelledby="itinerary-title">
        <p className="eyebrow">Route planner</p>
        <h1 id="itinerary-title">{start && destination ? `${start.name} to ${destination.name}` : "Where are you heading?"}</h1>
        <div className="route-options">
          <label><input type="checkbox" checked={policy.avoidWilderness}
            onChange={event => setPolicy(current => ({ ...current, avoidWilderness: event.target.checked }))} /> Avoid wilderness</label>
          <label>Banking <select value={policy.banking}
            onChange={event => setPolicy(current => ({ ...current, banking: event.target.value as RoutePolicy["banking"] }))}>
            <option value="allow">Allow</option><option value="never">Never</option>
          </select></label>
          <label>Resources <select value={policy.resources}
            onChange={event => setPolicy(current => ({ ...current, resources: event.target.value as RoutePolicy["resources"] }))}>
            <option value="fastest">Fastest</option><option value="preserve-consumables">Preserve consumables</option>
          </select></label>
        </div>
        {presets.isError && <p className="route-error">Account profiles could not be loaded.</p>}
        {route.isFetching && <p className="route-status" aria-live="polite">Calculating the shortest route…</p>}
        {route.isError && <p className="route-error" role="alert">{route.error.message}</p>}
        {route.data && <>
          <p className="route-summary"><strong>{route.data.reachable ? `${route.data.costTicks} ticks` : "No route found"}</strong><br />
            {route.data.segments.length} journey steps · Exact routing</p>
          <ol className="segments">
            {route.data.segments.map((segment, index) => <li key={`${segment.kind}-${index}`}>
              <span className={`segment-icon ${segment.kind}`} aria-hidden="true">
                {segment.kind === "walk" ? "↟" : segment.kind === "bank" ? "◇" : "✦"}
              </span>
              <p><strong>{segment.kind === "walk" ? `Walk ${segment.path.length - 1} tiles` : segment.kind === "bank" ? "Visit a bank" : segment.name}</strong>
                <br /><span>{segment.costTicks} ticks</span></p>
            </li>)}
          </ol>
        </>}
        {!route.data && !route.isFetching && !route.isError && <div className="empty-step">
          <span aria-hidden="true">⌖</span>
          <p><strong>No route yet</strong><br />Choose a start and destination above or on the map.</p>
        </div>}
      </aside>
    </main>
    {account && editingAccount && <Suspense fallback={null}><AccountEditor account={account} quests={quests} open
      onClose={() => setEditingAccount(false)} onSave={value => {
        localStorage.setItem(customAccountKey, JSON.stringify(value));
        setCustomAccount(value);
        setAccountId("custom");
        remember("account", "custom");
        setEditingAccount(false);
      }} /></Suspense>}
  </div>;
}
