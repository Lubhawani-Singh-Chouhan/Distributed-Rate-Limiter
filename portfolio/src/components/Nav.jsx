import { useEffect, useState } from "react";
import useScrollDirection from "../hooks/useScrollDirection";
import useActiveSection from "../hooks/useActiveSection";
import { RESUME_URL } from "../links";

export const NAV_LINKS = [
  { id: "about", name: "About" },
  { id: "experience", name: "Experience" },
  { id: "projects", name: "Projects" },
  { id: "contact", name: "Contact" },
];

const SECTION_IDS = NAV_LINKS.map((link) => link.id);

export default function Nav() {
  const { direction, atTop } = useScrollDirection();
  const active = useActiveSection(SECTION_IDS);
  const [menuOpen, setMenuOpen] = useState(false);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timeout = setTimeout(() => setMounted(true), 100);
    return () => clearTimeout(timeout);
  }, []);

  useEffect(() => {
    document.body.classList.toggle("menu-open", menuOpen);
    return () => document.body.classList.remove("menu-open");
  }, [menuOpen]);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === "Escape") {
        setMenuOpen(false);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const hidden = direction === "down" && !atTop && !menuOpen;

  return (
    <>
      <header
        className="fixed inset-x-0 top-0 z-30 flex items-center justify-between px-6 backdrop-blur-[10px] transition-all duration-300 ease-[cubic-bezier(0.645,0.045,0.355,1)] sm:px-10 lg:px-12"
        style={{
          height: atTop ? "100px" : "70px",
          transform: hidden ? "translateY(-70px)" : "translateY(0)",
          backgroundColor: "rgba(10, 25, 47, 0.85)",
          boxShadow: atTop ? "none" : "0 10px 30px -10px var(--navy-shadow)",
        }}
      >
        <a
          href="#top"
          aria-label="Home"
          className={`reveal${mounted ? " is-visible" : ""} flex h-[42px] w-[42px] items-center justify-center rounded border border-green font-mono text-sm text-green transition-transform hover:-translate-x-1 hover:-translate-y-1`}
        >
          LS
        </a>

        <nav className="hidden items-center md:flex" aria-label="Primary">
          <ol className="m-0 flex list-none items-center p-0">
            {NAV_LINKS.map((link, index) => (
              <li
                key={link.id}
                className={`reveal${mounted ? " is-visible" : ""} mx-[5px] font-mono text-[13px]`}
                style={{ transitionDelay: `${index * 100}ms` }}
              >
                <a
                  href={`#${link.id}`}
                  className="nav-link p-2.5"
                  style={{
                    color:
                      active === link.id
                        ? "var(--color-green)"
                        : "var(--color-lightest-slate)",
                  }}
                  aria-current={active === link.id ? "true" : undefined}
                >
                  <span className="mr-[5px] font-mono text-xs text-green">
                    0{index + 1}.
                  </span>
                  {link.name}
                </a>
              </li>
            ))}
          </ol>

          <div
            className={`reveal${mounted ? " is-visible" : ""} ml-4`}
            style={{ transitionDelay: `${NAV_LINKS.length * 100}ms` }}
          >
            <a
              className="v4-button v4-button-sm"
              href={RESUME_URL}
              target="_blank"
              rel="noreferrer"
            >
              Resume
            </a>
          </div>
        </nav>

        <button
          type="button"
          className="relative z-30 flex h-10 w-10 items-center justify-center md:hidden"
          aria-expanded={menuOpen}
          aria-controls="mobile-menu"
          aria-label={menuOpen ? "Close menu" : "Open menu"}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span className="flex w-[30px] flex-col items-end gap-[6px]">
            <span
              className="block h-[2px] w-full bg-green transition-transform duration-200"
              style={
                menuOpen
                  ? { transform: "translateY(8px) rotate(45deg)" }
                  : undefined
              }
            />
            <span
              className="block h-[2px] w-full bg-green transition-opacity duration-200"
              style={menuOpen ? { opacity: 0 } : undefined}
            />
            <span
              className="block h-[2px] w-4/5 bg-green transition-all duration-200"
              style={
                menuOpen
                  ? { width: "100%", transform: "translateY(-8px) rotate(-45deg)" }
                  : undefined
              }
            />
          </span>
        </button>
      </header>

      <button
        type="button"
        tabIndex={-1}
        aria-hidden="true"
        className="fixed inset-0 z-20 cursor-default bg-dark-navy/60 transition-opacity duration-300 md:hidden"
        style={{
          opacity: menuOpen ? 1 : 0,
          pointerEvents: menuOpen ? "auto" : "none",
        }}
        onClick={() => setMenuOpen(false)}
      />

      <aside
        id="mobile-menu"
        className="fixed inset-y-0 right-0 z-20 flex w-3/4 max-w-[400px] flex-col items-center justify-center bg-light-navy px-6 transition-transform duration-300 ease-[cubic-bezier(0.645,0.045,0.355,1)] md:hidden"
        style={{ transform: menuOpen ? "translateX(0)" : "translateX(100%)" }}
        aria-hidden={!menuOpen}
      >
        <nav aria-label="Mobile">
          <ol className="m-0 flex list-none flex-col items-center gap-6 p-0 text-center">
            {NAV_LINKS.map((link, index) => (
              <li key={link.id}>
                <a
                  href={`#${link.id}`}
                  className="nav-link text-lightest-slate"
                  onClick={() => setMenuOpen(false)}
                  tabIndex={menuOpen ? 0 : -1}
                >
                  <span className="block font-mono text-sm text-green">
                    0{index + 1}.
                  </span>
                  <span className="text-lg">{link.name}</span>
                </a>
              </li>
            ))}
          </ol>
        </nav>

        <a
          className="v4-button mt-12"
          href={RESUME_URL}
          target="_blank"
          rel="noreferrer"
          tabIndex={menuOpen ? 0 : -1}
        >
          Resume
        </a>
      </aside>
    </>
  );
}
