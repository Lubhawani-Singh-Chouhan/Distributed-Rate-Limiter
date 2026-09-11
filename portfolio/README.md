# Lubhawani Singh — Portfolio

Single-page developer portfolio. Static React + Vite + Tailwind CSS. No backend, no database.

The UI is adapted from [Brittany Chiang's v4 portfolio](https://github.com/bchiang7/v4) (MIT): the same
navy/green palette, monospace section numbers, sticky nav, fixed side rails, `▹` lists, and offset
button shadows. Two deliberate differences: Calibre and SF Mono are licensed fonts, so this uses v4's
own fallbacks (Inter + Fira Code); and the fade-in-on-scroll is a small `IntersectionObserver` hook
instead of ScrollReveal, so there is no animation dependency in the bundle.

## Run locally

```bash
npm install && npm run dev
```

Open the URL Vite prints (typically `http://localhost:5173`).

## Build

```bash
npm run build
npm run preview
```

The production output is written to `dist/`.

## Deploy to Vercel (one click)

1. Push this repo to GitHub (already done if you are reading this on GitHub).
2. Go to [vercel.com/new](https://vercel.com/new) and sign in with GitHub.
3. Import **Lubhawani-Singh-Chouhan/Portfolio**.
4. Leave the defaults (Framework Preset: Vite, Build Command: `npm run build`, Output Directory: `dist`).
5. Click **Deploy**.

CLI alternative:

```bash
npm i -g vercel
vercel
```

Accept the defaults. For a production deploy: `vercel --prod`.

## Placeholders to fill in

These URLs are marked with `// TODO: add link` in `src/links.js`:

- LinkedIn
- Email
- Resume PDF — drop your file at `public/resume.pdf` (the button already points to `/resume.pdf`)
