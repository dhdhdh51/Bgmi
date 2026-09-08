<?php
/**
 * The sensitivity formula.
 *
 * Everything tunable lives in this one file on purpose: the Android app has no
 * calculation logic at all, so the recommendation can be retuned from feedback
 * data by editing the constants below — no app update, no release.
 *
 * Baselines are the values validated on a 6.5" reference device and scaled by:
 *   - screen size    (a bigger panel needs a bigger swipe for the same turn)
 *   - refresh rate   (higher frame rate tracks faster, needs slightly more)
 *   - touch sampling (a faster digitiser registers the swipe sooner)
 */

declare(strict_types=1);

final class Sensitivity
{
    /** Reference device the baselines were tuned on. */
    public const REFERENCE_SCREEN_INCHES = 6.5;

    /** BGMI accepts 1–300 for every sensitivity slider. */
    public const MIN_VALUE = 1;
    public const MAX_VALUE = 300;

    /** Camera / ADS / scope baselines for the reference device. */
    public const BASELINES = [
        'free_look' => 45,
        'tpp_no_scope' => 90,
        'fpp_no_scope' => 95,
        'red_dot_tpp' => 62,
        'red_dot_fpp' => 65,
        'scope_3x' => 28,
        'scope_4x' => 22,
        'scope_6x' => 17,
        'scope_8x' => 11,
        'ads_sensitivity' => 55,
    ];

    /** Gyroscope baselines for the reference device. */
    public const GYRO_BASELINES = [
        '3x' => 120,
        '4x' => 90,
        '6x' => 55,
        '8x' => 35,
    ];

    /**
     * Calculates every recommended value.
     *
     * @param array{screen_size_inches?:float,refresh_rate_hz?:int,touch_sampling_rate_hz?:int} $specs
     * @return array{sensitivities:array<string,mixed>,factors:array<string,float>}
     */
    public static function calculate(array $specs): array
    {
        $screenInches = (float) ($specs['screen_size_inches'] ?? self::REFERENCE_SCREEN_INCHES);
        $refreshHz = (int) ($specs['refresh_rate_hz'] ?? 60);
        $touchHz = (int) ($specs['touch_sampling_rate_hz'] ?? 240);

        $screenFactor = self::screenFactor($screenInches);
        $refreshFactor = self::refreshFactor($refreshHz);
        $touchFactor = self::touchFactor($touchHz);
        $combined = $screenFactor * $refreshFactor * $touchFactor;

        $scale = static fn (int $baseline): int => self::clamp((int) round($baseline * $combined));

        $sensitivities = [
            'camera' => [
                'free_look' => $scale(self::BASELINES['free_look']),
                'tpp_no_scope' => $scale(self::BASELINES['tpp_no_scope']),
                'fpp_no_scope' => $scale(self::BASELINES['fpp_no_scope']),
            ],
            'red_dot_holo_2x' => [
                'tpp' => $scale(self::BASELINES['red_dot_tpp']),
                'fpp' => $scale(self::BASELINES['red_dot_fpp']),
            ],
            'scope_3x' => $scale(self::BASELINES['scope_3x']),
            'scope_4x' => $scale(self::BASELINES['scope_4x']),
            'scope_6x' => $scale(self::BASELINES['scope_6x']),
            'scope_8x' => $scale(self::BASELINES['scope_8x']),
            'ads_sensitivity' => $scale(self::BASELINES['ads_sensitivity']),
            'gyroscope' => [
                '3x' => $scale(self::GYRO_BASELINES['3x']),
                '4x' => $scale(self::GYRO_BASELINES['4x']),
                '6x' => $scale(self::GYRO_BASELINES['6x']),
                '8x' => $scale(self::GYRO_BASELINES['8x']),
            ],
        ];

        return [
            'sensitivities' => $sensitivities,
            'factors' => [
                'screen' => round($screenFactor, 4),
                'refresh' => round($refreshFactor, 4),
                'touch_sampling' => round($touchFactor, 4),
                'combined' => round($combined, 4),
            ],
        ];
    }

    /**
     * Larger screens need proportionally more sensitivity, but the relationship
     * flattens out at the extremes, so the factor is clamped.
     */
    private static function screenFactor(float $screenInches): float
    {
        if ($screenInches < 3.0 || $screenInches > 12.0) {
            return 1.0;
        }

        return self::clampFloat($screenInches / self::REFERENCE_SCREEN_INCHES, 0.85, 1.20);
    }

    private static function refreshFactor(int $refreshHz): float
    {
        if ($refreshHz >= 144) {
            return 1.08;
        }
        if ($refreshHz >= 90) {
            return 1.05;
        }

        return 1.0;
    }

    /**
     * A faster digitiser reports the swipe sooner, so slightly less sensitivity
     * is needed for the same perceived speed.
     */
    private static function touchFactor(int $touchHz): float
    {
        if ($touchHz >= 480) {
            return 0.97;
        }
        if ($touchHz >= 360) {
            return 0.99;
        }

        return 1.0;
    }

    private static function clamp(int $value): int
    {
        return max(self::MIN_VALUE, min(self::MAX_VALUE, $value));
    }

    private static function clampFloat(float $value, float $min, float $max): float
    {
        return max($min, min($max, $value));
    }
}
