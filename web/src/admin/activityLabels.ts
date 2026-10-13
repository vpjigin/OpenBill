import type { Channel } from "../shared/api";

export const activityChannelLabel: Record<Channel, string> = {
  ADMIN: "Admin",
  LOCAL: "This PC",
  LAN: "Wi‑Fi",
  PUBLIC: "Public",
};

export const activityActionLabels: Record<string, string> = {
  LOGIN: "Signed in",
  LOGIN_FAILED: "Sign-in failed",
  LOGOUT: "Signed out",
  SETUP: "Initial setup",
  USER_CREATED: "User created",
  USER_UPDATED: "User updated",
  PASSWORD_RESET: "Password reset",
  PASSWORD_CHANGED: "Password changed",
  LAN_STARTED: "Wi‑Fi access started",
  LAN_STOPPED: "Wi‑Fi access stopped",
  PUBLIC_STARTED: "Public link started",
  PUBLIC_STOPPED: "Public link stopped",
  DEVICE_SIGNED_OUT: "Device signed out",
  SETTINGS_UPDATED: "Settings updated",
  ITEM_CREATED: "Item created",
  ITEM_UPDATED: "Item updated",
  ITEM_DELETED: "Item deleted",
  CREDIT_NOTE_CREATED: "Credit note",
  SERVER_STARTED: "Server started",
};
