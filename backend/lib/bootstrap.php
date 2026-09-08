<?php
/**
 * Shared start-up: configuration, error handling and class loading.
 */

declare(strict_types=1);

require_once __DIR__ . '/Http.php';
require_once __DIR__ . '/Input.php';
require_once __DIR__ . '/Database.php';
require_once __DIR__ . '/PhoneRepository.php';
require_once __DIR__ . '/Sensitivity.php';
require_once __DIR__ . '/Handlers.php';

/*
 * mbstring is present on virtually every host, but the API only uses it for
 * length checks — so degrade to byte semantics instead of dying if it is absent.
 */
if (!function_exists('mb_strlen')) {
    function mb_strlen(string $string, ?string $encoding = null): int
    {
        return strlen($string);
    }
}
if (!function_exists('mb_substr')) {
    function mb_substr(string $string, int $start, ?int $length = null, ?string $encoding = null): string
    {
        return substr($string, $start, $length);
    }
}

if (!extension_loaded('pdo_mysql')) {
    Http::fail('The pdo_mysql PHP extension is required but not enabled on this host.', 500);
}

/** @return array<string,mixed> */
function bgmi_load_config(): array
{
    $configFile = dirname(__DIR__) . '/config.php';
    if (!is_file($configFile)) {
        Http::fail(
            'The API is not configured yet: copy backend/config.sample.php to '
            . 'backend/config.php and fill in the database credentials.',
            500
        );
    }

    $config = require $configFile;
    if (!is_array($config)) {
        Http::fail('config.php must return an array.', 500);
    }

    return $config;
}

/**
 * Errors are logged, never printed: a PHP warning in the middle of a JSON body
 * would break the client's parser.
 */
function bgmi_install_error_handling(bool $debug): void
{
    ini_set('display_errors', '0');
    ini_set('log_errors', '1');
    error_reporting(E_ALL);

    set_exception_handler(static function (Throwable $e) use ($debug): void {
        error_log('[bgmi-api] ' . $e::class . ': ' . $e->getMessage() . ' @ ' . $e->getFile() . ':' . $e->getLine());
        $detail = $debug ? ' (' . $e->getMessage() . ')' : '';
        Http::fail('Internal server error.' . $detail, 500);
    });

    set_error_handler(static function (int $severity, string $message, string $file, int $line): bool {
        if ((error_reporting() & $severity) === 0) {
            return false;
        }
        throw new ErrorException($message, 0, $severity, $file, $line);
    });
}
