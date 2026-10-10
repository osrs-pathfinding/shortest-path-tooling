import { useQuery } from "@tanstack/react-query";
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { loadPresets } from "../../api/presets";
import { calculateRoute } from "../../api/routes";
import { places } from "../../data/places";
import { accountLabel } from "../../domain/accounts";
import type { AccountBuild, Location, WorldPoint } from "../../domain/contracts";
import { Itinerary } from "../itinerary/Itinerary";
import { ShareRoute } from "./ShareRoute";
import { RouteOptions } from "./RouteOptions";
import { defaultPolicy, plannerUrlWarnings, policyParams, profileFromHash, readPlannerUrl, readStoredPolicy, SharedProfileError, writePlannerUrl, type PlannerPolicy, type PlannerUrlState } from "./plannerState";

const customAccountKey = "osrs-travel.custom-account.v1";
const policyKey = "osrs-travel.route-policy.v1";
const AccountPanel = lazy(() => import("../../components/AccountPanel").then(module => ({ default: module.AccountPanel })));
const OsrsMap = lazy(() => import("../../map/OsrsMap").then(module => ({ default: module.OsrsMap })));

function loadCustomAccount(): AccountBuild | undefined {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(customAccountKey) || "null");
    return isUsableStoredAccount(value) ? value : undefined;
  } catch { return undefined; }
}

function loadStoredPolicy(): PlannerPolicy {
  try { return readStoredPolicy(localStorage.getItem(policyKey)); }
  catch { return readStoredPolicy(null); }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function isUsableStoredAccount(value: unknown): value is AccountBuild {
  if (!isRecord(value) || value.schemaVersion !== 1 || value.id !== "custom" || typeof value.name !== "string") return false;
  return isRecord(value.levels) && Array.isArray(value.completedQuests) && isRecord(value.diaries)
    && isRecord(value.inventory) && isRecord(value.equipment) && isRecord(value.runePouch) && isRecord(value.bank)
    && Array.isArray(value.plantedSpiritTrees) && isRecord(value.poh) && isRecord(value.poh.portals)
    && Array.isArray(value.poh.portals.destinations) && isRecord(value.runtime) && isRecord(value.runtime.minigameTeleport)
    && isRecord(value.routingVariables);
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

function hasExplicitPlannerState(params: URLSearchParams): boolean {
  return ["from", "to", "account", ...policyParams].some(key => params.has(key));
}

export function RoutePlannerPage() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [customAccount, setCustomAccount] = useState(loadCustomAccount);
  const [shared, setShared] = useState<{ account?: AccountBuild; error?: SharedProfileError; loading?: boolean }>({});
  const [accountPanel, setAccountPanel] = useState<"closed" | "open" | "hidden">("closed");
  const [selectedSegment, setSelectedSegment] = useState<number>();
  const [mobileItineraryOpen, setMobileItineraryOpen] = useState(false);
  const accountToggle = useRef<HTMLButtonElement>(null);
  const paramsKey = searchParams.toString();
  const storedPolicy = useMemo(loadStoredPolicy, []);
  const urlState = useMemo(() => readPlannerUrl(new URLSearchParams(paramsKey),
    hasExplicitPlannerState(new URLSearchParams(paramsKey)) ? defaultPolicy : storedPolicy), [paramsKey, storedPolicy]);

  useEffect(() => {
    let current = true;
    if (urlState.accountId !== "shared") { setShared({}); return () => { current = false; }; }
    setShared({ loading: true });
    void profileFromHash(location.hash).then(account => { if (current) setShared({ account }); }, error => {
      if (current) setShared({ error: error instanceof SharedProfileError ? error : new SharedProfileError("This route link contains an invalid account profile.") });
    });
    return () => { current = false; };
  }, [location.hash, urlState.accountId]);

  const presets = useQuery({ queryKey: ["account-presets"], queryFn: loadPresets });
  const accounts = useMemo(() => [...presets.data || [], ...customAccount ? [customAccount] : [], ...shared.account ? [shared.account] : []],
    [presets.data, customAccount, shared.account]);
  const account = accounts.find(candidate => candidate.id === urlState.accountId)
    || (urlState.accountId !== "shared" ? presets.data?.find(candidate => candidate.id === "mid") || presets.data?.[0] : undefined);
  const quests = useMemo(() => Array.from(new Set([
    ...(presets.data?.flatMap(preset => preset.completedQuests) || []), ...(account?.completedQuests || []),
  ])).sort(), [account, presets.data]);
  const urlWarnings = useMemo(() => presets.data ? plannerUrlWarnings(new URLSearchParams(paramsKey),
    [...accounts.map(candidate => candidate.id), "shared"]) : [], [accounts, paramsKey, presets.data]);

  const updateUrl = (state: PlannerUrlState, options?: { replace?: boolean; hash?: string }) => {
    const search = writePlannerUrl(state).toString();
    navigate({ pathname: "/route", search: search ? `?${search}` : "", hash: options?.hash ?? location.hash },
      { replace: options?.replace });
  };
  const update = (partial: Partial<PlannerUrlState>, replace = false) => updateUrl({ ...urlState, ...partial }, { replace });
  const chooseAccount = (id: string) => updateUrl({ ...urlState, accountId: id }, { hash: id === "shared" ? location.hash : "" });

  useEffect(() => {
    try { localStorage.setItem(policyKey, JSON.stringify(urlState.policy)); }
    catch { /* The URL still preserves the active policy when storage is unavailable. */ }
  }, [urlState.policy]);
  useEffect(() => { setSelectedSegment(undefined); }, [urlState.start, urlState.destination, account, urlState.policy]);

  const request = useMemo(() => account && urlState.start && urlState.destination
    ? { account, start: urlState.start, destination: urlState.destination, policy: urlState.policy } : undefined,
    [account, urlState.start, urlState.destination, urlState.policy]);
  const route = useQuery({
    queryKey: ["route", account, urlState.start?.coordinate, urlState.destination?.coordinate, urlState.policy],
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
      <button type="button" ref={accountToggle} className="account-toggle" disabled={!account}
        aria-expanded={accountPanel === "open"} aria-controls="account-panel"
        onClick={() => accountPanel === "open" ? closeAccountPanel() : setAccountPanel("open")}>
        <span>Account</span><strong>{account ? accountLabel(account) : "Loading…"}</strong>
      </button>
    </header>

    {shared.error && <div className="link-error" role="alert"><strong>Shared account could not be opened.</strong> {shared.error.message}</div>}
    {urlWarnings.length > 0 && <div className="link-warning" role="status">{urlWarnings.map(warning => <span key={warning}>{warning}</span>)}</div>}
    <main className={accountPanel === "open" ? "workspace with-account" : "workspace"}>
      {account && accountPanel !== "closed" && <Suspense fallback={<aside className="account-panel"><p className="panel-loading">Loading account…</p></aside>}>
        <AccountPanel accounts={accounts} account={account} quests={quests} open={accountPanel === "open"}
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
        error={presets.isError ? new Error("Account profiles could not be loaded.") : route.error} selectedSegment={selectedSegment}
        mobileOpen={mobileItineraryOpen} onMobileToggle={() => setMobileItineraryOpen(value => !value)}
        onSelectSegment={setSelectedSegment} onRetry={() => void (presets.isError ? presets.refetch() : route.refetch())}
        controls={<><RouteOptions policy={urlState.policy} onChange={policy => update({ policy })} />
          {account && <ShareRoute state={urlState} account={account}
            disabled={!urlState.start || !urlState.destination || route.isFetching} />}</>} />
    </main>
  </div>;
}
