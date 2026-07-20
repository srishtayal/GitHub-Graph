"use client";

import { FormEvent, useEffect, useState } from "react";
import { LogIn, UserRound } from "lucide-react";
import { getCurrentUser, login, register } from "@/lib/api-client";
import type { AuthUser } from "@/lib/types";

export function AccountPanel() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [open, setOpen] = useState(false);
  const [registerMode, setRegisterMode] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    getCurrentUser().then(setUser).catch(() => undefined);
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      const response = registerMode
        ? await register({
            displayName: String(form.get("displayName") ?? ""),
            email: String(form.get("email") ?? ""),
            password: String(form.get("password") ?? "")
          })
        : await login({ email: String(form.get("email") ?? ""), password: String(form.get("password") ?? "") });
      window.localStorage.setItem("github-graph-access-token", response.accessToken);
      setUser(response.user);
      setMessage("Signed in. Your saved repositories are now available.");
      setOpen(false);
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "Unable to authenticate.");
    }
  }

  if (user && !open) {
    return <button className="account-trigger" onClick={() => setOpen(true)}><UserRound size={16} /> {user.displayName}</button>;
  }

  return (
    <div className="account-panel">
      <button className="account-trigger" onClick={() => setOpen((current) => !current)}><LogIn size={16} /> {user ? user.displayName : "Sign in"}</button>
      {open ? (
        <form className="account-form" onSubmit={submit}>
          <strong>{registerMode ? "Create account" : "Sign in"}</strong>
          {registerMode ? <input name="displayName" placeholder="Display name" required maxLength={120} /> : null}
          <input name="email" type="email" placeholder="Email" required />
          <input name="password" type="password" placeholder="Password (10+ characters)" required minLength={10} />
          <button type="submit">{registerMode ? "Create account" : "Sign in"}</button>
          <button type="button" className="text-button" onClick={() => setRegisterMode((current) => !current)}>
            {registerMode ? "Already have an account?" : "Create an account"}
          </button>
        </form>
      ) : null}
      {message ? <small className="account-message">{message}</small> : null}
    </div>
  );
}
