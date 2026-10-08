import type { PlayerState } from '../lib/player.js';
import { fmtTime, thumb } from '../lib/player.js';
import type { IconName } from './Icon.js';
import { Icon } from './Icon.js';

function Ctl(props: { icon: IconName; big?: boolean; onTap: () => void }) {
  return (
    <view className={`ctl${props.big ? ' ctl--big' : ''}`} bindtap={props.onTap}>
      <Icon name={props.icon} big={props.big} />
    </view>
  );
}

function Small(props: { label: string; on?: boolean; onTap: () => void }) {
  return (
    <view className={`small${props.on ? ' small--on' : ''}`} bindtap={props.onTap}>
      <text className={`small-text${props.on ? ' small-text--on' : ''}`}>{props.label}</text>
    </view>
  );
}

export function PlayerBar(props: {
  st: PlayerState;
  fav: boolean;
  onCommand: (action: 'toggle' | 'next' | 'prev') => void;
  onSkip: (sec: number) => void;
  onFav: () => void;
  onSleep: () => void;
}) {
  const { st } = props;

  // video tanlanmaguncha panel ko'rinmaydi — butun joy YouTube'ga
  if (!st.videoId) return null;

  const pct = st.duration > 0 ? Math.min(100, (st.position / st.duration) * 100) : 0;
  const time = st.duration > 0 ? `${fmtTime(st.position)} / ${fmtTime(st.duration)}` : fmtTime(st.position);
  const sleepMin = Math.ceil(st.sleepLeft / 60);

  return (
    <view className="bar">
      <view className="progress">
        <view className="progress-fill" style={{ width: `${pct}%` }} />
      </view>
      <view className="now">
        <image className="thumb" src={thumb(st.videoId)} mode="aspectFill" />
        <view className="meta">
          <text className="title" text-maxline="1">
            {st.title || 'YouTube'}
          </text>
          <text className="sub" text-maxline="1">
            {st.channel ? `${st.channel} · ${time}` : time}
          </text>
        </view>
      </view>
      <view className="controls">
        <Small label={props.fav ? '♥' : '♡'} on={props.fav} onTap={props.onFav} />
        <Small label="−10" onTap={() => props.onSkip(-10)} />
        <Ctl icon="prev" onTap={() => props.onCommand('prev')} />
        <Ctl icon={st.playing ? 'pause' : 'play'} big onTap={() => props.onCommand('toggle')} />
        <Ctl icon="next" onTap={() => props.onCommand('next')} />
        <Small label="+10" onTap={() => props.onSkip(10)} />
        <Small label={sleepMin > 0 ? `☾${sleepMin}` : '☾'} on={sleepMin > 0} onTap={props.onSleep} />
      </view>
    </view>
  );
}
