package app.alerts;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


public abstract class AlertHandler {
    private AlertHandler next;

    public AlertHandler setNext(AlertHandler next) {
        this.next = next;
        return next;
    }

    public final void handle(ExpiryAlertContext context) {
        process(context);
        if (next != null) {
            next.handle(context);
        }
    }

    protected abstract void process(ExpiryAlertContext context);
}
