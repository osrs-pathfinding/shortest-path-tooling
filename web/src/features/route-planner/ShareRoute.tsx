import { useState } from "react";
import type { AccountBuild } from "../../domain/contracts";
import { buildShareUrl, SharedProfileError, type PlannerUrlState } from "./plannerState";

export function ShareRoute({ state, account, disabled }: { state: PlannerUrlState; account: AccountBuild; disabled?: boolean }) {
  const [message, setMessage] = useState("");
  const [manualUrl, setManualUrl] = useState("");

  const copy = async () => {
    setMessage("");
    setManualUrl("");
    try {
      const url = buildShareUrl(window.location.href, state, account);
      if (!navigator.clipboard?.writeText) throw Object.assign(new Error("Clipboard unavailable"), { url });
      await navigator.clipboard.writeText(url);
      setMessage(account.id === "custom" || account.id === "shared"
        ? "Route and account profile copied" : "Route link copied");
    } catch (error) {
      if (error instanceof SharedProfileError) setMessage(error.message);
      else {
        try {
          setManualUrl((error as { url?: string }).url || buildShareUrl(window.location.href, state, account));
          setMessage("Copy the link below");
        } catch (shareError) { setMessage(shareError instanceof Error ? shareError.message : "The route link could not be created."); }
      }
    }
  };

  return <div className="share-route">
    <button type="button" className="share-button" disabled={disabled} onClick={() => void copy()}>Copy route link</button>
    {(account.id === "custom" || account.id === "shared") && <small>The link includes this account profile.</small>}
    {message && <span className="share-message" role="status">{message}</span>}
    {manualUrl && <input aria-label="Route link" readOnly value={manualUrl} onFocus={event => event.currentTarget.select()} />}
  </div>;
}
