-- ---------------------------------------------------------------------------
-- Seed data: 51 phones that are common in the BGMI player base.
--
-- IMPORTANT — read before trusting these numbers:
--   * screen_size_inches / refresh_rate_hz are the panel figures published by
--     the manufacturers and are accurate to the nearest published spec.
--   * density_dpi is the *reported* Android densityDpi bucket and can differ
--     slightly between firmware builds.
--   * touch_sampling_rate_hz is the least reliable column: vendors quote peak
--     or "instantaneous" figures that the panel does not sustain. Values here
--     are conservative steady-state estimates.
--
-- Treat this as a starting point, not gospel. The app flags any recommendation
-- built on missing data as an estimate, and the /feedback endpoint exists so
-- these values can be corrected over time. To refine a row:
--
--   UPDATE phones
--      SET touch_sampling_rate_hz = 480,
--          build_model_codes = '23013RK75C'   -- adb shell getprop ro.product.model
--    WHERE model = 'Redmi Note 12 Pro';
--
-- Re-running this file is safe: existing rows are updated, not duplicated.
-- ---------------------------------------------------------------------------

SET NAMES utf8mb4;

INSERT INTO phones
    (manufacturer, model, screen_size_inches, density_dpi, refresh_rate_hz, touch_sampling_rate_hz)
VALUES
    -- Xiaomi / Redmi / Poco
    ('Xiaomi',   'Redmi Note 13 Pro',      6.67, 440, 120, 240),
    ('Xiaomi',   'Redmi Note 13',          6.67, 440, 120, 240),
    ('Xiaomi',   'Redmi Note 12 Pro',      6.67, 440, 120, 240),
    ('Xiaomi',   'Redmi Note 12',          6.67, 440, 120, 240),
    ('Xiaomi',   'Redmi Note 11',          6.43, 409,  90, 180),
    ('Xiaomi',   'Redmi Note 10 Pro',      6.67, 440, 120, 240),
    ('Xiaomi',   'Redmi 13C',              6.74, 400,  90, 120),
    ('Xiaomi',   'Xiaomi 14',              6.36, 460, 120, 480),
    ('Xiaomi',   'Xiaomi 13',              6.36, 440, 120, 480),
    ('Xiaomi',   'Poco X6 Pro',            6.67, 440, 120, 480),
    ('Xiaomi',   'Poco X5 Pro',            6.67, 440, 120, 240),
    ('Xiaomi',   'Poco F5',                6.67, 440, 120, 480),
    ('Xiaomi',   'Poco M6 Pro',            6.79, 395,  90, 240),

    -- Realme
    ('realme',   'realme 11 Pro',          6.70, 440, 120, 240),
    ('realme',   'realme 9 Pro',           6.60, 400, 120, 240),
    ('realme',   'realme narzo 60',        6.43, 409,  90, 180),
    ('realme',   'realme C55',             6.72, 400,  90, 180),
    ('realme',   'realme GT Neo 3',        6.70, 440, 120, 360),

    -- Samsung
    ('samsung',  'Galaxy S23 Ultra',       6.80, 500, 120, 240),
    ('samsung',  'Galaxy S23',             6.10, 425, 120, 240),
    ('samsung',  'Galaxy S22',             6.10, 425, 120, 240),
    ('samsung',  'Galaxy S21 FE',          6.40, 420, 120, 240),
    ('samsung',  'Galaxy A54',             6.40, 403, 120, 240),
    ('samsung',  'Galaxy A34',             6.60, 400, 120, 240),
    ('samsung',  'Galaxy A14',             6.60, 400,  90, 120),
    ('samsung',  'Galaxy M14',             6.60, 400,  90, 120),
    ('samsung',  'Galaxy F23',             6.60, 400, 120, 240),

    -- OnePlus
    ('OnePlus',  'OnePlus 11',             6.70, 525, 120, 360),
    ('OnePlus',  'OnePlus Nord 3',         6.74, 450, 120, 240),
    ('OnePlus',  'OnePlus Nord CE 3 Lite', 6.72, 400, 120, 240),
    ('OnePlus',  'OnePlus 9R',             6.55, 440, 120, 360),

    -- iQOO
    ('iQOO',     'iQOO 11',                6.78, 500, 144, 300),
    ('iQOO',     'iQOO Neo 7',             6.78, 450, 120, 300),
    ('iQOO',     'iQOO Z7',                6.38, 409,  90, 180),

    -- vivo
    ('vivo',     'vivo T2 Pro',            6.78, 440, 120, 240),
    ('vivo',     'vivo V27',               6.78, 440, 120, 240),
    ('vivo',     'vivo Y100',              6.38, 409,  90, 180),

    -- OPPO
    ('OPPO',     'OPPO Reno 10',           6.70, 440, 120, 240),
    ('OPPO',     'OPPO F23',               6.72, 400, 120, 240),

    -- Motorola
    ('motorola', 'moto edge 40',           6.55, 440, 144, 360),
    ('motorola', 'moto g84',               6.55, 400, 120, 240),
    ('motorola', 'moto g62',               6.55, 400, 120, 240),

    -- Nothing
    ('Nothing',  'Nothing Phone (2)',      6.70, 460, 120, 240),
    ('Nothing',  'Nothing Phone (1)',      6.55, 440, 120, 240),

    -- Google
    ('Google',   'Pixel 8',                6.20, 420, 120, 240),
    ('Google',   'Pixel 7a',               6.10, 420,  90, 240),

    -- Infinix / TECNO
    ('Infinix',  'Infinix Note 30',        6.78, 400, 120, 180),
    ('Infinix',  'Infinix Zero 5G',        6.78, 400, 120, 180),
    ('TECNO',    'TECNO POVA 5',           6.78, 400, 120, 180),

    -- ASUS (gaming panels with genuinely high sampling rates)
    ('asus',     'ROG Phone 7',            6.78, 400, 165, 720),
    ('asus',     'ROG Phone 6',            6.78, 400, 165, 720)
-- VALUES(col) rather than the newer "AS new" row alias: shared cPanel hosting
-- is often MariaDB, which does not support the row-alias form.
ON DUPLICATE KEY UPDATE
    manufacturer           = VALUES(manufacturer),
    screen_size_inches     = VALUES(screen_size_inches),
    density_dpi            = VALUES(density_dpi),
    refresh_rate_hz        = VALUES(refresh_rate_hz),
    touch_sampling_rate_hz = VALUES(touch_sampling_rate_hz);
