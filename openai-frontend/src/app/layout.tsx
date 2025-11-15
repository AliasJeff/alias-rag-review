import "./globals.css";

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
      <body>{children}</body>
    </html>
  );
}
