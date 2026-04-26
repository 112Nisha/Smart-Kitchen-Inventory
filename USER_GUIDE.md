# User Guide - Smart Kitchen Inventory

## 1. What This System Does

Smart Kitchen Inventory helps restaurant teams:

- Track ingredient quantity, unit, expiry date, and low-stock threshold
- View ingredient lifecycle state in one place
- Receive expiry and low-stock notifications
- Get dish suggestions for near-expiry ingredients
- Generate shopping lists automatically
- Record a suggested dish as cooked and view waste-impact feedback

## 2. Who Should Use It

- Restaurant owners
- Kitchen managers
- Chefs
- Procurement staff

## 3. Sign In or Register

When you open the application:

- logged-out users are redirected to the login page
- new users can register from the registration page

Registration rules:

- the first user for a new restaurant must register as a `manager`
- later users can register as `manager` or `chef`
- usernames must be valid email addresses

After login:

- managers go to the dashboard
- chefs go to dish recommendations

## 4. Tenant / Restaurant Model

Each logged-in account is tied to one restaurant tenant. The current tenant is shown in the header after login.

The prototype includes sample tenant inventory data for demonstration, but access still depends on registering and logging in with a restaurant account.

## 5. Main Screens

### Dashboard

Shows summary counts for the current tenant:

- total ingredients
- near-expiry ingredients
- expired ingredients
- items needing shopping
- notifications sent

### Inventory

Use the inventory screen to:

- add ingredients with quantity, unit, expiry date, and low-stock threshold
- update quantities and expiry details
- log ingredient usage
- discard spoiled ingredients
- review lifecycle state: `FRESH`, `NEAR_EXPIRY`, `EXPIRED`, `DISCARDED`

### Expiry Alerts & Notifications

Use this page to:

- view tenant-specific notifications
- review near-expiry and low-stock activity
- update alert settings such as near-expiry days and retention days

Saved alert settings trigger an immediate re-evaluation of notifications.

### Dish Suggestions

This page:

- lists dishes that can use near-expiry ingredients
- lets chefs or managers log a suggested dish as cooked
- shows a sustainability / waste-impact summary after cooking

### Shopping List

This page is intended for non-chef users.

It can:

- list ingredients at or below threshold
- show suggested reorder quantities
- mark items as purchased
- mark items as ignored
- reset ignored items
- export the current shopping list as CSV

## 6. Sustainability Impact Feedback

When a suggested dish is logged as cooked, the system reports:

- near-expiry ingredients rescued
- amount used by unit
- near-expiry kilograms used
- near-expiry liters used

## 7. Good Operating Practice

- Register each staff member under the correct restaurant
- Update inventory immediately after receiving or using stock
- Review notifications regularly
- Prioritize recommended dishes for near-expiry items
- Confirm shopping-list items before procurement
- Log discarded ingredients so the inventory state stays accurate
