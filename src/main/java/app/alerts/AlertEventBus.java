package app.alerts;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



import java.util.ArrayList;
import java.util.List;

public class AlertEventBus {
    private final List<ExpiryObserver> observers = new ArrayList<>();

    public void subscribe(ExpiryObserver observer) {
        observers.add(observer);
    }

    public void publish(ExpiryAlertContext context) {
        observers.forEach(observer -> observer.onAlert(context));
    }
}
