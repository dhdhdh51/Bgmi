<?php
/**
 * Lazy PDO connection.
 */

declare(strict_types=1);

final class Database
{
    private static ?PDO $pdo = null;

    /** @param array<string,mixed> $config */
    public static function connect(array $config): PDO
    {
        if (self::$pdo instanceof PDO) {
            return self::$pdo;
        }

        $db = $config['db'] ?? [];

        // An explicit DSN wins: some hosts only expose MySQL over a unix socket
        // (e.g. "mysql:unix_socket=/var/lib/mysql/mysql.sock;dbname=...").
        $dsn = trim((string) ($db['dsn'] ?? ''));
        if ($dsn === '') {
            $dsn = sprintf(
                'mysql:host=%s;port=%d;dbname=%s;charset=%s',
                (string) ($db['host'] ?? 'localhost'),
                (int) ($db['port'] ?? 3306),
                (string) ($db['name'] ?? ''),
                (string) ($db['charset'] ?? 'utf8mb4')
            );
        }

        $options = [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ];
        if (str_starts_with($dsn, 'mysql:')) {
            // Real prepared statements; MySQL-only attribute.
            $options[PDO::ATTR_EMULATE_PREPARES] = false;
        }

        try {
            self::$pdo = new PDO(
                $dsn,
                (string) ($db['user'] ?? ''),
                (string) ($db['pass'] ?? ''),
                $options
            );
        } catch (PDOException $e) {
            // Never leak DSN/credentials to the client.
            $detail = ($config['debug'] ?? false) ? ' (' . $e->getMessage() . ')' : '';
            Http::fail('The API cannot reach its database.' . $detail, 503);
        }

        return self::$pdo;
    }
}
