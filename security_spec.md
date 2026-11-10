# kAM Lite Security Specification (Phase 0 TDD & Red Team Audit)

## 1. Data Invariants
1. **Owner-Only PII Isolation (`/users/{userId}`)**: A user profile contains `email` (PII) and personal card preferences (`enabledCards`). Only `request.auth.uid == userId` may `get`, `list`, `create`, `update`, or `delete` their own `/users/{userId}` document. No self-assigned RBAC `role` field exists on `UserProfile`.
2. **Authoritative Role Enforcement (`/groups/{groupId}`)**:
   - **Admin (الأمين)**: Defined strictly by `get(/databases/$(database)/documents/groups/$(groupId)).data.adminId == request.auth.uid`. Only the Admin can delete `groups`, `blocks`, `students`, `invitations`, or `monthlyBalances`.
   - **Servant (الخادم)**: Defined strictly by membership in `memberUids` (`request.auth.uid in group.memberUids`). Servants can view, create, and update `blocks`, `students` (including optional compressed `avatarBase64` `<= 50000` chars), `cardEvents`, and `monthlyBalances`, but **cannot delete** `students`, `blocks`, `groups`, `invitations`, or `monthlyBalances`.
3. **Zero `get()`/`exists()` in `allow list`**: Every subcollection document (`invitations`, `blocks`, `students`, `cardEvents`, `monthlyBalances`) carries a bounded `memberUids` array (`size() >= 1 && size() <= 10`) so `allow list` validates `request.auth.uid in resource.data.memberUids` locally with O(1) cost.
4. **CardEvent Integrity**: `cardType` must be one of the 7 approved types (`ATTENDANCE`, `QUIET`, `PARTICIPATION`, `PRAISE`, `GOLD`, `MEMORIZATION`, `CONFESSION`) and `points` must match the exact card value (`3, 5, 2, 10, 20, 6, 4`). Core event fields (`studentId`, `cardType`, `points`, `servantId`, `monthKey`, `weekKey`, `createdAt`) are immutable after creation.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("groups/KAM123/students").get()` -> `PERMISSION_DENIED`.
2. **Cross-User PII Read**: `bobDb.collection("users").doc("alice_uid").get()` -> `PERMISSION_DENIED`.
3. **Shadow Field Injection on UserProfile**: Adding `{"isAdmin": true}` to `/users/{userId}` -> Rejected by `hasOnly()`.
4. **Identity Spoofing on Group Creation**: `alice` creates `/groups/KAM123` with `adminId: "bob_uid"` -> Rejected by `incoming().adminId == request.auth.uid`.
5. **Unbounded Array Attack**: Updating `memberUids` with 15 elements -> Rejected by `data.memberUids.size() <= 10`.
6. **Servant Deleting a Student**: Servant (in `memberUids`, `!= adminId`) calls `delete()` on `/groups/KAM123/students/stu1` -> Rejected by `isGroupAdmin(groupId)`.
7. **Servant Deleting a Block**: Servant calls `delete()` on `/groups/KAM123/blocks/blk1` -> Rejected by `isGroupAdmin(groupId)`.
8. **Outsider Listing Students**: Authenticated user not in `memberUids` queries `/groups/KAM123/students` -> Rejected by `request.auth.uid in resource.data.memberUids`.
9. **Forged Card Points**: Servant writes `cardType: "ATTENDANCE", points: 999` -> Rejected by `isValidCardPoints(data.cardType, data.points)`.
10. **Mutating Immutable CardEvent**: Attempting to change `points` or `studentId` on an existing `cardEvent` -> Rejected by `affectedKeys().hasOnly(['memberUids', 'updatedAt'])`.
11. **Terminal Invitation State Reversal**: Servant attempts to change an `'ACCEPTED'` invitation back to `'PENDING'` -> Rejected by terminal state lock.
12. **Future Timestamp Spoofing**: Writing `createdAt` in the future (`> request.time`) -> Rejected by `incoming().createdAt <= request.time`.
