-- DEV ONLY (loaded via the dev profile). All users share the password: Password@123

INSERT INTO teams (name) VALUES ('Application Support'), ('Infrastructure');

INSERT INTO users (role, email, password_hash, full_name, department, team_id, max_workload, available, experience_years) VALUES
 ('ADMINISTRATOR','admin@iitsm.local',   '$2a$10$xT.EDuHP9sOQY3SizZwf2..mnpd4cKI8..8winamNlKIfUt1YVBtu','System Admin',   NULL,    NULL, NULL, NULL, NULL),
 ('IT_MANAGER',   'manager@iitsm.local', '$2a$10$xT.EDuHP9sOQY3SizZwf2..mnpd4cKI8..8winamNlKIfUt1YVBtu','IT Manager',     NULL,    NULL, NULL, NULL, NULL),
 ('EMPLOYEE',     'emp1@iitsm.local',    '$2a$10$xT.EDuHP9sOQY3SizZwf2..mnpd4cKI8..8winamNlKIfUt1YVBtu','Asha Employee',  'Finance',NULL,NULL, NULL, NULL),
 ('EMPLOYEE',     'emp2@iitsm.local',    '$2a$10$xT.EDuHP9sOQY3SizZwf2..mnpd4cKI8..8winamNlKIfUt1YVBtu','Ravi Employee',  'Sales', NULL, NULL, NULL, NULL);

INSERT INTO users (role, email, password_hash, full_name, team_id, max_workload, available, experience_years)
SELECT v.role, v.email, '$2a$10$xT.EDuHP9sOQY3SizZwf2..mnpd4cKI8..8winamNlKIfUt1YVBtu', v.full_name, t.id, v.max_workload, TRUE, v.exp
FROM (VALUES
 ('TEAM_LEAD',  'lead.app@iitsm.local',  'Meera Lead',  'Application Support', 5, 8),
 ('TECHNICIAN', 'tech1@iitsm.local',     'Kiran Tech',  'Application Support', 5, 4),
 ('TECHNICIAN', 'tech2@iitsm.local',     'Sana Tech',   'Infrastructure',      5, 2),
 ('TECHNICIAN', 'tech3@iitsm.local',     'Dev Tech',    'Infrastructure',      3, 6)
) AS v(role, email, full_name, team, max_workload, exp)
JOIN teams t ON t.name = v.team;

UPDATE teams SET lead_id = (SELECT id FROM users WHERE email = 'lead.app@iitsm.local')
 WHERE name = 'Application Support';

INSERT INTO it_services (name, description, business_criticality) VALUES
 ('Payment Application', 'Customer payment processing', 5),
 ('Corporate Email',     'Employee email',              3),
 ('VPN Gateway',         'Remote access',               4),
 ('Internal Wiki',       'Documentation portal',        1);

INSERT INTO technician_skills (technician_id, skill_id, proficiency)
SELECT u.id, s.id, v.prof
FROM (VALUES
 ('tech1@iitsm.local','Application Support',5),('tech1@iitsm.local','Database',3),
 ('tech2@iitsm.local','Networking',4),('tech2@iitsm.local','Cloud',3),
 ('tech3@iitsm.local','Database',5),('tech3@iitsm.local','Application Support',2),
 ('lead.app@iitsm.local','Application Support',5),('lead.app@iitsm.local','Authentication',4)
) AS v(email, skill, prof)
JOIN users u ON u.email = v.email
JOIN skills s ON s.name = v.skill;
