import { useQuery } from "@tanstack/react-query";
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { loadCatalog } from "../../api/catalog";
import { loadPresets } from "../../api/presets";
import { calculateRoute } from "../../api/routes";
import { places } from "../../data/places";
import type { PlannerAccount } from "../../domain/accounts";
import type { Account, Location, WorldPoint } from "../../domain/contracts";
import { asSettings, validSettings, type ChangedSettings } from "../../domain/settings";
import { Itinerary } from "../itinerary/Itinerary";
import { ShareRoute } from "./ShareRoute";
import { RouteSettings } from "./RouteSettings";
import { plannerUrlWarnings, profileFromHash, readPlannerUrl, readStoredSettings, SharedProfileError, writePlannerUrl, type PlannerUrlState } from "./plannerState";

const customAccountKey = "osrs-travel.custom-account.v2";
const settingsKey = "osrs-travel.route-settings.v1";
const AccountPanel = lazy(() => import("../../components/AccountPanel").then(module => ({ default: module.AccountPanel })));
const OsrsMap = lazy(() => import("../../map/OsrsMap").then(module => ({ default: module.OsrsMap })));

async function loadCustomAccount(): Promise<Account | undefined> {
  try {
    const { validateAccount } = await import("../../domain/accountValidation");
    return validateAccount(JSON.parse(localStorage.getItem(customAccountKey) || "null"));
  } catch { return undefined; }
}

function loadStoredSettings(): ChangedSettings {
  try { return readStoredSettings(localStorage.getItem(settingsKey)); }
  catch { return {}; }
}

function PlaceInput({ label, location, onSelect }: { label: string; location?: Location; onSelect(location?: Location): void }) {
  const [text, setText] = useState(location?.name || "");
  useEffect(() => setText(location?.name || ""), [location]);
  const choose = (value: string) => {
    setText(value);
    const place = places.find(candidate => candidate.name?.toLowerCase() === value.toLowerCase());
    if (place) onSelect(place);
    else if (!value) onSelect(undefined);
  };
  return <label><span>{label}</span><input type="search" list="places" value={text}
    placeholder={`Choose ${label.toLowerCase()}`} onChange={event => choose(event.target.value)}
    onBlur={() => { if (!places.some(place => place.name?.toLowerCase() === text.toLowerCase())) setText(location?.name || ""); }} /></label>;
}

function customLocation(point: WorldPoint): Location {
  return { name: `${point.x}, ${point.y}${point.plane ? `, plane ${point.plane}` : ""}`, coordinate: point };
}

export function RoutePlannerPage() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [customAccount, setCustomAccount] = useState<Account>();
  const [shared, setShared] = useState<{ account?: Account; error?: SharedProfileError; loading?: boolean }>({});
  const [accountPanel, setAccountPanel] = useState<"closed" | "open" | "hidden">("closed");
  const [selectedSegment, setSelectedSegment] = useState<number>();
  const [mobileItineraryOpen, setMobileItineraryOpen] = useState(false);
  const accountToggle = useRef<HTMLButtonElement>(null);
  const paramsKey = searchParams.toString();
  const storedSettings = useMemo(loadStoredSettings, []);
  const urlState = useMemo(() => readPlannerUrl(new URLSearchParams(paramsKey), storedSettings), [paramsKey, storedSettings]);

  useEffect(() => { void loadCustomAccount().then(setCustomAccount); }, []);

  useEffect(() => {
    let current = true;
    if (urlState.accountId !== "shared") { setShared({}); return () => { current = false; }; }
    setShared({ loading: true });
    void profileFromHash(location.hash).then(account => { if (current) setShared({ account }); }, error => {
      if (current) setShared({ error: error instanceof SharedProfileError ? error : new SharedProfileError("This route link contains an invalid account.") });
    });
    return () => { current = false; };
  }, [location.hash, urlState.accountId]);

  const presets = useQuery({ queryKey: ["account-presets"], queryFn: loadPresets });
  const catalog = useQuery({ queryKey: ["catalog"], queryFn: loadCatalog });
  const accounts = useMemo<PlannerAccount[]>(() => [...presets.data || [],
    ...customAccount ? [{ id: "custom", name: customAccount.name, account: customAccount }] : [],
    ...shared.account ? [{ id: "shared", name: `${shared.account.name} (shared)`, account: shared.account }] : []],
  [presets.data, customAccount, shared.account]);
  const selected = accounts.find(candidate => candidate.id === urlState.accountId)
    || (urlState.accountId !== "shared" ? presets.data?.find(candidate => candidate.id === "mid") || presets.data?.[0] : undefined);
  const settings = useMemo(() => catalog.data ? validSettings(urlState.settings, catalog.data.settings) : undefined,
    [catalog.data, urlState.settings]);
  const urlWarnings = useMemo(() => presets.data ? [...plannerUrlWarnings(new URLSearchParams(paramsKey),
    [...accounts.map(candidate => candidate.id), "shared"]),
  ...settings?.dropped.length ? [`Unknown or invalid settings were ignored: ${settings.dropped.join(", ")}.`] : []] : [],
  [accounts, paramsKey, presets.data, settings]);

  const updateUrl = (state: PlannerUrlState, options?: { replace?: boolean; hash?: string }) => {
    const search = writePlannerUrl(state).toString();
    navigate({ pathname: "/route", search: search ? `?${search}` : "", hash: options?.hash ?? location.hash },
      { replace: options?.replace });
  };
  const update = (partial: Partial<PlannerUrlState>, replace = false) => updateUrl({ ...urlState, ...partial }, { replace });
  const chooseAccount = (id: string) => updateUrl({ ...urlState, accountId: id }, { hash: id === "shared" ? location.hash : "" });

  useEffect(() => {
    if (!settings) return;
    try { localStorage.setItem(settingsKey, JSON.stringify(settings.settings)); }
    catch { /* The URL still preserves the active settings when storage is unavailable. */ }
  }, [settings]);
  useEffect(() => { setSelectedSegment(undefined); }, [urlState.start, urlState.destination, selected, settings]);

  const request = useMemo(() => selected && settings && urlState.start && urlState.destination
    ? { account: selected.account, settings: asSettings(settings.settings), start: urlState.start, destination: urlState.destination }
    : undefined, [selected, settings, urlState.start, urlState.destination]);
  const route = useQuery({
    queryKey: ["route", request],
    queryFn: ({ signal }) => calculateRoute(request!, signal), enabled: Boolean(request), retry: false,
    placeholderData: previous => previous,
  });
  useEffect(() => { if (route.data && !route.isFetching) setMobileItineraryOpen(true); }, [route.data, route.isFetching]);

  const chooseStart = (start?: Location) => update({ start });
  const chooseDestination = (destination?: Location) => update({ destination });
  const closeAccountPanel = () => { setAccountPanel("hidden"); accountToggle.current?.focus(); };
  const pickMap = (coordinate: WorldPoint) => {
    if (!urlState.start || urlState.destination) update({ start: customLocation(coordinate), destination: undefined });
    else update({ destination: customLocation(coordinate) });
  };

  return <div className="app-shell">
    <datalist id="places">{places.map(place => <option key={place.placeId} value={place.name} />)}</datalist>
    <header className="topbar">
      <a className="brand" href="/route" aria-label="OSRS Travel home"><span className="brand-mark" aria-hidden="true">✦</span>OSRS Travel</a>
      <div className="searches" aria-label="Route endpoints">
        <PlaceInput label="From" location={urlState.start} onSelect={chooseStart} /><span className="arrow" aria-hidden="true">→</span>
        <PlaceInput label="To" location={urlState.destination} onSelect={chooseDestination} />
      </div>
      <button type="button" ref={accountToggle} className="account-toggle" disabled={!selected || !catalog.data}
        aria-expanded={accountPanel === "open"} aria-controls="account-panel"
        onClick={() => accountPanel === "open" ? closeAccountPanel() : setAccountPanel("open")}>
        <span>Account</span><strong>{selected ? selected.name : "Loading…"}</strong>
      </button>
    </header>

    {shared.error && <div className="link-error" role="alert"><strong>Shared account could not be opened.</strong> {shared.error.message}</div>}
    {urlWarnings.length > 0 && <div className="link-warning" role="status">{urlWarnings.map(warning => <span key={warning}>{warning}</span>)}</div>}
    <main className={accountPanel === "open" ? "workspace with-account" : "workspace"}>
      {selected && catalog.data && accountPanel !== "closed" && <Suspense fallback={<aside className="account-panel"><p className="panel-loading">Loading account…</p></aside>}>
        <AccountPanel accounts={accounts} selected={selected} catalog={catalog.data} open={accountPanel === "open"}
          onSelect={chooseAccount} onClose={closeAccountPanel} onSave={value => {
            try { localStorage.setItem(customAccountKey, JSON.stringify(value)); }
            catch { throw new Error("This browser could not save the custom build. Check its storage permissions and try again."); }
            setCustomAccount(value); chooseAccount("custom");
          }} />
      </Suspense>}
      <section className="map" aria-label="OSRS route map">
        <Suspense fallback={<div className="map-loading" role="status">Loading map…</div>}><OsrsMap start={urlState.start}
          destination={urlState.destination} route={route.data} selectedSegment={selectedSegment}
          onSelectSegment={setSelectedSegment} onPick={pickMap} onMoveStart={point => update({ start: customLocation(point) })}
          onMoveDestination={point => update({ destination: customLocation(point) })} /></Suspense>
        {!urlState.start || !urlState.destination ? <p className="map-message">Search or click the map to choose two places</p> : null}
      </section>
      <Itinerary start={urlState.start} destination={urlState.destination} route={route.data} isFetching={route.isFetching}
        error={presets.isError || catalog.isError ? new Error("The planner's accounts and options could not be loaded.") : route.error}
        selectedSegment={selectedSegment}
        mobileOpen={mobileItineraryOpen} onMobileToggle={() => setMobileItineraryOpen(value => !value)}
        onSelectSegment={setSelectedSegment}
        onRetry={() => void (presets.isError ? presets.refetch() : catalog.isError ? catalog.refetch() : route.refetch())}
        controls={<>{catalog.data && settings && <RouteSettings settings={catalog.data.settings} changed={settings.settings}
          onChange={changed => update({ settings: changed })} />}
          {selected && <ShareRoute state={urlState} account={selected}
            disabled={!urlState.start || !urlState.destination || route.isFetching} />}</>} />
    </main>
  </div>;
}
