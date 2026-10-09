# D-MOTO quirks

The Motorola phone. Moved verbatim from `TESTING-TEMPLATE.md` §7a on 2026-09-30. **Unless an entry names a unit, it was measured on D-HU.**

- **D-MOTO is not rooted, so its hotspot cannot be scripted.** `cmd wifi start-softap` and
  `cmd wifi get-softap-config` both refuse with `SecurityException: Uid 2000 does not have access`,
  and this build has no non-root tethering shell command. A run that needs D-MOTO to host a network
  is hand-operated: the operator sets the band, reads off the SSID and password, and says so in
  Setup notes.
- **D-MOTO can be behind a PIN rather than a swipe, and adb has no way past one.** Round 7 met both
  states in the same round with no adb action in between, so it is session-dependent (a trust-agent
  grace window). A run that needs D-MOTO's screen unattended should confirm the lock state first,
  or be scheduled where a person can unlock it once.
