/** Header decorations: perspective grid, striped sun, drifting shapes, a bust and a palm (inline SVG, no downloads). */
export function HeaderArt() {
  return (
    <div className="deco" aria-hidden="true">
      <div className="deco__sun" />
      <svg className="deco__palm" viewBox="0 0 120 160" fill="#3d2a5c">
        <path d="M58 160 C60 120 62 90 70 60 L74 61 C67 92 66 122 66 160 Z" />
        <path d="M71 58 C55 40 30 38 8 50 C30 44 50 48 66 62 Z" />
        <path d="M71 58 C62 34 44 18 20 14 C42 24 56 38 66 60 Z" />
        <path d="M72 58 C80 32 98 18 118 18 C100 28 86 40 76 62 Z" />
        <path d="M72 60 C92 46 110 48 120 62 C104 56 90 58 74 66 Z" />
        <path d="M70 60 C66 70 52 84 40 104 C56 86 66 76 74 64 Z" />
      </svg>
      <div className="deco__grid" />
      <svg className="deco__shape deco__shape--pyramid" viewBox="0 0 60 56" fill="none" stroke="#fff" strokeWidth="2">
        <path d="M30 2 L58 50 L2 50 Z" />
        <path d="M30 2 L38 54 L58 50 M30 2 L2 50 L38 54" />
      </svg>
      <div className="deco__shape deco__shape--checker" />
      <div className="deco__shape deco__shape--dot" />
      <svg className="deco__bust" viewBox="0 0 120 150" fill="#fff" stroke="#3d2a5c" strokeWidth="2">
        <path d="M60 10 C40 10 30 26 31 44 C32 56 36 64 38 70 C34 74 36 80 42 82 C44 90 48 96 52 98 L50 108 C30 112 14 122 10 150 L110 150 C106 122 90 112 70 108 L68 98 C76 92 80 82 82 72 C88 70 88 62 84 60 C88 50 90 40 88 30 C84 16 74 10 60 10 Z" />
        <path d="M44 30 C50 22 66 20 76 28 M40 44 C46 40 52 40 56 44 M66 44 C70 40 76 40 80 44" fill="none" />
        <path d="M58 52 L54 70 L62 72" fill="none" />
        <path d="M50 82 C56 86 64 86 70 82" fill="none" />
      </svg>
    </div>
  );
}
