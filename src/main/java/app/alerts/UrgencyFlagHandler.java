package app.alerts;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


public class UrgencyFlagHandler extends AlertHandler {
    private final double highQuantityThreshold;

    public UrgencyFlagHandler(double highQuantityThreshold) {
        this.highQuantityThreshold = highQuantityThreshold;
    }

    @Override
    protected void process(ExpiryAlertContext context) {
        boolean criticalDays = context.getDaysUntilExpiry() <= 1;
        boolean highQuantity = context.getIngredient().getQuantity() >= highQuantityThreshold;
        if (criticalDays || highQuantity) {
            context.setUrgent(true);
            context.addEvent("UrgencyFlagHandler: Marked as urgent");
        }
    }
}
