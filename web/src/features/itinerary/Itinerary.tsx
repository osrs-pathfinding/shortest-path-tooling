import { useState, type ReactNode } from "react";
import { RouteApiError } from "../../api/routes";
import type { Location, RoutePlan } from "../../domain/contracts";

export function formatDuration(ticks: number): string {
  const seconds = Math.round(ticks * .6);
  if (seconds < 60) return `about ${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return `about ${minutes}m${remainder ? ` ${remainder}s` : ""}`;
}

function coordinate(point: { x: number; y: number; plane: number }) {
  return `${point.x}, ${point.y}${point.plane ? `, plane ${point.plane}` : ""}`;
}

function segmentTitle(segment: RoutePlan["segments"][number]): string {
  if (segment.kind === "walk") return `Walk ${Math.max(0, segment.path.length - 1)} tiles`;
  if (segment.kind === "bank") return "Visit a bank";
  return segment.name;
}

function segmentDetail(segment: RoutePlan["segments"][number]): string | undefined {
  if (segment.kind === "walk") return segment.path.length > 1
    ? `${coordinate(segment.path[0])} → ${coordinate(segment.path[segment.path.length - 1])}` : undefined;
  if (segment.kind === "bank") return coordinate(segment.location);
  return `${coordinate(segment.from)} → ${coordinate(segment.to)}`;
}

function itineraryText(route: RoutePlan): string {
  const lines = [
    `${route.start.name || coordinate(route.start.coordinate)} → ${route.destination.name || coordinate(route.destination.coordinate)}`,
    `${route.costTicks ?? 0} ticks (${formatDuration(route.costTicks ?? 0)})`,
  ];
  route.segments.forEach((segment, index) => lines.push(`${index + 1}. ${segmentTitle(segment)} — ${segment.costTicks} ticks`));
  return lines.join("\n");
}

export function routeErrorMessage(error: unknown): string {
  if (!(error instanceof RouteApiError)) return error instanceof Error ? error.message : "The route could not be calculated.";
  if (error.status === 503) return "The route service is busy. Wait a moment and try again.";
  if (error.status === 408 || error.status === 504) return "The route calculation timed out. Try again.";
  if (error.status >= 500) return "The route service is temporarily unavailable.";
  return error.message;
}

export function Itinerary({ start, destination, route, isFetching, error, selectedSegment, controls, mobileOpen = false,
  onMobileToggle, onSelectSegment, onRetry }: {
  start?: Location;
  destination?: Location;
  route?: RoutePlan;
  isFetching: boolean;
  error?: unknown;
  selectedSegment?: number;
  controls?: ReactNode;
  mobileOpen?: boolean;
  onMobileToggle?(): void;
  onSelectSegment(index?: number): void;
  onRetry(): void;
}) {
  const [copyState, setCopyState] = useState<"idle" | "copied" | "manual">("idle");
  const copyItinerary = async () => {
    if (!route) return;
    try {
      if (!navigator.clipboard?.writeText) throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(itineraryText(route));
      setCopyState("copied");
    } catch { setCopyState("manual"); }
  };
  const requestId = error instanceof RouteApiError ? error.requestId : undefined;

  return <aside className={`itinerary${mobileOpen ? " mobile-open" : ""}`} aria-labelledby="itinerary-title">
    <button type="button" className="mobile-sheet-toggle" aria-expanded={mobileOpen} onClick={onMobileToggle}>
      <span aria-hidden="true">{mobileOpen ? "⌄" : "⌃"}</span>{mobileOpen ? "Collapse route" : "Show route"}
    </button>
    <p className="eyebrow">Route planner</p>
    <h1 id="itinerary-title">{start && destination ? `${start.name} to ${destination.name}` : "Where are you heading?"}</h1>
    {controls}

    {isFetching && !route && <p className="route-status" aria-live="polite">Calculating the shortest route…</p>}
    {isFetching && route && <p className="route-status subtle" aria-live="polite">Updating route…</p>}
    {Boolean(error) && <div className="route-error" role="alert">
      <p>{routeErrorMessage(error)}</p>
      <button type="button" className="secondary-button" onClick={onRetry}>Try again</button>
      {requestId && <details><summary>Error details</summary><code>Request {requestId}</code></details>}
    </div>}

    {route && <>
      <div className={`route-summary${error ? " stale" : ""}`}>
        <strong>{route.reachable && route.costTicks !== null ? `${route.costTicks} ticks` : "No route found"}</strong>
        {route.reachable && route.costTicks !== null && <span>{formatDuration(route.costTicks)} · {route.segments.length} journey steps</span>}
        {Boolean(error) && <small>Showing the previous route</small>}
      </div>
      {route.reachable ? <>
        <div className="journey-bookend"><span>A</span><p><small>Start</small><strong>{route.start.name || coordinate(route.start.coordinate)}</strong></p></div>
        <ol className="segments">
          {route.segments.map((segment, index) => <li key={`${segment.kind}-${index}`} className={selectedSegment === index ? "selected" : undefined}>
            <button type="button" className="segment-button" aria-pressed={selectedSegment === index}
              onFocus={() => onSelectSegment(index)} onMouseEnter={() => onSelectSegment(index)} onClick={() => onSelectSegment(index)}>
              <span className={`segment-icon ${segment.kind}`} aria-hidden="true">
                {segment.kind === "walk" ? "↟" : segment.kind === "bank" ? "◇" : segment.kind === "transport" ? "→" : "✦"}
              </span>
              <span className="segment-copy"><strong>{segmentTitle(segment)}</strong>
                <small>{segmentDetail(segment)}</small><small>{segment.costTicks} ticks</small>
                {segment.kind !== "walk" && segment.kind !== "bank" && segment.requirements.length > 0 && <span className="requirements">
                  {segment.requirements.map(requirement => <span key={`${requirement.kind}-${requirement.name}`}>{requirement.name}</span>)}
                </span>}
              </span>
            </button>
          </li>)}
        </ol>
        <div className="journey-bookend destination"><span>B</span><p><small>Destination</small><strong>{route.destination.name || coordinate(route.destination.coordinate)}</strong></p></div>
        <button type="button" className="copy-itinerary" onClick={() => void copyItinerary()}>{copyState === "copied" ? "Itinerary copied" : "Copy text itinerary"}</button>
        {copyState === "manual" && <label className="manual-copy">Copy itinerary manually
          <textarea readOnly value={itineraryText(route)} onFocus={event => event.currentTarget.select()} />
        </label>}
      </> : <div className="empty-step"><span aria-hidden="true">⌖</span><p><strong>Destination unreachable</strong><br />Try another endpoint, account, or route policy.</p></div>}
    </>}

    {!route && !isFetching && !error && <div className="empty-step">
      <span aria-hidden="true">⌖</span><p><strong>No route yet</strong><br />Choose a start and destination above or on the map.</p>
    </div>}
  </aside>;
}
