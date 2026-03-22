package app.model;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


public enum IngredientLifecycle {
    FRESH,
    NEAR_EXPIRY,
    EXPIRED,
    DISCARDED
}
