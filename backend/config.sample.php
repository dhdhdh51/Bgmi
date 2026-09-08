<?php
/**
 * Copy this file to config.php and fill in your own values.
 *
 * config.php is git-ignored on purpose: it holds database credentials.
 * On cPanel/LiteSpeed hosting the database user is usually named
 * "cpaneluser_dbuser" and the database "cpaneluser_dbname".
 */

declare(strict_types=1);

return [
    'db' => [
        'host' => 'localhost',
        'port' => 3306,
        'name' => 'cpaneluser_bgmi',
        'user' => 'cpaneluser_bgmi',
        'pass' => 'change-me',
        'charset' => 'utf8mb4',

        /*
         * Optional. When set, this DSN is used verbatim and host/port/name are
         * ignored — needed on hosts that only expose MySQL over a socket:
         * 'mysql:unix_socket=/var/lib/mysql/mysql.sock;dbname=cpaneluser_bgmi;charset=utf8mb4'
         */
        'dsn' => '',
    ],

    // true prints exception messages in API responses. Keep false in production.
    'debug' => false,

    /*
     * Origins allowed to call the API from a browser. The Android app does not
     * need CORS at all, so leave this empty unless you build a web front end.
     * Use ['*'] to allow any origin.
     */
    'cors_origins' => [],

    // Maximum rows returned by GET /phones.
    'search_limit' => 50,

    /*
     * Fallback specs used when a phone is neither detected nor found in the
     * database. Any recommendation built on these is flagged is_estimate=true.
     */
    'defaults' => [
        'screen_size_inches' => 6.50,
        'density_dpi' => 400,
        'refresh_rate_hz' => 60,
        'touch_sampling_rate_hz' => 240,
    ],
];
