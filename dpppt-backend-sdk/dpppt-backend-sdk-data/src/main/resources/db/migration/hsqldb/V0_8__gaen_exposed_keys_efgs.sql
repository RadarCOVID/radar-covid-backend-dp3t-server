ALTER TABLE t_gaen_exposed
    ADD COLUMN country_origin   CHAR(2);
ALTER TABLE t_gaen_exposed
    ADD COLUMN report_type      SMALLINT;
ALTER TABLE t_gaen_exposed
    ADD COLUMN days_since_onset SMALLINT;
ALTER TABLE t_gaen_exposed
    ADD COLUMN efgs_sharing     BOOLEAN;

CREATE INDEX in_gaen_exposed_country_sharing_received
    ON t_gaen_exposed(country_origin, efgs_sharing, received_at)
;

CREATE TABLE T_VISITED (
    PFK_EXPOSED_ID INTEGER NOT NULL,
    COUNTRY        CHAR(2),
    CONSTRAINT PK_T_VISITED
        PRIMARY KEY (PFK_EXPOSED_ID, COUNTRY),
    CONSTRAINT R_GAEN_EXPOSED_VISITED
        FOREIGN KEY (PFK_EXPOSED_ID)
            REFERENCES T_GAEN_EXPOSED (PK_EXPOSED_ID) ON DELETE CASCADE
);

CREATE INDEX IN_VISITED_EXPOSED_COUNTRY
    ON T_VISITED(COUNTRY);