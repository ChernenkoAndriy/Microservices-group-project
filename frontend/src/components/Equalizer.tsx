export function Equalizer({ paused }: { paused: boolean }) {
  return (
    <span className={`eq ${paused ? 'eq--paused' : ''}`} aria-hidden="true" data-testid="equalizer">
      <span />
      <span />
      <span />
    </span>
  );
}
