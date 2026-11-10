# PRD: Co-Parenting Compliance & Coordination App

**Status:** Draft v0.1 (reverse-engineered from existing UI screens)
**Author:** Product (drafted with Claude)
**Date:** 2026-09-06

## 1. Summary

A mobile app that helps separated/divorced co-parents track and prove compliance with shared responsibilities for their children — medical, educational, and financial obligations — in one shared, auditable space.

## 2. Problem Statement

Co-parents currently coordinate child-related obligations through scattered channels (WhatsApp, receipts, memory), which leads to disputes, missed deadlines, difficulty proving compliance, and no safe way to loop in caregivers.

## 3. Goals & Non-Goals

**Goals**
- Give each parent a single view of their compliance status and their co-parent's
- Centralize obligations with clear ownership, categories, and status
- Track shared expenses with approval/dispute workflow
- Allow scoped, revocable access for third parties
- Create a timestamped, auditable trail

**Non-Goals (v1)**
- In-app messaging/chat
- Legal document generation or e-signatures
- Payment processing
- Calendar sync with external providers

## 4. Target Users

| User | Description |
|---|---|
| **Primary parent (progenitor)** | Logged-in user; monitors compliance, manages activities/expenses, grants third-party access |
| **Co-parent** | The other progenitor; mirrored account, linked to same child(ren)/case |
| **Authorized third party** | Grandparent, pediatrician, nanny — restricted, scoped access |

## 5. Key Concepts / Data Model

- **User (Progenitor):** name, linked co-parent, compliance %
- **Vínculo (Link):** parent-to-parent relationship/case; active/inactive state
- **Actividad (Activity):** obligation or event with type, category, status, due date, assigned party
- **Gasto (Expense):** shared cost with category, amount, submitted-by, date, status
- **Tercero (Third party):** name, role, access duration, permissions scoped to specific activities

## 6. Screen Requirements

### 6.1 Home / Dashboard
- Greeting with user's name and current date
- Mi Cumplimiento: rolling compliance percentage
- Co-parent's name and compliance percentage, link status
- Summary counters: pending items, overdue items, pending expenses
- Próximos vencimientos: next 3-5 upcoming items
- Últimas actividades: recent activity feed
- Bottom navigation: Inicio, Actividades, Gastos, Terceros

### 6.2 Actividades (Activities)
- Tabs: Todos, Eventos, Obligaciones
- List sorted by date with title, date, category tag, status tag
- + Nuevo action to create activity
- Fields: title, category, type, due date, assigned to, notes/attachment
- Status lifecycle: Creado → Asignado → En progreso → Por verificar → Cumplido
- Exception states: Vencido, Disputa

### 6.3 Gastos (Expenses)
- Summary bar: total pending amount, count pending, count paid
- + Solicitar action to submit new expense
- List with title, category, submitted-by, date, amount, status, warning flags
- Fields: title, category, amount, date, receipt upload, split arrangement
- Status: Pendiente, Aprobado, Pagado, with Disputa and Sin doc. flags

### 6.4 Terceros (Third Parties)
- Explanatory copy about restricted access
- List of authorized third parties with name, role, access duration
- Per-person actions: Editar, Revocar
- + Agregar tercero autorizado to invite
- Fields: name, role, access expiration, activity/category scoping

## 7. Cross-Cutting Requirements

- Notifications for due dates, disputes, approvals, expiring access
- Audit trail for all status changes
- Permissions: symmetric for co-parents, least-privilege for third parties
- Localization: Spanish (es-ES/es-LatAm)
- Accessibility: status/category must not rely on color alone

## 8. Open Questions

1. Exact compliance % formula
2. Expense split logic (auto vs manual)
3. Third party access to expense data
4. Dispute-resolution workflow beyond status tag
5. Single-child vs multi-child support
6. Paper trail/export feature for legal use
