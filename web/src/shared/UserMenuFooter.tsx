import { useState } from "react";
import type { CurrentUser } from "./api";
import { ChangePasswordDialog } from "./ChangePasswordDialog";

export function UserMenuFooter({
  user,
  channelLabel,
  onLogout,
  compact = false,
}: {
  user: CurrentUser;
  channelLabel?: string;
  onLogout: () => void;
  compact?: boolean;
}) {
  const [showPassword, setShowPassword] = useState(false);

  return (
    <>
      {showPassword && <ChangePasswordDialog onClose={() => setShowPassword(false)} />}
      <div className={`flex flex-col gap-2 ${compact ? "" : "border-t border-[#2a3631] px-2 pt-3.5"}`}>
        <span className={`text-sm font-medium ${compact ? "text-side-text" : "text-white"}`}>{user.name}</span>
        {channelLabel && (
          <span className="text-xs text-side-muted">
            {user.role.toLowerCase()} · via {channelLabel}
          </span>
        )}
        <button
          type="button"
          onClick={() => setShowPassword(true)}
          className="min-h-11 rounded-lg border border-[#3a4a43] bg-transparent text-base text-side-text hover:bg-side-active sm:text-[13px]"
        >
          Change password
        </button>
        <button
          type="button"
          onClick={onLogout}
          className="min-h-11 rounded-lg border border-[#3a4a43] bg-transparent text-base text-side-text hover:bg-side-active sm:text-[13px]"
        >
          Sign out
        </button>
      </div>
    </>
  );
}
