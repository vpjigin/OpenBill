import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { APP_NAME } from "./brand";

export interface NavItem {
  to: string;
  label: string;
  badge?: number;
}

/** Dark sidebar + content, as in the mockup. Sidebar stacks on top on narrow screens. */
export function Shell({
  subtitle,
  nav,
  sectionLabel,
  sectionNav,
  footer,
  children,
}: {
  subtitle: string;
  nav: NavItem[];
  sectionLabel?: string;
  sectionNav?: NavItem[];
  footer?: ReactNode;
  children: ReactNode;
}) {
  const link = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-2.5 text-sm no-underline ${
      isActive ? "bg-side-active font-semibold text-white" : "text-side-text hover:bg-side-active/60"
    }`;

  return (
    <div className="flex min-h-full flex-wrap">
      <nav
        aria-label="Main"
        className="no-print flex max-w-full flex-[1_1_210px] flex-col gap-1 bg-side px-3.5 py-5 text-side-text md:max-w-[240px]"
      >
        <div className="flex items-center gap-2.5 px-2 pb-4">
          <img src="/favicon.svg" alt="" className="size-[30px]" />
          <div className="flex flex-col">
            <span className="text-[15px] font-semibold text-white">{APP_NAME}</span>
            <span className="text-xs text-side-muted">{subtitle}</span>
          </div>
        </div>
        {nav.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.to === "/"} className={link}>
            <span className="flex items-center justify-between gap-2">
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
          <NavLink key={n.to} to={n.to} className={link}>
            {n.label}
          </NavLink>
        ))}
        <div className="flex-1 basis-6" />
        {footer}
      </nav>
      <main className="flex min-w-0 flex-[999_1_560px] flex-col gap-5 px-5 py-7 sm:px-8">{children}</main>
    </div>
  );
}
