You are bootstrapping a new front-end repository for **ConfluxPay**, a payment gateway / aggregator. The Spring Boot backend already exists at `..\shadhinpay_workspace\` (artifactId `ConfluxPay`, package `pay.conflux.backend`). You are **not** touching the backend — only consuming its REST API.

You must produce a working Turborepo monorepo with two Next.js apps and shared packages, in a single pass, ending with all acceptance checks green.

---

## Context (backend you'll integrate with)

- **Stack:** Spring Boot 3.4.1 + Spring Modulith (12 feature modules), JWT auth, MFA (TOTP), API-key auth for merchant server-to-server calls, OpenAPI 3 spec served at `http://localhost:8080/v3/api-docs`, Swagger UI at `/swagger-ui.html`.
- **Audiences:**
  - **Merchants** — sign up, complete MFA, manage API keys, view payments / invoices / settlements, configure vendors (PARTNER vs CUSTOM).
  - **Admins** — onboard / freeze merchants, manage risk rules + blacklist, view audit logs.
- **Auth model:** username + password → MFA challenge → JWT access token + refresh token. Merchant API keys are issued from the merchant panel and used server-side by merchants when calling the public payment API — **the merchant panel itself uses JWT, never the API key.**
- **Header conventions:** signature header is `X-PGW-Signature`, trace header is `X-PGW-Trace-ID`.

Read these from the backend (if mounted on the same machine) before generating types:

- `..\shadhinpay_workspace\conflux-application\src\main\resources\application.yml` for port / context-path
- `http://localhost:8080/v3/api-docs` for the OpenAPI schema (if backend is running)

If the backend isn't running, you can still scaffold the code — type generation can be re-run later via `pnpm gen:api`.

---

## Goal

Create a Turborepo + pnpm-workspaces monorepo at the current directory

```
conflux-web/
├── apps/
│   ├── merchant/        # merchant panel  (port 3000)
│   └── admin/           # admin console   (port 3001)
├── packages/
│   ├── ui/              # shadcn/ui components, shared
│   ├── api-client/      # fetch wrapper + openapi-typescript generated types
│   ├── auth/            # Auth.js v5 config, session helpers, MFA flow
│   ├── lib/             # cross-app utilities (date, money, validation)
│   └── config/          # shared eslint / tsconfig / tailwind preset
├── turbo.json
├── pnpm-workspace.yaml
├── package.json
├── .env.example
├── .gitignore
├── .editorconfig
├── .nvmrc                # node 24
├── .prettierrc.cjs
├── README.md
└── .github/workflows/ci.yml
```

---

## Stack (LOCKED — pin the **exact** versions below; use Context7 to confirm release-note specifics before writing non-trivial code)

> Versions verified against npm registry on **2026-05-11**. If you bootstrap weeks later and find a newer minor/patch, take it; do not silently downgrade. If you find a newer **major**, stop and confirm.

| Concern            | Choice                                  | Pinned version   | Notes                                                                                                                                               |
| ------------------ | --------------------------------------- | ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Runtime            | **Node.js**                             | `24.x` LTS       | pinned in `.nvmrc` (`24`) and `engines`                                                                                                             |
| Package manager    | **pnpm**                                | `11.0.9`         | workspace + content-addressable store                                                                                                               |
| Build orchestrator | **Turborepo**                           | `2.9.12`         | remote-cache stub only; no Vercel acct                                                                                                              |
| Framework          | **Next.js** (App Router)                | `16.2.6`         | Turbopack is the default for `dev` and `build`; async dynamic APIs (`cookies()`, `headers()`, `params`, `searchParams`) are **mandatory**           |
| React              | **React**                               | `19.2.6`         | Server Components, Server Actions, `use()` for promises                                                                                             |
| Language           | **TypeScript**                          | `6.0.3`          | `strict: true`, `noUncheckedIndexedAccess: true`, `verbatimModuleSyntax: true`                                                                      |
| Styling            | **Tailwind CSS**                        | `4.3.0`          | CSS-first config via `@theme` + `@import "tailwindcss";` — **no `tailwind.config.ts`** in v4 unless using the legacy-compat shim                    |
| Components         | **shadcn/ui**                           | latest CLI       | copy components into `packages/ui`, NOT installed as a dep                                                                                          |
| Server state       | **TanStack Query**                      | `5.100.10`       |                                                                                                                                                     |
| Client state       | **Zustand**                             | `5.0.13`         | v5 removed default exports — use the named `create` only                                                                                            |
| Forms              | **React Hook Form**                     | `7.75.0`         | pair with `@hookform/resolvers@5.2.2` for the Zod adapter                                                                                           |
| Validation         | **Zod**                                 | `4.4.3`          | v4 — `z.string().email()` is now `z.email()` (same for `url`, `uuid`, `cuid`); `errorMap` replaced by per-check `error` option; ~50% smaller bundle |
| Auth               | **Auth.js v5 (`next-auth`)**            | `5.0.0-beta.31`  | beta, but the only viable v5 channel; Credentials provider, JWT strategy, HttpOnly cookies. Document the beta pin in README; pin exactly, no `^`.   |
| API types          | **openapi-typescript**                  | `7.13.0`         | run against backend `/v3/api-docs`                                                                                                                  |
| API client         | **openapi-fetch**                       | `0.17.0`         | typed fetch wrapper, middleware chain                                                                                                               |
| Charts             | **Recharts**                            | `3.8.1`          | dashboards                                                                                                                                          |
| Tables             | **TanStack Table**                      | `8.21.3`         |                                                                                                                                                     |
| Icons              | **lucide-react**                        | `1.14.0`         | tree-shakable                                                                                                                                       |
| Date               | **date-fns**                            | `4.1.0`          | no Day.js, no Moment                                                                                                                                |
| Money              | **dinero.js**                           | `2.0.2`          | never store money in `number` — `bigint` minor units only                                                                                           |
| Unit tests         | **Vitest** + **@testing-library/react** | `4.1.6` + latest | Vitest 4 drops Node 18, ships browser-mode by default                                                                                               |
| E2E                | **Playwright**                          | `1.59.1`         | one happy-path test per app to start                                                                                                                |
| Lint               | **ESLint flat config**                  | `10.3.0`         | with `@typescript-eslint`, `eslint-plugin-react`, `eslint-config-next@16`; ESLint 10 drops the legacy `.eslintrc` format entirely                   |
| Format             | **Prettier**                            | `3.8.3`          | + `prettier-plugin-tailwindcss`                                                                                                                     |
| Hooks              | **husky + lint-staged**                 | latest           | pre-commit only, no commit-msg hook                                                                                                                 |

**Forbidden:** MUI, Mantine, Chakra, Day.js, Moment, Redux/RTK, Jest, Yarn, npm-workspaces, legacy `tailwind.config.js` (use v4 CSS `@theme`), legacy `.eslintrc*` files.

### Notable upgrade call-outs (read before writing code)

- **Next.js 16:** Turbopack is the *default* compiler for `next dev` and `next build` — no flags needed. `cookies()`, `headers()`, `draftMode()`, `params`, `searchParams` are **always async** — `await` them in RSC + Route Handlers + Server Actions or types will fail. `next/image` requires a `qualities` allowlist if you set non-default `quality` props. `unstable_cache` is gone — use the new `'use cache'` directive + `cacheLife` / `cacheTag`. `useFormState` from `react-dom` is gone — use `useActionState` from `react`.
- **Tailwind v4:** **No `tailwind.config.ts`.** Theme is declared in CSS via `@theme { --color-primary: …; }`. Content scanning is automatic via Vite/Turbopack integration; the `@source` directive is only needed for files outside auto-detection. PostCSS plugin is `@tailwindcss/postcss`. Use `@tailwindcss/vite` for any Vite-based packages. The shared "preset" in `packages/config/tailwind/` is now a `.css` file, not `.ts`.
- **Zod 4:** Breaking — `z.string().email()` → `z.email()` (same shape for `url`, `uuid`, `cuid`, `regex`). `errorMap` replaced by per-check `error` option. Keep all Zod schemas in `packages/lib/src/schemas/` so the v4 API is consistent across apps.
- **ESLint 10:** Flat config only. No `eslint-config-prettier` overrides via legacy keys; spread the flat plugin. `eslint-config-next@16` exposes a flat-config entry at `eslint-config-next/flat`.
- **Auth.js v5 beta:** API has been stable since beta.20+. Use the `NextAuth({ ... })` named export, not the default. Edge-compatible JWT callbacks. Document the beta pin in README + add a Renovate/Dependabot ignore rule until v5 GA.
- **TypeScript 6:** Adds `--erasableSyntaxOnly` and `--isolatedDeclarations`. Pair `verbatimModuleSyntax: true` with `import type` everywhere — TS 6 emits diagnostics for value-imports used only in type positions.
- **pnpm 11:** Default `node-linker` is `isolated`. Use `pnpm dlx` for one-off scripts. `--filter` syntax for monorepo commands is unchanged from v9/v10.

---

## Setup Tasks (execute in order — do not parallelize within a task)

### Task 1 — Workspace skeleton

1. Create `pnpm-workspace.yaml`:
   
   ```yaml
   packages:
     - "apps/*"
     - "packages/*"
   ```

2. Create root `package.json`:
   
   - `"name": "conflux-web"`, `"private": true`, `"engines": { "node": ">=24", "pnpm": ">=11" }`
   - `"packageManager": "pnpm@11.0.9"` (or current latest 11.x — never downgrade)
   - Scripts: `dev`, `build`, `lint`, `typecheck`, `test`, `test:e2e`, `gen:api`, `format`, `clean` — all delegating to `turbo run <task>` where applicable.

3. Create `turbo.json` with pipelines for `build`, `dev` (persistent, no cache), `lint`, `typecheck`, `test`, `gen:api`.

4. `.gitignore`, `.editorconfig`, `.nvmrc` (`24`), `.prettierrc.cjs` with `prettier-plugin-tailwindcss`.

5. `git init && git add -A && git commit -m "chore: workspace skeleton"`

### Task 2 — Shared config package (`packages/config`)

Owns the source of truth for tooling:

- `packages/config/tsconfig/base.json` — strict + bundler resolution
- `packages/config/tsconfig/nextjs.json` — extends base, adds Next plugin
- `packages/config/tsconfig/react-library.json` — for `packages/ui`
- `packages/config/eslint/base.js` — flat config, TS + import-sort + unused-imports
- `packages/config/eslint/next.js` — extends base + `eslint-config-next`
- `packages/config/tailwind/preset.css` — shared theme tokens via Tailwind v4 `@theme` (colors, radii, fonts); imported by each app's `globals.css`. **Not a `.ts` preset — that's v3-era.**

Export each via `package.json` `exports` map. Other packages reference them via `"@conflux/config/*"`.

### Task 3 — UI package (`packages/ui`)

1. Init the package with `tsup` for build (`esm` only, `dts: true`, external React).
2. Use `shadcn` CLI in *library* mode pointed at this package — `components.json` set to write into `packages/ui/src/components/`.
3. Bring in: `button`, `input`, `label`, `card`, `dialog`, `dropdown-menu`, `form`, `select`, `table`, `tabs`, `toast`, `tooltip`, `badge`, `avatar`, `skeleton`, `separator`, `sheet`, `command`, `popover`, `alert`, `alert-dialog`. No more — apps can pull additional components on demand.
4. Export each via subpath: `import { Button } from "@conflux/ui/button"`. Do not barrel — keep tree-shaking honest.
5. Re-export the shared Tailwind preset CSS so apps `@import "@conflux/ui/styles.css"` once and inherit theme tokens + content auto-detection. With Tailwind v4 there is no `content` array — content paths are discovered by Turbopack/Vite. For files Turbopack can't see (e.g. CSS in `packages/ui/dist`), add `@source "../../../packages/ui/dist/**/*.{js,mjs}";` to the app's CSS entry.

### Task 4 — Lib package (`packages/lib`)

Cross-app utilities only — no React.

- `money.ts` — dinero.js wrappers; `formatMoney(amount, currency)`; never accept `number`, accept `bigint` minor units.
- `date.ts` — date-fns wrappers; `formatTimestamp`, `relativeTime`, all assume UTC input.
- `result.ts` — `Result<T, E>` discriminated union for unhappy paths.
- `id.ts` — typed branded ids (`type MerchantId = string & { __brand: "MerchantId" }`).
- `env.ts` — Zod-validated process.env reader.

100% Vitest coverage on this package. It's tiny — no excuses.

### Task 5 — API client package (`packages/api-client`)

1. Install `openapi-typescript` and `openapi-fetch`.
2. `pnpm gen:api` script: `openapi-typescript http://localhost:8080/v3/api-docs -o src/generated/schema.d.ts`. If backend is offline, fall back to `src/generated/schema.d.ts` checked-in stub with a `// TODO: regenerate against live backend` banner.
3. Hand-written `client.ts`:
   - `createClient({ baseUrl, getToken, onUnauthorized })` returning a typed `openapi-fetch` client.
   - Middleware that injects `Authorization: Bearer <token>` from the `getToken` callback.
   - Middleware that intercepts 401 → calls `onUnauthorized` (which the auth package wires to refresh-or-logout).
   - Adds `X-PGW-Trace-ID` (crypto.randomUUID) per request.
4. `errors.ts`: map `ProblemDetails` (RFC 7807) responses into typed `ApiError` instances. Backend likely returns `{type, title, status, detail, instance, errors}` — confirm against the OpenAPI spec and adapt.
5. Re-export typed hooks: `useApiQuery`, `useApiMutation` thin wrappers over TanStack Query that take a path + method literal, fully inferring request/response types from the OpenAPI schema.

### Task 6 — Auth package (`packages/auth`)

Auth.js v5 setup, **shared between both apps** but with per-app config overrides.

1. `packages/auth/src/index.ts` exports a `createAuth(config)` factory.
2. `Credentials` provider with three-step flow:
   - Step 1 — POST `/api/v1/auth/login` → returns `{ challenge: "mfa_required", mfaToken }` or `{ accessToken, refreshToken, user }`.
   - Step 2 — if MFA required, frontend redirects to `/mfa`; POST `/api/v1/auth/mfa/verify` with `{ mfaToken, code }` → returns tokens.
   - Step 3 — tokens stored in HttpOnly cookies via Auth.js session callbacks.
3. JWT strategy with `jwt` + `session` callbacks that:
   - On sign-in, persist `accessToken`, `refreshToken`, `accessTokenExpiresAt` into the JWT.
   - On every call, if `accessTokenExpiresAt` is within 60s, call `/api/v1/auth/refresh` and update the JWT.
   - Surface only `user` + a function-friendly `getAccessToken` to the client session.
4. Middleware export: `createAuthMiddleware({ publicPaths })` for `apps/*/middleware.ts`.
5. Server-side helper `auth()` re-exported from Auth.js for RSC + Route Handlers.
6. `RoleGuard` React component that takes `requires: "MERCHANT" | "ADMIN" | "MERCHANT_ADMIN"` and renders children or a 403 page. Role comes from JWT claims.

The two apps differ only in:

- `apps/merchant` allows `MERCHANT` and `MERCHANT_ADMIN` roles.
- `apps/admin` allows only `PLATFORM_ADMIN` (or whatever the backend names it — confirm from OpenAPI).
- Sign-up route exists only in merchant; admin login screen has no sign-up link.

### Task 7 — Merchant app (`apps/merchant`)

Routes (all stubs — page renders heading + "TODO" + a `<Skeleton />` block, except auth pages which must work end-to-end):

- `/` → redirect to `/dashboard` if signed in else `/login`
- `/login` ✅ working
- `/signup` ✅ working (calls `/api/v1/auth/signup`)
- `/mfa` ✅ working (challenge entry)
- `/mfa/setup` ✅ working (QR code + verify, after first login)
- `/dashboard` — stub
- `/payments` — stub
- `/payments/[id]` — stub
- `/api-keys` — stub
- `/invoices` — stub
- `/settlements` — stub
- `/vendor-configs` — stub
- `/settings/profile` — stub
- `/settings/security` — stub (MFA reset, password change)

Layout: persistent sidebar (collapsible) + top bar with user menu. Use `Sheet` for mobile sidebar.

### Task 8 — Admin app (`apps/admin`)

Routes:

- `/` → redirect
- `/login` ✅ working
- `/mfa` ✅ working
- `/dashboard` — stub
- `/merchants` — stub (table)
- `/merchants/[id]` — stub
- `/risk/rules` — stub
- `/risk/blacklist` — stub
- `/audit-logs` — stub
- `/settings` — stub

Layout: same skeleton as merchant but different sidebar nav items + an "ADMIN" badge in the top bar.

### Task 9 — Local dev experience

- `.env.example` at repo root:
  
  ```
  # Both apps
  NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
  AUTH_SECRET=replace-me-32-bytes-base64
  AUTH_URL_MERCHANT=http://localhost:3000
  AUTH_URL_ADMIN=http://localhost:3001
  ```

- Each app reads its own `.env.local`.

- Root `pnpm dev` → `turbo run dev` runs both apps in parallel with `concurrently`-style output (Turbo handles this).

- Add a `pnpm dev:merchant` and `pnpm dev:admin` for solo runs.

- Document the assumption: backend is expected to be reachable at `localhost:8080` with CORS configured for `http://localhost:3000` and `http://localhost:3001`. If it isn't, add a note in the README pointing to the backend's CORS config file.

### Task 10 — Quality gates

- ESLint 10 flat config at root, extended per app/package. Use `eslint-config-next/flat` for the apps; do not generate any `.eslintrc.*` file.
- `pnpm typecheck` — runs `tsc --noEmit` in every workspace.
- `pnpm test` — Vitest.
- `pnpm test:e2e` — Playwright; one test per app: "user can load login page and see the form".
- `husky` + `lint-staged` pre-commit: format + lint changed files only.
- `.github/workflows/ci.yml`:
  - matrix: node 24 on ubuntu-latest
  - steps: setup pnpm, install, `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`
  - upload build artifacts (`.next/`) as a check
  - **do not** run E2E in CI yet — that needs the backend; flag as TODO.

### Task 11 — README

One screen of content. Include:

- One-paragraph project description (codename ConfluxPay, brand-agnostic by design).
- Prereqs (`node 24`, `pnpm 11`, backend running at `:8080`).
- Quickstart: `pnpm install`, copy `.env.example`, `pnpm gen:api`, `pnpm dev`.
- Where each piece lives (apps + packages table).
- How to add a new shadcn component (`cd packages/ui && pnpm dlx shadcn add ...`).
- How to regenerate API types.
- Link back to the backend repo path.

---

## Acceptance Gate

When the agent claims it's done, **all** of the following must pass. Run them in order and report each result inline.

| #   | Command (from repo root)                                                                                                                                                                    | Expected                                                                                                                                            |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | `pnpm install`                                                                                                                                                                              | clean install, no peer-dep warnings beyond known noise                                                                                              |
| 2   | `pnpm gen:api` (with backend live) OR confirm checked-in stub exists                                                                                                                        | `schema.d.ts` present in `packages/api-client/src/generated/`                                                                                       |
| 3   | `pnpm typecheck`                                                                                                                                                                            | passes across all workspaces                                                                                                                        |
| 4   | `pnpm lint`                                                                                                                                                                                 | passes                                                                                                                                              |
| 5   | `pnpm test`                                                                                                                                                                                 | Vitest passes; `packages/lib` at 100% line+branch                                                                                                   |
| 6   | `pnpm build`                                                                                                                                                                                | `.next/` produced for both apps; no errors                                                                                                          |
| 7   | `pnpm dev`                                                                                                                                                                                  | both apps boot; merchant on `:3000`, admin on `:3001`; both serve `/login` with the form rendered (use `curl -sI localhost:3000/login` returns 200) |
| 8   | `pnpm test:e2e` (only if Playwright browsers installed)                                                                                                                                     | one passing test per app                                                                                                                            |
| 9   | `git status`                                                                                                                                                                                | clean working tree on the bootstrap branch (everything committed in logical chunks)                                                                 |
| 10  | Visual check: open both apps in a browser, submit the login form with garbage creds. The error toast must show the backend's `ProblemDetails.detail` string, not a generic "Network error". | manual ack                                                                                                                                          |

Produce a `BOOTSTRAP_REPORT.md` at the repo root mirroring this table with PASS/FAIL + evidence (command output excerpts).

---

## Commit Strategy

Make ~10 small commits, one per task, with conventional-commit prefixes:

```
chore: workspace skeleton
chore(config): shared eslint/tsconfig/tailwind preset
feat(ui): shadcn components package
feat(lib): money/date/result/id utilities
feat(api-client): openapi-fetch wrapper + generated types
feat(auth): Auth.js v5 with MFA flow
feat(merchant): app skeleton with auth pages
feat(admin): app skeleton with auth pages
chore(ci): GitHub Actions workflow + pre-commit hooks
docs: README + bootstrap acceptance report
```

Do **not** squash. Each commit must be independently buildable (`pnpm install && pnpm typecheck` green at every commit).

---

## Out of Scope (explicitly)

- Implementing feature pages beyond auth + MFA. Stubs only.
- Theming/branding polish — use shadcn defaults + neutral palette.
- i18n — English only.
- Mobile apps / React Native.
- Storybook — defer until UI package has >10 components in real use.
- Server-side caching / ISR — pages are dynamic until proven otherwise.
- WebSocket / SSE — backend doesn't expose these yet.
- Visual regression testing.
- Bundle size budgets — set after first real feature lands.

If you find yourself implementing any of the above, **stop and ask**.

---

## Decisions That Need User Sign-off Before Coding

Surface these at the top of your first reply and wait for answers — do not assume defaults:

1. **Repo location:** `..\conflux-web\` (sibling) or inside `shadhinpay_workspace\frontend\` (subdir of backend)?
2. **Auth.js v5 vs custom JWT proxy.** Auth.js is recommended; custom is simpler if you want zero magic and direct cookie control.
3. **Role names in JWT claims** — confirm exact strings from backend OpenAPI (`MERCHANT_ADMIN` vs `MERCHANT_OWNER`, `PLATFORM_ADMIN` vs `SUPER_ADMIN`, etc.). Do not guess.
4. **Tailwind v4 vs v3.** v4 is the lock above. shadcn/ui's `init` and component templates have been Tailwind v4-native since mid-2025, so this should be a non-issue. If you genuinely hit a blocker, surface it before downgrading — do not silently drop to v3.

---

## Reference Material

- Next.js 16 App Router — https://nextjs.org/docs/app
- Next.js 16 upgrade guide — https://nextjs.org/docs/app/guides/upgrading/version-16
- Tailwind CSS v4 — https://tailwindcss.com/docs
- Auth.js v5 — https://authjs.dev
- shadcn/ui monorepo guide — https://ui.shadcn.com/docs/monorepo
- Turborepo handbook — https://turbo.build/repo/docs
- openapi-typescript — https://openapi-ts.dev
- TanStack Query — https://tanstack.com/query/latest
- Zod v4 migration — https://zod.dev/v4/migration

Use **Context7** for any of the above before generating non-trivial code — your training cutoff may be behind. Specifically confirm: Next 16 async-API call sites (`cookies()`, `params`, `searchParams`), Tailwind v4 `@theme` syntax, Auth.js v5 `NextAuth({ ... })` named-export signature, Zod v4 top-level format checks (`z.email()`, `z.url()`, etc.), and ESLint 10 flat-config plugin spread syntax.
