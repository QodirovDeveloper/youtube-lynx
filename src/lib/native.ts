// Android modullariga ko'prik (MainApplication.java'da ro'yxatdan o'tgan).
// Faqat background thread'da (effect va event handler ichida) chaqiriladi.
// Brauzerdagi preview'da modullar yo'q: kutubxonani xotirada saqlaymiz, buyruqlar jim o'tadi.

const KEY = 'library-v1';
let memory: string | null = null;

function modules() {
  return typeof NativeModules === 'undefined' ? undefined : NativeModules;
}

export function loadLibrary(cb: (raw: string | null) => void) {
  const storage = modules()?.StorageModule;
  if (!storage) {
    cb(memory);
    return;
  }
  try {
    storage.getItem(KEY, (value) => cb(value ?? null));
  } catch {
    cb(null);
  }
}

export function saveLibrary(raw: string) {
  memory = raw;
  try {
    modules()?.StorageModule?.setItem(KEY, raw);
  } catch {
    // saqlab bo'lmadi: ilova ishlashda davom etadi
  }
}

export type Command = 'play' | 'pause' | 'toggle' | 'next' | 'prev' | 'skip' | 'sleep' | 'back';

// skip: soniya (+/-), sleep: daqiqa (0 = o'chirish)
export function command(action: Command, value = 0) {
  try {
    modules()?.PlayerModule?.command(action, value);
  } catch {
    // preview'da pleyer yo'q
  }
}

export function openUrl(url: string) {
  try {
    modules()?.PlayerModule?.load(url);
  } catch {
    // preview'da pleyer yo'q
  }
}
