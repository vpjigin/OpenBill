import { ErrorNote } from "../shared/ui";
import { AccessTile } from "./AccessTile";
import { adminApi } from "./adminApi";
import { useAccess } from "./useAccess";

/** The three channels side by side: This computer, Wi-Fi/LAN, Public. */
export function AccessTiles() {
  const { status, busy, error, toggle } = useAccess();
  const open = (url: string) => {
    adminApi.openBilling(url).catch(() => undefined);
  };

  return (
    <div className="flex flex-col gap-3">
      <ErrorNote message={error} />
      <div className="grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-3.5">
        <AccessTile
          title="This computer"
          description="Bill on this PC in the browser. On while this app runs."
          status={status?.local}
          alwaysOn
          onOpen={open}
          stoppedHint=""
        />
        <AccessTile
          title="Wi-Fi / LAN"
          description="Counters, laptops and phones on the shop Wi-Fi. No internet needed."
          status={status?.lan}
          busy={busy === "lan"}
          onToggle={(run) => toggle("lan", run)}
          onOpen={open}
          stoppedHint="Start to get the Wi-Fi link for other devices."
          note="Tip: reserve this computer's IP in your router so the link never changes."
        />
        <AccessTile
          title="Public (internet)"
          description="Open from anywhere through a secure tunnel."
          status={status?.pub}
          busy={busy === "public"}
          onToggle={(run) => toggle("public", run)}
          onOpen={open}
          stoppedHint="Start to get the public link. Needs internet."
          note="Only users with web sign-in allowed can log in. If the internet drops, Wi-Fi and this computer keep working."
        />
      </div>
    </div>
  );
}
