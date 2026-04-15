package app.repository;

import app.model.NotificationMessage;

import java.time.LocalDate;
import java.util.List;

/**
 * Abstraction over the notification persistence layer so the alerting pipeline
 * can be backed by either the legacy in-memory list or the durable SQLite
 * store without changing any callers.
 *
 * Contract:
 *   - save(message) is idempotent within a calendar day for the same
 *     (tenantId, ingredientId, recipientRole) tuple. Callers may invoke it on
 *     every sweep; duplicates are silently dropped.
 *   - all() / allForTenant() return an immutable snapshot, newest-first.
 *     Callers must not assume the list is stable — another sweep may add rows
 *     between consecutive calls.
 *   - Prefer allForTenant(tenantId) over all() + client-side filter: the SQLite
 *     backend pushes the filter into the DB, which matters once the table grows.
 */
public interface NotificationStore {
    void save(NotificationMessage message);

    List<NotificationMessage> all();

    List<NotificationMessage> allForTenant(String tenantId);

    /**
     * Deletes notifications whose createdAt falls strictly before {@code cutoff}.
     * Intended for the retention pruner — notifications fire within ~3 days of
     * an ingredient's expiry, so a 30-day cutoff on createdAt is a close
     * pragmatic stand-in for "30 days past the item's expiry" without adding
     * an expiry column to the notifications table.
     *
     * @return number of rows actually removed (for logging / tests).
     */
    int pruneOlderThan(LocalDate cutoff);
}
