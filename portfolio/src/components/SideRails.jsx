import { useEffect, useState } from "react";
import { Icon } from "./icons";
import { EMAIL, MAILTO, SOCIALS } from "../links";

export default function SideRails() {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timeout = setTimeout(() => setMounted(true), 600);
    return () => clearTimeout(timeout);
  }, []);

  const revealClass = `reveal${mounted ? " is-visible" : ""}`;

  return (
    <>
      <div
        className={`${revealClass} fixed bottom-0 left-5 z-10 hidden w-10 text-light-slate md:block lg:left-10`}
      >
        <ul className="m-0 flex list-none flex-col items-center p-0 after:mx-auto after:block after:h-[90px] after:w-px after:bg-light-slate after:content-['']">
          {SOCIALS.map((social) => (
            <li key={social.name} className="last-of-type:mb-5">
              <a
                href={social.url}
                aria-label={social.name}
                target={social.url.startsWith("http") ? "_blank" : undefined}
                rel={social.url.startsWith("http") ? "noreferrer" : undefined}
                className="nav-link block p-2.5 transition-transform hover:-translate-y-[3px]"
              >
                <Icon name={social.name} className="h-5 w-5" />
              </a>
            </li>
          ))}
        </ul>
      </div>

      <div
        className={`${revealClass} fixed bottom-0 right-5 z-10 hidden w-10 text-light-slate md:block lg:right-10`}
      >
        <div className="flex flex-col items-center after:mx-auto after:block after:h-[90px] after:w-px after:bg-light-slate after:content-['']">
          <a
            href={MAILTO}
            className="nav-link my-5 p-2.5 font-mono text-xs tracking-[0.1em] transition-transform hover:-translate-y-[3px] [writing-mode:vertical-rl]"
          >
            {EMAIL}
          </a>
        </div>
      </div>
    </>
  );
}
