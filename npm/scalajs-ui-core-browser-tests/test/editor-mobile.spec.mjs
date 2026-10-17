import { test, expect } from '@playwright/test';

test.use({ viewport: { width: 390, height: 780 }, hasTouch: true });

// Installed Firefox via WebDriver BiDi currently exposes maxTouchPoints=0 and delivers no
// events at all for touchscreen.tap (even on a plain button). Exercise its narrow layout
// with real mouse input; Chromium, WebKit and the bundled touch-capable engines use touch.
function usesMouse() {
  return test.info().project.name === 'firefox' && process.env.EMBER_FIREFOX_CHANNEL === 'moz-firefox';
}

async function activate(locator) {
  if (usesMouse()) await locator.click();
  else await locator.tap();
}

test.beforeEach(() => {
  if (usesMouse()) {
    test.info().annotations.push({ type: 'input', description: 'Firefox BiDi: mouse; no touch emulation' });
  }
});

async function mount(page) {
  await page.goto('/editor');
  await page.waitForFunction(() => window.fixtures);
  await page.evaluate(() => window.fixtures.mount(document.querySelector('#root'), 'en'));
  await expect(page.locator('.scalajs-ui-editor__quickbar')).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
}

async function selectOriginal(page) {
  await page.getByRole('textbox', { name: 'body' }).evaluate(surface => {
    surface.focus();
    const text = document.createTreeWalker(surface.querySelector('p'), NodeFilter.SHOW_TEXT).nextNode();
    window.getSelection().setBaseAndExtent(text, 0, text, 8);
  });
}

const value = page => page.evaluate(() => window.fixtures.value());
const panel = page => page.locator('.scalajs-ui-editor__mobile-panel:popover-open');

test('six touch targets stay in one 52px row and menus do not move the document', async ({ page }) => {
  await mount(page);
  for (const [width, height] of [[320, 780], [390, 780], [640, 780], [390, 280]]) {
    await page.setViewportSize({ width, height });
    const bar = page.locator('.scalajs-ui-editor__quickbar');
    const boxes = await bar.locator(':scope > button').evaluateAll(buttons => buttons.map(button => {
      const r = button.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right };
    }));
    expect(boxes).toHaveLength(6);
    expect(new Set(boxes.map(b => b.y)).size).toBe(1);
    for (const box of boxes) {
      expect(box.width).toBeGreaterThanOrEqual(44);
      expect(box.height).toBeGreaterThanOrEqual(44);
      expect(box.right).toBeLessThanOrEqual(width);
    }
    expect((await bar.boundingBox()).height).toBe(52);
    const before = await page.getByRole('textbox', { name: 'body' }).boundingBox();
    await activate(page.getByRole('button', { name: 'Formatting', exact: true }));
    await expect(panel(page)).toBeVisible();
    expect((await page.getByRole('textbox', { name: 'body' }).boundingBox()).y).toBe(before.y);
    const popup = await panel(page).boundingBox();
    expect(popup.x).toBeGreaterThanOrEqual(0);
    expect(popup.x + popup.width).toBeLessThanOrEqual(width);
    expect(popup.y + popup.height).toBeLessThanOrEqual(height);
    await activate(page.getByRole('button', { name: 'Close tools' }));
  }
});

test('formatting keeps the selected text through the quickbar and popup, undo and redo', async ({ page }) => {
  await mount(page);
  await selectOriginal(page);
  await activate(page.getByRole('button', { name: 'Bold', exact: true }));
  await expect.poll(() => value(page)).toBe('**Original** text\n');
  await expect(page.getByRole('textbox', { name: 'body' })).toBeFocused();
  await activate(page.getByRole('button', { name: 'Formatting', exact: true }));
  await activate(panel(page).getByRole('button', { name: 'Inline code', exact: true }));
  await expect(panel(page)).toHaveCount(0);
  await expect.poll(() => value(page)).toContain('`Original`');
  await activate(page.getByRole('button', { name: 'Undo', exact: true }));
  await expect.poll(() => value(page)).toBe('**Original** text\n');
  await activate(page.getByRole('button', { name: 'More tools', exact: true }));
  await activate(panel(page).getByRole('button', { name: 'Redo', exact: true }));
  await expect.poll(() => value(page)).toContain('`Original`');
});

test('popup rows stay vertical and readable in every design and color scheme', async ({ page }) => {
  await mount(page);
  await selectOriginal(page);
  for (const design of ['atlas', 'ember', 'flora', 'terra']) {
    for (const scheme of ['light', 'dark']) {
      await page.evaluate(({ design, scheme }) => {
        document.documentElement.dataset.design = design;
        document.documentElement.dataset.colorScheme = scheme;
      }, { design, scheme });
      for (const menu of ['Formatting', 'Insert', 'More tools']) {
        await activate(page.getByRole('button', { name: menu, exact: true }));
        const popup = panel(page);
        const appearance = await popup.evaluate(element => {
          const editor = document.querySelector('.scalajs-ui-editor-host');
          const actions = element.querySelector('.scalajs-ui-editor__mobile-actions');
          const rows = [...actions.querySelectorAll('button')].map(button => {
            const r = button.getBoundingClientRect();
            return { x: r.x, y: r.y, width: r.width, height: r.height,
              label: button.getAttribute('aria-label'), text: button.textContent,
              icon: button.querySelector('[aria-hidden="true"]').textContent };
          });
          return { rows, background: getComputedStyle(element).backgroundColor,
            editorBackground: getComputedStyle(editor).backgroundColor,
            font: getComputedStyle(actions.querySelector('button')).fontFamily,
            appFont: getComputedStyle(document.documentElement).fontFamily,
            scrollWidth: actions.scrollWidth, width: actions.clientWidth };
        });
        expect(appearance.background).toBe(appearance.editorBackground);
        expect(appearance.font).toBe(appearance.appFont);
        expect(appearance.scrollWidth).toBe(appearance.width);
        for (const [index, row] of appearance.rows.entries()) {
          expect(row.height).toBeGreaterThanOrEqual(44);
          expect(row.text).toContain(row.label);
          expect(row.icon).not.toBe('');
          expect(row.x).toBe(appearance.rows[0].x);
          expect(row.width).toBe(appearance.rows[0].width);
          if (index) expect(row.y).toBeGreaterThanOrEqual(appearance.rows[index - 1].y + appearance.rows[index - 1].height);
        }
        if (design === 'ember' && menu === 'Formatting') {
          const screenshot = test.info().outputPath(`mobile-formatting-${scheme}.png`);
          await page.screenshot({ path: screenshot });
          await test.info().attach(`mobile-formatting-${scheme}`, {
            path: screenshot, contentType: 'image/png',
          });
        }
        await activate(popup.getByRole('button', { name: 'Close tools' }));
      }
    }
  }
});

test('link dialog applies to the selection retained while opening Insert', async ({ page }) => {
  await mount(page);
  await selectOriginal(page);
  await activate(page.getByRole('button', { name: 'Insert', exact: true }));
  await activate(panel(page).getByRole('button', { name: 'Link', exact: true }));
  await expect(panel(page)).toHaveCount(0);
  const dialog = page.locator('.scalajs-ui-editor-dialog');
  await dialog.getByLabel('Address', { exact: true }).fill('https://example.test/article');
  await dialog.getByRole('button', { name: 'Apply', exact: true }).click();
  await expect.poll(() => value(page)).toContain('[Original](https://example.test/article) text');
});

test('panels close with Escape, outside input and Tab and preserve keyboard focus', async ({ page }) => {
  await mount(page);
  await selectOriginal(page);
  const format = page.getByRole('button', { name: 'Formatting', exact: true });
  await format.focus();
  await page.keyboard.press('Enter');
  await expect(panel(page).getByRole('button', { name: 'Inline code', exact: true })).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await expect(panel(page).getByRole('button', { name: 'Paragraph', exact: true })).toBeFocused();
  await page.keyboard.press('ArrowUp');
  await expect(panel(page).getByRole('button', { name: 'Inline code', exact: true })).toBeFocused();
  await page.keyboard.press('End');
  await expect(panel(page).locator('.scalajs-ui-editor__mobile-actions button:enabled').last()).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await expect(panel(page).getByRole('button', { name: 'Inline code', exact: true })).toBeFocused();
  await page.keyboard.press('ArrowUp');
  await expect(panel(page).locator('.scalajs-ui-editor__mobile-actions button:enabled').last()).toBeFocused();
  await page.keyboard.press('Home');
  await expect(panel(page).getByRole('button', { name: 'Inline code', exact: true })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(panel(page)).toHaveCount(0);
  await expect(format).toBeFocused();
  await expect(format).toHaveAttribute('aria-expanded', 'false');
  await activate(format);
  await expect(panel(page)).toBeVisible();
  if (usesMouse()) await page.mouse.click(12, 750);
  else await page.touchscreen.tap(12, 750);
  await expect(panel(page)).toHaveCount(0);
  await expect(format).toHaveAttribute('aria-expanded', 'false');
  await format.focus();
  await page.keyboard.press('Enter');
  await page.keyboard.press('Tab');
  await expect(panel(page)).toHaveCount(0);
});

test('table commands appear only inside a table', async ({ page }) => {
  await mount(page);
  await selectOriginal(page);
  const more = page.getByRole('button', { name: 'More tools', exact: true });
  await activate(more);
  await expect(panel(page).locator('[data-command^="table-"]')).toHaveCount(0);
  await activate(page.getByRole('button', { name: 'Close tools' }));
  await activate(page.getByRole('button', { name: 'Insert', exact: true }));
  await activate(panel(page).getByRole('button', { name: 'Insert table', exact: true }));
  const surface = page.getByRole('textbox', { name: 'body' });
  await expect(surface.locator('table')).toBeVisible();
  await activate(surface.locator('td,th').first());
  await activate(more);
  await expect(panel(page).getByRole('button', { name: 'Insert row below', exact: true })).toBeEnabled();
  await activate(panel(page).getByRole('button', { name: 'Insert row below', exact: true }));
  await expect(surface.locator('tr')).toHaveCount(4);
  await activate(surface.locator('p').last());
  await activate(more);
  await expect(panel(page).locator('[data-command^="table-"]')).toHaveCount(0);
});

test('resize, locale changes, readonly and disposal do not replace the editor session', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await mount(page);
  await selectOriginal(page);
  await activate(page.getByRole('button', { name: 'Bold', exact: true }));
  const before = await value(page);
  await activate(page.getByRole('button', { name: 'Formatting', exact: true }));
  await page.setViewportSize({ width: 900, height: 780 });
  await expect(panel(page)).toHaveCount(0);
  await expect(page.locator('.ember-toolbar--ribbon')).toBeVisible();
  await expect(page.locator('.scalajs-ui-editor__quickbar')).toHaveCount(0);
  expect(await value(page)).toBe(before);
  await page.setViewportSize({ width: 390, height: 780 });
  await page.getByRole('button', { name: 'Undo', exact: true }).focus();
  await page.evaluate(() => window.fixtures.setLocale('de'));
  await expect(page.getByRole('button', { name: 'Rückgängig', exact: true })).toBeFocused();
  await expect(page.getByRole('button', { name: 'Formatierung', exact: true })).toBeVisible();
  await activate(page.getByRole('button', { name: 'Rückgängig', exact: true }));
  await expect.poll(() => value(page)).toBe('Original text\n');
  expect(await page.evaluate(() => window.fixtures.sessions())).toBe(1);
  await activate(page.getByRole('button', { name: 'Formatierung', exact: true }));
  await page.evaluate(() => window.fixtures.setEditable(false));
  await expect(panel(page)).toHaveCount(0);
  await expect(page.locator('.scalajs-ui-editor__quickbar')).toBeHidden();
  await page.evaluate(() => window.fixtures.dispose());
  await page.setViewportSize({ width: 900, height: 780 });
  await expect(page.locator('#root')).toBeEmpty();
  expect(errors).toEqual([]);
});
