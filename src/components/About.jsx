import Reveal from "./Reveal";
import SectionHeading from "./SectionHeading";

const SKILL_GROUPS = [
  { title: "Languages", items: ["Java", "Python", "JavaScript"] },
  { title: "Backend", items: ["Spring Boot", "Spring Batch", "Node.js"] },
  { title: "Data", items: ["PostgreSQL", "Redis", "MongoDB"] },
  {
    title: "DevOps",
    items: ["Docker", "Jenkins", "SonarQube", "Kubernetes (basics)"],
  },
  { title: "AI/ML", items: ["GPT-4 API", "Pinecone", "RAG"] },
];

const FACTS = [
  { label: "Role", value: "SDE-1 @ Verizon" },
  { label: "Based in", value: "Hyderabad, India" },
  { label: "Degree", value: "B.Tech CS, Mody University" },
  { label: "Published", value: "2 research papers" },
  { label: "DSA", value: "450+ problems solved" },
];

export default function About() {
  return (
    <Reveal
      as="section"
      id="about"
      className="mx-auto max-w-[900px] py-[60px] sm:py-20 lg:py-[100px]"
    >
      <SectionHeading number="01">About Me</SectionHeading>

      <div className="grid gap-[50px] md:grid-cols-[3fr_2fr]">
        <div>
          <p className="text-[17px] sm:text-lg">
            Hello! I&rsquo;m Lubhawani, a software engineer who enjoys turning
            slow, manual processes into pipelines nobody has to think about. I
            joined Verizon&rsquo;s billing team as an intern and stayed on as an
            SDE-1, where most of my work is backend.
          </p>

          <p className="mt-4 text-[17px] sm:text-lg">
            Day to day that means Java, Spring Boot, and Spring Batch on the
            service side, Python for internal tooling, and PostgreSQL, Redis,
            and MongoDB for storage. Outside of work I build side projects
            around distributed systems and GenAI.
          </p>

          <p className="mt-4 text-[17px] sm:text-lg">
            I hold a B.Tech in Computer Science from Mody University, have
            published two research papers, and have solved 450+ DSA problems.
          </p>

          <p className="mt-6 text-[17px] sm:text-lg">
            Here are the technologies I work with:
          </p>

          <div className="mt-5 grid grid-cols-1 gap-x-4 gap-y-5 sm:grid-cols-2">
            {SKILL_GROUPS.map((group) => (
              <div key={group.title}>
                <h3 className="mb-2 font-mono text-xs tracking-[0.1em] text-light-slate uppercase">
                  {group.title}
                </h3>
                <ul className="skills-list">
                  {group.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        {/* TODO: add link — swap this card for a headshot at public/me.jpg if you want v4's photo treatment */}
        <div className="relative mx-auto w-full max-w-[300px] self-start md:mx-0">
          <span
            aria-hidden="true"
            className="pointer-events-none absolute left-3.5 top-3.5 h-full w-full rounded border-2 border-green"
          />
          <div className="card-shadow relative rounded border border-lightest-navy bg-light-navy p-6">
            <dl className="m-0">
              {FACTS.map((fact) => (
                <div key={fact.label} className="mb-4 last:mb-0">
                  <dt className="font-mono text-[11px] tracking-[0.1em] text-green uppercase">
                    {fact.label}
                  </dt>
                  <dd className="m-0 mt-1 text-sm text-lightest-slate">
                    {fact.value}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        </div>
      </div>
    </Reveal>
  );
}
