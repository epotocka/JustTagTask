CREATE SCHEMA result;

CREATE TABLE processing_run (
    id           UUID PRIMARY KEY,
    file_id      UUID        NOT NULL REFERENCES uploaded_file (id),
    type         VARCHAR(20) NOT NULL,
    left_column  VARCHAR(30),
    right_column VARCHAR(30),
    operation    VARCHAR(20),
    group_by     VARCHAR(30),
    value_column VARCHAR(30),
    function     VARCHAR(20),
    status       VARCHAR(20) NOT NULL,
    result_table VARCHAR(63) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT processing_run_settings_match_type CHECK (
        (type = 'OPERATION' AND left_column IS NOT NULL AND right_column IS NOT NULL AND operation IS NOT NULL
            AND group_by IS NULL AND value_column IS NULL AND function IS NULL)
        OR
        (type = 'AGGREGATION' AND group_by IS NOT NULL AND value_column IS NOT NULL AND function IS NOT NULL
            AND left_column IS NULL AND right_column IS NULL AND operation IS NULL)
    )
);

CREATE INDEX processing_run_file_id_created_at_idx ON processing_run (file_id, created_at);

CREATE UNIQUE INDEX processing_run_one_active_per_file_idx ON processing_run (file_id)
    WHERE status IN ('STARTING', 'STARTED');
