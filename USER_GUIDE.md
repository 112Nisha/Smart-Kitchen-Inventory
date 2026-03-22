# User Guide - Smart Kitchen Inventory

## 1. What This System Does
Smart Kitchen Inventory helps restaurant teams:
- Track ingredient quantity and expiry dates in one place.
- Receive expiry alerts before ingredients spoil.
- Get dish suggestions using near-expiry ingredients.
- Generate automatic shopping lists when stock is low.
- View sustainability impact after using expiring ingredients in a dish.

## 2. Who Should Use It
- Restaurant owners
- Kitchen managers
- Chefs
- Procurement staff

## 3. Start Using the Dashboard
1. Open the app in your browser after deployment.
2. You will land on the dashboard.
3. Select or enter a `tenant` (restaurant ID) to switch between restaurants.

## 4. Main Screens
### Dashboard
- Shows ingredient count, near-expiry count, expired count, shopping-needed count, and total notifications.

### Inventory
- Add ingredient name, quantity, unit, expiry date, and low-stock threshold.
- Use ingredient quantities as they are consumed.
- Discard ingredients when spoiled.
- View each ingredient state: `FRESH`, `NEAR_EXPIRY`, `EXPIRED`, `DISCARDED`.

### Expiry Alerts
- Runs the alert pipeline.
- Shows which ingredients are near expiry/expired.
- Shows whether an alert is urgent and the event trail.

### Dish Suggestions
- Displays dishes that can use near-expiry ingredients.
- Click `Log as Cooked` to record usage and see sustainability impact message.

### Shopping List
- Auto-lists ingredients below or equal to threshold.
- Shows suggested reorder quantity.

## 5. Sustainability Message
When you log a suggested dish as cooked, the system estimates:
- Food saved from landfill (kg)
- Methane emissions avoided (kg)
- CO2e avoided (kg)

## 6. Good Operating Practice
- Update quantities immediately after receiving or using ingredients.
- Review expiry alerts once per shift.
- Prioritize suggested dishes for near-expiry items.
- Validate shopping list before placing orders.
