package app.service;

public class WasteImpactService {
    private static final double METHANE_KG_PER_KG_FOOD = 0.05;
    private static final double CO2E_MULTIPLIER = 28.0;

    public Impact estimateImpact(double savedFoodKg) {
        if (!Double.isFinite(savedFoodKg) || savedFoodKg < 0) {
            throw new IllegalArgumentException("savedFoodKg must be a finite value >= 0");
        }
        double methaneKg = savedFoodKg * METHANE_KG_PER_KG_FOOD;
        double co2EquivalentKg = methaneKg * CO2E_MULTIPLIER;
        return new Impact(savedFoodKg, methaneKg, co2EquivalentKg);
    }

    public record Impact(double savedFoodKg, double methaneAvoidedKg, double co2EquivalentKg) {
    }
}
