// Saqlanganlar va ko'rish tarixi. Toza funksiyalar: o'zgarmasa o'sha obyektni qaytaradi,
// shunda chaqiruvchi "o'zgardimi?" ni `next !== prev` bilan biladi va keraksiz saqlamaydi.

export type Item = { id: string; title: string; channel: string };
export type Library = { fav: Item[]; hist: Item[] };

export const HIST_MAX = 100;
export const EMPTY_LIB: Library = { fav: [], hist: [] };

function isItem(v: unknown): v is Item {
  const o = v as Item;
  return !!o && typeof o.id === 'string' && typeof o.title === 'string' && typeof o.channel === 'string';
}

export function parse(raw: string | null): Library {
  if (!raw) return EMPTY_LIB;
  try {
    const d = JSON.parse(raw) as Partial<Library>;
    return {
      fav: Array.isArray(d.fav) ? d.fav.filter(isItem) : [],
      hist: Array.isArray(d.hist) ? d.hist.filter(isItem).slice(0, HIST_MAX) : [],
    };
  } catch {
    return EMPTY_LIB;
  }
}

// Video tarix boshiga chiqadi. Allaqachon boshida tursa, faqat nomi/kanali yangilanadi
// (YouTube nomni ba'zan kechroq beradi).
export function addHistory(lib: Library, item: Item): Library {
  const first = lib.hist[0];
  if (first && first.id === item.id) {
    if (first.title === item.title && first.channel === item.channel) return lib;
    return { ...lib, hist: [item, ...lib.hist.slice(1)] };
  }
  const rest = lib.hist.filter((h) => h.id !== item.id);
  return { ...lib, hist: [item, ...rest].slice(0, HIST_MAX) };
}

export function isFav(lib: Library, id: string): boolean {
  return lib.fav.some((f) => f.id === id);
}

export function toggleFav(lib: Library, item: Item): Library {
  return isFav(lib, item.id)
    ? { ...lib, fav: lib.fav.filter((f) => f.id !== item.id) }
    : { ...lib, fav: [item, ...lib.fav] };
}

export function removeItem(lib: Library, list: keyof Library, id: string): Library {
  return { ...lib, [list]: lib[list].filter((i) => i.id !== id) };
}

export function clearHistory(lib: Library): Library {
  return lib.hist.length ? { ...lib, hist: [] } : lib;
}
