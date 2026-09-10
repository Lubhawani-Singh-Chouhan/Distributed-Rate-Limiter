const GROUPS = [
  {
    title: "Languages",
    items: ["Java", "Python", "JavaScript"],
  },
  {
    title: "Backend",
    items: ["Spring Boot", "Spring Batch", "Node.js"],
  },
  {
    title: "Data",
    items: ["PostgreSQL", "Redis", "MongoDB"],
  },
  {
    title: "DevOps",
    items: ["Docker", "Jenkins", "SonarQube", "Kubernetes basics"],
  },
  {
    title: "AI/ML",
    items: ["GPT-4 API", "Pinecone", "RAG"],
  },
];

export default function Skills() {
  return (
    <section id="skills" className="px-5 sm:px-8">
      <div className="mx-auto max-w-5xl border-x border-t border-line px-5 py-16 sm:px-10 sm:py-20">
        <h2 className="font-serif text-2xl text-ink md:text-3xl">Skills</h2>
        <div className="mt-10 grid gap-10 sm:grid-cols-2 lg:grid-cols-3">
          {GROUPS.map((group) => (
            <div key={group.title}>
              <h3 className="text-xs tracking-[0.16em] text-accent uppercase">
                {group.title}
              </h3>
              <ul className="mt-4 space-y-2 text-base text-ink/90">
                {group.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
