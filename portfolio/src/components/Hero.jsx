import { useEffect, useState } from "react";
import { RESUME_URL } from "../links";

function FadeUp({ delay, mounted, children }) {
  return (
    <div
      className={`reveal${mounted ? " is-visible" : ""}`}
      style={{ transitionDelay: `${delay}ms` }}
    >
      {children}
    </div>
  );
}

export default function Hero() {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timeout = setTimeout(() => setMounted(true), 150);
    return () => clearTimeout(timeout);
  }, []);

  return (
    <section
      id="top"
      className="mx-auto flex min-h-screen max-w-[1000px] flex-col items-start justify-center"
    >
      <FadeUp delay={100} mounted={mounted}>
        <h1 className="mb-[30px] ml-1 font-mono font-normal text-green" style={{ fontSize: "clamp(14px, 5vw, 16px)" }}>
          Hi, my name is
        </h1>
      </FadeUp>

      <FadeUp delay={200} mounted={mounted}>
        <h2
          className="m-0 text-lightest-slate"
          style={{ fontSize: "clamp(40px, 8vw, 80px)" }}
        >
          Lubhawani Singh.
        </h2>
      </FadeUp>

      <FadeUp delay={300} mounted={mounted}>
        <h3
          className="mt-[5px] leading-[0.9] text-slate"
          style={{ fontSize: "clamp(40px, 8vw, 80px)" }}
        >
          I build backend systems.
        </h3>
      </FadeUp>

      <FadeUp delay={400} mounted={mounted}>
        <p className="mt-5 max-w-[540px] text-slate">
          I&rsquo;m a Software Engineer at{" "}
          <a
            href="https://www.verizon.com/"
            target="_blank"
            rel="noreferrer"
            className="inline-link"
          >
            Verizon
          </a>{" "}
          working across backend and full-stack &mdash; mostly Java, Spring
          Boot, and Python, with a side interest in distributed systems. Based
          in Hyderabad, India.
        </p>
      </FadeUp>

      <FadeUp delay={500} mounted={mounted}>
        <div className="mt-[50px] flex flex-wrap gap-4">
          <a className="v4-button" href="#contact">
            Get In Touch
          </a>
          <a
            className="v4-button"
            href={RESUME_URL}
            target="_blank"
            rel="noreferrer"
          >
            Download Resume
          </a>
        </div>
      </FadeUp>
    </section>
  );
}
