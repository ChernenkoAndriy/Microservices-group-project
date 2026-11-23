import { pastelGradient } from '../lib/format';

interface Props {
  seed: number | string;
  url?: string;
  label?: string;
  className?: string;
}

/** Album / track cover: the image when there is one, else a pastel gradient with the first letters. */
export function Cover({ seed, url, label, className }: Props) {
  return (
    <div className={`cover ${className ?? ''}`} style={url ? undefined : { background: pastelGradient(seed) }}>
      {url ? (
        <img src={url} alt="" loading="lazy" />
      ) : (
        <span className="cover__glyph" aria-hidden="true">
          {(label ?? '').slice(0, 14)}
        </span>
      )}
    </div>
  );
}
