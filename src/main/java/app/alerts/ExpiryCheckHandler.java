package app.alerts;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


public class ExpiryCheckHandler extends AlertHandler {
    private final int nearExpiryDays;

    public ExpiryCheckHandler(int nearExpiryDays) {
        this.nearExpiryDays = nearExpiryDays;
    }

    @Override
    protected void process(ExpiryAlertContext context) {
        long days = context.getDaysUntilExpiry();
        if (days <= nearExpiryDays) {
            context.addEvent("ExpiryCheckHandler: Ingredient is in alert threshold");
        } else {
            context.addEvent("ExpiryCheckHandler: Ingredient is not in alert threshold");
        }
    }
}
