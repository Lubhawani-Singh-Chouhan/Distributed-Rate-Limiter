import { EMAIL, GITHUB_URL, LINKEDIN_URL } from "../links";

export default function Contact() {
  return (
    <section id="contact" className="px-5 sm:px-8">
      <div className="mx-auto max-w-5xl border-x border-t border-line px-5 py-16 sm:px-10 sm:py-20">
        <h2 className="font-serif text-2xl text-ink md:text-3xl">Contact</h2>
        <p className="mt-6 max-w-lg text-base leading-relaxed text-muted">
          Open to conversations about backend systems, full-stack work, and
          interesting problems.
        </p>
        <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:gap-8">
          <a
            href={EMAIL}
            className="text-base text-ink underline-offset-4 hover:text-accent hover:underline"
          >
            Email
          </a>
          <a
            href={LINKEDIN_URL}
            className="text-base text-ink underline-offset-4 hover:text-accent hover:underline"
          >
            LinkedIn
          </a>
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noreferrer"
            className="text-base text-ink underline-offset-4 hover:text-accent hover:underline"
          >
            GitHub
          </a>
        </div>
      </div>
    </section>
  );
}
