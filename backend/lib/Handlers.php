<?php
/**
 * Endpoint handlers.
 *
 * Kept out of index.php so the routing/wiring stays readable and the handlers
 * can be exercised directly against any PDO connection.
 */

declare(strict_types=1);

/**
 * Builds the recommendation for the posted specs.
 *
 * Anything the app could not measure is filled in from the phone database, and
 * anything the database does not know either falls back to a documented default
 * — in which case the response is flagged is_estimate so the UI can say so.
 *
 * @param array<string,mixed> $body
 * @param array<string,mixed> $config
 * @return array<string,mixed>
 */
function bgmi_handle_calculate(array $body, PhoneRepository $phones, array $config): array
{
    $model = Input::requiredString($body, 'model', 128);
    $manufacturer = Input::optionalString($body, 'manufacturer', 64) ?? '';

    $requestedScreen = Input::optionalFloat($body, 'screen_size_inches', 3.0, 12.0);
    $requestedDpi = Input::optionalInt($body, 'density_dpi', 100, 900);
    $requestedRefresh = Input::optionalInt($body, 'refresh_rate_hz', 24, 480);
    $requestedTouch = Input::optionalInt($body, 'touch_sampling_rate_hz', 30, 3000);
    $androidSdk = Input::optionalInt($body, 'android_sdk_int', 1, 100);

    $defaults = (array) ($config['defaults'] ?? []);
    $phone = $phones->findBestMatch($model, $manufacturer);
    $notes = [];
    $isEstimate = false;

    if ($phone === null) {
        $notes[] = sprintf(
            '"%s" is not in the phone database yet, so only the specs reported by the device were used.',
            $model
        );
    }

    // Screen size: measured on-device > database > default.
    $screen = $requestedScreen ?? ($phone['screen_size_inches'] ?? null);
    if ($screen === null || $screen <= 0.0) {
        $screen = (float) ($defaults['screen_size_inches'] ?? Sensitivity::REFERENCE_SCREEN_INCHES);
        $isEstimate = true;
        $notes[] = sprintf('Screen size was unavailable, assumed %.2f inches.', $screen);
    }

    // Refresh rate: measured on-device > database > default.
    $refresh = $requestedRefresh ?? ($phone['refresh_rate_hz'] ?? null);
    if ($refresh === null || $refresh <= 0) {
        $refresh = (int) ($defaults['refresh_rate_hz'] ?? 60);
        $isEstimate = true;
        $notes[] = sprintf('Refresh rate was unavailable, assumed %d Hz.', $refresh);
    }

    $dpi = $requestedDpi ?? ($phone['density_dpi'] ?? null);

    /*
     * Touch sampling rate is never available on-device: no public Android API
     * exposes it, so it can only come from the database (or a default, which
     * makes the whole result an estimate).
     */
    $touchSource = 'database';
    $touch = $requestedTouch ?? ($phone['touch_sampling_rate_hz'] ?? null);
    if ($requestedTouch !== null) {
        $touchSource = 'request';
    }
    if ($touch === null || $touch <= 0) {
        $touch = (int) ($defaults['touch_sampling_rate_hz'] ?? 240);
        $touchSource = 'default';
        $isEstimate = true;
        $notes[] = sprintf(
            'Touch sampling rate is not exposed by Android and is not recorded for this phone, assumed %d Hz.',
            $touch
        );
    }

    $calculated = Sensitivity::calculate([
        'screen_size_inches' => $screen,
        'refresh_rate_hz' => $refresh,
        'touch_sampling_rate_hz' => $touch,
    ]);

    return [
        'model' => $phone['model'] ?? $model,
        'phone_id' => $phone['id'] ?? null,
        'matched_in_db' => $phone !== null,
        'is_estimate' => $isEstimate,
        'notes' => $notes,
        'inputs_used' => [
            'screen_size_inches' => round((float) $screen, 2),
            'density_dpi' => $dpi,
            'refresh_rate_hz' => (int) $refresh,
            'touch_sampling_rate_hz' => (int) $touch,
            'touch_sampling_source' => $touchSource,
            'android_sdk_int' => $androidSdk,
            'factors' => $calculated['factors'],
        ],
        'sensitivities' => $calculated['sensitivities'],
    ];
}

/**
 * Stores a rating against a phone, for retuning the formula later.
 *
 * @param array<string,mixed> $body
 * @return array<string,mixed>
 */
function bgmi_handle_feedback(array $body, PhoneRepository $phones): array
{
    $rating = Input::requiredInt($body, 'rating', 1, 5);
    $phoneId = Input::optionalInt($body, 'phone_id', 1, PHP_INT_MAX);
    $model = Input::optionalString($body, 'model', 128);
    $scope = Input::optionalString($body, 'scope', 32);
    $comment = Input::optionalString($body, 'comment', 500);

    $allowedScopes = [
        'overall', 'camera', 'red_dot_2x', '3x', '4x', '6x', '8x', 'ads', 'gyroscope',
    ];
    if ($scope !== null && !in_array($scope, $allowedScopes, true)) {
        Http::fail(
            sprintf('"scope" must be one of: %s.', implode(', ', $allowedScopes)),
            422
        );
    }

    // A rating is only useful if we know what it is about.
    if ($phoneId === null && $model === null) {
        Http::fail('Send either "phone_id" or "model".', 422);
    }

    // Resolve/verify the phone so the foreign key stays valid.
    $resolvedId = null;
    if ($phoneId !== null) {
        if ($phones->findById($phoneId) === null) {
            Http::fail(sprintf('No phone with id %d.', $phoneId), 422);
        }
        $resolvedId = $phoneId;
    } elseif ($model !== null) {
        $match = $phones->findBestMatch($model);
        $resolvedId = $match['id'] ?? null;
    }

    $id = $phones->insertFeedback($resolvedId, $model, $rating, $scope, $comment);

    return [
        'status' => 'stored',
        'id' => $id,
        'phone_id' => $resolvedId,
    ];
}
