// Runs Tailwind (and vendor prefixing) over src/app/globals.css during `next build` / `next dev`.
// Without this file the @tailwind directives are shipped to the browser unprocessed.
module.exports = {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
