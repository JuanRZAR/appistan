import { defineConfig } from 'astro/config';
import tailwindcss from '@tailwindcss/vite';

const pages = process.env.APPISTAN_PAGES === 'true';
export default defineConfig({
  output: 'static',
  site: pages ? 'https://juanrzar.github.io' : undefined,
  base: pages ? '/appistan/' : '/',
  vite: { plugins: [tailwindcss()] },
  devToolbar: { enabled: false },
});
