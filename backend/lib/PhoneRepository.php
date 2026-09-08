<?php
/**
 * Queries against the `phones` and `feedback` tables.
 */

declare(strict_types=1);

final class PhoneRepository
{
    private PDO $pdo;

    public function __construct(PDO $pdo)
    {
        $this->pdo = $pdo;
    }

    /**
     * Phones matching a free-text search, for the manual-select autocomplete.
     *
     * @return array<int,array<string,mixed>>
     */
    public function search(string $term, int $limit): array
    {
        $limit = max(1, min(200, $limit));

        if ($term === '') {
            $statement = $this->pdo->prepare(
                'SELECT * FROM phones ORDER BY manufacturer, model LIMIT ' . $limit
            );
            $statement->execute();

            return $this->mapRows($statement->fetchAll());
        }

        // Note: each placeholder appears exactly once. PDO with native prepares
        // (ATTR_EMULATE_PREPARES => false) does not allow reusing a named
        // placeholder, so :model_like and :manufacturer_like are separate.
        $statement = $this->pdo->prepare(
            'SELECT * FROM phones
              WHERE model LIKE :model_like OR manufacturer LIKE :manufacturer_like
              ORDER BY
                CASE WHEN model LIKE :prefix THEN 0 ELSE 1 END,
                manufacturer, model
              LIMIT ' . $limit
        );
        $escaped = $this->escapeLike($term);
        $statement->execute([
            'model_like' => '%' . $escaped . '%',
            'manufacturer_like' => '%' . $escaped . '%',
            'prefix' => $escaped . '%',
        ]);

        return $this->mapRows($statement->fetchAll());
    }

    public function findById(int $id): ?array
    {
        $statement = $this->pdo->prepare('SELECT * FROM phones WHERE id = :id LIMIT 1');
        $statement->execute(['id' => $id]);
        $row = $statement->fetch();

        return is_array($row) ? $this->mapRow($row) : null;
    }

    /** Exact (case-insensitive) model lookup. */
    public function findByModel(string $model): ?array
    {
        $statement = $this->pdo->prepare('SELECT * FROM phones WHERE model = :model LIMIT 1');
        $statement->execute(['model' => $model]);
        $row = $statement->fetch();

        return is_array($row) ? $this->mapRow($row) : null;
    }

    /**
     * Best-effort match for a model string reported by the app.
     *
     * Build.MODEL is not standardised: some phones report a marketing name
     * ("Redmi Note 12"), others an internal code ("SM-A546E", "CPH2451"). So:
     *   1. exact match on `model`
     *   2. exact match on one of the comma-separated `build_model_codes`
     *   3. match after normalising (lowercase, alphanumerics only)
     *   4. give up — the caller then uses defaults and flags an estimate
     *
     * Steps 2–3 scan the table in PHP. `phones` holds tens to a few thousand
     * rows, so this stays cheap; add a normalised generated column + index if
     * the table ever grows past that.
     */
    public function findBestMatch(string $model, string $manufacturer = ''): ?array
    {
        $exact = $this->findByModel($model);
        if ($exact !== null) {
            return $exact;
        }

        $needle = $this->normalise($model);
        if ($needle === '') {
            return null;
        }

        $needleWithBrand = $this->normalise($manufacturer . $model);
        $statement = $this->pdo->query('SELECT * FROM phones LIMIT 5000');
        $rows = $statement === false ? [] : $statement->fetchAll();

        $partialMatch = null;
        foreach ($rows as $row) {
            $candidateModel = $this->normalise((string) ($row['model'] ?? ''));
            $candidateFull = $this->normalise(
                (string) ($row['manufacturer'] ?? '') . (string) ($row['model'] ?? '')
            );

            foreach ($this->buildCodes($row) as $code) {
                if ($this->normalise($code) === $needle) {
                    return $this->mapRow($row);
                }
            }

            if ($candidateModel === $needle || $candidateFull === $needle
                || $candidateModel === $needleWithBrand || $candidateFull === $needleWithBrand) {
                return $this->mapRow($row);
            }

            if ($partialMatch === null && $candidateModel !== '' && str_contains($needle, $candidateModel)) {
                $partialMatch = $row;
            }
        }

        return $partialMatch !== null ? $this->mapRow($partialMatch) : null;
    }

    /** Stores a rating. Returns the new row id. */
    public function insertFeedback(
        ?int $phoneId,
        ?string $model,
        int $rating,
        ?string $scope,
        ?string $comment
    ): int {
        $statement = $this->pdo->prepare(
            'INSERT INTO feedback (phone_id, model, rating, scope, comment)
             VALUES (:phone_id, :model, :rating, :scope, :comment)'
        );
        $statement->execute([
            'phone_id' => $phoneId,
            'model' => $model,
            'rating' => $rating,
            'scope' => $scope,
            'comment' => $comment,
        ]);

        return (int) $this->pdo->lastInsertId();
    }

    /** @return string[] */
    private function buildCodes(array $row): array
    {
        $codes = (string) ($row['build_model_codes'] ?? '');
        if (trim($codes) === '') {
            return [];
        }

        return array_filter(array_map('trim', explode(',', $codes)), static fn ($c) => $c !== '');
    }

    private function normalise(string $value): string
    {
        return strtolower((string) preg_replace('/[^a-z0-9]/i', '', $value));
    }

    private function escapeLike(string $term): string
    {
        return str_replace(['\\', '%', '_'], ['\\\\', '\\%', '\\_'], $term);
    }

    /** @param array<int,array<string,mixed>> $rows */
    private function mapRows(array $rows): array
    {
        return array_map([$this, 'mapRow'], $rows);
    }

    /** Normalises column types so JSON output is numeric, not stringly typed. */
    private function mapRow(array $row): array
    {
        return [
            'id' => isset($row['id']) ? (int) $row['id'] : null,
            'manufacturer' => (string) ($row['manufacturer'] ?? ''),
            'model' => (string) ($row['model'] ?? ''),
            'screen_size_inches' => isset($row['screen_size_inches'])
                ? (float) $row['screen_size_inches'] : null,
            'density_dpi' => isset($row['density_dpi']) ? (int) $row['density_dpi'] : null,
            'refresh_rate_hz' => isset($row['refresh_rate_hz']) ? (int) $row['refresh_rate_hz'] : null,
            'touch_sampling_rate_hz' => isset($row['touch_sampling_rate_hz'])
                ? (int) $row['touch_sampling_rate_hz'] : null,
        ];
    }
}
