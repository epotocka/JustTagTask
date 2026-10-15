CREATE SCHEMA canonical;

CREATE TABLE canonical.ad_record (
    file_id           UUID           NOT NULL REFERENCES internal.uploaded_file (id),
    row_number        BIGINT         NOT NULL,
    advertiser_id     BIGINT         NOT NULL,
    campaign_id       BIGINT         NOT NULL,
    creative_id       BIGINT         NOT NULL,
    ad_box_uuid       UUID           NOT NULL,
    impressions       BIGINT         NOT NULL,
    clicks            BIGINT         NOT NULL,
    revenue           NUMERIC(19, 4) NOT NULL,
    advertiser_margin NUMERIC(19, 4) NOT NULL,
    PRIMARY KEY (file_id, row_number)
);
