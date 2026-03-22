package app.alerts;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public class ChefObserver implements ExpiryObserver {
    @Override
    public void onAlert(ExpiryAlertContext context) {
        context.addEvent("Observer: Chef subscribed alert stream");
    }
}
