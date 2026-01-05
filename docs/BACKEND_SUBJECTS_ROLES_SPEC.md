# SafeDocs Backend Spec — Subjects (Collections) + Family Roles (Classroom)

Audience: Java Spring Boot backend developer

This spec is aligned to the current API style in Swagger:
- Base response envelope: `success`, `message`, `actionCode`, `data`, `errors`, `paginator`
- Bearer JWT auth (`Bearer Authentication`)
- Existing resources: `/api/documents`, `/api/family`

## 0) Why this feature (product intent)

We want SafeDocs to be daily-use for students/teachers:
- Students: open a subject and see only that subject’s docs.
- Teachers: upload lecture material per subject.
- Class/Family groups: allow Teacher + CR to upload, students view/download only.

This adds **Subjects** (organization) and **Roles** (permissions) without breaking existing document flows.

Non-goals for this phase:
- OCR/AI, version history, approval workflows, realtime collaboration

---

## 1) Current backend behavior (from Swagger)

### Documents
- `GET /api/documents` supports query params: `visibility`, `type`, `category`, `search`, `page`, `size`, `familyId`.
- `POST /api/documents` creates a document using `CreateDocumentRequest`.
- `PUT /api/documents/{id}` updates using `UpdateDocumentRequest`.

Document models in OpenAPI:
- `DocumentListItem` and `DocumentResponse` include (among others):
  - `publicId` (uuid), `driveFileId`, `fileName`, `title`, `mimeType`, `sizeBytes`
  - `visibility` enum: `PERSONAL|FAMILY|SHARED`
  - `familyId` (uuid)
  - `accessLevel` enum: `OWNER|WRITER|READER`

### Family
- `GET /api/family` returns `FamilySummaryResponse` with `role` enum: `HEAD|MEMBER`.
- `GET /api/family/{familyId}` returns `FamilyProfileResponse` with members.
- `GET /api/family/{familyId}/members` returns `FamilyMemberResponse` items with `role` enum: `HEAD|MEMBER`.

---

## 2) New feature — Subjects / Collections

### 2.1 Data model

Add table: `subjects`

Fields:
- `id` (UUID PK)
- `name` (varchar, required, trimmed, recommended max 80)
- `scope` (enum: `PERSONAL|FAMILY`)
- `ownerUserId` (long or uuid depending on user model; nullable)
- `familyId` (uuid; nullable)
- `createdAt`, `updatedAt` (timestamp)

Constraints:
- Exactly one of `ownerUserId` or `familyId` must be non-null.
- Uniqueness (recommended):
  - Personal: unique `(ownerUserId, lower(name))`
  - Family: unique `(familyId, lower(name))`

Indexes:
- `(ownerUserId, updatedAt desc)`
- `(familyId, updatedAt desc)`

### 2.2 Documents table change

Add nullable FK to existing documents:
- `subjectId` UUID NULL

Index:
- `(subjectId, createdAt desc)` (or `updatedAt desc`)

Rules:
- Backward compatible: existing docs have `subjectId = null`.
- When set:
  - If doc visibility is `PERSONAL`, subject must be `PERSONAL` owned by current user.
  - If doc visibility is `FAMILY`, subject must be `FAMILY` and `familyId` must match.

### 2.3 New endpoints (Subjects)

All endpoints use the same BaseResponse envelope pattern.

#### List subjects
`GET /api/subjects`

Query params:
- `scope` = `PERSONAL|FAMILY` (required)
- `familyId` (required when scope=FAMILY)
- `page`, `size` (optional; match current pagination conventions)

Response data type: `SubjectPageResponse`

`SubjectListItem` should include:
- `id`, `name`, `scope`, `familyId`, `ownerUserId`
- `documentCount` (important for fast UI)
- `createdAt`, `updatedAt`

#### Create subject
`POST /api/subjects`

Request: `CreateSubjectRequest`
```json
{ "name": "OS", "scope": "FAMILY", "familyId": "..." }
```
Rules:
- scope=PERSONAL: ignore/forbid `familyId`, set `ownerUserId` from token
- scope=FAMILY: require `familyId` and require role HEAD (Phase B)

#### Rename subject
`PUT /api/subjects/{subjectId}`

Request: `UpdateSubjectRequest`
```json
{ "name": "OS - Unit 1" }
```

#### Delete subject
`DELETE /api/subjects/{subjectId}`

Default rule (recommended): **block deletion if it still has documents**.
- If any documents exist with this `subjectId`, return conflict.

(Alternative later: unlink docs by setting `subjectId=null`.)

---

## 3) API changes required by the Android frontend

### 3.1 Documents list filter by subject

Extend existing endpoint:
`GET /api/documents`

Add query param:
- `subjectId` (uuid, optional)

Behavior:
- If `subjectId` is provided: return only documents assigned to that subject.
- The client will call this for “Subject Documents Screen”.

Optional (recommended) additional query param for uncategorized view:
- `uncategorized` (boolean, default false)
  - If true: return documents with `subjectId IS NULL` within the given scope.

### 3.2 Include subjectId in document responses

Update these schemas to include:
- `subjectId` (uuid, nullable)

Affected response types:
- `DocumentListItem`
- `DocumentResponse`

### 3.3 Accept subjectId in create/update document

Update request schemas:
- `CreateDocumentRequest`: add optional `subjectId`
- `UpdateDocumentRequest`: add optional `subjectId`

Validation rules:
- If `subjectId` is provided, validate it exists and matches doc scope.
- If `subjectId` is null, treat as “Uncategorized”.

### 3.4 (Optional but clean) Dedicated endpoint to move document between subjects

Instead of only PUT `/api/documents/{id}` (which updates many fields), add:
`PATCH /api/documents/{id}/subject`

Request:
```json
{ "subjectId": "uuid-or-null" }
```

This keeps the frontend simple and reduces accidental overwrites.

---

## 4) New feature — Family roles for classroom workflows

Current roles are `HEAD|MEMBER`. We need a 3-role model:
- `HEAD` (Teacher)
- `CONTRIBUTOR` (CR/TA)
- `VIEWER` (Student)

### 4.1 Role migration strategy (avoid breaking older clients)

Option A (recommended):
- Replace `MEMBER` with `VIEWER` in database enum.
- Migrate existing members: `MEMBER -> VIEWER`.

Option B (compatibility-first):
- Keep DB as-is temporarily, but map:
  - `MEMBER` returned in API means `VIEWER` in new clients.
  - Add new enum values and allow new assignments.

### 4.2 Update existing family response models

Update schemas:
- `FamilyMemberResponse.role` enum to `HEAD|CONTRIBUTOR|VIEWER` (and optionally keep MEMBER temporarily)
- `FamilySummaryResponse.role` enum likewise

Frontend relies on this to show/hide Upload buttons.

### 4.3 New endpoint: update member role

Add:
`PATCH /api/family/{familyId}/members/{userId}`

Request schema: `UpdateFamilyMemberRoleRequest`
```json
{ "role": "CONTRIBUTOR" }
```
Rules:
- Only `HEAD` can change roles.
- Cannot demote the last HEAD (optional rule, recommended).

### 4.4 Permission rules (server-side enforcement)

Enforce on document create/update/delete and subject create/update/delete.

Matrix:
- FAMILY scope:
  - Create subject: HEAD only
  - Upload/create document: HEAD + CONTRIBUTOR
  - Update document metadata (title/category/subjectId): HEAD + CONTRIBUTOR (recommended)
  - Delete document: HEAD only
  - Download/view: all members

When a VIEWER tries to upload in family scope:
- Return 403 (`FOR403`) with message “Only teachers/contributors can upload.”

---

## 5) Production-grade requirements (to avoid future rework)

### 5.1 Authorization and scope validation
- Never trust incoming `ownerUserId`/`familyId` from client without verifying membership.
- Validate `subjectId` belongs to the same scope (personal vs family).

### 5.2 Pagination consistency
- Subjects list should support `page` + `size` to match existing `/api/documents` list.
- Return totals like document paging does (`items`, `page`, `size`, `total`).

### 5.3 Error handling consistency
Backend already has `actionCode` enum in BaseResponse.
Use existing patterns:
- 400 validation error: `VAL400`
- 401 unauth: `UN_AUTH401` / `ATH401`
- 403 forbidden: `FOR403`
- 404 not found: `NFD404`
- 409 conflict/duplicate: `DUP409`

Add error details (`errors[]`) when useful, but keep `message` user-readable.

### 5.4 Data integrity rules to implement now
- Disallow assigning documents to a subject from a different family.
- Disallow creating family subject by non-HEAD.
- Disallow uploads for VIEWER in family scope.

### 5.5 Document counts (UX/performance)
- Include `documentCount` in subject list response.
  - Either computed on the fly (with indexed query) or maintained via triggers/jobs.

---

## 6) Suggested OpenAPI additions (schemas)

### Subject schemas
- `SubjectListItem`
- `SubjectResponse`
- `SubjectPageResponse` (items + page + size + total)

### Requests
- `CreateSubjectRequest` (name, scope, familyId?)
- `UpdateSubjectRequest` (name)
- `UpdateFamilyMemberRoleRequest` (role)
- Optional: `UpdateDocumentSubjectRequest` (subjectId)

---

## 7) Frontend impact summary (what Android will call)

New:
- `GET /api/subjects?scope=PERSONAL`
- `GET /api/subjects?scope=FAMILY&familyId=...`
- `POST /api/subjects`
- `PUT /api/subjects/{id}`
- `DELETE /api/subjects/{id}`

Changes:
- `GET /api/documents` add `subjectId` query param (and optional `uncategorized=true`).
- `DocumentListItem` + `DocumentResponse` add `subjectId`.
- `CreateDocumentRequest` + `UpdateDocumentRequest` add `subjectId`.

Roles:
- Update family member role endpoint.
- Extend role enums to support `CONTRIBUTOR|VIEWER`.

---

## 8) Acceptance criteria (backend “done”)

- Subjects CRUD works for personal and family scope.
- Document list can filter by `subjectId`.
- Documents support `subjectId` assign/unassign.
- Family role enums expanded; role returned in `GET /api/family`, `GET /api/family/{id}`, `GET /api/family/{id}/members`.
- Server enforces permissions for family uploads and subject creation.
- No breaking changes for existing clients: subjectId is nullable; old docs remain visible.
