import { test, expect } from '@playwright/test';

async function mount(page, locale = 'en') {
  await page.goto('/editor');
  await page.waitForFunction(() => window.fixtures);
  await page.evaluate(locale => window.fixtures.mount(document.querySelector('#root'), locale), locale);
  await expect(page.getByRole('textbox', { name: 'body' })).toBeVisible();
}

test('editor defaults to English without an I18n provider', async ({ page }) => {
  await mount(page, '');
  await expect(page.getByRole('toolbar', { name: 'Edit text' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Undo', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Bold', exact: true })).toHaveAttribute('title', 'Bold');
  const surface = page.getByRole('textbox', { name: 'body' });
  await surface.click();
  await page.getByRole('button', { name: 'Edit image', exact: true }).click();
  const dialog = page.locator('.scalajs-ui-editor-dialog');
  await expect(dialog.getByLabel('Image address', { exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Apply', exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Cancel', exact: true })).toBeVisible();
  await dialog.getByLabel('Image address', { exact: true }).fill('https://example.test/private.png');
  await dialog.getByRole('button', { name: 'Apply', exact: true }).click();
  await expect(dialog.getByRole('alert')).toHaveText('The image address is not allowed.');
});

test('changing language preserves the session, document, focus and undo history', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await mount(page);
  const surface = page.getByRole('textbox', { name: 'body' });
  await surface.click();
  await surface.press('ControlOrMeta+End');
  await page.keyboard.insertText(' added');
  await expect.poll(() => page.evaluate(() => window.fixtures.value())).toContain('added');
  const before = await page.evaluate(() => window.fixtures.value());
  const undo = page.getByRole('button', { name: 'Undo', exact: true });
  await undo.focus();
  await page.evaluate(() => window.fixtures.setLocale('de'));
  const translatedUndo = page.getByRole('button', { name: 'Rückgängig', exact: true });
  await expect(translatedUndo).toBeFocused();
  await expect(translatedUndo).toBeEnabled();
  await expect(page.getByRole('toolbar', { name: 'Text bearbeiten' })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Verlauf', exact: true })).toBeVisible();
  expect(await page.evaluate(() => window.fixtures.sessions())).toBe(1);
  expect(await page.evaluate(() => window.fixtures.value())).toBe(before);
  await translatedUndo.click();
  await expect.poll(() => page.evaluate(() => window.fixtures.value())).not.toContain('added');
  await page.getByRole('button', { name: 'Wiederholen', exact: true }).click();
  await expect.poll(() => page.evaluate(() => window.fixtures.value())).toBe(before);
  await page.evaluate(() => {
    window.fixtures.dispose();
    window.fixtures.setLocale('en');
  });
  await expect(page.locator('#root')).toBeEmpty();
  expect(errors).toEqual([]);
});

test('an open dialog and pending upload status follow the owning editor locale', async ({ page }) => {
  await mount(page);
  const surface = page.getByRole('textbox', { name: 'body' });
  await surface.click();
  await surface.press('ControlOrMeta+Home');
  await surface.press('Shift+End');
  await page.getByRole('button', { name: 'Link', exact: true }).click();
  const dialog = page.locator('.scalajs-ui-editor-dialog');
  await dialog.getByLabel('Address', { exact: true }).fill('https://example.test/article');
  await dialog.getByLabel('Title', { exact: true }).fill('Unsaved title');
  await page.evaluate(() => {
    window.fixtures.pending(2);
    window.fixtures.setLocale('de');
  });
  await expect(dialog.getByLabel('Adresse', { exact: true })).toHaveValue('https://example.test/article');
  await expect(dialog.getByLabel('Titel', { exact: true })).toHaveValue('Unsaved title');
  await expect(dialog.getByRole('button', { name: 'Übernehmen', exact: true })).toBeVisible();
  await expect(page.getByText('Link bearbeiten', { exact: true })).toBeVisible();
  await expect(page.locator('.scalajs-ui-editor__media-status')).toHaveText('2 Bilder werden hochgeladen');
  await dialog.getByRole('button', { name: 'Übernehmen', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => window.fixtures.value())).toContain('https://example.test/article');
});
