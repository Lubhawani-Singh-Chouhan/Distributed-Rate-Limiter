export default function SectionHeading({ number, children }) {
  return (
    <h2
      className="mt-2.5 mb-10 flex w-full items-center whitespace-nowrap"
      style={{ fontSize: "clamp(26px, 5vw, 32px)" }}
    >
      <span
        className="relative bottom-px mr-2.5 font-mono font-normal text-green"
        style={{ fontSize: "clamp(16px, 3vw, 20px)" }}
      >
        {number}.
      </span>
      {children}
      <span
        aria-hidden="true"
        className="relative -top-1 ml-3 h-px max-w-[300px] flex-1 bg-lightest-navy sm:ml-5"
      />
    </h2>
  );
}
