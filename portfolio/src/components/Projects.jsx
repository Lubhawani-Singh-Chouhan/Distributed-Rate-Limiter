import Reveal from "./Reveal";
import SectionHeading from "./SectionHeading";
import { IconFolder, IconGitHub } from "./icons";

const PROJECTS = [
  {
    title: "AI-Powered Course Builder",
    description:
      "GenAI course generation platform with a React frontend, a Node.js backend, and a Python RAG service that pulls learning material through the YouTube API.",
    result: "Pinecone vector search and GPT-4 assemble personalized course modules.",
    tech: ["React", "Node.js", "Python", "RAG", "Pinecone", "GPT-4"],
    github: "https://github.com/Lubhawani-Singh-Chouhan/Ai-Powered-Course-Builder",
  },
  {
    title: "Distributed Rate Limiter",
    description:
      "Token bucket rate limiter built on Java and Spring Boot with Redis-backed counters, packaged with Docker and Kubernetes manifests.",
    result: "~1,000 req/s sustained under a Gatling load test.",
    tech: ["Java", "Spring Boot", "Redis", "Docker", "Kubernetes", "Gatling"],
    github: "https://github.com/Lubhawani-Singh-Chouhan/Distributed-Rate-Limiter",
  },
  {
    title: "FraudGuard",
    subtitle: "Real-Time Transaction Stream Processor",
    description:
      "A Kafka event stream feeds a Spring Boot consumer and a Python FastAPI scorer, with Postgres for storage.",
    result: "Scores transactions in real time behind Prometheus and Grafana dashboards.",
    tech: ["Kafka", "Spring Boot", "FastAPI", "Postgres", "Prometheus", "Grafana"],
    github: "https://github.com/Lubhawani-Singh-Chouhan/Fraudguard",
  },
];

export default function Projects() {
  return (
    <section
      id="projects"
      className="mx-auto max-w-[1000px] py-[60px] sm:py-20 lg:py-[100px]"
    >
      <Reveal>
        <SectionHeading number="03">Things I&rsquo;ve Built</SectionHeading>
      </Reveal>

      <ul className="m-0 grid list-none grid-cols-1 gap-4 p-0 sm:grid-cols-2 lg:grid-cols-3">
        {PROJECTS.map((project, index) => (
          <Reveal as="li" key={project.title} delay={index * 100}>
            <div className="card-shadow group relative flex h-full flex-col items-start justify-between rounded bg-light-navy p-7 transition-transform duration-300 hover:-translate-y-[7px] focus-within:-translate-y-[7px]">
              <header className="w-full">
                <div className="mb-8 flex items-center justify-between">
                  <IconFolder className="h-10 w-10 text-green" />
                  <a
                    href={project.github}
                    target="_blank"
                    rel="noreferrer"
                    aria-label={`${project.title} on GitHub`}
                    className="nav-link p-1.5 text-light-slate"
                  >
                    <IconGitHub className="h-5 w-5" />
                  </a>
                </div>

                <h3 className="mb-1 text-[22px] text-lightest-slate transition-colors group-hover:text-green">
                  <a href={project.github} target="_blank" rel="noreferrer">
                    {project.title}
                  </a>
                </h3>

                {project.subtitle && (
                  <p className="mb-2 font-mono text-xs text-light-slate">
                    {project.subtitle}
                  </p>
                )}

                <p className="text-[17px] text-light-slate">
                  {project.description}
                </p>

                <p className="mt-3 font-mono text-[13px] leading-relaxed text-green">
                  {project.result}
                </p>
              </header>

              <footer className="w-full">
                <ul className="m-0 mt-5 flex list-none flex-wrap items-end gap-x-4 p-0">
                  {project.tech.map((tech) => (
                    <li
                      key={tech}
                      className="font-mono text-xs leading-[1.75] text-slate"
                    >
                      {tech}
                    </li>
                  ))}
                </ul>

                <a
                  href={project.github}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-link mt-5 font-mono text-[13px]"
                >
                  View on GitHub
                </a>
              </footer>
            </div>
          </Reveal>
        ))}
      </ul>
    </section>
  );
}
