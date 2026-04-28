// Theme controller: light / dark / platform. Persisted in localStorage so the chosen mode
// survives reloads. The synchronous bootstrap script in index.html applies the saved value
// before any stylesheet loads, preventing flash-of-wrong-theme on reload (SC-008).

const KEY = 'playground-theme';
const VALID = new Set(['light', 'dark', 'platform']);

export function currentTheme() {
  try {
    const v = localStorage.getItem(KEY);
    return VALID.has(v) ? v : 'platform';
  } catch (e) {
    return 'platform';
  }
}

export function setTheme(mode) {
  if (!VALID.has(mode)) mode = 'platform';
  document.documentElement.dataset.theme = mode;
  try {
    localStorage.setItem(KEY, mode);
  } catch (e) {
    // ignore — the in-memory dataset.theme is already set.
  }
}

/**
 * Renders a 3-way segmented control into the given host element. Calls setTheme on click and
 * keeps aria-pressed in sync. Returns nothing.
 */
export function renderThemeToggle(host) {
  host.innerHTML = '';
  const wrap = document.createElement('div');
  wrap.className = 'theme-toggle';
  wrap.setAttribute('role', 'group');
  wrap.setAttribute('aria-label', 'Theme');

  const modes = [
    { value: 'light', label: 'Light' },
    { value: 'dark', label: 'Dark' },
    { value: 'platform', label: 'Platform' },
  ];

  for (const m of modes) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = m.label;
    btn.dataset.mode = m.value;
    btn.setAttribute('aria-pressed', String(currentTheme() === m.value));
    btn.addEventListener('click', () => {
      setTheme(m.value);
      wrap.querySelectorAll('button').forEach((b) =>
        b.setAttribute('aria-pressed', String(b.dataset.mode === m.value))
      );
    });
    wrap.appendChild(btn);
  }
  host.appendChild(wrap);
}
