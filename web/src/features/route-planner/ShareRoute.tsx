import { useState } from "react";
import type { PlannerAccount } from "../../domain/accounts";
import { buildShareUrl, SharedProfileError, type PlannerUrlState } from "./plannerState";

export function ShareRoute({ state, account, disabled }: { state: PlannerUrlState; account: PlannerAccount; disabled?: boolean }) {
  const [message, setMessage] = useState("");
  const [manualUrl, setManualUrl] = useState("");
  const carriesAccount = account.id === "custom" || account.id === "shared";
  const url = () => buildShareUrl(window.location.href, state, account.id, account.account);

  const copy = async () => {
    setMessage("");
    setManualUrl("");
    try {
      const link = url();
      if (!navigator.clipboard?.writeText) throw Object.assign(new Error("Clipboard unavailable"), { url: link });
      await navigator.clipboard.writeText(link);
      setMessage(carriesAccount ? "Route and account copied" : "Route link copied");
    } catch (error) {
      if (error instanceof SharedProfileError) setMessage(error.message);
      else {
        try {
          setManualUrl((error as { url?: string }).url || url());
          setMessage("Copy the link below");
        } catch (shareError) { setMessage(shareError instanceof Error ? shareError.message : "The route link could not be created."); }
      }
    }
  };

  return <div className="share-route">
    <button type="button" className="share-button" disabled={disabled} onClick={() => void copy()}>Copy route link</button>
    {carriesAccount && <small>The link includes this account.</small>}
    {message && <span className="share-message" role="status">{message}</span>}
    {manualUrl && <input aria-label="Route link" readOnly value={manualUrl} onFocus={event => event.currentTarget.select()} />}
  </div>;
}
