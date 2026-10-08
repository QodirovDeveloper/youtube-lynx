import { useRef } from '@lynx-js/react';
import type { NodesRef } from '@lynx-js/types';

const noop = () => {};

export function TopBar(props: {
  canGoBack: boolean;
  libOpen: boolean;
  onBack: () => void;
  onHome: () => void;
  onSearch: (query: string) => void;
  onLibrary: () => void;
}) {
  const inputRef = useRef<NodesRef>(null);

  const submit = (value: string) => {
    props.onSearch(value);
    // klaviaturani yopamiz, natija ko'rinsin
    inputRef.current?.invoke({ method: 'blur' }).exec();
  };

  return (
    <view className="top">
      <view className={`tb${props.canGoBack ? '' : ' tb--off'}`} bindtap={props.canGoBack ? props.onBack : noop}>
        <text className="tb-text tb-text--back">‹</text>
      </view>
      <view className="logo" bindtap={props.onHome}>
        <view className="tri tri--logo" />
      </view>
      <view className="search">
        <input
          ref={inputRef}
          className="search-input"
          placeholder="Qidirish yoki havola"
          placeholder-color="#8c8c99"
          confirm-type="search"
          bindconfirm={(e) => submit(e.detail.value)}
        />
      </view>
      <view className={`tb${props.libOpen ? ' tb--on' : ''}`} bindtap={props.onLibrary}>
        <text className={`tb-text${props.libOpen ? ' tb-text--on' : ''}`}>☰</text>
      </view>
    </view>
  );
}
