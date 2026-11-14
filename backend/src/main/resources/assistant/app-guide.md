# I-ITSM user guide (reference for the assistant)

I-ITSM is an IT service desk. Employees report IT problems ("incidents"), the system prioritises them and assigns
a technician, technicians work them, and administrators oversee everything.

## Roles

- **Employee**: reports incidents and follows their own. Pages: "My incidents", "Report an incident".
- **Technician**: works incidents assigned to them. Page: "My work".
- **Team lead**: a technician who also leads a team; sees incidents assigned to their team, receives SLA
  warnings and first-level escalations for the team, and can add internal notes ("Add note") on the team's incidents.
- **Administrator**: sees everything. Pages: Dashboard, Incidents (search), Audit log, Users, Teams,
  Technicians, Services, Categories, SLA policies, Assignment settings.
- **IT manager**: an administrator who also receives the last level of escalation.

Anyone can register an account from the login page; new accounts are employees. After 10 wrong passwords for
one account within 15 minutes, logging in to it is blocked for a while; wait and try again. Descriptions, resolutions
and comments can be up to 5000 characters long.

## Reporting an incident (employee)

Open "Report an incident" and fill in: Title, Description, Service (the affected IT service), Category,
Impact (how badly work is affected, Low / Medium / High), Urgency (how soon it must be fixed, Low / Medium / High)
and Affected users (how many people are hit).

- Next to the Category list there is a **"Suggest"** button: after writing the title or description, click it and
  the system suggests categories from keywords in the text; click a suggested category to pick it, or choose another
  from the list. A category is required.
- Click **"Submit incident"** to report it. After submitting ("Report another" starts a new form), the system immediately: sets a **priority**, sets **SLA deadlines**, checks for
  **possible duplicates**, and tries to **assign a technician automatically**. Possible duplicates are shown so you
  can check whether someone already reported the same problem.

## Priority (P1 highest to P4 lowest)

score = impact (1-3) x urgency (1-3) x the service's criticality (1-5).
- score 27 or more: P1; 12-26: P2; 5-11: P3; under 5: P4.
- If the affected-user count reaches the bump threshold (default 100), the priority goes up one level (P1 stays P1).
- An administrator can change the priority manually ("Change priority") until the incident is resolved; the SLA
  deadlines are recalculated.

## SLA (service level agreement) deadlines

Each priority has a **response** deadline (time until a technician acknowledges) and a **resolution** deadline
(time until it is resolved). Defaults: P1 5 min / 30 min, P2 15 min / 2 h, P3 1 h / 8 h, P4 4 h / 24 h.
Administrators can change these on the "SLA policies" page, including per-service policies.

SLA status: **On track**; **At risk** once 80% of a window has passed without that step being done; **Violated**
once a deadline is missed. While an incident waits for the reporter ("Pending info"), the SLA clock is
**paused**, and the deadlines move later by the time spent waiting.

A background monitor checks deadlines regularly. An SLA warning notifies the assignee and their team lead; a
violation notifies the assignee and all administrators (also by email).

## Incident lifecycle (statuses)

- **New**: just created.
- **Classified**: priority and SLA set. It stays here if no technician could be assigned automatically; an
  administrator then assigns one.
- **Assigned**: a technician has it but has not acknowledged it yet.
- **Acknowledged**: the technician has accepted it (this meets the response deadline).
- **In progress**: the technician is working on it.
- **Pending info**: the technician asked the reporter a question; the SLA clock is paused until the reporter answers.
- **Resolved**: the technician says it is fixed and wrote what was done.
- **Verified**: the reporter confirmed the fix.
- **Closed**: finished (after feedback, or closed by an administrator).
- **Cancelled**: withdrawn before work started.
- **Duplicate**: the same problem as another incident, which is tracked instead.

Allowed moves: New -> Classified, Cancelled, Duplicate. Classified -> Assigned, Cancelled, Duplicate.
Assigned -> Acknowledged, Cancelled, Duplicate. Acknowledged -> In progress, Duplicate.
In progress -> Pending info, Resolved. Pending info -> In progress. Resolved -> Verified, or back to In progress
(reopened). Verified -> Closed. Closed, Cancelled and Duplicate are final. Any other move is refused, for
example an incident cannot jump from New to Closed, and it cannot be cancelled once work has started.

## Who can do what on an incident page (button names in quotes)

- **Assigned technician**: "Acknowledge" (when Assigned), "Start work" (when Acknowledged), "Resolve" (must write
  what was done) and "Request information" (when In progress), and "Add note" for internal notes while open.
- **Reporter (employee)**: answers information requests in the Conversation box ("Your answer" / "Send") which
  restarts the SLA clock; "Confirm it is fixed" or "Still broken: reopen" (when Resolved); after confirming, rates
  the service 1-5 stars with "Send feedback and close", which closes the incident; "Cancel incident" while New,
  Classified or Assigned.
- **Administrator**: "Assign"/"Reassign" (from Classified until the incident is resolved; once work has started,
  the new technician takes over and the status and SLA times stay the same), "Change priority" (from Classified
  until resolved), "Mark duplicate" (New, Classified, Assigned or Acknowledged: either type the original incident's
  number, or click "Mark duplicate of INC..." in the "Possible duplicates" box on the incident page),
  "Close without feedback" (when Verified), "Cancel incident" (New, Classified or Assigned), and internal notes.
  An incident cannot be marked a duplicate of a cancelled incident or of one that is itself a duplicate (link to
  its original instead).

Who can view an incident: its reporter, its assignee, the team lead of the assignee's team, and administrators.
Internal notes are hidden from the reporter. Every incident page shows the full history (audit trail).

## Automatic assignment

The administrator picks the strategy on "Assignment settings":
- **Skill based** (default): the technician with the best level in the skill the category needs, then the least busy.
- **Load based**: the technician with the fewest active incidents.
- **Round robin**: technicians take turns.

Only active, available technicians below their maximum workload are considered. Team leads are skipped unless the
setting allows them or the incident has been escalated. A technician is never assigned an incident they reported
themselves. If nobody fits, the incident stays Classified and administrators are notified to assign it manually.
Administrators can see recommended technicians when assigning. Many incidents reported at the same moment are
assigned one after another, so nobody ends up above their maximum workload.

## Escalation (P1 only)

If a P1 incident is still not acknowledged at its response deadline, it escalates: level 1 notifies the team lead
of the assignee's team, level 2 (one timeout later, default 5 minutes) all administrators, level 3 the IT managers.
Escalation notifies people; it does not reassign the incident. Acknowledging stops it.

## Duplicate detection

When an incident is reported, open incidents on the same service from the last 24 hours with similar wording are
found as possible duplicates. Different words for the same problem count as similar ("unavailable", "down",
"failing" and "unable" all mean an outage; "payments" and "payment" are the same word). The reporter is only shown
possible duplicates they reported themselves; administrators see all of them in the "Possible duplicates" box on
the incident page and can link one with a single click.

## Notifications

The bell in the top bar shows in-app notifications (checked every 15 seconds); click one to open its incident, or
"Mark all read". Important events are also emailed: new incident (reporter), assignment (technician), information
requested and resolved (reporter), assignment failed and SLA violated (administrators), escalations.

## Administrator pages

- **Dashboard**: summary counts and charts: incidents reported per day, SLA compliance, incidents by priority,
  technician workload against each person's limit, incidents by service and by category.
- **Incidents**: find any incident by number, or filter all incidents by status, priority, SLA status, service,
  category and technician, with sorting.
- **Audit log**: every recorded change, by whom and when. It cannot be edited.
- **Users**: "New user" then "Create user" to add staff; "Edit" then "Save" to change someone or deactivate them.
  A technician who still has open incidents cannot be deactivated: reassign their incidents first.
- **Teams**: "Create team"; each team's lead is chosen from a drop-down list in its row, which saves at once.
- **Technicians**: "Edit" then "Save" to change skills, availability, maximum workload, experience and team.
- **Services**: "New service" / "Create service", or "Edit" / "Save"; each service has a business criticality 1-5.
- **Categories**: "New category" / "Create category", or "Edit" / "Save"; each category has keywords (used by the
  "Suggest" button) and an optional required skill.
- **SLA policies**: "Edit" then "Save" to change the response and resolution minutes of a priority;
  "New service override" then "Create" for different times on one service; "Delete" then "Confirm delete" removes an
  override.
- **Assignment settings**: there is no Save button here. Choosing a strategy (Skill based, Load based, Round robin)
  takes effect immediately, and so does the switch "Assign new incidents to team leads automatically".
- The Dashboard and "My work" pages have a "Refresh" button; the Audit log and Incidents pages have filters
  ("Apply", "Clear filters").
