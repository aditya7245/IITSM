-- Readable incident numbers (INC1001, INC1002, ...) come from this sequence.
CREATE SEQUENCE incident_number_seq START 1001;

-- 0.5 was too strict for short incident titles; 0.3 still avoids most false matches.
UPDATE system_config SET config_value = '0.3' WHERE config_key = 'duplicate.threshold';
