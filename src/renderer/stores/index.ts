import { createContext, useContext } from "react";
import { AuthStore } from "./AuthStore";
import { ThemeStore } from "./ThemeStore";
import { ModStore } from "./ModStore";
import { InstanceStore } from "./InstanceStore";
import { SharingStore } from "./SharingStore";
import { NotificationStore } from "./NotificationStore";

export class RootStore {
  auth = new AuthStore();
  theme = new ThemeStore();
  mods = new ModStore();
  instances = new InstanceStore();
  sharing = new SharingStore();
  notifications = new NotificationStore();
}

const rootStore = new RootStore();
const StoreContext = createContext(rootStore);

export const useStore = () => useContext(StoreContext);
export { StoreContext, rootStore };
