import { createContext, useContext, useState } from "react";
import { api } from "../api";

const AuthContext = createContext(null);

/** Učitava korisnika iz localStorage pri pokretanju aplikacije. */
function loadStoredUser() {
  const raw = localStorage.getItem("user");
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadStoredUser);

  function persist(authResponse) {
    const stored = {
      userId: authResponse.userId,
      username: authResponse.username,
      role: authResponse.role,
    };
    localStorage.setItem("token", authResponse.token);
    localStorage.setItem("user", JSON.stringify(stored));
    setUser(stored);
    return stored;
  }

  async function login(credentials) {
    const response = await api.login(credentials);
    return persist(response);
  }

  async function register(data) {
    const response = await api.register(data);
    return persist(response);
  }

  function logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    setUser(null);
  }

  const value = {
    user,
    login,
    register,
    logout,
    isLoggedIn: user !== null,
    isOrganizer: user?.role === "ORGANIZER",
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** Skraćenica koju ekrani koriste umjesto useContext(AuthContext). */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth se mora koristiti unutar AuthProvider komponente");
  }
  return context;
}