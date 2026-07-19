import { expect, test } from '@playwright/test';

test('all pages load and navigate without runtime errors', async ({ page }) => {
  const pageErrors: string[] = [];

  page.on('pageerror', (error) => {
    pageErrors.push(`pageerror: ${error.message}`);
  });

  page.on('console', (message) => {
    if (message.type() === 'error') {
      pageErrors.push(`console: ${message.text()}`);
    }
  });

  await page.goto('/connect');
  await page.evaluate(async () => {
    await window.__appDebug?.clearAllData();
  });
  await page.reload();
  await page.evaluate(async () => {
    await window.__fakeCloudDebug?.issueAppSyncCode('E2E-ROUTES-CODE-67890');
  });

  await expect(page.getByRole('heading', { name: 'ברוכים הבאים ל-MedMinder' })).toBeVisible();

  await page.getByLabel('Sync Code').fill('E2E-ROUTES-CODE-67890');
  await page.getByTestId('connect-button').click();
  await expect(page).toHaveURL(/.*\/intakes/);
  await expect(page.getByRole('heading', { name: /נטילות/ })).toBeVisible();

  await page.getByRole('link', { name: 'תרופות' }).click();
  await expect(page).toHaveURL(/.*\/medications/);
  await expect(page.getByRole('heading', { name: /התרופות שלי/ })).toBeVisible();

  await page.getByRole('link', { name: 'היסטוריה' }).click();
  await expect(page).toHaveURL(/.*\/history/);
  await expect(page.getByRole('heading', { level: 1, name: /היסטוריה/ })).toBeVisible();

  await page.getByRole('link', { name: 'הגדרות' }).click();
  await expect(page).toHaveURL(/.*\/settings/);
  await expect(page.getByRole('heading', { name: /מצב סנכרון/ })).toBeVisible();

  await page.getByRole('button', { name: /יומן פנימי/ }).click();
  await expect(page).toHaveURL(/.*\/hidden-log/);
  await expect(page.getByRole('heading', { name: /יומן פנימי/ })).toBeVisible();

  expect(pageErrors, pageErrors.join('\n')).toEqual([]);
});
