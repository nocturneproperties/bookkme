# Legal documents

The user service records which version of the privacy policy and the terms of service each user accepted, and when. The server publishes the version in effect. Sign-up must record the version in effect of every published document under the current product rules. After that, the app compares the version in effect with what the user accepted and asks them to accept a new one; the server doesn't block an existing user who hasn't.

The texts themselves, for BookkMe and as a template for self-hosting businesses, are in [`docs/legal/`](legal/README.md), in English, Polish and Ukrainian. Complete the operator details and publication dependencies in that README and the [legal review](legal/legal-review.md) before publication.

The API calls both records “acceptance”; for a privacy policy this documents receipt of information, not blanket consent to processing. The client must distinguish accepting the terms from acknowledging privacy information. Any purpose-specific consent required by applicable law needs a separate flow and record; this API does not provide one.

## Publishing a version

A version is a row in `user.legal_document_version`, inserted by hand. There is no admin route. In prod, insert it in NocoDB through the `user` source that logs in as `nocodb_writer`, which may only read and insert rows of this table (no `UPDATE` or `DELETE`, no other table; see NocoDB in `deployment/ansible/README.md`), or with the statement below.

```sql
INSERT INTO `user`.legal_document_version (document, version, url, effective_at)
VALUES ('PRIVACY_POLICY', '2026-11-03', 'https://bookkme.com/privacy/2026-11-03', '2026-11-03 00:00:00');
```

- `document` is `PRIVACY_POLICY` or `TERMS_OF_SERVICE` (stored by name).
- `version` is any string up to 32 characters, unique per document. A date is the convention.
- `url` is where the app links to the text of that version (up to 512 characters). Link to a page that keeps this version's text, not to one that always shows the latest, so the record still shows what the user read.
- Each version's page must give visible links to its English, Polish and Ukrainian texts. The single stored URL can point to a version-specific language selector; every translation must retain that version's meaning and remain available. Language does not select governing law or override mandatory local rights.
- `effective_at` is a UTC time. The version in effect is the row with the latest `effective_at` that has passed, so a version can be published ahead and takes effect on its own. Until then, nobody can accept it and `GET /api/user/legal` still returns the previous one.

A document with no row in effect is left out of both read routes and needs no acceptance at sign-up. Publishing a version makes it required for every sign-up from that moment, so publish it when the text is live at its link.

Never delete or edit a version once it is published. Users' acceptances reference it (the foreign key refuses to delete an accepted version), and it is the record of what they agreed to. To fix a mistake, publish a new version.

## Routes

| Route | Auth | What it does |
|---|---|---|
| `GET /api/user/legal` | none | The version in effect of each document, with its link (`LegalDocumentVersion`). |
| `GET /api/user/me/legal` | jwt | The same versions, each with the time the caller accepted it, or null (`LegalDocumentStatus`). |
| `POST /api/user/me/legal` | jwt | Records the caller's acceptance of the sent versions (`AcceptedLegalDocuments`), 204. See [Accept legal documents](operations/user/accept-legal-documents.md). |
| `POST /api/auth/sign_up` | none | `acceptedDocuments` (required, 400 without it) must name the version in effect of every published document, otherwise 422 `LEGAL_DOCUMENTS_NOT_ACCEPTED` (100004) and no account. Recorded in the same transaction as the profile. See [Sign-up](operations/authorization/sign-up.md). |

Only a published version already in effect can be accepted. Anything else is 422 `LEGAL_DOCUMENT_VERSION_UNKNOWN` (100003), on sign-up too. Accepting a version again keeps the first time.

## Storage and lifetime

`user.legal_document_acceptance` keeps one row per user and accepted version, never updated, so it shows every version a user agreed to over time. The rows are deleted with the profile when the account is deleted (`ON DELETE CASCADE`). See the [user schema](database/user.md).
