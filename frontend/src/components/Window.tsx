import type { ReactNode } from 'react';

/** A 90s desktop window frame with a purple title bar and fake controls. */
export function Window({ title, children, className }: { title: string; children: ReactNode; className?: string }) {
  return (
    <section className={`window ${className ?? ''}`} aria-label={title}>
      <div className="window__title">
        <span>{title}</span>
        <span className="window__ctl" aria-hidden="true">
          _
        </span>
        <span className="window__ctl" aria-hidden="true">
          □
        </span>
        <span className="window__ctl" aria-hidden="true">
          ×
        </span>
      </div>
      <div className="window__body">{children}</div>
    </section>
  );
}
