import { useEffect, useRef, useState } from '@lynx-js/react';

import './App.css';
import type { Tab } from './components/LibraryPanel.js';
import { LibraryPanel } from './components/LibraryPanel.js';
import { PlayerBar } from './components/PlayerBar.js';
import { TopBar } from './components/TopBar.js';
import type { Item, Library } from './lib/library.js';
import { addHistory, clearHistory, isFav, parse, removeItem, toggleFav } from './lib/library.js';
import { command, hasPlayer, loadLibrary, openUrl, saveLibrary } from './lib/native.js';
import type { PlayerState } from './lib/player.js';
import { EMPTY, HOME_URL, nextSleep, readState, toUrl, watchUrl } from './lib/player.js';

type Toast = { text: string; id: number };

export function App() {
  const [st, setSt] = useState<PlayerState>(EMPTY);
  const [lib, setLib] = useState<Library | null>(null); // null — hali yuklanmagan
  const [panel, setPanel] = useState<Tab | null>(null);
  const [toast, setToast] = useState<Toast | null>(null);
  const [native, setNative] = useState(true);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Saqlangan kutubxona (Android: SharedPreferences)
  useEffect(() => {
    loadLibrary((raw) => setLib(parse(raw)));
    setNative(hasPlayer());
  }, []);

  const apply = (next: Library) => {
    setLib(next);
    saveLibrary(JSON.stringify(next));
  };

  // Har yangi video tarixga tushadi. Kutubxona yuklanmaguncha yozmaymiz — aks holda
  // bo'sh ro'yxat saqlangan ma'lumotning ustiga yozilib ketadi.
  useEffect(() => {
    if (!lib || !st.videoId || !st.title) return;
    const next = addHistory(lib, { id: st.videoId, title: st.title, channel: st.channel });
    if (next !== lib) apply(next);
  }, [lib, st.videoId, st.title, st.channel]);

  const say = (text: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToast({ text, id: Date.now() });
    toastTimer.current = setTimeout(() => setToast(null), 2200);
  };

  const onSearch = (query: string) => {
    const url = toUrl(query);
    if (!url) return;
    openUrl(url);
    setPanel(null);
  };

  const onOpen = (item: Item) => {
    openUrl(watchUrl(item.id));
    setPanel(null);
  };

  const onFav = () => {
    if (!lib || !st.videoId) return;
    const was = isFav(lib, st.videoId);
    apply(toggleFav(lib, { id: st.videoId, title: st.title || 'YouTube', channel: st.channel }));
    say(was ? "Saqlanganlardan olib tashlandi" : 'Saqlandi ♥');
  };

  const onSleep = () => {
    const min = nextSleep(st.sleepLeft);
    command('sleep', min);
    say(min ? `Uxlash taymeri: ${min} daqiqadan keyin pauza` : "Uxlash taymeri o'chirildi");
  };

  return (
    <view className="root">
      <TopBar
        canGoBack={st.canGoBack}
        libOpen={panel !== null}
        onBack={() => command('back')}
        onHome={() => {
          openUrl(HOME_URL);
          setPanel(null);
        }}
        onSearch={onSearch}
        onLibrary={() => setPanel(panel ? null : 'fav')}
      />

      <view className="stage">
        <yt-webview
          className="web"
          blocked={panel !== null}
          bindstate={(e) => setSt(readState(e.detail))}
          bindback={() => setPanel(null)}
        />
        {native ? null : (
          <view className="nonative">
            <text className="nonative-title">YouTube faqat YT Lynx APK'da ochiladi</text>
            <text className="nonative-text">
              Lynx Go va brauzerda faqat interfeys ko'rinadi. YouTube oynasi va fonda ijro uchun
              GitHub → Releases → latest → yt-lynx.apk ni o'rnating.
            </text>
          </view>
        )}
        {panel && lib ? (
          <LibraryPanel
            lib={lib}
            tab={panel}
            currentId={st.videoId}
            onTab={setPanel}
            onClose={() => setPanel(null)}
            onOpen={onOpen}
            onRemove={(tab, id) => apply(removeItem(lib, tab, id))}
            onClear={() => apply(clearHistory(lib))}
          />
        ) : null}
        {toast ? (
          <view key={toast.id} className="toast" flatten={false}>
            <text className="toast-text">{toast.text}</text>
          </view>
        ) : null}
      </view>

      <PlayerBar
        st={st}
        fav={!!lib && !!st.videoId && isFav(lib, st.videoId)}
        onCommand={(action) => command(action)}
        onSkip={(sec) => command('skip', sec)}
        onFav={onFav}
        onSleep={onSleep}
      />
    </view>
  );
}
