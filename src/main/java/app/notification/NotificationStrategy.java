package app.notification;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;



public interface NotificationStrategy {
    void send(NotificationMessage message);
}
