# Provider transfers and Crashlytics audit

Checked on 2026-11-09 against public provider terms and the local mobile source at `/Users/bookkme/AndroidStudioProjects/Bookk`. The operator confirmed Cloudflare is used for DNS, reverse proxy, DDoS protection and general security, and Postmark message retention is 45 days. The review covers published standard contracts and source configuration; private account agreements, dashboards and deployed network traffic were not inspected. No provider agreement was accepted or changed during this review.

## Published provider arrangements

| Provider | Verified finding | Consequence for these drafts |
|---|---|---|
| Cloudflare | Its self-service agreement incorporates the customer DPA. The DPA contains Data Privacy Framework provisions and SCCs for restricted transfers, including controller-to-processor and processor-to-processor modules. | The BookkMe policy now identifies the actual proxy/security uses and links the existing contractual arrangements. A separate signed DPA is not generally required under the published self-service agreement. Sources: [agreement, section 6.1](https://www.cloudflare.com/terms/), [DPA, section 6](https://www.cloudflare.com/cloudflare-customer-dpa/). |
| Postmark | Its standard terms incorporate the DPA. The published DPA provides transfer clauses. Newer accounts' terms identify AC PM, LLC; legacy account agreements may differ. | The policy uses the Postmark brand and links its terms and DPA rather than assuming the former ActiveCampaign contracting entity. Sources: [terms](https://postmarkapp.com/terms-of-service), [DPA and legacy-account distinction](https://postmarkapp.com/dpa). |
| Firebase Cloud Messaging and Crashlytics | Both services reference the Firebase Data Processing and Security Terms. Section 10 supplies a transfer framework, with DPF coverage and SCC arrangements as applicable, and allows global processing. | The policy identifies the actual services and avoids implying that processing occurs only in the EU or only in the US. Sources: [service-specific terms](https://firebase.google.com/terms/), [processing terms, section 10](https://firebase.google.com/terms/data-processing-terms). |

The previous generic instruction to obtain separate signatures from all providers was too broad. Under the published standard terms, incorporation can establish the provider DPA. Keep the applicable agreement/version with the operator's records; a separate signature is only relevant if the actual contract requires it. The BookkMe-to-business DPA remains a different document and is still needed.

The DPF references above describe the providers' contractual provisions. The official DPF registry requires JavaScript and did not expose a live record to this review, so no independent current registry-status verification is claimed. The policies describe the applicable contractual alternatives without asserting that every transfer is DPF-covered. A provider-specific assessment of an SCC-dependent transfer can still require review of destination risks and supplementary measures; this document is not a completed transfer impact assessment.

The operator decided on 2026-11-11 that people who operate the service, including its employees, may access production data from the countries where they are located. On 2026-11-12 the operator excluded access from Ukraine. The BookkMe policy and DPA now state both, offer the current list of access countries on request and apply the safeguards required where such access is a transfer. On 2026-11-12 the hosting provider's published DPA was checked as well: it is part of its standard terms, applies SCC modules 2 and 3 to transfers outside the EEA, including onward transfers to its own US subprocessors, and is named in Annex 3 of the BookkMe DPA only. Copies of every provider's current terms and DPA are kept with the private BookkMe texts. Custom provider agreements remain outside the evidence reviewed. Later on 2026-11-12 the operator confirmed that only the company's employees and director have access, so the texts no longer offer a list of access countries. The operator also confirmed that the legal and support addresses forward through Cloudflare Email Routing to a consumer Gmail mailbox with no DPA and are answered through Postmark SMTP; the policies disclose this.

Cloudflare also acts as controller for specified network/service data, while traffic content and customer logs have a processor role. Google distinguishes Firebase service data from customer data. The policies now reflect those separate roles and link the relevant notices. Sources: [Cloudflare privacy policy, sections 3 and 6](https://www.cloudflare.com/privacypolicy/), [Firebase privacy information](https://firebase.google.com/support/privacy).

## Crashlytics source inspection

On 2026-11-09 the source showed the default-on finding below. On 2026-11-11 the operator turned collection off by default with `firebase_crashlytics_collection_enabled=false` (Android) and `FirebaseCrashlyticsCollectionEnabled=false` (iOS). Crashlytics uses that value until the user changes the switch, so installations that never touched it become opt-in as well. The policies now give consent as the basis.

| Area | Source evidence | Finding |
|---|---|---|
| Android startup | `androidApp/src/main/AndroidManifest.xml`; `AndroidCrashReporter.kt` | No manifest override disables Crashlytics by default, and the wrapper delegates directly to the SDK. Analytics and advertising identifiers are separately disabled. |
| iOS startup | `iosApp/iosApp/Info.plist`; `FirebaseDelegate.swift`; `Library/CrashReporting/IOSCrashReporter.swift` | Firebase is configured at application launch. No plist override disables Crashlytics by default. |
| User choice | `feature/settings/.../PrivacySettingsViewModel.kt`; `UpdateCrashReportingEnabledImpl.kt` | The switch is in Settings → Account → Privacy. Disabling calls the SDK disable method and requests deletion of unsent reports; enabling requests deletion before enabling. These calls do not delete previously uploaded reports. |
| Sign-up | `feature/authorization/.../sign_up/SignUpViewModel.kt` and legal-document state | The legal-document checkbox does not provide a separate optional diagnostics choice or gate Crashlytics startup. |
| Extra identifiers | Search across Android, iOS, shared, core, library and feature source | No explicit Crashlytics user-ID attachment, custom crash logs or exception-recording calls were found. This does not prove that automatic crash traces cannot contain identifying exception text. |

Google documents automatic collection as the default and separate opt-in controls for [Android](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports) and [Apple platforms](https://firebase.google.com/docs/crashlytics/ios/customize-crash-reports). This was a source inspection, not a clean-install device test or payload capture.

The implementation currently offers opt-out rather than prior optional consent. Device-access exemptions need a purpose- and operation-specific assessment; legitimate interest for later processing does not itself establish an exemption. [EDPB Guidelines 2/2023](https://www.edpb.europa.eu/system/files/2024-10/edpb_guidelines_202302_technical_scope_art_53_eprivacydirective_v2_en_0.pdf) explain the technical scope; [CNIL's mobile recommendations, section 5.1.2](https://www.cnil.fr/sites/cnil/files/2024-09/recommandation-applications-mobiles.pdf) describe consent and exemptions.

My recommendation for the reusable product is default-off optional reporting with a separate affirmative choice, unless a documented exemption has been established for the deployment. That is an implementation recommendation, not a finding that every crash-reporting tool always requires consent. This request asked for inspection; no mobile source was changed. The default-off change was made on 2026-11-11; device tests remain.

A concrete implementation would set `firebase_crashlytics_collection_enabled=false` on Android and `FirebaseCrashlyticsCollectionEnabled=false` on iOS before SDK startup, preserve the existing SDK-backed preference, provide optional diagnostics information and a choice, and verify that stored reports are handled appropriately when enabling or withdrawing. Existing installations with a persisted enabled value also need a migration decision; changing manifest/plist defaults alone does not necessarily reset that value. Validate fresh install, refusal, acceptance, withdrawal, restart and existing-install upgrades on both platforms before changing the published default.

## Confirmed retention and product decisions

- **Postmark:** use 45 days for message content/activity/metadata, confirmed by the operator and [Postmark's retention documentation](https://postmarkapp.com/support/article/how-long-are-inbound-and-outbound-messages-stored-in-activity). Suppression addresses and historical hard-bounce/spam-complaint records can remain longer, so the policy does not promise deletion of every Postmark record after 45 days.
- **Crashlytics:** Google says removal of crash traces and associated identifiers from live and backup systems starts after 90 days. This is not a promise that all copies are gone at day 90; the six policies now use that distinction. See [Firebase retention information](https://firebase.google.com/support/privacy).
- **Appointment history:** the operator deliberately retains it while the business exists, without an appointment-age deletion job. The existing separate client/account erasure rules still remove client details and notes; retained reasons and other identifiers still need appropriate handling where an erasure request applies. The policy states the chosen lifetime. No appointment-deletion job was added, and business existence alone is not a legal assessment of every retained field.
- **Support and submitted usage logs:** during this review, a separate workspace change removed the contact-form endpoint and storage code; user migration `V6` drops `contact_form` and its records. The legal texts now describe support by email. Production holds no contact-form records. BookkMe keeps support correspondence 24 months after the last message, longer only for a legal claim (operator decision 2026-11-11); the mailbox must apply it. Ordinary security/server logs are separate and currently have a 14-day retention configuration.

The sender now sets `TrackOpens=false` and `TrackLinks=None` on every email, which overrides the server and stream settings, so the no-tracking statement is enforced in code.

## Validation

- [x] Public provider incorporation and transfer terms checked against primary sources.
- [x] Cloudflare service purposes and provider controller/processor distinctions reflected in all six privacy policies.
- [x] Android/iOS startup defaults, settings control, sign-up and explicit identifier attachment inspected.
- [x] BookkMe's Postmark retention set to 45 days; templates retain deployment-specific configuration.
- [x] Crashlytics deletion wording and appointment-history decision aligned across English, Polish and Ukrainian.
- [x] Operator access decided (staff access from where they are located, never from Ukraine), support retention set to 24 months, open and link tracking disabled in the sender, Crashlytics default-off in both apps.
- [ ] Private account settings and clean-install, opt-in, opt-out and upgraded-device behaviour were not verified.
