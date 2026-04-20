package app.repository;

import app.model.ExpiryAlertContext;
import app.service.ExpiryAlertScheduler;
import app.service.IngredientStateTracker;
import app.service.StakeholderNotificationHandler;
import app.model.*;
import app.repository.InMemoryNotificationStore;
import app.repository.NotificationStore;
import app.repository.SqliteNotificationStore;
import app.service.DashboardNotificationStrategy;
import app.service.NotificationService;
import app.service.NotificationStrategy;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class InMemoryNotificationStore implements NotificationStore {
    private final List<NotificationMessage> notifications = Collections.synchronizedList(new ArrayList<>());
    // Dedup index: one entry per (tenantId, ingredientId, recipientRole, createdAt-date).
    // Without this, every /expiry-alerts refresh would append a duplicate row —
    // the "refresh = spam" bug called out in the expiry subsystem review.
    private final Set<String> dedupKeys = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void save(NotificationMessage message) {
        String key = dedupKeyOf(message);
        // synchronized block keeps the check-and-add atomic under concurrent
        // observer/handler callers; HashSet.add returns false if already present.
        synchronized (dedupKeys) {
            if (!dedupKeys.add(key)) {
                return;
            }
        }
        notifications.add(message);
    }

    @Override
    public List<NotificationMessage> all() {
        synchronized (notifications) {
            // Reverse so newest appears first — matches the SQLite store's
            // ORDER BY id DESC semantics so callers see the same ordering
            // regardless of backend.
            List<NotificationMessage> reversed = new ArrayList<>(notifications);
            Collections.reverse(reversed);
            return List.copyOf(reversed);
        }
    }

    @Override
    public List<NotificationMessage> allForTenant(String tenantId) {
        synchronized (notifications) {
            List<NotificationMessage> filtered = new ArrayList<>();
            for (NotificationMessage message : notifications) {
                if (message.getTenantId().equals(tenantId)) {
                    filtered.add(message);
                }
            }
            Collections.reverse(filtered);
            return List.copyOf(filtered);
        }
    }

    @Override
    public int pruneOlderThan(LocalDate cutoff) {
        int removed = 0;
        synchronized (notifications) {
            Iterator<NotificationMessage> it = notifications.iterator();
            while (it.hasNext()) {
                NotificationMessage message = it.next();
                if (message.getCreatedAt().toLocalDate().isBefore(cutoff)) {
                    it.remove();
                    // Evict the matching dedup key so memory doesn't bloat
                    // over time — dedup keys are date-scoped, so keys for
                    // pruned days can never legitimately match again.
                    dedupKeys.remove(dedupKeyOf(message));
                    removed++;
                }
            }
        }
        return removed;
    }

    @Override
    public int pruneByIngredient(String tenantId, String ingredientId) {
        int removed = 0;
        synchronized (notifications) {
            Iterator<NotificationMessage> it = notifications.iterator();
            while (it.hasNext()) {
                NotificationMessage m = it.next();
                if (m.getTenantId().equals(tenantId) && m.getIngredientId().equals(ingredientId)) {
                    it.remove();
                    dedupKeys.remove(dedupKeyOf(m));
                    removed++;
                }
            }
        }
        return removed;
    }

    private String dedupKeyOf(NotificationMessage message) {
        // Date portion of createdAt: one "alert window" per (ingredient, role)
        // per calendar day. New day → fresh alert.
        LocalDate day = message.getCreatedAt().toLocalDate();
        return message.getTenantId() + "|" + message.getIngredientId() + "|" + message.getRecipientRole() + "|" + day;
    }
}
