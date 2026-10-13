import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { APP_NAME } from "./brand";
import { MobileTabSpacer } from "./layout";

export interface NavItem {
  to: string;
  label: string;
  badge?: number;
}

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `flex min-h-11 items-center rounded-md px-3 py-2 text-base no-underline sm:text-sm ${
    isActive ? "bg-side-active font-semibold text-white" : "text-side-text hover:bg-side-active/60"
  }`;

function NavLinks({
  nav,
  sectionLabel,
  sectionNav,
  onNavigate,
}: {
  nav: NavItem[];
  sectionLabel?: string;
  sectionNav?: NavItem[];
  onNavigate?: () => void;
}) {
  return (
    <>
      {nav.map((n) => (
        <NavLink key={n.to} to={n.to} end={n.to === "/"} className={linkClass} onClick={onNavigate}>
          <span className="flex w-full items-center justify-between gap-2">
            {n.label}
            {n.badge != null && n.badge > 0 && (
              <span className="rounded-full bg-amber-500 px-1.5 py-0.5 text-[10px] font-bold text-[#1a1200]">
                {n.badge > 99 ? "99+" : n.badge}
              </span>
            )}
          </span>
        </NavLink>
      ))}
      {sectionLabel && (
        <span className="px-3 pb-1 pt-3 text-[11px] uppercase tracking-[0.08em] text-[#7e9188]">{sectionLabel}</span>
      )}
      {sectionNav?.map((n) => (
        <NavLink key={n.to} to={n.to} className={linkClass} onClick={onNavigate}>
          {n.label}
        </NavLink>
      ))}
    </>
  );
}

function pageTitle(pathname: string, items: NavItem[]): string {
  if (pathname === "/") return items.find((n) => n.to === "/")?.label ?? APP_NAME;
  const match = items.find((n) => n.to !== "/" && (pathname === n.to || pathname.startsWith(`${n.to}/`)));
  return match?.label ?? APP_NAME;
}

export function Shell({
  subtitle,
  nav,
  sectionLabel,
  sectionNav,
  footer,
  children,
  mobileTabs,
  contentLayout = "data",
}: {
  subtitle: string;
  nav: NavItem[];
  sectionLabel?: string;
  sectionNav?: NavItem[];
  footer?: ReactNode;
  children: ReactNode;
  /** Bottom tabs on phones; last item should use to "#more". */
  mobileTabs?: NavItem[];
  contentLayout?: "data" | "form";
}) {
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const allNav = useMemo(() => [...nav, ...(sectionNav ?? [])], [nav, sectionNav]);
  const title = pageTitle(location.pathname, allNav);

  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!drawerOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setDrawerOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [drawerOpen]);

  const mainPad =
    contentLayout === "form"
      ? "mx-auto w-full max-w-3xl px-5 py-6 sm:px-8 lg:py-8"
      : "w-full min-w-0 flex-1 px-5 py-6 sm:px-8 lg:px-8 lg:py-8 xl:px-[32px]";

  return (
    <div className="flex min-h-full min-w-0 max-w-[100vw] flex-col overflow-x-hidden lg:flex-row">
      {/* Desktop sidebar */}
      <nav
        aria-label="Main"
        className="no-print hidden w-[240px] shrink-0 flex-col gap-1 bg-side px-3.5 py-5 text-side-text lg:flex"
      >
        <div className="flex items-center gap-2.5 px-2 pb-4">
          <img src="/favicon.svg" alt="" className="size-[30px]" />
          <div className="flex min-w-0 flex-col">
            <span className="text-[15px] font-semibold text-white">{APP_NAME}</span>
            <span className="text-xs text-side-muted">{subtitle}</span>
          </div>
        </div>
        <NavLinks nav={nav} sectionLabel={sectionLabel} sectionNav={sectionNav} />
        <div className="flex-1 basis-6" />
        {footer}
      </nav>

      {/* Mobile / tablet top bar */}
      <header className="no-print sticky top-0 z-30 flex min-h-14 items-center gap-3 border-b border-line bg-side px-4 py-2 text-side-text lg:hidden">
        <button
          type="button"
          aria-label="Open menu"
          aria-expanded={drawerOpen}
          className="flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-lg border border-[#3a4a43] text-xl leading-none"
          onClick={() => setDrawerOpen(true)}
        >
          ☰
        </button>
        <img src="/favicon.svg" alt="" className="size-7 shrink-0" />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold text-white">{title}</div>
          <div className="truncate text-xs text-side-muted">{subtitle}</div>
        </div>
      </header>

      {/* Drawer */}
      {drawerOpen && (
        <div className="no-print fixed inset-0 z-40 lg:hidden" role="presentation">
          <button type="button" className="absolute inset-0 bg-black/50" aria-label="Close menu" onClick={() => setDrawerOpen(false)} />
          <nav
            aria-label="Main"
            className="absolute bottom-0 left-0 top-0 flex w-[min(100%,280px)] flex-col gap-1 overflow-y-auto bg-side px-3.5 py-5 shadow-xl"
          >
            <div className="mb-2 flex items-center justify-between gap-2 px-2">
              <span className="text-[15px] font-semibold text-white">{APP_NAME}</span>
              <button
                type="button"
                aria-label="Close menu"
                className="min-h-11 min-w-11 rounded-lg border border-[#3a4a43] text-lg"
                onClick={() => setDrawerOpen(false)}
              >
                ×
              </button>
            </div>
            <NavLinks
              nav={nav}
              sectionLabel={sectionLabel}
              sectionNav={sectionNav}
              onNavigate={() => setDrawerOpen(false)}
            />
            <div className="flex-1 basis-6" />
            {footer}
          </nav>
        </div>
      )}

      <main className={`flex min-w-0 flex-col gap-5 ${mainPad}`}>
        {children}
        {mobileTabs && mobileTabs.length > 0 && <MobileTabSpacer />}
      </main>

      {/* Mobile bottom tabs */}
      {mobileTabs && mobileTabs.length > 0 && (
        <nav
          aria-label="Quick navigation"
          className="no-print fixed bottom-0 left-0 right-0 z-30 flex border-t border-line bg-surface sm:hidden"
        >
          {mobileTabs.map((tab) => {
            if (tab.to === "#more") {
              return (
                <button
                  key="more"
                  type="button"
                  className="flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 text-xs font-medium text-muted"
                  onClick={() => setDrawerOpen(true)}
                >
                  More
                </button>
              );
            }
            return (
              <NavLink
                key={tab.to}
                to={tab.to}
                end={tab.to === "/"}
                className={({ isActive }) =>
                  `flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 text-xs font-medium no-underline ${
                    isActive ? "text-brand" : "text-muted"
                  }`
                }
              >
                {tab.label}
              </NavLink>
            );
          })}
        </nav>
      )}
    </div>
  );
}
