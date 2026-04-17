import { createContext, useContext } from "react";
import { AuthStore } from "./AuthStore";
import { ThemeStore } from "./ThemeStore";
import { ModStore } from "./ModStore";
import { InstanceStore } from "./InstanceStore";
import { SharingStore } from "./SharingStore";
import { NotificationStore } from "./NotificationStore";
import { InstallStore } from "./InstallStore";

export class RootStore {
  auth: AuthStore;
  theme: ThemeStore;
  mods: ModStore;
  instances: InstanceStore;
  sharing: SharingStore;
  notifications: NotificationStore;
  installs: InstallStore;

  constructor() {
    this.auth = new AuthStore();
    this.theme = new ThemeStore();
    this.mods = new ModStore();
    this.instances = new InstanceStore();
    this.sharing = new SharingStore();
    this.notifications = new NotificationStore();
    this.installs = new InstallStore(this);
  }
}

const rootStore = new RootStore();
const StoreContext = createContext(rootStore);

export const useStore = () => useContext(StoreContext);
export { StoreContext, rootStore };
