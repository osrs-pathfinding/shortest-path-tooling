import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { account } from "../../test/fixtures";
import { profileFromHash } from "./plannerState";
import { ShareRoute } from "./ShareRoute";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("route sharing", () => {
  it("copies a self-contained custom-account route", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { ...navigator, clipboard: { writeText } });
    render(<ShareRoute account={{ id: "custom", name: "Shared iron", account: { ...account, name: "Shared iron" } }} state={{
      accountId: "custom", settings: { useFairyRings: false },
      start: { name: "Lumbridge", placeId: "lumbridge", coordinate: { x: 3222, y: 3218, plane: 0 } },
      destination: { name: "Varrock", placeId: "varrock", coordinate: { x: 3210, y: 3424, plane: 0 } },
    }} />);

    fireEvent.click(screen.getByRole("button", { name: "Copy route link" }));
    await waitFor(() => expect(writeText).toHaveBeenCalledOnce());
    const url = new URL(writeText.mock.calls[0][0]);
    expect(url.searchParams.get("from")).toBe("lumbridge");
    expect(url.searchParams.get("account")).toBe("shared");
    expect(url.searchParams.get("useFairyRings")).toBe("false");
    await expect(profileFromHash(url.hash)).resolves.toMatchObject({ name: "Shared iron" });
    expect(screen.getByRole("status").textContent).toContain("account copied");
  });
});
