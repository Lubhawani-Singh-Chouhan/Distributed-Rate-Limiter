import { useEffect, useState } from "react";

export default function useScrollDirection() {
  const [direction, setDirection] = useState("up");
  const [atTop, setAtTop] = useState(true);

  useEffect(() => {
    let lastY = window.scrollY;
    let queued = false;

    const update = () => {
      const y = window.scrollY;
      setAtTop(y < 50);

      if (Math.abs(y - lastY) >= 5) {
        setDirection(y > lastY ? "down" : "up");
        lastY = y;
      }

      queued = false;
    };

    const onScroll = () => {
      if (queued) {
        return;
      }
      queued = true;
      window.requestAnimationFrame(update);
    };

    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return { direction, atTop };
}
