import "./globals.css";
import { RootLayoutClient } from "./RootLayoutClient";

export const metadata = {
  title: "AI RAG Code Review",
  description: "A modern AI conversation & code review application",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN">
      <body>
        <RootLayoutClient>{children}</RootLayoutClient>
      </body>
    </html>
  );
}
