// Pleyer ikonkalari CSS shakllardan (uchburchak = border hiylasi, chiziq = view).
// Emoji ishlatmaymiz: Android'da ⏮ ⏭ rangli kvadrat bo'lib chiqadi.

export type IconName = 'play' | 'pause' | 'next' | 'prev';

export function Icon(props: { name: IconName; big?: boolean }) {
  const s = props.big ? ' icon--big' : '';
  switch (props.name) {
    case 'play':
      return (
        <view className={`icon${s}`}>
          <view className={`tri${s ? ' tri--big' : ''}`} />
        </view>
      );
    case 'pause':
      return (
        <view className={`icon icon--gap${s}`}>
          <view className={`stick${s ? ' stick--big' : ''}`} />
          <view className={`stick${s ? ' stick--big' : ''}`} />
        </view>
      );
    case 'next':
      return (
        <view className={`icon${s}`}>
          <view className="tri tri--small" />
          <view className="stick stick--thin" />
        </view>
      );
    case 'prev':
      return (
        <view className={`icon${s}`}>
          <view className="stick stick--thin" />
          <view className="tri tri--small tri--left" />
        </view>
      );
  }
}
