CREATE TABLE IF NOT EXISTS trip (slot INTEGER PRIMARY KEY CHECK(slot = 1), version INTEGER NOT NULL, document TEXT);
INSERT OR IGNORE INTO trip(slot, version, document) VALUES (1, 0, NULL);
