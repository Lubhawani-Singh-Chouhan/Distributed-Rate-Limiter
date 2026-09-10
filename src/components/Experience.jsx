const BULLETS = [
  "Spring Batch pipeline replacing a legacy scheduler, syncing ~4,000 locations / ~25,000 records daily.",
  "Python/Streamlit fuzzy-matching tool automating 10,000+ equipment imports.",
  "Automation reducing a migration process from 1–2 days to 10–15 minutes.",
];

export default function Experience() {
  return (
    <section id="experience" className="px-5 sm:px-8">
      <div className="mx-auto max-w-5xl border-x border-t border-line px-5 py-16 sm:px-10 sm:py-20">
        <h2 className="font-serif text-2xl text-ink md:text-3xl">Experience</h2>
        <div className="mt-10 border-l border-line pl-6 sm:pl-8">
          <p className="text-xs tracking-[0.16em] text-muted uppercase">
            Verizon
          </p>
          <h3 className="mt-2 font-serif text-2xl text-ink">SDE-1</h3>
          <p className="mt-1 text-sm text-muted">
            Including a 6-month internship on the billing team
          </p>
          <ul className="mt-6 space-y-3 text-[0.95rem] leading-relaxed text-ink/90">
            {BULLETS.map((bullet) => (
              <li key={bullet} className="relative">
                <span className="absolute -left-[29px] top-2 h-1.5 w-1.5 rounded-full bg-accent sm:-left-[37px]" />
                {bullet}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
