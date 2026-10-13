import { useEffect, useState } from "react";
import { billingApi, type ShopInfo } from "./billingApi";

let cached: ShopInfo | null = null;
let inflight: Promise<ShopInfo> | null = null;

const TOKEN_KEY = "billing.token";

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

async function fetchLogoObjectUrl(): Promise<string | null> {
  const token = readToken();
  const res = await fetch("/api/shop/logo", {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) return null;
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

async function enrichShop(base: Omit<ShopInfo, "logoObjectUrl">): Promise<ShopInfo> {
  if (!base.hasLogo) {
    return { ...base, logoObjectUrl: null };
  }
  try {
    const logoObjectUrl = await fetchLogoObjectUrl();
    return { ...base, logoObjectUrl };
  } catch {
    return { ...base, logoObjectUrl: null };
  }
}

function loadShop(): Promise<ShopInfo> {
  if (cached) return Promise.resolve(cached);
  if (!inflight) {
    inflight = billingApi.shop().then(enrichShop).then((s) => {
      cached = s;
      return s;
    });
  }
  return inflight;
}

export function invalidateShopCache(): void {
  if (cached?.logoObjectUrl) {
    URL.revokeObjectURL(cached.logoObjectUrl);
  }
  cached = null;
  inflight = null;
}

export function useShop(): ShopInfo | null {
  const [shop, setShop] = useState<ShopInfo | null>(cached);
  useEffect(() => {
    loadShop().then(setShop).catch(() => undefined);
  }, []);
  return shop;
}

export function prefetchShop(): void {
  loadShop().catch(() => undefined);
}
