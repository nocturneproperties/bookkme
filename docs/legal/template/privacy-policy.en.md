# {{SERVICE_NAME}} Privacy Policy

Version: {{VERSION}} · Effective from: {{EFFECTIVE_DATE}}

This policy explains what personal data {{BUSINESS_NAME}} collects when you use {{SERVICE_NAME}}, our online booking service, why, who sees it, how long we keep it, and what rights you have.

This document is available in English, Polish and Ukrainian. Each language describes the same policy; no language automatically overrides another or limits mandatory local rights. Contact us if you find a difference.

## 1. Who we are

{{SERVICE_NAME}} is run by {{BUSINESS_LEGAL_NAME}}, {{BUSINESS_ADDRESS}}, {{BUSINESS_COUNTRY}} ("we", "us"). We are the controller of the personal data described here.

Questions about this policy or your data: {{PRIVACY_EMAIL}}.

[If appointed: Our data protection officer is {{DPO_NAME}}, {{DPO_EMAIL}}.]

[If required under Article 27 GDPR: Our representative in the EU is {{EU_REPRESENTATIVE}}.]

[If required under UK GDPR: Our representative in the UK is {{UK_REPRESENTATIVE}}.]

We process personal data under {{DATA_PROTECTION_LAW}}, including GDPR where it applies. The language you choose does not determine which laws protect you.

{{SERVICE_NAME}} runs on Bookk, booking software that we operate ourselves. The authors of the Bookk software do not receive or have access to your data.

[If a contractor operates the service for you: {{OPERATOR_NAME}} operates the service on our behalf as our processor.]

## 2. What data we collect

**Account.** Your first and last name, your email address (confirmed with a one-time code before the account is created), and, if you add it, your phone number.

**Sign-in.** We use passkeys instead of passwords. We store your passkey's public key and identifier, never your fingerprint, face or device PIN, which stay on your device. For each device you sign in on we store an identifier the app creates when it is installed, the device name the system reports (usually its model), sign-in tokens, and when you last signed in. We record the date you were last active so that we can delete unused accounts (section 7).

**Notifications.** Your device's push notification token and language, your notification settings, and whether you turned on email notifications.

**Legal document records.** Which versions of this policy you acknowledged and of the Terms of Service you accepted, and when.

**Support.** If you contact us by email, we receive your email address, the contents of your message and anything you attach, such as usage logs.

**Your client record and appointments.** Your name, phone and email, appointments and appointment requests (services, employee, time, price, status, the note you write, cancellation and decline reasons, completion details and price adjustments with their reasons), and any description our staff add to your client record. When you change your name, phone or email in your profile, your client record and employee profile are updated to match; if we already had another client record with your new confirmed email, it is merged into yours.

**Sources.** We receive information from you, our staff who create client or employee records, the person booking for you, and selected device contacts our staff lawfully import. We provide privacy information to people who do not have an account through {{INDIRECT_NOTICE_METHOD}}, within the applicable legal deadlines.

**If you work for us.** Your employee profile (name, contact details, services, working hours, days off, permissions, and whether your access is suspended) and the appointments assigned to you.

**Data exports** you request, kept until they expire (section 7).

**Contacts, only if our staff import clients.** Our staff can import clients from their phone's contacts after allowing the app to access them. The app reads the contacts on the device and shows them; only the contacts selected (name, phone and email) are sent to our server and become client records. The address book itself is never uploaded.

[If your app build includes Firebase Crashlytics, as the BookkMe client does: **Crash reports.** When the app crashes, it sends Firebase Crashlytics technical details about the error and your device (model, operating system and app version, an installation identifier, and the time of the crash), so we can fix it. We do not deliberately attach your account identifier, name, email or business/client records. Crash traces can include exception messages, so reports are not guaranteed to be anonymous. Crash reports are off by default and are sent only if you turn them on in Settings → Account → Privacy. You can turn them off there at any time, which also deletes reports not yet sent.]

**On your device.** The app keeps a copy of the data you see on your device so it opens quickly. The copy is deleted when you sign out. If you turn on biometric unlock, the check happens on your device and we receive nothing.

**Technical data.** Your IP address and request details pass through our servers to route requests and protect the service against abuse. Our server logs may contain your IP address, account identifiers and error details.

We do not access your location or photos through {{SERVICE_NAME}}, the app uses no analytics or advertising identifiers, our emails do not track whether you open them or click their links, we do not use your data for advertising, and we do not sell it.

## 3. Why we use it and on what basis

| Purpose | Data | Basis |
|---|---|---|
| Create and run your account, sign you in, keep your devices signed in | Account, sign-in | Performing our contract with you (the Terms of Service) |
| Book, change and cancel your appointments and provide the booked services | Account, client record, appointments | Performing our contract with you |
| Keep a record of our clients and their visits | Client record, appointments | Our legitimate interest in running our business [and, where it applies, our legal duty to keep records: {{RECORD_KEEPING_LAW}}] |
| Send booking and account notifications by push, and by email if you turn it on | Notifications, appointments | Performing our contract with you |
| Send account notices (deletion of an unused account, a data export is ready) by email | Account | Performing our contract with you |
| Organise our staff's work: schedules, services, permissions | Employee profile | Your employment or service contract with us |
| Record terms acceptance and receipt of privacy information | Legal document records | Our legitimate interest in documenting the agreement and the privacy information provided |
| Keep the service secure, prevent abuse, find and fix errors | Technical data, sign-in | Our legitimate interest in a secure, working service |
| [With Crashlytics] Find and fix app crashes | Crash reports | Your consent, given by turning crash reports on; you can withdraw it by turning them off |
| Answer support requests | Support emails and their attachments | Our legitimate interest in resolving requests and maintaining the service |
| Answer your requests and exercise of your rights; comply with the law | What the request or law needs | Our legal obligations |

Acknowledging this policy records that you received the information; it is not consent to every processing activity. Where consent is required, it must be requested separately for a specific purpose and can be withdrawn through {{PRIVACY_EMAIL}} or the relevant setting, without deleting your account. Withdrawal does not affect earlier lawful processing.

**What you must give us.** To create an account you need to give your first and last name, confirm your email address and create a passkey; without them we cannot create the account. Your phone number, a note on a booking and notifications[ and crash reports] are optional.

**Automation.** We do not profile you or use automated scoring for eligibility, pricing or advertising. Retention and dormancy deletions run automatically as described in section 7, including deletion of an unused account after three warnings. Contact {{PRIVACY_EMAIL}} if you believe an automatic deletion is incorrect.

## 4. Notes and sensitive information

Use notes and client descriptions only for information needed to arrange an appointment. Do not enter health information or other specially protected data unless we have first explained the purpose and established the additional legal condition required for that processing. Where explicit consent is required, we must obtain it separately; typing a note or acknowledging this policy does not by itself provide it. This booking service is not a medical record system.

Cancellation, decline and price adjustment reasons must not contain personal details or sensitive information. Cancellation and price adjustment reasons remain in the appointment record after client details are removed.

## 5. Who sees your data

- **Our staff** see what their role requires: employees see the appointments and client records their permissions allow.
- **Clients** see our business profile and the name of the employee serving them.
- **Our service providers**, acting as processors for customer data under our instructions. Some also act as controllers for specified service or network data under their own privacy notices:

| Provider | What it does | Where |
|---|---|---|
| {{HOSTING_PROVIDER}} | Servers, database and backups | {{HOSTING_LOCATION}} |
| Google (Firebase Cloud Messaging[, Firebase Crashlytics]) | Delivers push notifications[; collects crash reports] | Global processing, including the United States |
| Postmark | Sends emails (sign-up codes, notifications, account notices) | United States |
| [If you put Cloudflare in front of the service] Cloudflare, Inc. | DNS, reverse proxy, DDoS protection and other network security | Global network; United States company |

- **Authorities**, when the law requires it.

Cloudflare describes its separate processing of network and service data in its [privacy policy](https://www.cloudflare.com/privacypolicy/). Google describes Firebase service-data processing in its [privacy information](https://firebase.google.com/support/privacy).

## 6. Transfers to other countries

Your data is stored in {{HOSTING_LOCATION}}. Google, Postmark [and Cloudflare] may process data in the United States and through their service networks. Access by us or our contractors from another country can also constitute an international transfer.

The countries, recipients and safeguards for each transfer, including remote access, are described here: {{TRANSFER_SAFEGUARDS}}. This information identifies the applicable transfer arrangements, including any adequacy decision or standard contractual clauses and supplementary measures. To request a copy of relevant safeguards, contact {{PRIVACY_EMAIL}}; confidential information may be redacted.

## 7. How long we keep it

| Data | How long |
|---|---|
| Your account | Until you delete it. An account not used for 3 years is deleted; we warn you 60, 30 and 7 days before. An owner's account is kept while another member of the business has been active within that time. |
| A device you signed in on | Deleted after 30 days without use. |
| Email confirmation codes | 10 minutes. |
| Your client record | Until {{CLIENT_RETENTION_MONTHS}} months after your last visit or the last change to it, then deleted automatically. We can delete it sooner. |
| Appointments | Kept while the business exists in {{SERVICE_NAME}}, with no automatic age-based deletion of appointment history. Client details and notes are removed when the client record is erased, including under the client's separate retention period. |
| Appointment requests | 60 days after the requested appointment ends. |
| Data exports | 72 hours. |
| Legal document records | Until your account is deleted. |
| Support correspondence | {{SUPPORT_RETENTION}}. |
| Records that an account or client was erased (identifiers only) | 60 days, so that an erasure can be repeated after a backup is restored. |
| Server logs | {{LOG_RETENTION}}. |
| Internal messages between parts of our service that could not be delivered (may contain booking or profile details) | 30 days. |
| Email content, activity and message metadata kept by Postmark | {{EMAIL_RETENTION}}. |
| Postmark suppression records, such as bounced or complaining email addresses | Kept beyond the message-retention period to prevent further unwanted or undeliverable emails; contact us about erasure requests. |
| [With Crashlytics] Crash reports | Google starts removing crash traces and associated identifiers from live and backup systems after 90 days; this is not a guaranteed completion deadline. |
| Backups | {{BACKUP_RETENTION}}. Names, contact details, notes and client descriptions in them become unreadable {{KEY_BACKUP_RETENTION}} after the person is erased (section 8). |

When you delete your account, we delete your profile, sign-in data, devices and notification settings, delete your client and employee records, cancel your upcoming appointments, and erase your client details and note from appointments and replace your employee name and remove the employee account link. Cancellation and price adjustment reasons and other appointment details remain; they must not contain personal details. Remaining appointment identifiers, service and time details, or identifying text in other fields can still allow a person to be identified. Your name, contact details and notes are encrypted with a key of your own, which the deletion destroys. Copies of that key in our backups are deleted within {{KEY_BACKUP_RETENTION}}, so after that those details can no longer be read from any backup; your other data leaves the backups as they expire. If we ever restore a backup taken before the deletion, we erase your data again before the service resumes.

If you own the business, deleting your account also deletes the business, its employees, clients, services and appointments, including when your unused account is deleted. Export the business's data and satisfy any separate legal record-keeping duties before deletion.

## 8. How we protect it

Sign-in uses passkeys, so there is no password to steal. Connections are encrypted (TLS). Each person's name, email address, phone number, notes and client description, and the addresses we send notifications to, are encrypted with a key of that person's own, which is itself encrypted with a master key kept outside the database; deleting the person destroys their key (section 7). To find an account or client by email address or phone number without decrypting it, we store a keyed hash (HMAC) of it, deleted with the record. Each employee only sees what their permissions allow. [Describe only measures you actually have, for example: The database and backups are encrypted at rest, and backups are kept on a separate server. Access to production data is limited to {{WHO_HAS_ACCESS}}.]

## 9. Your rights

You have the right to:

- **access and export your data.** In the app, request a data export (once a day); it is kept for 72 hours.
- **correct it.** Edit your profile in the app. Changing your email address needs a code sent to the new address.
- **delete it.** Delete your account in the app; this is confirmed with your passkey. To have only your client record deleted, contact us. If you can no longer use the app, write to {{PRIVACY_EMAIL}} from your account's email address.
- **object** to processing based on our legitimate interests, and ask us to **restrict** processing.
- **withdraw your consent** at any time, without affecting what we did before.
- **complain** to {{SUPERVISORY_AUTHORITY}}. Where GDPR applies, you may also complain to a supervisory authority in the EEA country of your habitual residence, workplace or the alleged infringement.

These rights apply under the conditions and exceptions in the applicable law; portability is distinct from the app's export feature.

For anything you cannot do in the app, write to {{PRIVACY_EMAIL}}. The app's daily export limit and 72-hour download window do not limit your statutory rights. We may ask for proportionate information to verify your identity, including an alternative if you cannot access your account email. Where GDPR applies, we respond without undue delay and within one month; a permitted extension of up to two further months is explained within the first month. Any shorter mandatory local deadline applies. Requests are normally free; any lawful refusal or fee is explained with the available remedies. For requests outside GDPR, the applicable response period is {{RESPONSE_TIME}}, subject to any shorter mandatory deadline.

## 10. Age

You must be at least {{MINIMUM_AGE}} to create an account. We do not knowingly allow anyone younger to create an account through {{SERVICE_NAME}}. [If you serve children: A parent or guardian books from their own account. Explain the lawful basis and safeguards for any information about the child before collecting it; sensitive data requires the additional safeguards in section 4.]

## 11. Changes to this policy

When we change this policy we publish a new version with a new date and keep earlier versions available. The app asks you to read and acknowledge the new version; that acknowledgement is not blanket consent to processing.

## 12. Contact

{{BUSINESS_LEGAL_NAME}}, {{BUSINESS_ADDRESS}}, {{BUSINESS_COUNTRY}}. Email: {{PRIVACY_EMAIL}}.
