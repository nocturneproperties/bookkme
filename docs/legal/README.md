# Privacy policy and terms of service

Two document sets, each in English (`.en.md`), Polish (`.pl.md`) and Ukrainian (`.uk.md`):

| Folder | For | Who is the controller |
|---|---|---|
| `bookkme/` (not in the public repository) | The BookkMe service at bookkme.com, operated by us for many businesses | BookkMe for accounts; each business for its workspace records, with BookkMe as processor |
| [`template/`](template) | A business running its own copy of Bookk under [`LICENSE-SELF-HOSTING.md`](../../LICENSE-SELF-HOSTING.md) | That business for its deployment |

| Document | English | Polish | Ukrainian |
|---|---|---|---|
| Self-hosting privacy policy | [EN](template/privacy-policy.en.md) | [PL](template/privacy-policy.pl.md) | [UK](template/privacy-policy.uk.md) |
| Self-hosting terms | [EN](template/terms-of-service.en.md) | [PL](template/terms-of-service.pl.md) | [UK](template/terms-of-service.uk.md) |

These are unpublished drafts, with unresolved operator details and publication dependencies. The [legal gap review](legal-review.md) and follow-up [provider and Crashlytics audit](provider-and-crashlytics-audit.md) record the findings, evidence and remaining work. The common text can be adapted to different countries; it does not establish compliance everywhere. Determine the operator's establishment, target markets and applicable rules before publication. Selecting Polish or Ukrainian does not select Polish or Ukrainian law, and none of the translations automatically prevails over the others.

The server records document versions through the existing acceptance API ([publishing instructions](../legal-documents.md)). The privacy-policy record documents receipt of information; it must not be presented as blanket consent. Separate consent, where needed, requires its own purpose, affirmative action, evidence and withdrawal route. No application behaviour was changed in this document revision.

## Keeping the texts accurate

When a change affects the statements below, update both sets in all three languages in the same change, and the annexes of the BookkMe data processing agreement (workspace data, retention, security measures, subprocessors), which is kept with the BookkMe texts. Keep placeholders, numbered sections, retention values and legal meaning aligned. Publish a new BookkMe document version after completing the relevant publication steps; never overwrite text already accepted by users.

| Statement | Decided by |
|---|---|
| Data collected per account, device, business, employee, client and appointment | Entities in `service/*/domain/api` |
| Account deleted after 3 years without use, warnings at 60, 30 and 7 days; owner exception while another business member remains active | `HandleDormantAccountsImpl` |
| Device deleted after 30 days without use | `DeleteInactiveDevicesImpl` |
| Email code valid 10 minutes | `EmailCodeIssuerImpl.CODE_LIFETIME` |
| Client retention 12 to 120 months, 36 by default | `Business.CLIENT_DATA_RETENTION_MONTHS_RANGE` |
| Requests deleted 60 days after their slot ends; erasure records and tombstones after 60 days | `docs/agents/personal-data.md` |
| Client erasure clears contact details and note, while cancellation and price-adjustment reasons remain; only employees write those reasons, and the terms forbid personal details in them | `AppointmentDataSourceImpl.eraseClientDetails`, `CancelAppointmentImpl` |
| Data export once a day, kept 72 hours | `library.export.DataExports` |
| Logs 14 days, backups 30 days (BookkMe production) | `deployment/prod/monitoring/loki/loki.yml`, `deployment/prod/backup` |
| Undelivered events kept 30 days | `ExhaustedEventReplayerImpl.DEFAULT_RETENTION` |
| Profile changes propagate to client and employee records; duplicate client records merge by confirmed email | `SyncUserProfileImpl` |
| Business deletion follows owner-account deletion and deletes employees, clients, services and appointments | `BusinessEventHandlerImpl`, appointments `BusinessEvent.Deleted` handler |
| Providers include Firebase Cloud Messaging, Postmark, Cloudflare and hosting | Notification and email senders, deployment configuration |
| Emails carry no open or click tracking; BookkMe Postmark message retention is 45 days, with suppression records kept longer | `PostmarkEmailSender` sends every email with `TrackOpens=false` and `TrackLinks=None`, overriding server/stream settings; retention confirmed by the operator on 2026-11-09 and provider documentation |
| No Telegram notifications yet | `TelegramNotificationSender` is a stub; add Telegram to both sets in all languages if it starts sending |
| Minimum account age 16 | Product decision; contractual capacity and guardian authorisation depend on local law |
| Crashlytics off by default and sent only after the user turns it on in Settings → Account → Privacy (consent); no deliberate attached user id; Firebase Analytics and advertising ids disabled | Client `firebase_crashlytics_collection_enabled=false` in `AndroidManifest.xml` and `FirebaseCrashlyticsCollectionEnabled=false` in `Info.plist` (2026-11-11) |
| Google starts Crashlytics removal after 90 days rather than guaranteeing completion by then | Firebase privacy/retention documentation |
| Appointment history has no age-based expiry while the business exists; client/account erasure rules still apply | Operator decision confirmed on 2026-11-09; current server behaviour |
| No in-app contact form; support is by email only. BookkMe keeps support correspondence 24 months after the last message, longer only for a legal claim | Contact form removed from server and app, prod holds no contact-form records; support retention decided by the operator on 2026-11-11 and applied in the support mailbox |
| People who operate BookkMe, including its employees, may access production data from the countries where they are located | Operator decision on 2026-11-11 |
| Contacts read locally for client import; only selected contacts uploaded | Client `DeviceContactsDataSource`, `ImportClientsImpl` |
| System device name and per-install device id; local data cleared on sign-out | Client `library/device`, `LogOutAction`, `docs/database/preferences.md` |
| No location or photo access and no other SDKs | Client manifests and dependency inventory; recheck current builds |

Never name BookkMe's hosting provider in these files. Use “our hosting provider” and the actual countries. The self-hosting template can name that deployment's hosting provider. Do not replace real hosting or processing countries with generic wording simply to make a document reusable.

## Values shared by both document sets

Replace every placeholder in the documents you publish. Resolve each optional square-bracket block independently: keep the applicable wording and remove its instruction, or remove the entire block. DPO, EU representative and UK representative are separate decisions. Their placeholders disappear if the corresponding blocks do not apply.

| Placeholder | Required value |
|---|---|
| `{{VERSION}}`, `{{EFFECTIVE_DATE}}` | Immutable document version and date of effect, aligned with `legal_document_version`; every translation of that version has the same meaning |
| `{{PRIVACY_EMAIL}}`, `{{SUPPORT_EMAIL}}` | Monitored privacy and support contacts; they may be the same address |
| `{{DATA_PROTECTION_LAW}}` | Actual applicable privacy laws after establishment and target-market assessment, including local supplements where relevant |
| `{{SUPERVISORY_AUTHORITY}}` | Name, website and contact details of the relevant privacy regulator; preserve GDPR complaint options in the text |
| `{{GOVERNING_LAW}}` | Chosen contract law with mandatory consumer protections preserved; it does not select the only applicable privacy law |
| `{{COURTS}}` | Lawful agreed venue for disputes with business users only; it must not override protected consumers' jurisdiction rights |
| `{{TRANSFER_SAFEGUARDS}}` | Templates only: the complete deployment-specific transfer description, including remote access. Identify applicable mechanisms and supplementary measures and how to obtain a copy. BookkMe's notice states its access arrangement directly |
| `{{EMAIL_RETENTION}}` | Self-hosting deployment's actual Postmark message retention, default 45 days; BookkMe's three notices already state its confirmed 45 days. Suppression records are separate; the server turns open and link tracking off on every email |
| `{{SUPPORT_RETENTION}}` | Templates only, default 24 months after the last message in the conversation, longer only for a legal claim; BookkMe's notices state that default. Support is by email only; align the mailbox/helpdesk process |
| `{{COMPLAINT_RESPONSE_TIME}}` | Actual complaint deadline meeting each target market's rules, including any shorter mandatory deadline; do not copy the privacy-rights deadline |
| `{{CONSUMER_INFORMATION}}` | Local pre-contract disclosures, applicable withdrawal conditions and model form, conformity remedies, and relevant ADR information; state any actual participation obligation or commitment |
| `{{TECHNICAL_REQUIREMENTS}}` | Verified OS and app requirements, internet and email access, passkey support, compatibility and accessibility information; make available before sign-up |
| `{{DPO_NAME}}`, `{{DPO_EMAIL}}` | DPO identity and contact, only if appointed |
| `{{EU_REPRESENTATIVE}}` | Article 27 GDPR representative identity and contact where required |
| `{{UK_REPRESENTATIVE}}` | UK GDPR representative identity and contact where required |

Use market-specific additions where required rather than hiding differences behind the generic placeholders. A Polish translation does not by itself supply the Polish consumer disclosures. Review Polish complaint handling, electronic-service rules and consumer-law applicability before targeting Poland; the review links primary sources.

## BookkMe operator details

| Placeholder | Required value |
|---|---|
| `{{COMPANY_NAME}}`, `{{COMPANY_FORM}}`, `{{COMPANY_REGISTRATION_NUMBER}}` | Actual operator legal name, form and public registration identifier |
| `{{COMPANY_ADDRESS}}`, `{{COMPANY_COUNTRY}}` | Registered postal address and country; no assumed establishment country |
| `{{ACCOUNT_DELETION_URL}}` | Public page explaining deletion without the app, including an identity-verification alternative when account email is inaccessible |
| `{{DPA_URL}}` | Published, versioned URL of the BookkMe data processing agreement, incorporated into business-owner terms; it is drafted with the BookkMe texts in all three languages and uses the same operator placeholders |

## Self-hosting details

Copy `template/` into your site and adapt it to your actual deployment and services.

| Placeholder | Required value or default |
|---|---|
| `{{SERVICE_NAME}}` | Name shown to clients, such as “Studio Nova Booking” |
| `{{BUSINESS_NAME}}`, `{{BUSINESS_LEGAL_NAME}}` | Trading name and registered legal name; add locally required registration details before publication |
| `{{BUSINESS_ADDRESS}}`, `{{BUSINESS_COUNTRY}}` | Registered postal address and country |
| `{{HOSTING_PROVIDER}}`, `{{HOSTING_LOCATION}}` | Actual hosting provider and server, database and backup countries |
| `{{CLIENT_RETENTION_MONTHS}}` | Configured value from 12 to 120, default 36; justify the period for the data and purpose |
| `{{LOG_RETENTION}}`, `{{BACKUP_RETENTION}}` | Actual deployment values; repository production defaults are 14 days and 30 days |
| `{{RECORD_KEEPING_LAW}}` | A specific applicable record-keeping obligation, if any; the app does not automatically implement legal holds |
| `{{INDIRECT_NOTICE_METHOD}}` | Actual method and timing for informing imported clients and employees without accounts; ensure required source information reaches them |
| `{{WHO_HAS_ACCESS}}` | Actual people or roles with production access |
| `{{MINIMUM_AGE}}` | Supported minimum account age; default 16, subject to capacity and local child-protection assessment |
| `{{RESPONSE_TIME}}` | Actual deadline for privacy requests outside GDPR; the text separately preserves the GDPR one-month deadline and shorter mandatory periods |
| `{{CURRENCY}}`, `{{PAYMENT_TERMS}}` | Currency and payment method for booked services |
| `{{SERVICE_TERMS_REFERENCE}}` | Accessible terms for the underlying services, provided before booking |
| `{{CANCELLATION_POLICY}}`, `{{LATE_MINUTES}}` | Lawful cancellation/no-show terms and tolerated lateness, disclosed before booking |
| `{{ALTERNATIVE_CONTACT}}` | Working alternative booking channel, such as a phone number or email |
| `{{OPERATOR_NAME}}` | Contractor operating the deployment, if applicable; conclude the required processing agreement |

Sign-up codes use Postmark and push notifications use Firebase Cloud Messaging in the current implementation; preserve those disclosures unless the implementation actually changes. Remove the Cloudflare row if that deployment does not use it. The optional Crashlytics content must match the actual app build and consent configuration.

Deleting the owner's account deletes the business and all its employees, clients and appointments. The same can happen after 3 years without use, subject to the active-member exception. Export required business records first and arrange any separate legally required retention.

## Before publication

1. Complete the operator and target-market assessment in the [review](legal-review.md), obtain local legal review, and resolve every placeholder and optional block in all published languages.
2. Publish the BookkMe-to-business DPA, which is drafted, and keep its commitments: publish a new DPA version before a subprocessor change takes effect, and notify business owners of a breach without undue delay. The follow-up audit verifies incorporated DPAs in standard provider terms; retain the applicable contracts and check any custom agreement without assuming a separate signature is required. Keep the private hosting-provider inventory.
3. Keep a current list of the countries BookkMe staff access production data from, to answer requests; the notice and DPA state that access happens from where staff are located. The three named providers' published mechanisms are linked; assess any necessary supplementary measures, and do not treat ordinary service necessity as a general transfer exemption.
4. Crashlytics is now off by default and opt-in in the app. Verify clean-install, opt-in, opt-out and upgraded-device behaviour on both platforms, and check website cookies.
5. Record the chosen business-lifetime appointment history without adding an age-based deletion job, preserve client/account erasure and handle retained personal text appropriately. Delete support mail after 24 months in the mailbox. BookkMe Postmark message retention is confirmed as 45 days and tracking is off per email; check suppression/deletion handling, encryption and restore controls.
6. Establish complaint, moderation and privacy-request handling, with deadlines, review of errors, any necessary restriction or correction of retained text, and an identity-verification route. Set up legally required advance notices and durable confirmations; an in-app acceptance prompt alone is not evidence these processes work.
7. Confirm BookkMe remains free. Complete target-market consumer information and verify technical requirements, account age/capacity rules and underlying booking terms.
8. Publish all three languages under immutable URLs for the same version, for example language-specific pages linked by a version landing page. Then insert `legal_document_version` rows as described in [publishing instructions](../legal-documents.md). Its single `url` must let users reach every published language of that version; provide visible language links even when choosing a default from browser settings. Publish a new version for any previously published text that needs correction.
