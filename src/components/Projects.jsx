const PROJECTS = [
  {
    title: "Distributed Rate Limiter",
    description:
      "Token bucket rate limiter built with Redis, Spring Boot, and Docker Compose. Load-tested with Gatling: ~864 req/s average, 1,000 req/s sustained.",
    tags: ["Java", "Spring Boot", "Redis", "Docker Compose", "Gatling"],
    href: "https://github.com/Lubhawani-Singh-Chouhan/Distributed-Rate-Limiter",
  },
  {
    title: "FraudGuard",
    description:
      "Real-time fraud detection pipeline: Kafka (KRaft) event stream, Spring Boot consumer, Redis for user history, Python FastAPI rules-based scorer, Postgres, Spring Cloud Gateway, Prometheus + Grafana, Resilience4j circuit breakers, Kubernetes manifests with HPA, and Terraform (EC2/S3/ECR) stretch.",
    tags: [
      "Kafka",
      "Spring Boot",
      "Redis",
      "FastAPI",
      "Postgres",
      "Kubernetes",
      "Terraform",
    ],
    href: "https://github.com/Lubhawani-Singh-Chouhan/Fraudguard",
  },
  {
    title: "AI-Powered Course Builder",
    description:
      "GenAI course generation platform. React frontend, Node.js backend, Python AI/RAG service, Pinecone vector search, and GPT-4 for personalized learning content sourced via the YouTube API.",
    tags: ["React", "Node.js", "Python", "Pinecone", "GPT-4", "RAG"],
    href: "https://github.com/Lubhawani-Singh-Chouhan/Ai-Powered-Course-Builder",
  },
];

export default function Projects() {
  return (
    <section id="projects" className="px-5 sm:px-8">
      <div className="mx-auto max-w-5xl border-x border-t border-line px-5 py-16 sm:px-10 sm:py-20">
        <h2 className="font-serif text-2xl text-ink md:text-3xl">Projects</h2>
        <div className="mt-10 grid gap-6 md:grid-cols-2">
          {PROJECTS.map((project) => (
            <article
              key={project.title}
              className="flex flex-col border border-line bg-panel p-6 sm:p-7"
            >
              <h3 className="font-serif text-2xl text-ink">{project.title}</h3>
              <p className="mt-3 flex-1 text-[0.95rem] leading-relaxed text-muted">
                {project.description}
              </p>
              <ul className="mt-5 flex flex-wrap gap-2">
                {project.tags.map((tag) => (
                  <li
                    key={tag}
                    className="border border-line px-2 py-0.5 text-xs tracking-wide text-muted"
                  >
                    {tag}
                  </li>
                ))}
              </ul>
              <a
                href={project.href}
                target="_blank"
                rel="noreferrer"
                className="mt-6 inline-flex text-sm text-accent underline-offset-4 hover:underline"
              >
                View on GitHub
              </a>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
