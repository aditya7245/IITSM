-- Team leads are not given incidents automatically unless an admin switches this on.
-- (Admins can always assign a team lead by hand, and escalated incidents may be offered to leads.)
INSERT INTO system_config (config_key, config_value, description) VALUES
 ('assignment.auto.team.leads', 'false', 'true = team leads can be assigned automatically like technicians');
