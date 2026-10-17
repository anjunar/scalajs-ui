import { test, expect } from '@playwright/test';

test('a window opened from another window stays in front', async ({ page }) => {
  await page.goto('/editor');
  await page.waitForFunction(() => window.fixtures);
  await page.evaluate(() => window.fixtures.mountWindows(document.querySelector('#root')));

  const first = page.locator('.ui-window').filter({ has: page.locator('.ui-window__title', { hasText: 'First' }) });
  await expect(first).toBeVisible();
  await first.getByRole('button', { name: 'Open Second' }).click();

  const second = page.locator('.ui-window').filter({ has: page.locator('.ui-window__title', { hasText: 'Second' }) });
  await expect(second).toBeVisible();
  const layer = window => window.evaluate(element => Number(getComputedStyle(element).zIndex));
  expect(await layer(second)).toBeGreaterThan(await layer(first));

  await second.getByRole('button', { name: 'Open Third' }).click();
  const third = page.locator('.ui-window').filter({ has: page.locator('.ui-window__title', { hasText: 'Third' }) });
  await expect(third).toBeVisible();
  expect(await layer(third)).toBeGreaterThan(await layer(second));
  expect(await layer(third)).toBeGreaterThan(await layer(first));
  expect(await third.evaluate(element => {
    const bounds = element.getBoundingClientRect();
    return element.contains(document.elementFromPoint(bounds.left + bounds.width - 40, bounds.top + bounds.height - 40));
  })).toBe(true);

  // Activating an older window still brings it forward for ordinary interaction.
  await first.locator('.ui-window__header').click({ position: { x: 12, y: 12 } });
  expect(await layer(first)).toBeGreaterThan(await layer(third));
});

test('a window opened with the keyboard stays in front', async ({ page }) => {
  await page.goto('/editor');
  await page.waitForFunction(() => window.fixtures);
  await page.evaluate(() => window.fixtures.mountWindows(document.querySelector('#root')));

  await page.getByRole('button', { name: 'Open Second' }).focus();
  await page.keyboard.press('Enter');
  const layers = await page.locator('.ui-window').evaluateAll(elements =>
    elements.map(element => Number(getComputedStyle(element).zIndex)));
  expect(layers).toHaveLength(2);
  expect(layers[1]).toBeGreaterThan(layers[0]);
});
