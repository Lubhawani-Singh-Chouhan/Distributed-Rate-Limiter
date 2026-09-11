import Reveal from "./Reveal";
import { GITHUB_URL, LINKEDIN_URL, MAILTO } from "../links";

export default function Contact() {
  return (
    <Reveal
      as="section"
      id="contact"
      className="mx-auto max-w-[600px] py-[60px] text-center sm:py-20 lg:py-[100px]"
    >
      <h2 className="mb-5 font-mono text-base font-normal text-green">
        <span className="mr-2">04.</span>What&rsquo;s Next?
      </h2>

      <h3
        className="text-lightest-slate"
        style={{ fontSize: "clamp(40px, 5vw, 60px)" }}
      >
        Get In Touch
      </h3>

      <p className="mt-4 text-[17px] sm:text-lg">
        My inbox is always open. Whether it&rsquo;s a backend role, a question
        about one of these projects, or just to say hi, I&rsquo;ll do my best to
        get back to you.
      </p>

      <div className="mt-[50px] flex flex-wrap items-center justify-center gap-6">
        <a className="v4-button" href={MAILTO}>
          Say Hello
        </a>
      </div>

      <div className="mt-8 flex flex-wrap items-center justify-center gap-6 font-mono text-[13px]">
        <a
          href={GITHUB_URL}
          target="_blank"
          rel="noreferrer"
          className="inline-link"
        >
          GitHub
        </a>
        <a href={LINKEDIN_URL} className="inline-link">
          LinkedIn
        </a>
      </div>
    </Reveal>
  );
}
