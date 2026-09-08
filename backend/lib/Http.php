<?php
/**
 * Request parsing and JSON responses.
 */

declare(strict_types=1);

final class Http
{
    /** @var array<string,mixed>|null */
    private static ?array $jsonBody = null;

    public static function method(): string
    {
        return strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
    }

    /**
     * The route being requested, without leading/trailing slashes.
     *
     * Works both with pretty URLs (/api/calculate-sensitivity, via .htaccess)
     * and without URL rewriting (/api/index.php?route=calculate-sensitivity),
     * because some shared hosts disable mod_rewrite.
     */
    public static function route(): string
    {
        if (isset($_GET['route']) && is_string($_GET['route'])) {
            return trim($_GET['route'], '/');
        }

        $path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';

        // Strip the directory the front controller lives in, e.g. "/api".
        $base = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
        if ($base !== '' && str_starts_with($path, $base)) {
            $path = substr($path, strlen($base));
        }

        $path = trim($path, '/');
        if ($path === 'index.php') {
            return '';
        }

        return $path;
    }

    /** Decoded JSON request body (empty array when there is none). */
    public static function jsonBody(): array
    {
        if (self::$jsonBody !== null) {
            return self::$jsonBody;
        }

        $raw = file_get_contents('php://input');
        if ($raw === false || trim($raw) === '') {
            return self::$jsonBody = [];
        }

        $decoded = json_decode($raw, true);
        if (!is_array($decoded)) {
            self::fail('Request body must be a JSON object.', 400);
        }

        return self::$jsonBody = $decoded;
    }

    public static function queryString(string $key, string $default = ''): string
    {
        $value = $_GET[$key] ?? $default;

        return is_string($value) ? trim($value) : $default;
    }

    /** @param array<string,mixed> $payload */
    public static function json(array $payload, int $status = 200): void
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_PRESERVE_ZERO_FRACTION);
        exit;
    }

    /** Sends an error response and terminates the request. */
    public static function fail(string $message, int $status = 400, array $extra = []): void
    {
        self::json(array_merge(['error' => $message, 'status' => $status], $extra), $status);
    }

    /** @param string[] $allowedOrigins */
    public static function applyCors(array $allowedOrigins): void
    {
        $origin = $_SERVER['HTTP_ORIGIN'] ?? '';

        if ($allowedOrigins !== [] && $origin !== '') {
            if (in_array('*', $allowedOrigins, true)) {
                header('Access-Control-Allow-Origin: *');
            } elseif (in_array($origin, $allowedOrigins, true)) {
                header('Access-Control-Allow-Origin: ' . $origin);
                header('Vary: Origin');
            }
            header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
            header('Access-Control-Allow-Headers: Content-Type');
            header('Access-Control-Max-Age: 600');
        }

        if (self::method() === 'OPTIONS') {
            http_response_code(204);
            exit;
        }
    }

    public static function requireMethod(string $expected): void
    {
        if (self::method() !== $expected) {
            header('Allow: ' . $expected);
            self::fail(sprintf('This endpoint expects %s.', $expected), 405);
        }
    }
}
