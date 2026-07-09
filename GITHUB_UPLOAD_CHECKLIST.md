# GitHub upload checklist

Before public release, check the following items:

- [ ] Confirm with the supervisor that the full Android source code can be released.
- [ ] Confirm whether `docs/hardware_app_communication_protocol.md` and `docs/protocol_original_v1.md` can be public.
- [ ] Confirm with the supervisor/institution that the included `LICENSE` is appropriate, especially the non-commercial restriction and patent-rights reservation.
- [ ] Ensure no signing keys are included: `*.jks`, `*.keystore`.
- [ ] Ensure no local environment files are included: `local.properties`, IDE user files.
- [ ] Ensure no generated build outputs are included: `app/release/`, `app/build/`, `*.apk`, `*.aab`.
- [ ] Replace generated demonstration data with real exported datasets if the repository is used as a formal data/code archive for a submitted manuscript.
