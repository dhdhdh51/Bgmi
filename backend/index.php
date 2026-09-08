<?php
/**
 * BGMI Scope Sensitivity Recommender — JSON API front controller.
 *
 * Routes (relative to wherever this directory is deployed, e.g. /api):
 *   GET  /phones?search=xyz          search the phone-spec database
 *   GET  /phone-specs?model=xyz      specs for one exact model
 *   POST /calculate-sensitivity      recommendation for the posted specs
 *   POST /feedback                   store a rating for future tuning
 *
 * Pretty URLs come from .htaccess; where mod_rewrite is unavailable the same
 * routes work as /index.php?route=phones.
 */

declare(strict_types=1);

require_once __DIR__ . '/lib/bootstrap.php';

$config = bgmi_load_config();
bgmi_install_error_handling((bool) ($config['debug'] ?? false));
Http::applyCors((array) ($config['cors_origins'] ?? []));

$route = Http::route();
$pdo = Database::connect($config);
$phones = new PhoneRepository($pdo);

switch ($route) {
    case '':
    case 'health':
        Http::json([
            'service' => 'bgmi-sensitivity-api',
            'status' => 'ok',
            'endpoints' => [
                'GET /phones?search=',
                'GET /phone-specs?model=',
                'POST /calculate-sensitivity',
                'POST /feedback',
            ],
        ]);
        // no break — Http::json() exits.

    case 'phones':
        Http::requireMethod('GET');
        $results = $phones->search(
            Http::queryString('search'),
            (int) ($config['search_limit'] ?? 50)
        );
        Http::json([
            'count' => count($results),
            'phones' => $results,
        ]);

    case 'phone-specs':
        Http::requireMethod('GET');
        $model = Http::queryString('model');
        if ($model === '') {
            Http::fail('Pass ?model=<exact model name>.', 422);
        }
        $phone = $phones->findByModel($model) ?? $phones->findBestMatch($model);
        if ($phone === null) {
            Http::fail(sprintf('No phone named "%s" in the database.', $model), 404);
        }
        Http::json(['phone' => $phone]);

    case 'calculate-sensitivity':
        Http::requireMethod('POST');
        Http::json(bgmi_handle_calculate(Http::jsonBody(), $phones, $config));

    case 'feedback':
        Http::requireMethod('POST');
        Http::json(bgmi_handle_feedback(Http::jsonBody(), $phones), 201);

    default:
        Http::fail(sprintf('Unknown endpoint "%s".', $route), 404);
}
