import { pluginQRCode } from '@lynx-js/qrcode-rsbuild-plugin';
import { pluginReactLynx } from '@lynx-js/react-rsbuild-plugin';
import { defineConfig } from '@lynx-js/rspeedy';
import { pluginTypeCheck } from '@rsbuild/plugin-type-check';

export default defineConfig({
  // APK uchun build qilganda rasmlarni bundle ichiga joylaymiz (assets'dan yuklanadi, server yo'q)
  // Oddiy build/dev'da brauzer preview ham yig'iladi: /__web_preview?casename=main.web.bundle
  ...(process.env.APK_BUILD
    ? { output: { dataUriLimit: Number.MAX_SAFE_INTEGER } }
    : { environments: { web: {}, lynx: {} } }),
  // 3000-port boshqa Lynx loyihalarda band bo'lishi mumkin
  server: { port: Number(process.env.PORT) || 3030 },
  plugins: [
    pluginQRCode({
      schema(url) {
        // We use `?fullscreen=true` to open the page in LynxExplorer in full screen mode
        return `${url}?fullscreen=true`;
      },
    }),
    pluginReactLynx(),
    pluginTypeCheck(),
  ],
});
