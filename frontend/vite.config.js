import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // `npm run build` writes the finished website into the Java server, so one server serves everything.
  build: { outDir: '../backend/src/main/resources/static', emptyOutDir: true },
  // While developing (`npm run dev`) API calls go to the Java server on port 8080.
  server: { proxy: { '/api': 'http://localhost:8080' } },
})
