import { useRef, useState } from "react";
import Reveal from "./Reveal";
import SectionHeading from "./SectionHeading";

// TODO: add link — fill in the real date ranges for each role
const JOBS = [
  {
    tab: "SDE-1",
    title: "Software Engineer (SDE-1)",
    company: "Verizon",
    url: "https://www.verizon.com/",
    range: "Hyderabad, India",
    bullets: [
      "Build and maintain backend services in Java and Spring Boot, backed by PostgreSQL, Redis, and MongoDB.",
      "Built a Spring Batch pipeline to replace a legacy scheduler, syncing ~4,000 locations and ~25,000 records daily.",
      "Built a Python/Streamlit fuzzy-matching tool that automated 10,000+ equipment imports.",
      "Automated a migration process that previously took 1–2 days, bringing it down to 10–15 minutes.",
    ],
  },
  {
    tab: "Intern",
    title: "Software Engineer Intern",
    company: "Verizon",
    url: "https://www.verizon.com/",
    range: "Billing team · 6 months",
    // TODO: add link — add specific internship highlights here
    bullets: [
      "Six-month internship on the billing team, before converting to a full-time SDE-1 role.",
    ],
  },
];

export default function Experience() {
  const [activeTab, setActiveTab] = useState(0);
  const tabRefs = useRef([]);

  const onKeyDown = (event) => {
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") {
      return;
    }
    event.preventDefault();
    const offset = event.key === "ArrowDown" ? 1 : -1;
    const next = (activeTab + offset + JOBS.length) % JOBS.length;
    setActiveTab(next);
    tabRefs.current[next]?.focus();
  };

  return (
    <Reveal
      as="section"
      id="experience"
      className="mx-auto max-w-[700px] py-[60px] sm:py-20 lg:py-[100px]"
    >
      <SectionHeading number="02">Where I&rsquo;ve Worked</SectionHeading>

      <div className="block sm:flex sm:min-h-[340px]">
        <div
          role="tablist"
          aria-label="Job tabs"
          onKeyDown={onKeyDown}
          className="relative z-[3] mb-8 flex w-full overflow-x-auto sm:mb-0 sm:w-max sm:flex-col sm:overflow-visible"
        >
          {JOBS.map((job, index) => (
            <button
              key={job.tab}
              ref={(node) => {
                tabRefs.current[index] = node;
              }}
              type="button"
              role="tab"
              id={`tab-${index}`}
              aria-selected={activeTab === index}
              aria-controls={`panel-${index}`}
              tabIndex={activeTab === index ? 0 : -1}
              onClick={() => setActiveTab(index)}
              className="flex h-[42px] min-w-[120px] shrink-0 items-center justify-center whitespace-nowrap border-b-2 bg-transparent px-4 font-mono text-[13px] transition-colors hover:bg-light-navy sm:w-full sm:justify-start sm:border-b-0 sm:border-l-2 sm:px-5"
              style={{
                color:
                  activeTab === index
                    ? "var(--color-green)"
                    : "var(--color-slate)",
                borderColor:
                  activeTab === index
                    ? "var(--color-green)"
                    : "var(--color-lightest-navy)",
              }}
            >
              {job.tab}
            </button>
          ))}
        </div>

        <div className="relative w-full sm:ml-5">
          {JOBS.map((job, index) => (
            <div
              key={job.tab}
              id={`panel-${index}`}
              role="tabpanel"
              aria-labelledby={`tab-${index}`}
              hidden={activeTab !== index}
              tabIndex={activeTab === index ? 0 : -1}
              className="w-full px-1 py-2.5"
            >
              <h3 className="mb-0.5 text-[22px] font-medium leading-tight">
                <span>{job.title}</span>
                <span className="text-green">
                  {" "}
                  @{" "}
                  <a
                    href={job.url}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-link"
                  >
                    {job.company}
                  </a>
                </span>
              </h3>

              <p className="mb-6 font-mono text-[13px] text-light-slate">
                {job.range}
              </p>

              <ul className="fancy-list">
                {job.bullets.map((bullet) => (
                  <li key={bullet}>{bullet}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </Reveal>
  );
}
