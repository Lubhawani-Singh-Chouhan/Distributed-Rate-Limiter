import { GITHUB_URL, LINKEDIN_URL, RESUME_URL } from "../links";

export default function Hero() {
  return (
    <section id="top" className="px-5 sm:px-8">
      <div className="mx-auto max-w-5xl border-x border-line px-5 py-20 sm:px-10 sm:py-28 md:py-36">
        <p className="text-sm tracking-[0.18em] text-muted uppercase">
          Software Engineer
        </p>
        <h1 className="mt-6 font-serif text-5xl leading-[1.05] tracking-tight text-ink sm:text-6xl md:text-7xl">
          Lubhawani Singh
        </h1>
        <p className="mt-6 max-w-xl text-lg text-ink/90 sm:text-xl">
          Software Engineer @ Verizon | Backend &amp; Full-Stack
        </p>
        <p className="mt-4 max-w-xl text-base leading-relaxed text-muted sm:text-lg">
          Java/Spring Boot, Python, distributed systems. Based in Hyderabad,
          India.
        </p>
        <div className="mt-10 flex flex-wrap gap-3">
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center border border-ink px-5 py-2.5 text-sm text-ink transition-colors hover:bg-ink hover:text-bg"
          >
            GitHub
          </a>
          <a
            href={LINKEDIN_URL}
            className="inline-flex items-center border border-line px-5 py-2.5 text-sm text-muted transition-colors hover:border-ink hover:text-ink"
          >
            LinkedIn
          </a>
          <a
            href={RESUME_URL}
            className="inline-flex items-center border border-line px-5 py-2.5 text-sm text-muted transition-colors hover:border-ink hover:text-ink"
          >
            Download Resume
          </a>
        </div>
      </div>
    </section>
  );
}
