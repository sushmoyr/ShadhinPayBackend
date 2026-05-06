# Identity Module — Outbound Events

This package is the published `events` named-interface for the `identity` module.
All events are immutable Java records and are dispatched via the Spring Modulith
JDBC Event Publication Registry (see `shadhinpay-common` TECH_SPEC §5).

## `MerchantVerifiedEvent`
**Signals:** A merchant's `MerchantProfile.onboardingStatus` has transitioned to `VERIFIED` by an admin.
**Fires from:** `VerifyMerchantUseCase` (admin-only) on successful state change, inside the verifying transaction.
**Current consumers:** `provisioning` (to enable a default `Business` and vendor configuration), `quota` (to seed the merchant's free-tier counters). No payment-core consumer.

## `UserBlockedEvent`
**Signals:** A `User` (merchant or admin) has been blocked — `User.status` moved to `BLOCKED`.
**Fires from:** `BlockUserUseCase` / admin moderation flows in identity.
**Current consumers:** `payment-core` (rejects new `InitiatePayment` requests for the user with `UNAUTHORIZED`), `risk` (raises the user's risk score / adds them to the watch list).
