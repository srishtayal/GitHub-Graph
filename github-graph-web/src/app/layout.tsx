import "../styles/globals.css";
import "@xyflow/react/dist/style.css";
import "@fontsource-variable/manrope";
import "@fontsource-variable/jetbrains-mono";
import type { Metadata } from "next";
import { ReactNode } from "react";

export const metadata: Metadata = {
  title: "GitHub Graph · Repository Intelligence",
  description:
    "Explore repository structure, dependencies, impact, similarity and root causes through an interactive code graph."
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
