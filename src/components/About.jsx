export default function About() {
  return (
    <section id="about" className="px-5 sm:px-8">
      <div className="mx-auto grid max-w-5xl border-x border-t border-line px-5 py-16 sm:px-10 sm:py-20 md:grid-cols-[160px_1fr] md:gap-12">
        <h2 className="font-serif text-2xl text-ink md:text-3xl">About</h2>
        <div className="mt-6 space-y-4 text-base leading-relaxed text-muted md:mt-1 md:text-[1.05rem]">
          <p>
            SDE-1 at Verizon, with about 1.5 years of experience including a
            6-month internship on the billing team. I work on backend systems
            and internal tools that replace slow, manual processes with
            reliable pipelines.
          </p>
          <p>
            B.Tech in Computer Science from Mody University. Two published
            research papers. 450+ DSA problems solved.
          </p>
        </div>
      </div>
    </section>
  );
}
