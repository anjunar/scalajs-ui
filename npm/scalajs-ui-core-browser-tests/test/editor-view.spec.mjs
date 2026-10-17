import { test, expect } from '@playwright/test';

for (const width of [390, 1280]) {
  test.describe(`toolbar view selector at ${width}px`, () => {
    test.use({ viewport: { width, height: 900 } });

    test('switches visual, Markdown and readonly views without losing edits', async ({ page }) => {
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto('/editor');
      await page.waitForFunction(() => window.fixtures);
      await page.evaluate(() => window.fixtures.mount(document.querySelector('#root'), 'en'));
      const frame = page.locator('.scalajs-ui-editor__toolbar-frame');
      const view = frame.getByRole('combobox', { name: 'Editor view', exact: true });
      await expect(view).toBeVisible();
      await expect(page.locator('.scalajs-ui-editor__markdown-actions')).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Markdown', exact: true })).toHaveCount(0);
      await expect(page.getByRole('link', { name: 'Readonly', exact: true })).toHaveCount(0);
      await expect(page.getByRole('textbox', { name: 'body', exact: true })).toBeVisible();

      await view.selectOption('markdown');
      const source = page.locator('textarea[name="body"]');
      await expect(source).toBeVisible();
      await source.fill('## Changed in Markdown\n\n**Keep these edits.**\n');
      const edited = await page.evaluate(() => window.fixtures.value());
      await view.selectOption('visual');
      await expect(page.getByRole('textbox', { name: 'body', exact: true })).toContainText('Changed in Markdown');
      expect(await page.evaluate(() => window.fixtures.value())).toBe(edited);
      const sessions = await page.evaluate(() => window.fixtures.sessions());

      await view.selectOption('readonly');
      await expect(view).toBeVisible();
      await expect(page.getByRole('textbox', { name: 'body', exact: true })).toHaveAttribute('aria-readonly', 'true');
      await view.selectOption('visual');
      await expect(page.getByRole('textbox', { name: 'body', exact: true })).toHaveAttribute('aria-readonly', 'false');
      expect(await page.evaluate(() => window.fixtures.value())).toBe(edited);
      expect(await page.evaluate(() => window.fixtures.sessions())).toBe(sessions);

      await page.evaluate(() => window.fixtures.setLocale('de'));
      await expect(frame.getByRole('combobox', { name: 'Ansicht', exact: true })).toBeVisible();
      await expect(frame.locator('select option[value="readonly"]')).toHaveText('Lesemodus');
      const bounds = await frame.boundingBox();
      expect(bounds.x).toBeGreaterThanOrEqual(0);
      expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
      expect(errors).toEqual([]);
    });
  });
}

test('hydrates the SSR toolbar and keeps all view transitions usable', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/editor');
  await page.waitForFunction(() => window.fixtures);
  await page.evaluate(() => window.fixtures.mountHydrated(document.querySelector('#root'), 'de'));
  const view = page.getByRole('combobox', { name: 'Ansicht', exact: true });
  await expect(view).toBeVisible();
  await view.selectOption('readonly');
  await expect(view).toBeEnabled();
  await view.selectOption('markdown');
  await expect(page.locator('textarea[name="body"]')).toHaveValue('Original text');
  await view.selectOption('visual');
  await expect(page.getByRole('textbox', { name: 'body', exact: true })).toContainText('Original text');
  expect(errors).toEqual([]);
});
