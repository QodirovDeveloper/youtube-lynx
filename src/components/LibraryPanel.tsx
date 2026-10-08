import type { Item, Library } from '../lib/library.js';
import { thumb } from '../lib/player.js';

export type Tab = keyof Library; // 'fav' | 'hist'

const EMPTY_TEXT: Record<Tab, string> = {
  fav: "Hali hech narsa saqlanmagan. Pastdagi ♡ tugmasi videoni shu yerga qo'shadi.",
  hist: "Ko'rilgan videolar shu yerda paydo bo'ladi.",
};

// WebView ustida ochiladigan panel. flatten={false}: haqiqiy Android view bo'lsin,
// aks holda u WebView'ning ostida chizilib qolishi mumkin.
export function LibraryPanel(props: {
  lib: Library;
  tab: Tab;
  currentId: string;
  onTab: (tab: Tab) => void;
  onClose: () => void;
  onOpen: (item: Item) => void;
  onRemove: (tab: Tab, id: string) => void;
  onClear: () => void;
}) {
  const { lib, tab } = props;
  const items = lib[tab];

  const tabView = (t: Tab, label: string) => (
    <view className={`tab${tab === t ? ' tab--on' : ''}`} bindtap={() => props.onTab(t)}>
      <text className={`tab-text${tab === t ? ' tab-text--on' : ''}`}>{`${label} · ${lib[t].length}`}</text>
    </view>
  );

  return (
    <view className="sheet" flatten={false}>
      <view className="sheet-head">
        <view className="tabs">
          {tabView('fav', 'Saqlangan')}
          {tabView('hist', 'Tarix')}
        </view>
        <view className="tb" bindtap={props.onClose}>
          <text className="tb-text">×</text>
        </view>
      </view>

      {items.length === 0 ? (
        <view className="empty">
          <text className="empty-text">{EMPTY_TEXT[tab]}</text>
        </view>
      ) : (
        <scroll-view className="list" scroll-orientation="vertical">
          {items.map((it) => (
            <view
              key={it.id}
              className={`row${it.id === props.currentId ? ' row--on' : ''}`}
              bindtap={() => props.onOpen(it)}
            >
              <image className="row-thumb" src={thumb(it.id)} mode="aspectFill" />
              <view className="row-meta">
                <text className="row-title" text-maxline="2">
                  {it.title}
                </text>
                <text className="row-sub" text-maxline="1">
                  {it.channel}
                </text>
              </view>
              <view className="row-x" catchtap={() => props.onRemove(tab, it.id)}>
                <text className="row-x-text">×</text>
              </view>
            </view>
          ))}
          {tab === 'hist' ? (
            <view className="clear" bindtap={props.onClear}>
              <text className="clear-text">Tarixni tozalash</text>
            </view>
          ) : null}
        </scroll-view>
      )}
    </view>
  );
}
