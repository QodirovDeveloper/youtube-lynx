import '@testing-library/jest-dom';
import { render } from '@lynx-js/react/testing-library';
import { describe, expect, test } from '@rstest/core';

import { App } from '../App';
import * as L from '../lib/library';
import * as P from '../lib/player';

test("App: video tanlanmaganda maslahat ko'rinadi", async () => {
  const { findByText } = render(<App />);
  expect(await findByText('Video tanlang')).toBeInTheDocument();
});

describe('player', () => {
  test('toUrl: matn → qidiruv, havola → o\'sha sahifa', () => {
    expect(P.toUrl('  ')).toBeNull();
    expect(P.toUrl('lofi beats')).toBe('https://m.youtube.com/results?search_query=lofi%20beats');
    expect(P.toUrl('https://youtu.be/dQw4w9WgXcQ?t=5')).toBe('https://m.youtube.com/watch?v=dQw4w9WgXcQ');
    expect(P.toUrl('https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=x')).toBe(
      'https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=x',
    );
    expect(P.toUrl('youtube.com')).toBe('https://m.youtube.com/');
  });

  test('fmtTime', () => {
    expect(P.fmtTime(0)).toBe('0:00');
    expect(P.fmtTime(75.9)).toBe('1:15');
    expect(P.fmtTime(3725)).toBe('1:02:05');
  });

  test('nextSleep: o\'chiq → 15 → 30 → 60 → o\'chiq', () => {
    expect(P.nextSleep(0)).toBe(15);
    expect(P.nextSleep(14 * 60)).toBe(30);
    expect(P.nextSleep(29 * 60)).toBe(60);
    expect(P.nextSleep(59 * 60)).toBe(0);
  });

  test('readState noto\'g\'ri qiymatlarni tozalaydi', () => {
    const s = P.readState({ videoId: 'abc', playing: true, duration: Infinity, position: -3, title: 5 });
    expect(s.videoId).toBe('abc');
    expect(s.playing).toBe(true);
    expect(s.duration).toBe(0);
    expect(s.position).toBe(0);
    expect(s.title).toBe('');
    expect(P.readState(undefined)).toEqual(P.EMPTY);
  });
});

describe('library', () => {
  const a = { id: 'a', title: 'A', channel: 'ch' };
  const b = { id: 'b', title: 'B', channel: 'ch' };

  test('tarix: yangisi boshida, takror yo\'q, o\'zgarmasa o\'sha obyekt', () => {
    let lib = L.addHistory(L.EMPTY_LIB, a);
    lib = L.addHistory(lib, b);
    lib = L.addHistory(lib, a);
    expect(lib.hist.map((i) => i.id)).toEqual(['a', 'b']);
    expect(L.addHistory(lib, a)).toBe(lib);
    const renamed = L.addHistory(lib, { ...a, title: 'A (to\'g\'ri nom)' });
    expect(renamed.hist[0]!.title).toBe('A (to\'g\'ri nom)');
    expect(renamed.hist).toHaveLength(2);
  });

  test('tarix HIST_MAX dan oshmaydi', () => {
    let lib = L.EMPTY_LIB;
    for (let i = 0; i < L.HIST_MAX + 5; i++) lib = L.addHistory(lib, { id: `v${i}`, title: 't', channel: 'c' });
    expect(lib.hist).toHaveLength(L.HIST_MAX);
    expect(lib.hist[0]!.id).toBe(`v${L.HIST_MAX + 4}`);
  });

  test('saqlash/olib tashlash va parse', () => {
    let lib = L.toggleFav(L.EMPTY_LIB, a);
    expect(L.isFav(lib, 'a')).toBe(true);
    lib = L.toggleFav(lib, a);
    expect(L.isFav(lib, 'a')).toBe(false);
    lib = L.removeItem(L.addHistory(lib, b), 'hist', 'b');
    expect(lib.hist).toHaveLength(0);
    expect(L.parse('{"fav":[{"id":"x","title":"X","channel":""},{"bad":1}]}').fav).toHaveLength(1);
    expect(L.parse('buzuq')).toEqual(L.EMPTY_LIB);
  });
});
