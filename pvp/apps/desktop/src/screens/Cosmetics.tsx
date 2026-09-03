/**
 * Cosmetics — `244:217`. Presentational, per the brief: there is no backend.
 *
 * §16.1 leaves the render Mixin and asset pipeline to its own doc, so nothing here
 * writes anything or calls a command. The cape set, glow colours, states and copy are
 * the frame's, verbatim, so the screen is a faithful placeholder rather than a
 * lorem-ipsum one — when the pipeline lands, the data shape is already visible.
 */

import { useState } from 'react';

import { Button, FilterTabs, Panel, Tag } from '../components';
import { CheckIcon, EyeIcon } from '../components/icons';

const TABS = ['Capes', 'Hats', 'Wings', 'Emotes', 'Bundles'] as const;
type Tab = (typeof TABS)[number];

interface Cape {
  id: string;
  name: string;
  glow: string;
  swatch: [string, string];
  state: string;
  tone: 'accent' | 'ok' | 'muted';
  isNew?: boolean;
}

const CAPES: Cape[] = [
  { id: 'void-trail', name: 'Void Trail', glow: 'rgba(115,89,242,0.45)', swatch: ['#7359f2', '#4a35a8'], state: 'Equipped', tone: 'accent' },
  { id: 'ember', name: 'Ember', glow: 'rgba(250,140,51,0.45)', swatch: ['#fa8c33', '#c2410c'], state: 'Owned', tone: 'ok' },
  { id: 'frost', name: 'Frost', glow: 'rgba(191,242,255,0.45)', swatch: ['#bff2ff', '#3aa8dd'], state: '1,200 coins', tone: 'muted' },
  { id: 'midnight', name: 'Midnight', glow: 'rgba(64,77,128,0.45)', swatch: ['#404d80', '#1d2340'], state: 'Owned', tone: 'ok' },
  { id: 'aurora', name: 'Aurora', glow: 'rgba(77,242,178,0.45)', swatch: ['#4df2b2', '#1e8f8f'], state: '900 coins', tone: 'muted', isNew: true },
  { id: 'solar', name: 'Solar', glow: 'rgba(255,217,77,0.45)', swatch: ['#ffd94d', '#e08a12'], state: '1,500 coins', tone: 'muted' },
];

export function CosmeticsScreen() {
  const [tab, setTab] = useState<Tab>('Capes');
  const [selected, setSelected] = useState('void-trail');
  const equipped = CAPES.find((c) => c.state === 'Equipped') ?? CAPES[0]!;

  return (
    <Panel
      title="Cosmetics"
      controls={
        <>
          <FilterTabs tabs={TABS} value={tab} onChange={setTab} />
          <span className="panel__counter">12 owned &nbsp;·&nbsp; 3 new this week</span>
        </>
      }
    >
      <div className="cosmetics">
        <div className="stage">
          <span className="stage__glow" style={{ background: equipped.glow }} />
          <span className="stage__skin" aria-hidden="true">
            <span className="stage__head" />
            <span className="stage__body" />
            <span className="stage__cape" style={{ background: `linear-gradient(180deg, ${equipped.swatch[0]}, ${equipped.swatch[1]})` }} />
          </span>
          <span className="stage__pill">
            <span className="stage__pill-dot" style={{ background: equipped.swatch[0] }} />
            Equipped · {equipped.name}
          </span>
        </div>

        <div className="cosmetics__grid">
          {CAPES.map((cape) => (
            <button
              key={cape.id}
              type="button"
              className={`cape${cape.id === selected ? ' is-selected' : ''}`}
              onClick={() => setSelected(cape.id)}
            >
              <span className="cape__preview">
                {cape.isNew ? (
                  <span className="cape__new">
                    <Tag tone="accent">NEW</Tag>
                  </span>
                ) : null}
                <span className="cape__hanger" />
                <span
                  className="cape__swatch"
                  style={{
                    background: `linear-gradient(180deg, ${cape.swatch[0]}, ${cape.swatch[1]})`,
                    boxShadow: `0 10px 24px -4px ${cape.glow}`,
                  }}
                />
              </span>
              <span className="cape__title">{cape.name}</span>
              <span className={`cape__state cape__state--${cape.tone}`}>{cape.state}</span>
            </button>
          ))}
        </div>

        <div className="cosmetics__footer">
          <Button variant="raised" icon={CheckIcon} disabled>
            Equipped
          </Button>
          <Button variant="accent" icon={EyeIcon} disabled title="Needs the in-game client">
            Preview in lobby
          </Button>
          <span className="cosmetics__note">Cosmetics show to everyone on VOID</span>
        </div>
      </div>
    </Panel>
  );
}
