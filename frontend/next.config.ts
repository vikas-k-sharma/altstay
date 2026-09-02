import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Emits .next/standalone — a self-contained server.js plus only the
  // node_modules it actually traced as reachable. Without this the runtime
  // image needs the full ~500MB dependency tree; with it the image is a few
  // hundred MB smaller and starts faster, which is what makes an Azure
  // Container Apps scale-to-zero cold start tolerable. See docs/deploy-azure.md.
  output: "standalone",
};

export default nextConfig;
