import { expect, test } from '@playwright/test';

test('can create a manual dose event and see it in history', async ({ page }) => {
  await page.goto('/connect');
  await page.evaluate(async () => {
    await window.__appDebug?.clearAllData();
  });
  await page.reload();
  await page.evaluate(async () => {
    await window.__fakeCloudDebug?.issueAppSyncCode('E2E-SYNC-CODE-DOSE-EVENT');
  });

  await page.getByLabel('Sync Code').fill('E2E-SYNC-CODE-DOSE-EVENT');
  await page.getByTestId('connect-button').click();
  await expect(page).toHaveURL(/.*\/intakes/);
  await page.getByRole('link', { name: 'תרופות' }).click();
  await expect(page).toHaveURL(/.*\/medications/);
  await page.getByRole('button', { name: /חדשה|הוספת תרופה ראשונה/ }).first().click();

  const medicationName = `אקמול-${Date.now()}`;
  await page.getByLabel('שם תרופה').fill(medicationName);
  await page.getByTestId('save-medication').click();

  const card = page.locator('[data-med-id]').filter({ hasText: medicationName }).first();
  await expect(card).toBeVisible();

  await card.getByRole('button', { name: new RegExp(`רישום נטילה של ${medicationName}`) }).click();
  await expect(page.getByRole('status')).toContainText('נרשמה נטילה');

  await page.getByRole('link', { name: 'היסטוריה' }).click();
  await expect(page).toHaveURL(/.*\/history/);
  await expect(page.locator('.history-event-row')).toHaveCount(1);
  await expect(page.locator('.history-event-row')).toContainText('קבוע');
  await expect(page.getByRole('heading', { name: medicationName })).toBeVisible();
});
