// <yt-webview> elementidan keladigan holat va unga oid yordamchilar.
// Holatni Android tomoni (Player.java) yuboradi: "state" hodisasi, e.detail ichida.

export type PlayerState = {
  url: string;
  canGoBack: boolean;
  videoId: string;
  title: string;
  channel: string;
  playing: boolean;
  position: number; // soniya
  duration: number; // soniya, jonli efirda 0
  sleepLeft: number; // uxlash taymeri: qolgan soniya, 0 = o'chiq
};

export const EMPTY: PlayerState = {
  url: '',
  canGoBack: false,
  videoId: '',
  title: '',
  channel: '',
  playing: false,
  position: 0,
  duration: 0,
  sleepLeft: 0,
};

export const HOME_URL = 'https://m.youtube.com/';

// Native'dan kelgan obyektni tekshirib, aniq turdagi holatga aylantiradi
export function readState(raw: unknown): PlayerState {
  const d = (raw ?? {}) as Record<string, unknown>;
  const str = (v: unknown) => (typeof v === 'string' ? v : '');
  const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) && v > 0 ? v : 0);
  return {
    url: str(d.url),
    canGoBack: d.canGoBack === true,
    videoId: str(d.videoId),
    title: str(d.title),
    channel: str(d.channel),
    playing: d.playing === true,
    position: num(d.position),
    duration: num(d.duration),
    sleepLeft: Math.round(num(d.sleepLeft)),
  };
}

export function watchUrl(id: string) {
  return `https://m.youtube.com/watch?v=${id}`;
}

export function thumb(id: string) {
  return `https://i.ytimg.com/vi/${id}/mqdefault.jpg`;
}

const ID = /^[\w-]{11}$/;

// Qidiruv qatoridagi matn: YouTube havolasi bo'lsa — o'sha videoni ochamiz, aks holda qidiramiz
export function toUrl(input: string): string | null {
  const q = input.trim();
  if (!q) return null;
  const link = q.match(/^(?:https?:\/\/)?(?:www\.|m\.|music\.)?(youtube\.com|youtu\.be)(\/\S*)?$/i);
  if (link) {
    const host = link[1]!.toLowerCase();
    const path = link[2] ?? '/';
    if (host === 'youtu.be') {
      const id = path.slice(1, 12);
      return ID.test(id) ? watchUrl(id) : HOME_URL;
    }
    return `https://m.youtube.com${path}`;
  }
  return `https://m.youtube.com/results?search_query=${encodeURIComponent(q)}`;
}

// 75 → "1:15", 3725 → "1:02:05"
export function fmtTime(sec: number): string {
  const s = Math.max(0, Math.floor(sec));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const ss = String(s % 60).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

// Uxlash taymeri tugmasi: o'chiq → 15 → 30 → 60 daqiqa → o'chiq
export function nextSleep(leftSec: number): number {
  if (leftSec <= 0) return 15;
  if (leftSec <= 15 * 60) return 30;
  if (leftSec <= 30 * 60) return 60;
  return 0;
}
