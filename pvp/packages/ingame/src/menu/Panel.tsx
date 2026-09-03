/**
 * The overlay Panel shell shared by Mods, Mod settings, Loadouts and Party.
 * Geometry: 960 × 600, radius 24, header band at y 19, footer hint at y ~565.
 */

import type { ReactNode } from 'react';
import { CloseButton } from '@/ui';

export interface PanelProps {
  title: string;
  /** Rendered between the title and the right-hand controls. */
  header?: ReactNode;
  /** Rendered flush right in the header band, before the close button. */
  controls?: ReactNode;
  /** The line under the title on the Loadouts / Mod-settings frames. */
  subtitle?: ReactNode;
  /** Footer hint copy, verbatim from the frame. */
  footer?: string;
  onClose: () => void;
  children: ReactNode;
  /** Replaces the title with a custom node (the `← Mods` back button row). */
  leading?: ReactNode;
}

export function Panel({
  title,
  header,
  controls,
  subtitle,
  footer,
  onClose,
  children,
  leading,
}: PanelProps) {
  return (
    <div className="panel-wrap">
      <div className="panel void-anim-in" role="dialog" aria-label={title}>
        <div className="panel__header">
          {leading}
          <div className="panel__title">{title}</div>
          {header}
          <div className="panel__header-gap" />
          {controls}
          <CloseButton onClick={onClose} />
        </div>
        {subtitle && <div className="panel__subtitle">{subtitle}</div>}
        {children}
        {footer && <div className="panel__footer vd-footer-hint">{footer}</div>}
      </div>
    </div>
  );
}
