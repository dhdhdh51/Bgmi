-- ---------------------------------------------------------------------------
-- BGMI Scope Sensitivity Recommender — MySQL schema
--
-- Import order:  schema.sql  ->  seed_phones.sql
--
-- On cPanel: create the database and user first, then import this file from
-- phpMyAdmin (Import tab) or the shell:
--   mysql -u cpaneluser_bgmi -p cpaneluser_bgmi < database/schema.sql
-- ---------------------------------------------------------------------------

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS phones (
    id                     INT AUTO_INCREMENT PRIMARY KEY,
    manufacturer           VARCHAR(64)    NOT NULL DEFAULT '',
    model                  VARCHAR(128)   NOT NULL,
    screen_size_inches     DECIMAL(4, 2)  NULL,
    density_dpi            INT            NULL,
    refresh_rate_hz        INT            NULL,
    touch_sampling_rate_hz INT            NULL,
    -- Added beyond the spec's starting point: Build.MODEL is not standardised
    -- (some phones report "Redmi Note 12", others "SM-A546E"), so this optional
    -- comma-separated list of raw Build.MODEL codes makes auto-detection match
    -- far more often. Fill it in with: adb shell getprop ro.product.model
    build_model_codes      VARCHAR(255)   NULL,
    created_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_phones_model (model),
    KEY idx_phones_manufacturer (manufacturer)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedback (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    phone_id   INT          NULL,
    -- `model` and `comment` are additions to the spec's starting point: ratings
    -- for phones that are not in `phones` yet are exactly the ones worth
    -- knowing about, and they would be lost with only a nullable phone_id.
    model      VARCHAR(128) NULL,
    rating     TINYINT      NOT NULL,
    scope      VARCHAR(32)  NULL,
    comment    VARCHAR(500) NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_feedback_phone (phone_id),
    KEY idx_feedback_created (created_at),
    CONSTRAINT fk_feedback_phone FOREIGN KEY (phone_id) REFERENCES phones (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_feedback_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
