import { expect, test } from '@playwright/test';

test('connects, creates medication, syncs, and applies cloud tombstone', async ({ page }) => {
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
    await window.__fakeCloudDebug?.issueAppSyncCode('E2E-SYNC-CODE-12345');
  });

  await page.getByLabel('Sync Code').fill('E2E-SYNC-CODE-12345');
  await page.getByTestId('connect-button').click();

  await expect(page).toHaveURL(/.*\/intakes/);
  await page.getByRole('link', { name: 'תרופות' }).click();
  await expect(page).toHaveURL(/.*\/medications/);
  await page.getByRole('button', { name: /חדשה|הוספת תרופה ראשונה/ }).first().click();

  const medicationName = `איבופרופן-${Date.now()}`;
  await page.getByLabel('שם תרופה').fill(medicationName);
  await page.getByLabel('מינון').fill('1');
  await page.getByLabel('יחידה').fill('כדור');
  await page.getByTestId('save-medication').click();

  const card = page.locator('[data-med-id]').filter({ hasText: medicationName }).first();
  await expect(card).toContainText(medicationName);
  const medicationId = await card.getAttribute('data-med-id');
  expect(medicationId).toBeTruthy();

  await page.getByRole('link', { name: 'הגדרות' }).click();
  const syncNowButton = page.getByTestId('sync-now-button');
  await syncNowButton.click();
  await expect(syncNowButton).toHaveText(/סנכרון עכשיו/);

  const datasetId = await page.evaluate(() => window.__fakeCloudDebug?.getActiveDatasetId() ?? null);
  expect(datasetId).toBeTruthy();

  await page.evaluate(({ recordId }) => {
    const activeDatasetId = window.__fakeCloudDebug?.getActiveDatasetId();
    if (!activeDatasetId) {
      return;
    }
    window.__fakeCloudDebug?.seedTombstone({
      datasetId: activeDatasetId,
      recordType: 'MEDICATION',
      recordId,
      updatedAt: Date.now() + 60000,
      deletedAt: Date.now() + 60000,
    });
  }, { recordId: medicationId as string });

  await expect.poll(async () => {
    await syncNowButton.click();
    await expect(syncNowButton).toHaveText(/סנכרון עכשיו/);
    await page.getByRole('link', { name: 'תרופות' }).click();
    const updatedCard = page.locator('[data-med-id]').filter({ hasText: medicationName }).first();
    await expect(updatedCard).toContainText(medicationName);
    return updatedCard.textContent();
  }, { timeout: 20000 }).toContain('מושבת');

  expect(pageErrors, pageErrors.join('\n')).toEqual([]);
});
