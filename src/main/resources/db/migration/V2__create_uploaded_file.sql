CREATE TABLE uploaded_file (
    id                UUID PRIMARY KEY,
    original_filename VARCHAR(255)  NOT NULL,
    storage_path      VARCHAR(1024) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    failure_reason    VARCHAR(2500),
    bytes_received    BIGINT        NOT NULL DEFAULT 0,
    row_count         BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX uploaded_file_status_created_at_idx ON uploaded_file (status, created_at);
CREATE INDEX uploaded_file_created_at_idx ON uploaded_file (created_at);
