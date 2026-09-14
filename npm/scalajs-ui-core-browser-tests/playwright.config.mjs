import { defineConfig } from '@playwright/test';

// Optional Windows fallback matching the editor harness: the installed Firefox
// speaks BiDi when the bundled build cannot start. CI keeps the bundled browser.
const firefoxChannel = process.env.EMBER_FIREFOX_CHANNEL;

export default defineConfig({
  testDir: './test',
  fullyParallel: true,
  workers: 3,
  outputDir: '../../target/core-browser-results',
  use: { baseURL: 'http://127.0.0.1:4187' },
  projects: ['chromium', 'firefox', 'webkit'].map(browserName => ({
    name: browserName, use: {
      browserName,
      ...(browserName === 'firefox' && firefoxChannel ? { channel: firefoxChannel } : {}),
    },
  })),
  webServer: {
    command: 'node server.mjs',
    url: 'http://127.0.0.1:4187',
    reuseExistingServer: false,
  },
});
