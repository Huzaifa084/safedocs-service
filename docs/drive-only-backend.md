# SafeDocs Backend: Drive-Only Storage Migration

This document explains the Drive-only backend changes and the new client flow.

## Summary

- Backend stores metadata only; Google Drive is the file authority.
- Backend never receives file uploads and never holds Drive tokens.
- Sharing and family changes enqueue permission jobs; the client executes Drive changes.
- Storage (MinIO/S3) and `/api/storage/**` endpoints are removed.

## Breaking Changes

- `POST /api/documents` now accepts JSON only (no multipart).
- `GET /api/documents?visibility=...` replaces `type=...` (type now returns 400).
- `GET /api/documents/{id}/download` always returns 410 Gone.
- `/api/storage/**` endpoints are removed.
- Document storage fields are removed (no `storageKey`, `storageFilename`, etc.).

## Data Model (Frontend-Relevant)

Document fields exposed to the client:

- `publicId` (UUID, API identifier)
- `driveFileId` (string, required)
- `fileName` (string, Drive filename)
- `title` (string, user editable; default = `fileName`)
- `category` (string)
- `visibility` (PERSONAL | FAMILY | SHARED)
- `familyId` (UUID, nullable; required when visibility = FAMILY)
- `referenceType` (FILE | SHORTCUT, default FILE)
- `mimeType` (string)
- `sizeBytes` (number, nullable)
- `status` (ACTIVE | DELETED_OR_REVOKED)
- `driveCreatedAt` (timestamp, nullable)
- `driveWebViewLink` (string, nullable)
- `driveMd5` (string, nullable)
- `accessLevel` (OWNER | WRITER | READER, nullable)
- `createdAt` / `updatedAt` (server controlled)

Permission job fields exposed to the client:

- `jobId` (UUID)
- `documentPublicId` (UUID)
- `driveFileId` (string)
- `targetUserEmail` (string)
- `action` (GRANT | REVOKE)
- `familyId` (UUID, nullable)
- `status` (PENDING | DONE | FAILED)
- `attempts` (int)
- `lastError` (string, nullable)
- `createdAt` / `updatedAt`

Important invariants:

- If `visibility = FAMILY`, `familyId` must be set.
- If `visibility != FAMILY`, `familyId` must be null.
- `ownerUserId` is derived from auth and never exposed.

## API Reference (Metadata-Only)

All responses are wrapped by `BaseResponse`:

```
{ "success": true, "message": "...", "data": ... }
```

### Create/Upsert Document

`POST /api/documents`

Behavior:

- Upsert by `(ownerUserId, driveFileId)`.
- If existing doc is `DELETED_OR_REVOKED`, it is revived to `ACTIVE`.
- `title` defaults to `fileName` if blank.

Request:

```
{
  "driveFileId": "1a2b3c",
  "fileName": "Passport.pdf",
  "title": "Passport",
  "mimeType": "application/pdf",
  "sizeBytes": 245120,
  "visibility": "PERSONAL",
  "category": "ID",
  "familyId": null,
  "driveCreatedAt": "2026-01-01T10:30:00Z",
  "driveWebViewLink": "https://drive.google.com/file/d/1a2b3c/view",
  "driveMd5": "a1b2c3",
  "accessLevel": "OWNER",
  "referenceType": "FILE"
}
```

### Update Document Metadata

`PUT /api/documents/{publicId}`

Updatable fields only:

- `title`, `category`, `visibility`, `familyId`

Immutable (400 if included):

- `driveFileId`, `fileName`, `referenceType`, `storageProvider`, `accessLevel`

Request:

```
{
  "title": "Passport (Renewed)",
  "category": "ID",
  "visibility": "PERSONAL",
  "familyId": null
}
```

### List Documents

`GET /api/documents?visibility=PERSONAL|FAMILY|SHARED&familyId=...`

Notes:

- `type=` is rejected with 400.
- Results include only `ACTIVE` documents.
- Search matches `title`, `category`, and `fileName`.

### Document Details

`GET /api/documents/{publicId}`

Returns metadata, including `driveFileId`.

### Delete Document Metadata

`DELETE /api/documents/{publicId}`

Behavior:

- Soft delete (`status = DELETED_OR_REVOKED`).
- Enqueues REVOKE jobs for FAMILY or SHARED docs.
- Does not delete Drive file bytes.

### Download (Deprecated)

`GET /api/documents/{publicId}/download`

Response: 410 Gone

Message: `Stored in Google Drive. Use Drive API.`

### Reconciliation

`POST /api/documents/reconcile`

Use when Drive returns 403/404:

```
{
  "missing": [
    { "publicId": "doc_123", "reason": "ACCESS_DENIED" },
    { "driveFileId": "4d5e6f", "reason": "NOT_FOUND" }
  ]
}
```

Backend marks matching docs `DELETED_OR_REVOKED`.

### Share Metadata (Queues Permission Jobs)

`POST /api/documents/{publicId}/share`

```
{ "emails": ["member@example.com"] }
```

Behavior:

- Only allowed when `visibility = SHARED`.
- Creates/rehydrates share records.
- Enqueues GRANT jobs for each recipient.

`DELETE /api/documents/{publicId}/share/{shareId}`

Behavior:

- Marks share REVOKED.
- Enqueues REVOKE job.

`GET /api/documents/{publicId}/share`

Returns active share recipients.

### Shared With Me

`GET /api/documents/shared/with-me`

Returns documents shared to the authenticated email.

## Permission Jobs API

These jobs are consumed by the owner’s client to apply Drive permissions.

### Create Jobs (Batch)

`POST /api/permissions/jobs`

Notes:

- `ownerUserId` in payload is ignored; owner is derived from auth.
- Jobs are idempotent by `(documentPublicId, ownerUserId, targetUserEmail, action)`.

```
{
  "jobs": [
    {
      "documentPublicId": "doc_123",
      "driveFileId": "1a2b3c",
      "targetUserEmail": "member@example.com",
      "action": "GRANT",
      "familyId": "fam_abc"
    }
  ]
}
```

### List Jobs (Owner Only)

`GET /api/permissions/jobs?status=PENDING&ownerUserId=me`

Notes:

- `ownerUserId` must be `me`.
- Default status = `PENDING`.

### Update Job (Owner Only)

`PATCH /api/permissions/jobs/{jobId}`

Rules:

- Only owner can update.
- Only `PENDING -> DONE` or `PENDING -> FAILED`.

```
{
  "status": "DONE",
  "attempts": 1,
  "lastError": null
}
```

## Client Flow (Updated)

### Upload + Save Metadata

1. Upload file to Google Drive using Drive SDK.
2. Capture `driveFileId`, `fileName`, `mimeType`, `sizeBytes`.
3. `POST /api/documents` with metadata.

### Sharing Flow

1. User triggers share in app.
2. Call `POST /api/documents/{id}/share`.
3. Owner app pulls `/api/permissions/jobs?status=PENDING&ownerUserId=me`.
4. Owner app applies Drive permissions (GRANT).
5. `PATCH /api/permissions/jobs/{jobId}` -> DONE/FAILED.

### Unshare Flow

1. Call `DELETE /api/documents/{id}/share/{shareId}`.
2. Owner app processes REVOKE jobs and marks them DONE/FAILED.

### Family Hooks (Automatic)

Enqueued by backend:

- Invite accepted -> GRANT for all FAMILY docs.
- New FAMILY document -> GRANT for all family members.
- Member removed/left -> REVOKE for all FAMILY docs.
- Family deleted -> REVOKE for all FAMILY docs.

Owner app still executes Drive changes and updates jobs.

### Reconcile Flow

If Drive returns 403/404:

1. Call `POST /api/documents/reconcile`.
2. Backend marks metadata as `DELETED_OR_REVOKED`.

## Migration Notes (Frontend)

- Remove all multipart uploads to backend.
- Remove `/api/storage/**` usage.
- Switch document listing to `visibility=` parameter.
- Expect 410 for `/download` (use Drive API instead).
- Ensure sharing and family actions now rely on permission jobs.
- Assume no MinIO data exists; clean state is expected.

## Error Notes

Common 400 responses:

- `Use visibility= instead of type=`
- `familyId is required for FAMILY documents`
- `familyId must be null unless visibility is FAMILY`
- `Drive fields are immutable and can only be set during creation`
- `ownerUserId must be 'me'`

## Enums (Client Reference)

```
DocumentVisibility: PERSONAL | FAMILY | SHARED
DocumentStatus: ACTIVE | DELETED_OR_REVOKED
DocumentReferenceType: FILE | SHORTCUT
DocumentAccessLevel: OWNER | WRITER | READER
PermissionJobAction: GRANT | REVOKE
PermissionJobStatus: PENDING | DONE | FAILED
```
