CREATE TABLE IN_FLIGHT
(
    BEHOVSSEKVENSID VARCHAR(100) PRIMARY KEY,
    BEHOVSSEKVENS JSONB NOT NULL,
    SIST_ENDRET DATE NOT NULL
);

CREATE TABLE ARKIV
(
    BEHOVSSEKVENSID VARCHAR(100) PRIMARY KEY,
    BEHOVSSEKVENS JSONB NOT NULL
);
