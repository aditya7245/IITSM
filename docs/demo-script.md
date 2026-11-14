# Demo script

Three workflows to follow in the recorded demo, with the seeded users. The backend runs in **demo mode**, so deadlines and escalations take seconds instead of minutes.

## Setup

1. Start Docker Desktop, then from the repo root, with a fresh database so the incident numbers start at INC1001 and no old incidents interfere:

   ```powershell
   docker compose down -v
   docker compose up -d
   cd backend
   $env:SPRING_PROFILES_ACTIVE = "dev,demo"
   mvn spring-boot:run
   ```

   `dev` loads the demo users, `demo` speeds up time. Look for "The following 2 profiles are active: dev, demo" in the log.

2. Open two browser tabs: Swagger UI at http://localhost:8080/swagger-ui.html and Mailpit (caught emails) at http://localhost:8025.

3. **Logging in as a user in Swagger:** call `POST /api/auth/login` with the user's email and `Password@123`, copy the `token` from the response, click **Authorize** (top right), paste the token (without the word Bearer) and click Authorize. To switch user, click Authorize again, press Logout, and paste the other token.

4. **Look up the ids once** (any logged-in user): `GET /api/services` and `GET /api/categories`. Note the `id` of *Payment Application*, *VPN Gateway*, *Application* and *Network*. The report forms below use `<...Id>` for these.

5. In URLs, `{id}` is the number called `id` in the response (for example 1), not the `INC1001` number.

### Users (all passwords `Password@123`)

| Email | Who | Role |
|---|---|---|
| `emp1@iitsm.local` | Asha Employee | employee |
| `emp2@iitsm.local` | Ravi Employee | employee |
| `tech1@iitsm.local` | Kiran Tech | technician, Application Support |
| `tech2@iitsm.local` | Sana Tech | technician, Infrastructure (Networking, Cloud) |
| `lead.app@iitsm.local` | Meera Lead | team lead, Application Support |
| `admin@iitsm.local` | System Admin | administrator |
| `manager@iitsm.local` | IT Manager | IT manager |

### What the demo speed means

The time scale is 20 and the checks run every 5 seconds, so real minutes become seconds:

| | Normal | In demo mode |
|---|---|---|
| P1 respond / resolve | 5 / 30 minutes | 15 / 90 seconds |
| P2 respond / resolve | 15 / 120 minutes | 45 seconds / 6 minutes |
| "At risk" warning | at 80% of the time | at 80% of the shorter time |
| Next escalation level | every minute | about 3 seconds, but never faster than the 5 second check |

Emails are sent a moment after the change is saved, so refresh Mailpit if one is missing.

---

## Workflow 1: reporting an outage (prioritised, assigned, notified)

**1.1 Employee reports the problem.** Log in as `emp1`. Call `POST /api/incidents`:

```json
{
  "title": "Payment application outage",
  "description": "Users cannot complete payment transactions",
  "categoryId": <ApplicationId>,
  "serviceId": <PaymentApplicationId>,
  "impact": 3,
  "urgency": 3,
  "affectedUsers": 250
}
```

Expected: `201`. In `incident`: `incidentNumber` INC1001, `priorityScore` 45, `priority` P1 (3 x 3 x criticality 5), `status` ASSIGNED, `assignee` Kiran Tech (best skill match for Application Support), `responseDeadline` 15 seconds after `createdAt` and `resolutionDeadline` 90 seconds after. `possibleDuplicates` is empty. Write down the incident `id`; the other workflows call it **incident 1**.

**1.2 In-app notifications.**
- `emp1`: `GET /api/notifications` shows two: CREATED and ASSIGNED.
- Log in as `tech1`: `GET /api/notifications` includes ASSIGNED (plus SLA warnings if you were slow), and `GET /api/technicians/me/workload` shows `active` 1.

**1.3 Emails.** In Mailpit there are two emails: "[INC1001] ..." to Asha (the report was received) and one to Kiran (assigned to you).

**1.4 History.** Log in as `admin`: `GET /api/incidents/{id}/history` lists CREATED, CLASSIFIED, ASSIGNED, with who did each (the system did the last two).

Do **not** acknowledge incident 1. Nobody picks it up, so it will warn, violate its SLA and escalate by itself within about 30 seconds. That is expected and is shown in workflow 3.

---

## Workflow 2: working an incident (info request, SLA pause, resolve, feedback, close)

This one uses a P2 incident, so there is time to click through (45 seconds to acknowledge).

**2.1 Report.** As `emp2`, `POST /api/incidents`:

```json
{
  "title": "VPN keeps disconnecting",
  "description": "Remote staff lose the VPN connection every few minutes",
  "categoryId": <NetworkId>,
  "serviceId": <VpnGatewayId>,
  "impact": 2,
  "urgency": 2,
  "affectedUsers": 5
}
```

Expected: P2 (score 16), assigned to Sana Tech (the only Networking skill). Write down the `id` (**incident 2**).

**2.2 Technician picks it up.** As `tech2`, within 45 seconds: `POST /api/incidents/{id}/acknowledge` (status ACKNOWLEDGED), then `POST /api/incidents/{id}/start` (IN_PROGRESS). If you are slower than 45 seconds the incident becomes SLA VIOLATED, which is also a valid thing to show, but acknowledging in time keeps the demo clean.

**2.3 Technician asks for information; the SLA clock pauses.** As `tech2`, `POST /api/incidents/{id}/comments`:

```json
{ "type": "INFO_REQUEST", "body": "Which VPN client version are you using?" }
```

Then `GET /api/incidents/{id}`: `status` PENDING_INFO and `slaPausedAt` is set. Note down `responseDeadline` and `resolutionDeadline`. `emp2` now has an INFO_REQUESTED notification and an email. Wait about 20 seconds.

**2.4 Employee answers; the deadlines move later.** As `emp2`, `POST /api/incidents/{id}/comments`:

```json
{ "type": "INFO_RESPONSE", "body": "Version 5.2 on Windows 11" }
```

Then `GET /api/incidents/{id}`: `status` IN_PROGRESS again, `slaPausedAt` is null, and both deadlines are later than before by about the 20 seconds you waited. `tech2` has an INFO_RECEIVED notification.

**2.5 Technician resolves.** As `tech2`, `POST /api/incidents/{id}/resolve`:

```json
{ "resolution": "Updated the VPN client and reset the gateway session" }
```

Status RESOLVED. `emp2` gets a RESOLVED notification and an email.

**2.6 Employee verifies and rates; the incident closes itself.** As `emp2`: `POST /api/incidents/{id}/verify` (VERIFIED), then `POST /api/incidents/{id}/feedback`:

```json
{ "rating": 5, "comments": "Fixed quickly" }
```

Expected `201`. Then `GET /api/incidents/{id}`: `status` CLOSED with `closedAt` set. As `admin`, `GET /api/incidents/{id}/history` shows the whole story from CREATED to CLOSED.

---

## Workflow 3: duplicates and automatic escalation

**3.1 A similar report is flagged.** As `emp2`, report the same kind of problem again, `POST /api/incidents`:

```json
{
  "title": "Payment application down",
  "description": "Users cannot complete payment transactions",
  "categoryId": <ApplicationId>,
  "serviceId": <PaymentApplicationId>,
  "impact": 3,
  "urgency": 3,
  "affectedUsers": 250
}
```

Expected: `201`, and `possibleDuplicates` contains INC1001 (incident 1) with a score of about 0.71. It is only a warning, the new incident is created anyway. Write down its `id` (**incident 3**).

**3.2 The administrator marks it as a duplicate.** As `admin`, `POST /api/incidents/{id}/duplicate` with incident 3's id in the URL:

```json
{ "originalId": <id of incident 1> }
```

Expected: `status` DUPLICATE and `duplicateOfNumber` INC1001. `emp2` (who reported incident 3) and Kiran are notified.

**3.3 An unacknowledged P1 escalates.** As `emp1`, report a fresh critical incident, `POST /api/incidents`:

```json
{
  "title": "Payment gateway timeout",
  "description": "Card payments time out at checkout",
  "categoryId": <ApplicationId>,
  "serviceId": <PaymentApplicationId>,
  "impact": 3,
  "urgency": 3,
  "affectedUsers": 250
}
```

Write down its `id` (**incident 4**). Do not acknowledge it. Wait about 30 seconds, then check:

- `tech1` (the assignee) has an SLA_WARNING (at 80% of the 15 seconds) and an SLA_VIOLATED notification.
- `lead.app` (Meera, the team lead of Application Support) has an ESCALATED notification: **level 1**.
- `admin` has an ESCALATED notification: **level 2**.
- `manager` has an ESCALATED notification: **level 3**.
- `GET /api/incidents/{id}` shows `escalationLevel` 3, `slaStatus` VIOLATED, and the assignee is still Kiran: escalation informs people, it does not reassign.
- Mailpit holds the matching emails.

Incident 1 from workflow 1 went through the same escalation in the background.

**3.4 (Optional) The numbers.** As `admin`: `GET /api/reports/overview`, `/api/reports/priority-distribution`, `/api/reports/sla-compliance` and `/api/reports/technician-workload` reflect everything done in the three workflows.

---

## Starting over

Press Ctrl+C in the backend window, run `docker compose down -v`, and repeat the setup. A fresh database brings back incident number INC1001 and clears all incidents and notifications.
