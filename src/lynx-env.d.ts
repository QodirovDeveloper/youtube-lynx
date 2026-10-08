import type { BaseEvent, StandardProps } from '@lynx-js/types';

declare module '@lynx-js/types' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface GlobalProps {
    /**
     * Define your global properties in this interface.
     * These types will be accessible through `lynx.__globalProps`.
     */
  }

  // xelement-input'da bor, lekin @lynx-js/types'da yozilmagan
  interface InputProps {
    'placeholder-color'?: string;
  }

  // O'zimizning native element: android/.../YtWebViewUI.java
  interface IntrinsicElements {
    'yt-webview': StandardProps & {
      // pleyer holati o'zgardi (e.detail → readState)
      bindstate?: (e: BaseEvent<'state', unknown>) => void;
      // true: WebView bosishlarni qabul qilmaydi (ustida panel ochiq)
      blocked?: boolean;
      // blocked paytida telefonning "orqaga" tugmasi bosildi → panelni yopish kerak
      bindback?: (e: BaseEvent<'back', unknown>) => void;
    };
  }
}

// This export makes the file a module
export {};
