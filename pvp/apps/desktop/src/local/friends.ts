/**
 * The friends list.
 *
 * §16.2: there is no friends backend yet, so this is the frame's own roster — the same
 * five names `Launcher-Friends.png` draws, three of them online. It lives here rather
 * than inside `screens/Friends` because the Play dock prints the online count too
 * ("3 ONLINE", in the frame's floating card), and two copies of a fixture is how they
 * drift apart.
 *
 * When the backend lands this becomes a store; nothing else about either call site
 * changes.
 */

export interface Friend {
  name: string;
  status: string;
  action: 'Join' | 'Invite' | 'Message';
  online: boolean;
}

export const FRIENDS: readonly Friend[] = [
  { name: 'marrow', status: 'Bedwars · Hypixel · 2h', action: 'Join', online: true },
  { name: 'pilot_ash', status: 'Sword duels · Minemen · 40m', action: 'Join', online: true },
  { name: 'nine', status: 'In lobby · Hypixel', action: 'Invite', online: true },
  { name: 'doorframe', status: 'Last seen 4 hours ago', action: 'Message', online: false },
  { name: 'kestrel', status: 'Last seen yesterday', action: 'Message', online: false },
];

/** How many are online — what the Play dock's readout counts. */
export const onlineCount = (): number => FRIENDS.filter((f) => f.online).length;
