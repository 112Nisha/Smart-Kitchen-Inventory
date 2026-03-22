package app.service;

import app.alerts.*;
import app.model.*;
import app.notification.*;
import app.repository.*;
import app.service.*;
import app.state.*;
import app.web.*;


public class WasteImpactService {
    private static final double METHANE_KG_PER_KG_FOOD = 0.05;
    private static final double CO2E_MULTIPLIER = 28.0;

    public Impact estimateImpact(double savedFoodKg) {
        double methaneKg = savedFoodKg * METHANE_KG_PER_KG_FOOD;
        double co2EquivalentKg = methaneKg * CO2E_MULTIPLIER;
        return new Impact(savedFoodKg, methaneKg, co2EquivalentKg);
    }

    public record Impact(double savedFoodKg, double methaneAvoidedKg, double co2EquivalentKg) {
    }
}
