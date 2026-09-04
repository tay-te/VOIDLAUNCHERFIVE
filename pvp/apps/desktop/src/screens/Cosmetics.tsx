/**
 * Cosmetics — `244:217`. Presentational, per the brief: there is no backend.
 *
 * §16.1 leaves the render Mixin and asset pipeline to its own doc, so nothing here
 * writes anything or calls a command. The cape set, glow colours, states and copy are
 * the frame's, verbatim, so the screen is a faithful placeholder rather than a
 * lorem-ipsum one — when the pipeline lands, the data shape is already visible.
 *
 * `CosmeticCard` is `@void/ui`'s. The character **stage** is not: it is the one region
 * of the design that is a 3D-ish render rather than a component, and it exists only in
 * this frame.
 */

import { Button, CosmeticCard, FilterTabs, Panel } from '@void/ui';
import { useState } from 'react';

const TABS = ['Capes', 'Hats', 'Wings', 'Emotes', 'Bundles'] as const;
type Tab = (typeof TABS)[number];

interface Cape {
  id: string;
  name: string;
  glow: string;
  swatch: [string, string];
  state: string;
  tone: 'equipped' | 'owned' | 'price';
  isNew?: boolean;
}

const CAPES: Cape[] = [
  { id: 'void-trail', name: 'Void Trail', glow: 'rgba(115,89,242,0.45)', swatch: ['#7359f2', '#4a35a8'], state: 'Equipped', tone: 'equipped' },
  { id: 'ember', name: 'Ember', glow: 'rgba(250,140,51,0.45)', swatch: ['#fa8c33', '#c2410c'], state: 'Owned', tone: 'owned' },
  { id: 'frost', name: 'Frost', glow: 'rgba(191,242,255,0.45)', swatch: ['#bff2ff', '#3aa8dd'], state: '1,200 coins', tone: 'price' },
  { id: 'midnight', name: 'Midnight', glow: 'rgba(64,77,128,0.45)', swatch: ['#404d80', '#1d2340'], state: 'Owned', tone: 'owned' },
  { id: 'aurora', name: 'Aurora', glow: 'rgba(77,242,178,0.45)', swatch: ['#4df2b2', '#1e8f8f'], state: '900 coins', tone: 'price', isNew: true },
  { id: 'solar', name: 'Solar', glow: 'rgba(255,217,77,0.45)', swatch: ['#ffd94d', '#e08a12'], state: '1,500 coins', tone: 'price' },
];

export function CosmeticsScreen() {
  const [tab, setTab] = useState<Tab>('Capes');
  const [selected, setSelected] = useState('void-trail');
  const equipped = CAPES.find((c) => c.state === 'Equipped') ?? CAPES[0]!;

  return (
    <Panel
      title="Cosmetics"
      headerRight={
        <>
          <FilterTabs
            label="Cosmetic kind"
            tabs={TABS.map((id) => ({ id, label: id }))}
            value={tab}
            onChange={(id) => setTab(id as Tab)}
          />
          <span className="v-spacer" />
          <span className="cosmetics__counter">12 owned &nbsp;·&nbsp; 3 new this week</span>
        </>
      }
    >
      <div className="stage">
        <span className="stage__glow" style={{ background: equipped.glow }} />
        {/* TODO(art): `246:16` is a rendered player skin. This is a blocky stand-in at
            the frame's box — head, shoulders, legs, and the equipped cape hanging
            behind — until the cosmetics pipeline can hand over a real render (§16.1). */}
        <span className="stage__skin" aria-hidden="true">
          <span
            className="stage__cape"
            style={{
              background: `linear-gradient(180deg, ${equipped.swatch[0]}, ${equipped.swatch[1]})`,
            }}
          />
          <span className="stage__head" />
          <span className="stage__torso">
            <span className="stage__arm" />
            <span className="stage__body" />
            <span className="stage__arm" />
          </span>
          <span className="stage__legs">
            <span className="stage__leg" />
            <span className="stage__leg" />
          </span>
        </span>
        <span className="stage__pill">
          <span className="stage__pill-dot" style={{ background: equipped.swatch[0] }} />
          Equipped &nbsp;·&nbsp; {equipped.name}
        </span>
      </div>

      <div className="cosmetics">
        <div className="cosmetics__grid">
          {CAPES.map((cape) => (
            <CosmeticCard
              key={cape.id}
              name={cape.name}
              state={cape.state}
              stateTone={cape.tone}
              color={`linear-gradient(180deg, ${cape.swatch[0]}, ${cape.swatch[1]})`}
              glow={cape.glow}
              isNew={cape.isNew}
              selected={cape.id === selected}
              onSelect={() => setSelected(cape.id)}
            />
          ))}
        </div>

        <div className="cosmetics__footer">
          <Button variant="raised" icon="check" disabled>
            Equipped
          </Button>
          <Button variant="accent" icon="eye" disabled title="Needs the in-game client">
            Preview in lobby
          </Button>
          <span className="cosmetics__note">Cosmetics show to everyone on VOID</span>
        </div>
      </div>
    </Panel>
  );
}
