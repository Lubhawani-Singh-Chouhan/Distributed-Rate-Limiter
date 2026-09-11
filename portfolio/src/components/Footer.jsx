import { Icon } from "./icons";
import { SOCIALS } from "../links";

export default function Footer() {
  return (
    <footer className="flex flex-col items-center justify-center px-4 py-6 text-center">
      <div className="mx-auto mb-3 w-full max-w-[270px] text-light-slate md:hidden">
        <ul className="m-0 flex list-none items-center justify-between p-0">
          {SOCIALS.map((social) => (
            <li key={social.name}>
              <a
                href={social.url}
                aria-label={social.name}
                target={social.url.startsWith("http") ? "_blank" : undefined}
                rel={social.url.startsWith("http") ? "noreferrer" : undefined}
                className="nav-link block p-2.5"
              >
                <Icon name={social.name} className="h-5 w-5" />
              </a>
            </li>
          ))}
        </ul>
      </div>

      <p className="font-mono text-xs leading-relaxed text-light-slate">
        Built by Lubhawani Singh
      </p>
    </footer>
  );
}
