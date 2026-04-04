# Smart Kitchen Inventory

Prototype implementation of a web-based restaurant inventory system focused on reducing food waste by tracking stock, monitoring expiry dates, triggering alerts, recommending dishes for near-expiry items, and generating shopping lists.

## Implemented Features
- Ingredient stock and expiry tracking (add/use/update/discard)
- Expiry alerts and escalation pipeline
- Dish recommendations for near-expiry ingredients
- Automatic shopping list generation for low stock
- Multi-restaurant tenant separation
- Sustainability impact message when cooking with expiring ingredients

## Tech Stack
- Frontend: JSP, HTML, CSS
- Backend: Java Servlets
- Packaging: Maven WAR
- Server: Apache Tomcat 10+
- Data layer: in-memory repository (prototype)

## Design and Architecture
- Singleton Pattern: centralized `InventoryManager`
- Observer Pattern: alert subscriber event bus
- Chain of Responsibility: expiry check -> urgency -> chef -> manager
- State Pattern: Fresh / NearExpiry / Expired / Discarded
- Strategy Pattern: pluggable notification channel

## Quick Start
```bash
mvn clean package
mvn tomcat7:run
```
Open:
`http://localhost:8080/smart-kitchen-inventory`
Note: Refresh the page with a control shift r 


## Documentation
- User documentation: `USER_GUIDE.md`
- Developer documentation: `DEVELOPER_GUIDE.md`
