import { api, type Channel, type Role } from "../shared/api";

export interface ChannelStatus {
  running: boolean;
  state: "RUNNING" | "STOPPED" | "CONNECTING" | "RECONNECTING" | "ERROR";
  urls: string[];
  error: string | null;
  devices: number;
  linkKind: "TEMPORARY" | "FIXED" | null;
}

export interface AccessStatus {
  local: ChannelStatus;
  lan: ChannelStatus;
  pub: ChannelStatus;
}

export interface Summary {
  shopName: string;
  billsToday: number;
  salesTodayPaise: number;
  devicesOnline: number;
  lastBackupAt: string | null;
}

export interface BackupFile {
  name: string;
  size: number;
  createdAt: string;
  kind: "backup" | "upload" | "pre-restore";
}

export interface BackupList {
  backups: BackupFile[];
  lastBackupAt: string | null;
  pendingRestore: string | null;
}

export interface AttentionItem {
  level: "warn" | "ok";
  code: string;
  message: string;
}

export interface ActivityEntry {
  id: string;
  createdAt: string;
  userId: string | null;
  userName: string | null;
  channel: Channel | null;
  ip: string | null;
  action: string;
  target: string | null;
  details: string | null;
}

export interface Device {
  id: string;
  userName: string;
  role: Role;
  channel: Channel;
  ip: string | null;
  userAgent: string | null;
  since: string;
  lastSeen: string;
}

export interface UserRow {
  id: string;
  name: string;
  username: string;
  role: Role;
  webAccess: boolean;
  active: boolean;
  lastLoginAt: string | null;
  version: number;
}

export interface ShopSettings {
  shopName: string;
  gstin: string | null;
  stateCode: string | null;
  address: string | null;
  phone: string | null;
  pincode: string | null;
  invoicePrefix: string;
  tunnelTokenSet: boolean;
  publicUrl: string | null;
  hasLogo: boolean;
  receiptShowLogo: boolean;
}

export type AccessChannel = "lan" | "public";

export type PrinterKind = "SYSTEM" | "NETWORK";

export interface PrinterConfig {
  id: string;
  name: string;
  kind: PrinterKind;
  systemName: string | null;
  host: string | null;
  port: number;
  paperMm: number;
  charsPerLine: number | null;
  openDrawer: boolean;
  cut: boolean;
  defaultPrinter: boolean;
  version: number;
}

export const adminApi = {
  setupStatus: () => api<{ setupDone: boolean }>("/api/setup/status"),
  setup: (body: unknown) => api<{ setupDone: boolean }>("/api/setup", { method: "POST", json: body }),
  access: () => api<AccessStatus>("/api/admin/access"),
  startAccess: (c: AccessChannel) => api<AccessStatus>(`/api/admin/access/${c}/start`, { method: "POST" }),
  stopAccess: (c: AccessChannel) => api<AccessStatus>(`/api/admin/access/${c}/stop`, { method: "POST" }),
  summary: () => api<Summary>("/api/admin/summary"),
  attention: () => api<AttentionItem[]>("/api/admin/attention"),
  devices: () => api<Device[]>("/api/admin/devices"),
  signOutDevice: (id: string) => api<void>(`/api/admin/devices/${id}`, { method: "DELETE" }),
  users: () => api<UserRow[]>("/api/admin/users"),
  createUser: (body: unknown) => api<UserRow>("/api/admin/users", { method: "POST", json: body }),
  updateUser: (id: string, body: unknown) => api<UserRow>(`/api/admin/users/${id}`, { method: "PUT", json: body }),
  resetUserPassword: (id: string, newPassword: string) =>
    api<void>(`/api/admin/users/${id}/reset-password`, { method: "POST", json: { newPassword } }),
  settings: () => api<ShopSettings>("/api/admin/settings"),
  saveSettings: (body: unknown) => api<ShopSettings>("/api/admin/settings", { method: "PUT", json: body }),
  uploadLogo: async (file: File) => {
    const form = new FormData();
    form.append("file", file);
    const token = localStorage.getItem("billing.token");
    const res = await fetch("/api/admin/settings/logo", {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    });
    if (!res.ok) {
      let message = `Request failed (${res.status})`;
      try {
        const data = await res.json();
        if (data?.error) message = data.error;
      } catch {
        /* not JSON */
      }
      throw new Error(message);
    }
    return res.json() as Promise<ShopSettings>;
  },
  removeLogo: () => api<ShopSettings>("/api/admin/settings/logo", { method: "DELETE" }),
  logoPreviewUrl: () => "/api/admin/settings/logo",
  openBilling: (url?: string) => api<{ url: string }>("/api/admin/open-billing", { method: "POST", json: { url: url ?? null } }),
  backups: () => api<BackupList>("/api/admin/backups"),
  createBackup: () => api<BackupFile>("/api/admin/backups", { method: "POST" }),
  uploadBackup: async (file: File): Promise<BackupFile> => {
    const token = localStorage.getItem("billing.token");
    const body = new FormData();
    body.append("file", file);
    const res = await fetch("/api/admin/backups/upload", {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body,
    });
    if (!res.ok) {
      let message = `Upload failed (${res.status})`;
      try {
        const data = await res.json();
        if (data?.error) message = data.error;
      } catch {
        /* ignore */
      }
      throw new Error(message);
    }
    return res.json() as Promise<BackupFile>;
  },
  restoreBackup: (name: string) =>
    api<void>(`/api/admin/backups/${encodeURIComponent(name)}/restore`, { method: "POST" }),
  cancelRestorePending: () => api<void>("/api/admin/backups/restore-pending", { method: "DELETE" }),
  openBackupFolder: () => api<void>("/api/admin/backups/open-folder", { method: "POST" }),
  osPrinters: () => api<string[]>("/api/admin/printers/os"),
  printers: () => api<PrinterConfig[]>("/api/admin/printers"),
  createPrinter: (body: unknown) => api<PrinterConfig>("/api/admin/printers", { method: "POST", json: body }),
  updatePrinter: (id: string, body: unknown) =>
    api<PrinterConfig>(`/api/admin/printers/${id}`, { method: "PUT", json: body }),
  deletePrinter: (id: string) => api<void>(`/api/admin/printers/${id}`, { method: "DELETE" }),
  setDefaultPrinter: (id: string) =>
    api<PrinterConfig>(`/api/admin/printers/${id}/default`, { method: "POST" }),
  testPrinter: (id: string) => api<void>(`/api/admin/printers/${id}/test`, { method: "POST" }),
  activity: (params?: { from?: string; to?: string; action?: string; user?: string; limit?: number }) => {
    const sp = new URLSearchParams();
    if (params?.from) sp.set("from", params.from);
    if (params?.to) sp.set("to", params.to);
    if (params?.action) sp.set("action", params.action);
    if (params?.user) sp.set("user", params.user);
    sp.set("limit", String(params?.limit ?? 100));
    return api<ActivityEntry[]>(`/api/admin/activity?${sp.toString()}`);
  },
};
