import { expect, test, type Page } from '@playwright/test';

const EMAIL = process.env.E2E_EMAIL ?? 'demo@spotty.local';
const PASSWORD = process.env.E2E_PASSWORD ?? 'Spotty-demo-1997';

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(EMAIL);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByTestId('avatar')).toBeVisible();
}

const currentTime = (page: Page) => page.getByTestId('audio').evaluate((a: HTMLAudioElement) => a.currentTime);
const isPaused = (page: Page) => page.getByTestId('audio').evaluate((a: HTMLAudioElement) => a.paused);

test('home page shows hero, bargain bin, track list and footer', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('now_suffering.exe')).toBeVisible();
  await expect(page.getByText('NOW SUFFERING:')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Bargain bin picks' })).toBeVisible();
  await expect(page.getByTestId('album-card').first()).toBeVisible();
  await expect(page.getByTestId('track-row').first()).toBeVisible();
  await expect(page.getByText('© 1997 Spotty Corp. All rights regretted.')).toBeVisible();
  await page.screenshot({ path: 'test-results/home-day.png', fullPage: true });
});

test('night mode toggle swaps the theme and survives a reload', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('theme-toggle').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'night');
  const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  expect(bg).toBe('rgb(26, 16, 48)');
  await page.screenshot({ path: 'test-results/home-night.png', fullPage: true });
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'night');
  await page.getByTestId('theme-toggle').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'day');
});

test('search finds a track and unknown queries show the empty copy', async ({ page }) => {
  await page.goto('/');
  await page.getByPlaceholder('Search for regrets…').fill('Printer');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/search\?q=Printer/);
  await expect(page.getByText('My Printer Hates Me').first()).toBeVisible();
  await page.getByPlaceholder('Search for regrets…').fill('zzzzqqqq');
  await page.keyboard.press('Enter');
  await expect(page.getByText('Nothing found.')).toBeVisible();
});

test('unknown route shows ERROR 404', async ({ page }) => {
  await page.goto('/definitely/not/here');
  await expect(page.getByText('ERROR 404: TASTE NOT FOUND')).toBeVisible();
});

test('anonymous play sends you to login', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('hero-play').click();
  await expect(page).toHaveURL(/\/login/);
});

test('login, play, keep playing while navigating, keyboard shortcuts', async ({ page }) => {
  await login(page);
  await page.goto('/');
  await page.getByTestId('hero-play').click();
  await expect.poll(() => currentTime(page), { timeout: 20_000 }).toBeGreaterThan(0.5);
  await expect(page.getByTestId('player-toggle')).toHaveAttribute('aria-label', 'Pause');

  // client-side navigation: the same audio element keeps playing
  await page.getByRole('link', { name: 'Search' }).click();
  await expect(page).toHaveURL(/\/search/);
  const before = await currentTime(page);
  await page.waitForTimeout(1500);
  expect(await isPaused(page)).toBe(false);
  expect(await currentTime(page)).toBeGreaterThan(before);

  // Space pauses, arrows seek 5 s
  await page.locator('main').click({ position: { x: 5, y: 5 } });
  await page.keyboard.press('Space');
  await expect.poll(() => isPaused(page)).toBe(true);
  const t0 = await currentTime(page);
  await page.keyboard.press('ArrowRight');
  await expect.poll(() => currentTime(page)).toBeGreaterThan(t0 + 4);
  await page.keyboard.press('Space');
  await expect.poll(() => isPaused(page)).toBe(false);
});

test('like a track and create a playlist', async ({ page }) => {
  await login(page);
  await page.goto('/search?q=Lukewarm');
  const like = page.getByRole('button', { name: /^(Like|Unlike)/ }).first();
  await expect(like).toBeVisible();
  if ((await like.getAttribute('aria-pressed')) === 'true') await like.click();
  await expect(page.getByRole('button', { name: /^Like/ }).first()).toHaveAttribute('aria-pressed', 'false');
  await page.getByRole('button', { name: /^Like/ }).first().click();
  await expect(page.getByRole('button', { name: /^Unlike/ }).first()).toBeVisible();

  await page.goto('/library?tab=playlists');
  const name = `E2E ${Date.now()}`;
  await page.getByPlaceholder('Name your mistake').fill(name);
  await page.getByRole('button', { name: '+ New playlist' }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
  await expect(page.getByText('Your playlist is empty. Honestly, good choice.')).toBeVisible();

  await page.goto('/search?q=Lukewarm');
  await page.getByRole('button', { name: 'Add to playlist' }).first().click();
  await page.getByRole('menuitem', { name }).click();
  await expect(page.getByText(`Added to ${name}`)).toBeVisible();
});

test('artist page can be followed', async ({ page }) => {
  await login(page);
  await page.goto('/artist/1');
  const follow = page.getByTestId('follow-button');
  await expect(follow).toBeVisible();
  if ((await follow.getAttribute('aria-pressed')) === 'true') {
    await follow.click();
    await expect(follow).toHaveAttribute('aria-pressed', 'false');
  }
  await follow.click();
  await expect(follow).toHaveAttribute('aria-pressed', 'true');
});

test('mobile layout uses a bottom nav and has no horizontal scroll', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 800 });
  await page.goto('/');
  await expect(page.getByTestId('player-bar')).toBeVisible();
  const box = await page.getByRole('navigation', { name: 'Main' }).boundingBox();
  expect(box!.y).toBeGreaterThan(600);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await page.screenshot({ path: 'test-results/home-mobile.png' });
});
