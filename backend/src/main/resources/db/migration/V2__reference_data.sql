-- Reference data required in every environment.

INSERT INTO system_config (config_key, config_value, description) VALUES
 ('assignment.strategy',      'SKILL_BASED', 'Active assignment strategy: SKILL_BASED | LOAD_BASED | ROUND_ROBIN'),
 ('duplicate.threshold',      '0.5',         'Jaccard similarity threshold for duplicate detection'),
 ('duplicate.window.hours',   '24',          'Look-back window for duplicate detection'),
 ('priority.bump.users',      '100',         'Affected-user count that bumps priority one tier');

INSERT INTO sla_policies (priority, service_id, response_minutes, resolution_minutes) VALUES
 ('P1', NULL,   5,   30),
 ('P2', NULL,  15,  120),
 ('P3', NULL,  60,  480),
 ('P4', NULL, 240, 1440);

INSERT INTO skills (name) VALUES
 ('Hardware'),('Software'),('Networking'),('Database'),('Cloud'),
 ('Security'),('Authentication'),('Application Support'),('Storage');

INSERT INTO categories (name, description, keywords, required_skill_id)
SELECT c.name, c.description, c.keywords, s.id
FROM (VALUES
 ('Hardware',       'Physical devices',          'laptop,printer,monitor,keyboard,mouse,battery,disk,hardware', 'Hardware'),
 ('Software',       'Installed software issues', 'install,update,crash,license,software,patch',                 'Software'),
 ('Network',        'Connectivity problems',     'network,wifi,vpn,dns,latency,router,switch,firewall,timeout', 'Networking'),
 ('Database',       'Database failures',         'database,sql,query,deadlock,replication,postgres,mysql',      'Database'),
 ('Cloud',          'Cloud infrastructure',      'cloud,aws,azure,gcp,instance,bucket,container,kubernetes',    'Cloud'),
 ('Security',       'Security incidents',        'malware,phishing,breach,virus,ransomware,suspicious',         'Security'),
 ('Authentication', 'Login and access',          'login,password,sso,locked,mfa,credentials,access,token',      'Authentication'),
 ('Application',    'Business applications',     'application,app,payment,transaction,portal,error,unavailable','Application Support'),
 ('Storage',        'Storage and backups',       'storage,backup,restore,quota,share,nas,volume',               'Storage')
) AS c(name, description, keywords, skill)
JOIN skills s ON s.name = c.skill;
