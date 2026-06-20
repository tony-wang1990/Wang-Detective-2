# Wang-Detective Frontend

This directory is the new maintainable Vue frontend source for the UI rebuild.

Current scope:

- Native Vue login page.
- Native dashboard shell with sidebar, topbar, theme switch, and route outlet.
- Native home page that reads the existing `/api/sys/glance` and `/actuator/health` data.
- Native Vue routes for configuration list, task list, service logs, system config, feature center, and ops terminal.
- A temporary `/legacy-dashboard.html` fallback for the old bundled UI while the new routes are tested in production.

It now builds to `src/main/resources/dist` as the production frontend. `emptyOutDir` is disabled for this transition so the old bundled chunks remain available to `/legacy-dashboard.html` until the new Vue routes pass deployment testing.

Commands:

```bash
cd frontend
npm install
npm run dev
npm run build
```

Migration plan:

1. Deploy and smoke-test the new production entry.
2. Fill in detailed create/edit/delete dialogs for configuration and task pages.
3. Expand ops terminal with upload/download progress, audit filters, and command templates.
4. Remove `/legacy-dashboard.html` and old bundled chunks after route parity is verified.
