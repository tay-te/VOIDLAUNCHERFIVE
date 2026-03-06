import { createContext, useContext } from "react";
import { AuthStore } from "./AuthStore";
import { ThemeStore } from "./ThemeStore";
import { ModStore } from "./ModStore";
import { InstanceStore } from "./InstanceStore";

export class RootStore {
  auth = new AuthStore();
  theme = new ThemeStore();
  mods = new ModStore();
  instances = new InstanceStore();
}

const rootStore = new RootStore();
const StoreContext = createContext(rootStore);

export const useStore = () => useContext(StoreContext);
export { StoreContext, rootStore };
