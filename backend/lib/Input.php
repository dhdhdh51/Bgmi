<?php
/**
 * Request payload validation.
 *
 * Missing values come back as null so the caller can substitute a database or
 * default value; values that are present but nonsensical are rejected with 422
 * rather than silently coerced.
 */

declare(strict_types=1);

final class Input
{
    /** @param array<string,mixed> $data */
    public static function requiredString(array $data, string $key, int $maxLength = 255): string
    {
        $value = $data[$key] ?? null;
        if (!is_string($value) || trim($value) === '') {
            Http::fail(sprintf('"%s" is required and must be a non-empty string.', $key), 422);
        }

        $value = trim((string) $value);
        if (mb_strlen($value) > $maxLength) {
            Http::fail(sprintf('"%s" must be at most %d characters.', $key, $maxLength), 422);
        }

        return $value;
    }

    /** @param array<string,mixed> $data */
    public static function optionalString(array $data, string $key, int $maxLength = 255): ?string
    {
        $value = $data[$key] ?? null;
        if ($value === null || $value === '') {
            return null;
        }
        if (!is_string($value)) {
            Http::fail(sprintf('"%s" must be a string.', $key), 422);
        }

        $value = trim((string) $value);
        if ($value === '') {
            return null;
        }
        if (mb_strlen($value) > $maxLength) {
            $value = mb_substr($value, 0, $maxLength);
        }

        return $value;
    }

    /** @param array<string,mixed> $data */
    public static function optionalInt(array $data, string $key, int $min, int $max): ?int
    {
        $value = $data[$key] ?? null;
        if ($value === null || $value === '') {
            return null;
        }
        if (!is_numeric($value)) {
            Http::fail(sprintf('"%s" must be a number.', $key), 422);
        }

        $int = (int) round((float) $value);
        if ($int < $min || $int > $max) {
            Http::fail(sprintf('"%s" must be between %d and %d.', $key, $min, $max), 422);
        }

        return $int;
    }

    /** @param array<string,mixed> $data */
    public static function requiredInt(array $data, string $key, int $min, int $max): int
    {
        $value = self::optionalInt($data, $key, $min, $max);
        if ($value === null) {
            Http::fail(sprintf('"%s" is required.', $key), 422);
        }

        return (int) $value;
    }

    /** @param array<string,mixed> $data */
    public static function optionalFloat(array $data, string $key, float $min, float $max): ?float
    {
        $value = $data[$key] ?? null;
        if ($value === null || $value === '') {
            return null;
        }
        if (!is_numeric($value)) {
            Http::fail(sprintf('"%s" must be a number.', $key), 422);
        }

        $float = (float) $value;
        if ($float < $min || $float > $max) {
            Http::fail(
                sprintf('"%s" must be between %s and %s.', $key, (string) $min, (string) $max),
                422
            );
        }

        return round($float, 2);
    }
}
