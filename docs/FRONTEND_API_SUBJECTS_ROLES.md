# SafeDocs Backend – Frontend API Integration Notes (Subjects + Roles)

Date: 2026-01-05

This document summarizes **new APIs** and **changes to existing APIs** for the Subjects/Collections feature and expanded Family roles.

Clarified semantics (locked):
- `Subject.lastDocumentActivityAt` is intended to drive the Subject workspace header "Updated Xd ago".
- It is updated only for learning-relevant activity: `UPLOAD`, `MOVE` (subject change), `DELETE`, `DOWNLOAD`.
- It is **not** updated for cosmetic edits like document rename, category changes, or subject metadata edits.

---

## 1) Common response envelope

Most endpoints return a JSON envelope:

```json
{
  "success": true,
  "message": "OK",
  "data": { }
}
```

Notes:
- The payload you want is in `data`.
- On errors, `success` is `false` and the backend returns an error HTTP status (400/401/403/404/409).

Action codes (when using the envelope):
- `BAD400` for most 400s thrown via `BadRequestException`
- `FOR403` for forbidden
- `ATH401` for unauthorized
- `NFD404` for not found
- `DUP409` for conflict

---

## 2) Enums (strings)

### 2.1 `SubjectScope`
- `PERSONAL`
- `FAMILY`

### 2.2 `DocumentVisibility`
- `PERSONAL`
- `FAMILY`
- `SHARED`

### 2.3 `FamilyRole`
- `HEAD`
- `CONTRIBUTOR`
- `VIEWER`

---

## 3) Subjects / Collections API (NEW)

Base path: `/api/subjects`

### 3.1 List subjects
`GET /api/subjects?scope=PERSONAL|FAMILY&familyId=<uuid?>&page=0&size=20`

Query params:
- `scope` (required): `PERSONAL` or `FAMILY`
- `familyId` (required when `scope=FAMILY`, forbidden/ignored when `scope=PERSONAL`)
- `page` (optional, default `0`)
- `size` (optional, default `20`)

Response (`data`):
```json
{
  "items": [
    {
      "id": "2c92d7d5-8b3b-4c0a-bf2d-22e7a7dcb35a",
      "name": "Math",
      "semesterLabel": "Semester 2",
      "scope": "FAMILY",
      "familyId": "9aa4a174-4c5b-4b1d-ae52-35ce5cf10b2b",
      "ownerUserId": null,
      "documentCount": 12,
      "createdAt": "2026-01-05T08:15:30.000",
      "updatedAt": "2026-01-05T09:20:10.000",
      "lastDocumentActivityAt": "2026-01-05T11:05:00.000"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

Default ordering:
- Sorted by `name` ascending (case-insensitive). Stable ordering for UX.

### 3.2 Create subject
`POST /api/subjects`

Request:
```json
{
  "name": "Math",
  "semesterLabel": "Semester 2",
  "scope": "FAMILY",
  "familyId": "9aa4a174-4c5b-4b1d-ae52-35ce5cf10b2b"
}
```

Rules:
- `scope=PERSONAL`: `familyId` must be omitted/null.
- `scope=FAMILY`: `familyId` is required.
- **FAMILY subjects are HEAD-only** to create/rename/delete.

Response (`data`): `SubjectListItem` (same shape as list items).

### 3.2.1 Update subject metadata (NEW)
`PATCH /api/subjects/{subjectId}/metadata`

Request:
```json
{
  "semesterLabel": "Semester 2"
}
```

Notes:
- `semesterLabel` is optional/nullable.
- `lastDocumentActivityAt` is not affected by this endpoint (by design).

### 3.3 Rename subject
`PUT /api/subjects/{subjectId}`

Request:
```json
{
  "name": "Math (Semester 2)"
}
```

Response (`data`): `SubjectListItem`.

### 3.4 Delete subject
`DELETE /api/subjects/{subjectId}`

Rules:
- Deleting a subject will **move all documents in that subject to Uncategorized** (`subjectId = null`), then delete the subject.
- This is an explicit backend behavior (no implicit orphan references).

---

## 4) Documents API (CHANGED)

Base path: `/api/documents`

### 4.1 Create document (metadata upsert)
`POST /api/documents`

New field:
- `subjectId` (optional): UUID of a subject/collection.

Request example (FAMILY document in a subject):
```json
{
  "driveFileId": "1AbC...",
  "fileName": "chapter1.pdf",
  "title": "Chapter 1",
  "mimeType": "application/pdf",
  "sizeBytes": 123456,
  "visibility": "FAMILY",
  "familyId": "9aa4a174-4c5b-4b1d-ae52-35ce5cf10b2b",
  "subjectId": "2c92d7d5-8b3b-4c0a-bf2d-22e7a7dcb35a",
  "driveCreatedAt": "2026-01-05T10:10:10Z",
  "driveWebViewLink": "https://drive.google.com/file/d/.../view",
  "driveMd5": "...",
  "accessLevel": "...",
  "referenceType": "FILE"
}
```

Subject rules:
- If `visibility=FAMILY`, `subjectId` (if provided) must be a **FAMILY** subject belonging to the same `familyId`.
- If `visibility!=FAMILY`, `subjectId` (if provided) must be a **PERSONAL** subject owned by the current user.

Nullability:
- `category` can be `null` in responses (and can be omitted/null in requests). FE should treat it as nullable.

Role rules (FAMILY docs):
- Only `HEAD` or `CONTRIBUTOR` can create/update.

Response (`data`) includes `subjectId`:
```json
{
  "publicId": "d9c7e874-4a7a-4a55-98d1-7d8b0c7edfd7",
  "driveFileId": "1AbC...",
  "fileName": "chapter1.pdf",
  "title": "Chapter 1",
  "mimeType": "application/pdf",
  "sizeBytes": 123456,
  "visibility": "FAMILY",
  "category": null,
  "familyId": "9aa4a174-4c5b-4b1d-ae52-35ce5cf10b2b",
  "subjectId": "2c92d7d5-8b3b-4c0a-bf2d-22e7a7dcb35a",
  "referenceType": "FILE",
  "status": "ACTIVE",
  "driveCreatedAt": "2026-01-05T10:10:10Z",
  "driveWebViewLink": "https://drive.google.com/file/d/.../view",
  "driveMd5": "...",
  "accessLevel": "...",
  "createdAt": "2026-01-05T10:11:00",
  "updatedAt": "2026-01-05T10:11:00"
}
```

### 4.2 Update document (title/category/visibility/family/subject)
`PUT /api/documents/{id}`

New field:
- `subjectId` (optional): set to a UUID to change subject.

Important behavior:
- If you **omit** `subjectId`, the subject stays unchanged.
- To **clear** a subject, use the dedicated PATCH endpoint below.

### 4.3 Update only subject (NEW)
`PATCH /api/documents/{id}/subject`

Request:
```json
{ "subjectId": "2c92d7d5-8b3b-4c0a-bf2d-22e7a7dcb35a" }
```

Clear subject:
```json
{ "subjectId": null }
```

Behavior for empty body:
- `{}` is treated the same as `{ "subjectId": null }` (i.e., clears the subject). If you want a no-op, don’t call this endpoint.

Response (`data`): `DocumentResponse`.

### 4.3.1 Bulk subject assignment (NEW)
`PATCH /api/documents/subject/bulk`

Request:
```json
{
  "documentIds": [
    "d9c7e874-4a7a-4a55-98d1-7d8b0c7edfd7",
    "2e0b1b79-5b9b-4a58-9a3e-7e7ccf4b1185"
  ],
  "subjectId": "2c92d7d5-8b3b-4c0a-bf2d-22e7a7dcb35a"
}
```

Clear subject for all:
```json
{
  "documentIds": ["..."],
  "subjectId": null
}
```

Response (`data`):
```json
{
  "updated": ["d9c7e874-4a7a-4a55-98d1-7d8b0c7edfd7"],
  "failed": [
    { "id": "2e0b1b79-5b9b-4a58-9a3e-7e7ccf4b1185", "reason": "PERMISSION_DENIED" }
  ]
}
```

Notes:
- Partial success is allowed.
- Per-document rules are identical to single-item `PATCH /api/documents/{id}/subject` (including FAMILY role checks).
- `reason` values: `NOT_FOUND`, `PERMISSION_DENIED`, `INVALID_SUBJECT`, `INVALID_REQUEST`.

### 4.3.2 Bulk delete (NEW)
`DELETE /api/documents/bulk`

Request:
```json
{
  "documentIds": [
    "d9c7e874-4a7a-4a55-98d1-7d8b0c7edfd7",
    "2e0b1b79-5b9b-4a58-9a3e-7e7ccf4b1185"
  ]
}
```

Response (`data`):
```json
{
  "deleted": ["d9c7e874-4a7a-4a55-98d1-7d8b0c7edfd7"],
  "failed": [
    { "id": "2e0b1b79-5b9b-4a58-9a3e-7e7ccf4b1185", "reason": "PERMISSION_DENIED" }
  ]
}
```

Notes:
- Soft delete: backend sets `status = DELETED_OR_REVOKED`.
- FAMILY docs require `HEAD` (same rule as single delete).

### 4.4 List documents (filters updated)
`GET /api/documents`

Query params:
- `visibility` (required): `PERSONAL|FAMILY|SHARED`
- `category` (optional)
- `search` (optional)
- `page` (default `0`)
- `size` (default `20`)
- `familyId` (optional; only meaningful for `visibility=FAMILY`)
- `subjectId` (optional): UUID string (example: `2c92d7d5-8b3b-4c0a-bf2d-22e7a7dcb35a`)
- `uncategorized` (optional boolean; default `false`)
- `createdAfter` (optional): date string `yyyy-MM-dd`
- `createdBefore` (optional): date string `yyyy-MM-dd`

Rules:
- `subjectId` and `uncategorized=true` are **mutually exclusive**.
- If both are provided (`subjectId` + `uncategorized=true`), backend returns **400** with actionCode `BAD400`.
- When filtering by `subjectId`, backend validates the subject belongs to you (PERSONAL) or to a family you’re in (FAMILY).
- If `visibility=FAMILY` and both `familyId` and `subjectId` are provided, they must refer to the same family.

Date filter semantics:
- `createdAfter=2026-01-01` means **created on/after** 2026-01-01 00:00.
- `createdBefore=2026-01-31` means **created before** 2026-02-01 00:00 (end-exclusive), so the whole day 2026-01-31 is included.
- `createdAfter` and `createdBefore` can be combined with `subjectId` (or with `uncategorized=true`).

---

## 5) Activity Logging (Backend foundation)

The backend now records document activity events (server-side only, no UI yet):
- `UPLOAD` when a new document metadata entry is created
- `MOVE` when a document’s `subjectId` changes (single or bulk)
- `DELETE` when a document is deleted (single or bulk)
- `DOWNLOAD` when the frontend reports a download

### 5.1 Download tracking (NEW)
`POST /api/documents/{id}/downloaded`

Notes:
- This endpoint only records activity; it does not download bytes (Drive-only architecture).
- Permission check is the same as viewing the document.

Response (`data`) is paginated:
```json
{
  "items": [
    {
      "publicId": "d9c7e874-4a7a-4a55-98d1-7d8b0c7edfd7",
      "driveFileId": "1AbC...",
      "fileName": "chapter1.pdf",
      "title": "Chapter 1",
      "mimeType": "application/pdf",
      "sizeBytes": 123456,
      "visibility": "FAMILY",
      "category": null,
      "familyId": "9aa4a174-4c5b-4b1d-ae52-35ce5cf10b2b",
      "subjectId": "2c92d7d5-8b3b-4c0a-bf2d-22e7a7dcb35a",
      "referenceType": "FILE",
      "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

### 4.5 Delete document (permission changed)
`DELETE /api/documents/{id}`

Rules:
- FAMILY documents: **HEAD-only** can delete.
- PERSONAL/SHARED: existing owner rules apply.

---

## 5) Family API (CHANGED + NEW)

Base path: `/api/family`

### 5.1 Invite member (default role)
`POST /api/family/{familyId}/invite`

Request:
```json
{ "email": "student@example.com" }
```

Behavior:
- Invited/accepted members now default to `VIEWER`.

### 5.2 Accept invite (default role)
`POST /api/family/invite/{inviteId}/accept`

Behavior:
- Membership is created with role `VIEWER`.

### 5.3 Update member role (NEW)
`PATCH /api/family/{familyId}/members/{userId}`

Request:
```json
{ "role": "CONTRIBUTOR" }
```

Rules:
- **HEAD-only**.
- Cannot demote the last remaining `HEAD`.

Response (`data`) is `FamilyMemberResponse`:
```json
{
  "userId": 123,
  "email": "student@example.com",
  "fullName": "Student Name",
  "role": "CONTRIBUTOR",
  "active": true
}
```

---

## 6) End-to-end flow (recommended FE sequence)

### 6.1 Teacher creates a class/family + subject
1) Create family (existing API).
2) Create subject: `POST /api/subjects` with `scope=FAMILY` + `familyId`.

### 6.2 Teacher adds students
1) Invite: `POST /api/family/{familyId}/invite`
2) Student accepts invite.
3) Teacher promotes a student to contributor (if needed): `PATCH /api/family/{familyId}/members/{userId}` -> `CONTRIBUTOR`.

### 6.3 Upload docs into a subject
1) Upload to Google Drive (client).
2) Save metadata to backend: `POST /api/documents` with `visibility=FAMILY`, `familyId`, and `subjectId`.

### 6.4 List docs for a subject
- `GET /api/documents?visibility=FAMILY&familyId=<familyId>&subjectId=<subjectId>&page=0&size=20`

### 6.5 List docs not in any subject
- `GET /api/documents?visibility=FAMILY&familyId=<familyId>&uncategorized=true&page=0&size=20`

---

## 7) Notes / gotchas
- `type` query param on documents list is deprecated; always use `visibility`.
- Role enforcement is server-side; FE should still hide/disable actions for non-permitted roles.
- This backend is Drive-metadata-only: it stores Drive identifiers and enqueues permission jobs; clients apply Drive permissions.

Swagger visibility note:
- The params are implemented on the controller method signature. If they’re not showing up in Swagger UI, it’s almost always a stale running build or cached UI. Restart the backend / redeploy and hard-refresh Swagger UI.
