import { useEffect, useState } from "react";

export default function useActiveSection(ids) {
  const [active, setActive] = useState("");
  const key = ids.join(",");

  useEffect(() => {
    if (typeof IntersectionObserver === "undefined") {
      return undefined;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const onScreen = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio);

        if (onScreen.length > 0) {
          setActive(onScreen[0].target.id);
        }
      },
      { rootMargin: "-40% 0px -40% 0px", threshold: [0, 0.2, 0.5, 1] },
    );

    key.split(",").forEach((id) => {
      const node = document.getElementById(id);
      if (node) {
        observer.observe(node);
      }
    });

    return () => observer.disconnect();
  }, [key]);

  return active;
}
