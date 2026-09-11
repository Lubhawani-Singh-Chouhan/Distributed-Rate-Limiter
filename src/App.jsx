import Nav from "./components/Nav";
import SideRails from "./components/SideRails";
import Hero from "./components/Hero";
import About from "./components/About";
import Experience from "./components/Experience";
import Projects from "./components/Projects";
import Contact from "./components/Contact";
import Footer from "./components/Footer";

export default function App() {
  return (
    <div className="min-h-screen bg-navy">
      <a
        href="#content"
        className="v4-button absolute left-[-999px] top-auto z-[-99] h-px w-px overflow-hidden focus:left-0 focus:top-0 focus:z-[99] focus:h-auto focus:w-auto focus:overflow-auto"
      >
        Skip to Content
      </a>

      <Nav />
      <SideRails />

      <main
        id="content"
        className="mx-auto w-full max-w-[1600px] px-6 sm:px-12 lg:px-24 xl:px-[150px]"
      >
        <Hero />
        <About />
        <Experience />
        <Projects />
        <Contact />
      </main>

      <Footer />
    </div>
  );
}
