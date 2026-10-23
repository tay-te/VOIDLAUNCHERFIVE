"""The Gallows Knight's fight clips (stage 1.5), written from key poses: basic, readable poses and tells, no polish
(the user's call, 2026-10-22). Every clip starts and ends on the idle's first pose; a key names only the bones that
leave it, the rest hold the ready pose. Writes src/main/motion/bestiary/gallows_knight/<clip>.motion.json.

    python tools/knight/knight_clips.py

Conventions (vanilla model space, degrees added to the rig's rest): limbs x < 0 swing forward and up (-90 points an
arm straight ahead, -170 overhead); torso x > 0 bends forward; y turns (root y > 0 turns the knight to its left); the
right arm's z > 0 lifts it out to the side. The chain hangs from the right hand, so with the arm pointing ahead and
chain_1 at 0 the chain and hook point ahead too.

Tells (BESTIARY §4.2, boss column): white 5+, orange 10+, red and purple 14+, the Hanging (ultimate) 20+ ticks from
the tell to the first ACTIVE tick; heavy, red and purple moves leave 8+ ticks of recovery. CreatureDataTest checks it.
"""
import json
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'motion', 'bestiary', 'gallows_knight')

BONES = ['root', 'pelvis', 'spine', 'chest', 'neck', 'head', 'r_thigh', 'r_shin', 'r_foot', 'l_thigh', 'l_shin', 'l_foot', 'skirt_f', 'skirt_b',
         'r_upper_arm', 'r_forearm', 'r_hand', 'chain_1', 'chain_2', 'chain_3', 'hook', 'l_upper_arm', 'l_forearm', 'l_hand', 'cape', 'cape_lower',
         'cage']

# The idle's first pose (idle.motion.json at 0.0): the ready stance every clip leaves from and returns to.
READY = {
    'pelvis': [4, 0, 0], 'spine': [8, 0, 0], 'chest': [4, -3, 0], 'neck': [-6, 0, 0], 'head': [-10, 6, -2],
    'r_thigh': [-24, 0, 4], 'r_shin': [30, 0, 0], 'r_foot': [-10, 0, -4], 'l_thigh': [-6, -10, -6], 'l_shin': [16, 0, 0], 'l_foot': [-14, 10, 6],
    'skirt_f': [-14, 0, 0], 'skirt_b': [6, 0, 0], 'r_upper_arm': [-12, 0, 8], 'r_forearm': [-38, 0, 0], 'r_hand': [10, 0, 0],
    'chain_1': [44, 0, -4], 'chain_2': [-3, 0, 3], 'chain_3': [-4, 0, 2], 'hook': [-6, 8, 0], 'l_upper_arm': [-8, 0, -8], 'l_forearm': [-30, 0, 0],
    'l_hand': [0, 0, 6], 'cape': [10, 0, 0], 'cape_lower': [6, 0, 0], 'cage': [-4, 0, 0],
}
READY_ROOT_POS = [0, -1.1, 0]
READY_ROOT_ROT = [0, 0, 0]


def write(name, length, keys, markers, cues=(), hits=None, hurts=None, windows=None, locators=None, damage=0, blend_in=3, blend_out=5,
          scale_keys=None):
    """keys: [(time, {bone: [x, y, z], 'root_pos': [...], 'root_rot': [...]}, interp)]; first and last default to READY."""
    bones = {}
    for b in BONES:
        rot, pos = [], []
        for t, pose, interp in keys:
            if b == 'root':
                rot.append([t, pose.get('root_rot', READY_ROOT_ROT), interp])
                pos.append([t, pose.get('root_pos', READY_ROOT_POS), interp])
            else:
                rot.append([t, pose.get(b, READY[b]), interp])
        entry = {'rotation': rot}
        if b == 'root':
            entry['position'] = pos
        if scale_keys and b in scale_keys:
            entry['scale'] = scale_keys[b]
        bones[b] = entry
    clip = {'format': 1, 'rig': 'bestiary:gallows_knight', 'ground': 'plant', 'length': length, 'loop': 'none', 'blend_in': blend_in,
            'blend_out': blend_out, 'bones': bones, 'cues': [list(c) for c in cues], 'markers': [list(m) for m in markers]}
    if locators:
        clip['locators'] = locators
    clip['hits'] = hits if hits is not None else []
    if hurts:
        clip['hurts'] = hurts
    if windows:
        clip['windows'] = windows
    clip['damage'] = damage
    clip['checks'] = ['ground']
    path = os.path.join(OUT, name + '.motion.json')
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(dump(clip))
    print('wrote', os.path.normpath(path))


def dump(clip):
    """Readable JSON: one bone a line, like the hand-written clips."""
    lines = ['{']
    keys = list(clip.keys())
    for i, k in enumerate(keys):
        comma = ',' if i < len(keys) - 1 else ''
        if k == 'bones':
            lines.append('  "bones": {')
            bk = list(clip['bones'].keys())
            for j, b in enumerate(bk):
                c2 = ',' if j < len(bk) - 1 else ''
                lines.append('    ' + json.dumps(b) + ': ' + json.dumps(clip['bones'][b], separators=(', ', ': ')) + c2)
            lines.append('  }' + comma)
        else:
            lines.append('  ' + json.dumps(k) + ': ' + json.dumps(clip[k], separators=(', ', ': ')) + comma)
    lines.append('}')
    return '\n'.join(lines) + '\n'


def P(**bones):
    """A pose: bone=[x, y, z] (root_pos, root_rot for the root)."""
    return bones


C, L = 'catmullrom', 'linear'

# ------------------------------------------------------------------ shared poses
# The cut: hook wound up high on the right, then a diagonal cut across the front.
CUT_WIND = P(root_rot=[0, 16, 0], spine=[6, 10, 0], chest=[0, 22, 0], head=[-14, -12, 0], r_upper_arm=[-130, 0, 50], r_forearm=[-40, 0, 0],
             chain_1=[70, 0, 40], chain_2=[20, 0, 10], chain_3=[20, 0, 10], hook=[30, 0, 0], l_upper_arm=[-30, 0, -20], l_forearm=[-50, 0, 0],
             l_thigh=[-12, -10, -6])
CUT_HOLD = dict(CUT_WIND, r_upper_arm=[-142, 0, 56], chain_1=[82, 0, 46], chest=[-2, 26, 0])
CUT_STRIKE = P(root_rot=[0, -6, 0], spine=[12, -6, 0], chest=[10, -10, 0], head=[-12, 4, 0], r_upper_arm=[-84, 0, 10], r_forearm=[-8, 0, 0],
               r_hand=[0, 0, 0], chain_1=[6, 0, 0], chain_2=[-8, 0, -4], chain_3=[-10, 0, -6], hook=[-30, 0, 0], l_upper_arm=[-20, 0, -24],
               l_forearm=[-40, 0, 0], l_thigh=[-30, -10, -8], l_shin=[36, 0, 0], r_thigh=[-14, 0, 4])
CUT_FOLLOW = dict(CUT_STRIKE, root_rot=[0, -18, 0], chest=[12, -24, 0], spine=[12, -10, 0], r_upper_arm=[-42, 0, -30], r_forearm=[-20, 0, 0],
                  chain_1=[-30, 0, -20], chain_2=[-20, 0, -10], chain_3=[-20, 0, -10], hook=[-40, 0, 0])
CUT_RECOVER = P(root_rot=[0, -6, 0], chest=[8, -10, 0], r_upper_arm=[-22, 0, -8], r_forearm=[-30, 0, 0], chain_1=[30, 0, -10], l_thigh=[-14, -10, -6])
# The return cut: from across the body (left), back out to the right.
BACK_WIND = P(root_rot=[0, -20, 0], spine=[8, -12, 0], chest=[6, -30, 0], head=[-12, 16, 0], r_upper_arm=[-112, 0, -45], r_forearm=[-30, 0, 0],
              chain_1=[50, 0, -50], chain_2=[10, 0, -10], chain_3=[10, 0, -10], hook=[20, 0, -10], l_upper_arm=[-20, 0, -30], l_forearm=[-50, 0, 0],
              l_thigh=[-26, -10, -8], l_shin=[30, 0, 0])
BACK_STRIKE = P(root_rot=[0, 6, 0], spine=[10, 6, 0], chest=[10, 10, 0], r_upper_arm=[-78, 0, 30], r_forearm=[-8, 0, 0], chain_1=[0, 0, 40],
                chain_2=[-6, 0, 10], chain_3=[-6, 0, 10], hook=[-30, 0, 20], l_upper_arm=[-20, 0, -24], l_forearm=[-40, 0, 0], l_thigh=[-26, -10, -8],
                l_shin=[30, 0, 0])
BACK_FOLLOW = dict(BACK_STRIKE, root_rot=[0, 16, 0], chest=[10, 24, 0], r_upper_arm=[-50, 0, 56], chain_1=[-20, 0, 50], hook=[-40, 0, 20])
# Overhead: the hook swung up behind, then down in front.
RAISE = P(root_pos=[0, -1.1, 1], chest=[-14, 6, 0], spine=[-6, 0, 0], head=[-24, 0, 0], r_upper_arm=[-172, 0, 14], r_forearm=[-22, 0, 0],
          chain_1=[100, 0, 0], chain_2=[10, 0, 0], chain_3=[10, 0, 0], hook=[30, 0, 0], l_upper_arm=[-60, 0, -30], l_forearm=[-60, 0, 0],
          r_thigh=[-14, 0, 4], l_thigh=[-20, -10, -6], l_shin=[24, 0, 0])
CHOP = P(chest=[24, 0, 0], spine=[20, 0, 0], head=[0, 0, 0], r_upper_arm=[-70, 0, 4], r_forearm=[-6, 0, 0], chain_1=[0, 0, 0], chain_2=[-6, 0, 0],
         chain_3=[-6, 0, 0], hook=[-40, 0, 0], l_upper_arm=[-30, 0, -24], l_forearm=[-40, 0, 0], l_thigh=[-34, -10, -8], l_shin=[40, 0, 0])
CHOP_END = dict(CHOP, r_upper_arm=[-34, 0, 4], chain_1=[-40, 0, 0], chain_2=[-14, 0, 0], chain_3=[-14, 0, 0], chest=[28, 0, 0])

# Cues: the tell's sound by colour (BESTIARY §4.1): a grunt, a deep wind-up note, a ring, a rising tone.
GRUNT = 'minecraft:entity.iron_golem.step'
DEEP = 'minecraft:block.chain.step'
RING = 'minecraft:block.anvil.land'
RISE = 'minecraft:block.amethyst_block.resonate'
SWISH = 'minecraft:entity.player.attack.sweep'
HEAVY = 'minecraft:entity.player.attack.strong'


def main():
    write('hook_cut', 1.2, [
        (0.0, {}, C), (0.3, CUT_WIND, C), (0.45, CUT_HOLD, C), (0.55, CUT_STRIKE, L), (0.65, CUT_FOLLOW, C), (0.9, CUT_RECOVER, C), (1.2, {}, C)],
        markers=[(0.1, 'tell', 'white'), (0.5, 'windup_end'), (0.65, 'active_end')],
        cues=[(0.1, 'sound', GRUNT), (0.5, 'sound', SWISH)], hits=['hook', 'chain'], damage=10)

    write('two_cut', 1.8, [
        (0.0, {}, C), (0.3, CUT_WIND, C), (0.45, CUT_HOLD, C), (0.55, CUT_STRIKE, L), (0.65, CUT_FOLLOW, C), (0.85, BACK_WIND, C),
        (1.05, BACK_STRIKE, L), (1.15, BACK_FOLLOW, C), (1.45, CUT_RECOVER, C), (1.8, {}, C)],
        markers=[(0.1, 'tell', 'white'), (0.5, 'windup_end'), (0.65, 'active_end'), (1.0, 'windup_end'), (1.15, 'active_end')],
        cues=[(0.1, 'sound', GRUNT), (0.5, 'sound', SWISH), (1.0, 'sound', SWISH)], hits=['hook', 'chain'], damage=10)

    # Phase 2: three cuts, the third an overhead chop after a held beat (the one delay).
    write('three_cut', 2.3, [
        (0.0, {}, C), (0.25, CUT_WIND, C), (0.4, CUT_HOLD, C), (0.5, CUT_STRIKE, L), (0.6, CUT_FOLLOW, C), (0.75, BACK_WIND, C),
        (0.92, BACK_STRIKE, L), (1.0, BACK_FOLLOW, C), (1.15, RAISE, C), (1.4, dict(RAISE, r_upper_arm=[-180, 0, 14], chest=[-18, 6, 0]), C),
        (1.55, CHOP, L), (1.65, CHOP_END, C), (1.95, CUT_RECOVER, C), (2.3, {}, C)],
        markers=[(0.1, 'tell', 'white'), (0.45, 'windup_end'), (0.6, 'active_end'), (0.85, 'windup_end'), (1.0, 'active_end'),
                 (1.5, 'windup_end'), (1.65, 'active_end')],
        cues=[(0.1, 'sound', GRUNT), (0.45, 'sound', SWISH), (0.85, 'sound', SWISH), (1.5, 'sound', HEAVY)], hits=['hook', 'chain'], damage=10)

    # The slam (orange, punish 14+): overhead, the hook bites into the ground and the knight hauls it free (its chest open).
    impact = P(root_pos=[0, -4, 0], pelvis=[12, 0, 0], spine=[24, 0, 0], chest=[34, 0, 0], head=[10, 0, 0], r_upper_arm=[-50, 0, 0],
               r_forearm=[-4, 0, 0], chain_1=[-58, 0, 0], chain_2=[-10, 0, 0], chain_3=[-10, 0, 0], hook=[-40, 0, 0], l_upper_arm=[-40, 0, -20],
               l_forearm=[-40, 0, 0], r_thigh=[-10, 0, 4], r_shin=[50, 0, 0], l_thigh=[-46, -10, -6], l_shin=[64, 0, 0], skirt_f=[-30, 0, 0])
    tug = P(root_pos=[0, -2.5, 1.5], pelvis=[0, 0, 0], spine=[0, 0, 0], chest=[-12, -6, 0], head=[-20, 0, 0], r_upper_arm=[-46, 0, 10],
            r_forearm=[-60, 0, 0], l_upper_arm=[-56, 0, -10], l_forearm=[-70, 0, 0], chain_1=[-84, 0, 0], chain_2=[-2, 0, 0], chain_3=[-2, 0, 0],
            hook=[-30, 0, 0], r_thigh=[-40, 0, 4], r_shin=[40, 0, 0], l_thigh=[4, -10, -6], l_shin=[30, 0, 0])
    tug_ease = dict(tug, chest=[8, -4, 0], spine=[10, 0, 0], r_upper_arm=[-60, 0, 8], root_pos=[0, -3, 0.5])
    write('slam', 2.8, [
        (0.0, {}, C), (0.2, P(r_upper_arm=[-90, 0, 14], r_forearm=[-30, 0, 0], chain_1=[70, 0, 0], chest=[-4, 3, 0]), C), (0.4, RAISE, C), (0.72, dict(RAISE, r_upper_arm=[-184, 0, 14], chest=[-18, 6, 0], root_pos=[0, -0.6, 1.5]), C),
        (0.86, dict(CHOP, root_pos=[0, -2, 0]), L), (0.95, impact, L), (1.2, impact, C), (1.5, tug, C), (1.8, tug_ease, C), (2.1, tug, C),
        (2.4, dict(tug, chain_1=[40, 0, 0], chest=[-16, -6, 0]), C), (2.8, {}, C)],
        markers=[(0.1, 'tell', 'orange'), (0.8, 'windup_end'), (0.95, 'active_end'), (0.95, 'impact')],
        cues=[(0.1, 'sound', DEEP), (0.8, 'sound', HEAVY), (0.95, 'sound', 'minecraft:block.anvil.land'), (1.5, 'sound', 'minecraft:block.chain.break'),
              (2.4, 'sound', 'minecraft:block.chain.fall')],
        hits=['hook', 'chain'], hurts={'chest': [22, 46]}, windows={'punish': [20, 48]}, damage=18, blend_out=6)

    # The shoulder charge (red, unblockable): crouch with the right shoulder leading, then three blocks of charge.
    crouch = P(root_pos=[0, -3, 0], root_rot=[0, 20, 0], pelvis=[16, 0, 0], spine=[18, -20, 0], chest=[16, -20, 0], head=[-24, 12, 0],
               r_upper_arm=[10, 0, 20], r_forearm=[-90, 0, 0], l_upper_arm=[-40, 0, -30], l_forearm=[-80, 0, 0], chain_1=[60, 0, 0],
               r_thigh=[-50, 0, 4], r_shin=[70, 0, 0], l_thigh=[10, -10, -6], l_shin=[44, 0, 0])
    charge_a = dict(crouch, root_pos=[0, -2.5, 0], chest=[24, -30, 0], spine=[24, -24, 0], r_thigh=[-60, 0, 4], r_shin=[30, 0, 0],
                    l_thigh=[30, -10, -6], l_shin=[20, 0, 0], chain_1=[80, 0, 0])
    charge_b = dict(charge_a, r_thigh=[20, 0, 4], r_shin=[30, 0, 0], l_thigh=[-50, -10, -6], l_shin=[40, 0, 0])
    stumble = P(root_pos=[0, -3, 0], root_rot=[0, 8, 0], pelvis=[10, 0, 0], spine=[26, -8, 0], chest=[30, -10, 0], head=[10, 0, 0],
                r_upper_arm=[-20, 0, 30], r_forearm=[-40, 0, 0], l_upper_arm=[-30, 0, -40], l_forearm=[-40, 0, 0], r_thigh=[-40, 0, 4],
                r_shin=[60, 0, 0], l_thigh=[0, -10, -6], l_shin=[30, 0, 0])
    write('shoulder_charge', 2.2, [
        (0.0, {}, C), (0.4, crouch, C), (0.8, dict(crouch, root_pos=[0, -4, 0.5], chest=[20, -26, 0]), C), (0.95, charge_a, L), (1.08, charge_b, L),
        (1.2, dict(charge_a, chest=[10, -30, 0]), C), (1.5, stumble, C), (1.9, CUT_RECOVER, C), (2.2, {}, C)],
        markers=[(0.1, 'tell', 'red'), (0.9, 'windup_end'), (1.2, 'active_end')],
        cues=[(0.1, 'sound', RING), (0.9, 'sound', 'minecraft:entity.ravager.step'), (1.2, 'sound', 'minecraft:entity.iron_golem.attack')],
        locators={'shoulder': {'bone': 'chest', 'a': [-6, -8, -4], 'b': [2, -4, -4], 'radius': 0.7}}, hits=['shoulder'], damage=18)

    # The kick (white, guard crusher: a block costs twice the stamina).
    chamber = P(chest=[-6, 0, 0], spine=[-4, 0, 0], head=[-14, 0, 0], r_thigh=[-72, 0, 4], r_shin=[84, 0, 0], r_foot=[-6, 0, -4],
                l_upper_arm=[-20, 0, -30], r_upper_arm=[-10, 0, 30], r_forearm=[-50, 0, 0], l_shin=[22, 0, 0])
    kick = dict(chamber, root_pos=[0, -1.1, 1], chest=[-12, 0, 0], r_thigh=[-86, 0, 4], r_shin=[0, 0, 0], r_foot=[-12, 0, -4])
    write('kick', 1.3, [
        (0.0, {}, C), (0.3, chamber, C), (0.42, dict(chamber, r_thigh=[-80, 0, 4], r_shin=[92, 0, 0]), C), (0.5, kick, L), (0.6, kick, C),
        (0.85, dict(chamber, r_thigh=[-40, 0, 4], r_shin=[60, 0, 0]), C), (1.3, {}, C)],
        markers=[(0.1, 'tell', 'white'), (0.45, 'windup_end'), (0.6, 'active_end'), (0.45, 'guard_crush')],
        cues=[(0.1, 'sound', GRUNT), (0.5, 'sound', 'minecraft:entity.iron_golem.attack')],
        locators={'foot': {'bone': 'r_foot', 'a': [0, 1, -4.5], 'b': [0, 1, 0], 'radius': 0.45}}, hits=['foot'], damage=8)

    # The chain lash (purple, a grab): the hook whirled overhead, thrown at the target, then hauled in (the chest open).
    whirl_a = P(chest=[-6, 14, 0], head=[-20, -6, 0], r_upper_arm=[-150, 0, 40], r_forearm=[-20, 0, 0], chain_1=[90, 0, 60], chain_2=[10, 0, 10],
                chain_3=[10, 0, 10], l_upper_arm=[-30, 0, -24], l_forearm=[-50, 0, 0])
    whirl_b = dict(whirl_a, r_upper_arm=[-162, 0, 20], chain_1=[90, 0, -40], chain_2=[10, 0, -10], chain_3=[10, 0, -10])
    throw = P(chest=[14, -10, 0], spine=[12, -6, 0], r_upper_arm=[-92, 0, 0], r_forearm=[-4, 0, 0], r_hand=[0, 0, 0], chain_1=[-8, 0, 0],
              chain_2=[0, 0, 0], chain_3=[0, 0, 0], hook=[-20, 0, 0], l_upper_arm=[-30, 0, -24], l_forearm=[-40, 0, 0], l_thigh=[-32, -10, -8],
              l_shin=[36, 0, 0])
    haul = P(root_pos=[0, -2, 1.5], chest=[-14, 8, 0], spine=[-6, 0, 0], head=[-16, 0, 0], r_upper_arm=[-30, 0, 10], r_forearm=[-90, 0, 0],
             l_upper_arm=[-50, 0, -20], l_forearm=[-80, 0, 0], chain_1=[-70, 0, 0], r_thigh=[10, 0, 4], r_shin=[30, 0, 0], l_thigh=[-40, -10, -6],
             l_shin=[40, 0, 0])
    write('chain_lash', 2.0, [
        (0.0, {}, C), (0.35, whirl_a, C), (0.55, whirl_b, C), (0.75, whirl_a, C), (0.9, throw, L), (1.0, dict(throw, r_upper_arm=[-82, 0, -4]), C),
        (1.25, haul, C), (1.55, dict(haul, chest=[-18, 10, 0], r_upper_arm=[-10, 0, 10]), C), (2.0, {}, C)],
        markers=[(0.1, 'tell', 'purple'), (0.9, 'windup_end'), (1.0, 'active_end'), (0.9, 'release')],
        cues=[(0.1, 'sound', RISE), (0.35, 'sound', 'minecraft:block.chain.step'), (0.9, 'sound', SWISH), (1.25, 'sound', 'minecraft:block.chain.break')],
        hits=[], hurts={'chest': [22, 34]}, windows={'pull': [18, 30]}, damage=6)

    # The Hanging (red ultimate, a 24-tick tell): the hook flung over the gallows, the chain hauled down hard: whoever stands
    # on the mound is struck. The chest is open while it hauls.
    up = P(chest=[-20, 0, 0], spine=[-8, 0, 0], head=[-34, 0, 0], r_upper_arm=[-172, 0, 10], r_forearm=[-10, 0, 0], chain_1=[10, 0, 0],
           l_upper_arm=[-60, 0, -30], l_forearm=[-40, 0, 0])
    grip = dict(up, l_upper_arm=[-162, 0, -10], l_forearm=[-20, 0, 0], r_upper_arm=[-164, 0, 10], chest=[-16, 0, 0])
    haul_a = dict(grip, root_pos=[0, -2, 0], chest=[-6, 0, 0], r_upper_arm=[-110, 0, 10], l_upper_arm=[-110, 0, -10], r_forearm=[-60, 0, 0],
                  l_forearm=[-60, 0, 0], head=[-20, 0, 0])
    hard = P(root_pos=[0, -3.5, 0], pelvis=[10, 0, 0], spine=[16, 0, 0], chest=[20, 0, 0], head=[4, 0, 0], r_upper_arm=[-30, 0, 10],
             l_upper_arm=[-30, 0, -10], r_forearm=[-80, 0, 0], l_forearm=[-80, 0, 0], chain_1=[-20, 0, 0], r_thigh=[-40, 0, 4], r_shin=[50, 0, 0],
             l_thigh=[-36, -10, -6], l_shin=[50, 0, 0])
    write('the_hanging', 3.2, [
        (0.0, {}, C), (0.18, P(r_upper_arm=[-92, 0, 10], r_forearm=[-30, 0, 0], chest=[-8, 0, 0], head=[-20, 0, 0]), C), (0.35, up, C), (0.6, grip, C), (0.9, haul_a, C), (1.1, dict(grip, root_pos=[0, -1.5, 0]), C), (1.3, hard, L), (1.5, hard, C),
        (2.0, dict(hard, root_pos=[0, -2, 0], chest=[8, 0, 0], spine=[10, 0, 0]), C), (2.6, CUT_RECOVER, C), (3.2, {}, C)],
        markers=[(0.1, 'tell', 'red'), (1.3, 'windup_end'), (1.5, 'active_end'), (1.3, 'drop')],
        cues=[(0.1, 'sound', RING), (0.35, 'sound', 'minecraft:block.chain.step'), (0.9, 'sound', 'minecraft:block.chain.place'),
              (1.3, 'sound', 'minecraft:block.anvil.land')],
        hits=[], hurts={'chest': [8, 26]}, damage=26)

    # The transition: it roars, reaches back, tears the gibbet cage off its back and casts it down (no hits).
    roar = P(root_pos=[0, -1.6, 0], chest=[-20, 0, 0], spine=[-6, 0, 0], head=[-30, 0, 0], neck=[-12, 0, 0], r_upper_arm=[-20, 0, 70],
             l_upper_arm=[-20, 0, -70], r_forearm=[-20, 0, 0], l_forearm=[-20, 0, 0])
    reach = P(chest=[16, 0, 0], spine=[10, 0, 0], head=[10, 0, 0], r_upper_arm=[-196, 0, 22], l_upper_arm=[-196, 0, -22], r_forearm=[-60, 0, 0],
              l_forearm=[-60, 0, 0])
    pull = dict(reach, chest=[30, 0, 0], spine=[18, 0, 0], r_upper_arm=[-120, 0, 30], l_upper_arm=[-120, 0, -30], cage=[-60, 0, 0])
    cast = P(chest=[10, 30, 0], spine=[8, 20, 0], root_rot=[0, 20, 0], r_upper_arm=[-40, 0, 50], l_upper_arm=[-40, 0, -50], r_forearm=[-20, 0, 0],
             l_forearm=[-20, 0, 0], cage=[-60, 0, 0])
    write('tear_cage', 2.6, [
        (0.0, {}, C), (0.4, roar, C), (0.8, dict(roar, chest=[-22, 0, 0]), C),
        (0.9, P(chest=[-4, 0, 0], r_upper_arm=[-110, 0, 50], l_upper_arm=[-110, 0, -50], r_forearm=[-40, 0, 0], l_forearm=[-40, 0, 0]), C), (1.0, reach, C), (1.3, pull, C), (1.45, cast, L),
        (1.9, P(chest=[-10, 0, 0], cage=[-60, 0, 0]), C), (2.6, P(cage=[-60, 0, 0]), C)],
        markers=[(1.4, 'tear')],
        cues=[(0.4, 'sound', 'minecraft:entity.ravager.roar'), (1.3, 'sound', 'minecraft:block.chain.break'), (1.45, 'sound', 'minecraft:block.anvil.fall')],
        scale_keys={'cage': [[0.0, [0, 0, 0], L], [1.4, [0, 0, 0], L], [1.45, [-1, -1, -1], L], [2.6, [-1, -1, -1], L]]}, blend_in=4, blend_out=8)

    # Broken (the Break: 60 ticks to punish): down on one knee, the chain slack, head bowed.
    kneel = P(root_pos=[0, -7, 0], pelvis=[18, 0, 0], spine=[28, 0, 0], chest=[20, 0, 0], head=[20, 0, 0], r_thigh=[-84, 0, 4], r_shin=[84, 0, 0],
              r_foot=[0, 0, -4], l_thigh=[24, -10, -6], l_shin=[110, 0, 0], l_foot=[-30, 10, 6], r_upper_arm=[10, 0, 20], r_forearm=[-10, 0, 0],
              chain_1=[10, 0, 0], l_upper_arm=[-30, 0, -20], l_forearm=[-60, 0, 0], skirt_f=[-40, 0, 0])
    write('broken', 3.0, [
        (0.0, {}, C), (0.25, kneel, C), (1.2, dict(kneel, chest=[24, 0, 4], head=[26, 0, 0]), C), (2.4, kneel, C),
        (2.7, dict(CUT_RECOVER, root_pos=[0, -3, 0]), C), (3.0, {}, C)],
        markers=[(0.05, 'stagger')], cues=[(0.0, 'sound', 'minecraft:entity.iron_golem.damage'), (0.25, 'sound', 'minecraft:block.chain.fall')],
        windows={'punish': [2, 58]}, blend_in=1, blend_out=6)


if __name__ == '__main__':
    main()
