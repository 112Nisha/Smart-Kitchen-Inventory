package app.config;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    private static final String DB_FOLDER = "data";
    private static final String DB_FILE = "data.db";
    private static final String URL = "jdbc:sqlite:" + DB_FOLDER + "/" + DB_FILE;

    public static void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite driver NOT found in classpath", e);
        }
        createDataFolder();
        createTables();
        seedDataIfEmpty();
        normalizeUnitsToKg();
    }

    private static void createDataFolder() {
        File folder = new File(DB_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private static void createTables() {

        String createUsersTable = """
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL,
                        password TEXT NOT NULL,
                        role TEXT NOT NULL CHECK(role IN ('manager', 'chef')),
                        restaurant_id INTEGER NOT NULL,
                        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
                        UNIQUE(username, restaurant_id)
                    );
                """;

        String createRecipesTable = """
                CREATE TABLE IF NOT EXISTS dish_recipes (
                    restaurant_id INTEGER NOT NULL,
                    id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    instructions TEXT NOT NULL,
                    PRIMARY KEY (restaurant_id, id),
                    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
                    UNIQUE(restaurant_id, name)
                );
                """;

        String createIngredientsTable = """
                CREATE TABLE IF NOT EXISTS recipe_ingredients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    restaurant_id INTEGER NOT NULL,
                    recipe_id INTEGER NOT NULL,
                    ingredient_name TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unit TEXT NOT NULL,
                    FOREIGN KEY (restaurant_id, recipe_id) REFERENCES dish_recipes(restaurant_id, id) ON DELETE CASCADE,
                    UNIQUE(restaurant_id, recipe_id, ingredient_name)
                );
                """;

        String createRestaurantsTable = """
                CREATE TABLE IF NOT EXISTS restaurants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
                );
                """;

        String createInventoryIngredientsTable = """
                CREATE TABLE IF NOT EXISTS inventory_ingredients (
                    id TEXT PRIMARY KEY,
                    tenant_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unit TEXT NOT NULL,
                    expiry_date TEXT NOT NULL,
                    low_stock_threshold REAL NOT NULL,
                    discarded INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (tenant_id) REFERENCES restaurants(name) ON DELETE CASCADE
                );
                """;

        // Notifications table (Fix 7): durable store for chef/manager alerts
        // so they survive restarts. `dedup_key` carries the same
        // tenant|ingredient|role|date string the in-memory store uses, and is
        // marked UNIQUE so INSERT OR IGNORE collapses repeats at the DB level.
        // ingredient_id is nullable to support legacy 4-arg NotificationMessage
        // constructions (the dedup logic falls back to subject when null).
        String createNotificationsTable = """
            CREATE TABLE IF NOT EXISTS notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tenant_id TEXT NOT NULL,
                ingredient_id TEXT,
                recipient_role TEXT NOT NULL,
                subject TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at TEXT NOT NULL,
                dedup_key TEXT NOT NULL UNIQUE
            );
            """;

        // Bundle 3: key/value store for operator-tunable alert thresholds
        // (near-expiry window, manager escalation window, notification
        // retention). A simple flat table keeps the read path a single
        // SELECT — the cache lives in AlertConfigService, not here.
        String createAppConfigTable = """
            CREATE TABLE IF NOT EXISTS app_config (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            """;

        String createShoppingListItemsTable = """
                CREATE TABLE IF NOT EXISTS shopping_list_items (
                    id TEXT PRIMARY KEY,
                    tenant_id TEXT NOT NULL,
                    ingredient_id TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    updated_at TEXT NOT NULL,
                    UNIQUE(tenant_id, ingredient_id),
                    FOREIGN KEY (tenant_id) REFERENCES restaurants(name) ON DELETE CASCADE
                );
                """;

        try (Connection conn = DriverManager.getConnection(URL);
                Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute(createRestaurantsTable);
            stmt.execute(createUsersTable);
            stmt.execute(createRecipesTable);
            stmt.execute(createIngredientsTable);
            stmt.execute(createInventoryIngredientsTable);
            stmt.execute(createNotificationsTable);
            stmt.execute(createAppConfigTable);
            stmt.execute(createShoppingListItemsTable);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database tables", e);
        }
    }

    private static void seedDataIfEmpty() {
        try (Connection conn = DriverManager.getConnection(URL);
                Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");
            insertSampleData(conn);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed database", e);
        }
    }

    private static void normalizeUnitsToKg() {
        try (Connection conn = DriverManager.getConnection(URL);
                Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.executeUpdate("""
                    UPDATE recipe_ingredients
                    SET unit = 'kg'
                    WHERE LOWER(TRIM(unit)) IN ('unit', 'units', 'kgs');
                    """);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to normalize recipe ingredient units to kg", e);
        }
    }

    private static void insertSampleData(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("PRAGMA foreign_keys = ON;");
        conn.setAutoCommit(false);

        try {
            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO restaurants (id, name) VALUES
                        (1, 'restaurant-a'),
                        (2, 'restaurant-b'),
                        (3, 'restaurant-c');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, restaurant_id, name, instructions) VALUES
                        (1, 1, 'Tomato Basil Pasta',
                        '1. Boil water and cook the pasta until al dente.
                    2. Heat olive oil in a pan and sauté garlic for 1 minute.
                    3. Add chopped tomatoes and cook until soft.
                    4. Stir in basil and cook briefly.
                    5. Mix the sauce with pasta and serve warm.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, restaurant_id, name, instructions) VALUES
                        (2, 1, 'Veggie Stir Fry',
                        '1. Wash and chop all vegetables.
                    2. Heat oil in a wok or pan.
                    3. Stir fry onion, carrot, and bell pepper for 4–5 minutes.
                    4. Add soy sauce and mix well.
                    5. Serve hot.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, restaurant_id, name, instructions) VALUES
                        (3, 1, 'Herb Omelette',
                        '1. Crack eggs into a bowl and whisk well.
                    2. Add chopped spinach, onion, and parsley.
                    3. Heat butter or oil in a pan.
                    4. Pour in the egg mixture and cook until set.
                    5. Fold and serve.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, restaurant_id, name, instructions) VALUES
                        (1, 2, 'Potato Soup',
                        '1. Peel and chop potatoes and onion.
                    2. Sauté onion and garlic in a pot.
                    3. Add potatoes and enough water or stock to cover.
                    4. Simmer until potatoes are soft.
                    5. Blend lightly, stir in cream, and serve.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, restaurant_id, name, instructions) VALUES
                        (2, 2, 'Citrus Salad',
                        '1. Wash and chop lettuce.
                    2. Peel and segment the orange.
                    3. Mix olive oil and lemon juice to make dressing.
                    4. Toss lettuce and orange together.
                    5. Drizzle dressing on top and serve fresh.');
                    """);
            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, restaurant_id, name, instructions) VALUES
                        (1, 3, 'Garlic Butter Pasta',
                        '1. Boil water and cook pasta until al dente.
                        2. In a pan, melt butter and sauté minced garlic.
                        3. Add cooked pasta to the pan.
                        4. Toss well and season with salt and pepper.
                        5. Garnish with parsley and serve hot.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, restaurant_id, name, instructions) VALUES
                        (2, 3, 'Grilled Chicken Sandwich',
                        '1. Marinate chicken with spices and oil.
                        2. Grill the chicken until fully cooked.
                        3. Toast the sandwich buns lightly.
                        4. Place lettuce, tomato, and grilled chicken in the bun.
                        5. Add sauce of choice and serve.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, restaurant_id, name, instructions) VALUES
                        (3, 3, 'Vegetable Stir Fry',
                        '1. Chop all vegetables into bite-sized pieces.
                        2. Heat oil in a wok or pan.
                        3. Add garlic and sauté briefly.
                        4. Add vegetables and stir fry on high heat.
                        5. Add soy sauce, mix well, and serve hot.');
                    """);

            stmt.executeUpdate(
                    """
                                    INSERT OR IGNORE INTO recipe_ingredients (recipe_id, restaurant_id, ingredient_name, quantity, unit) VALUES
                                    (1,1, 'Tomato', 0.3, 'kg'),
                                    (1,1, 'Basil', 0.05, 'kg'),
                                    (1,1, 'Garlic', 0.02, 'kg'),
                                    (1,1, 'Pasta', 0.2, 'kg'),

                                    (2,1, 'Bell Pepper', 0.2, 'kg'),
                                    (2,1, 'Onion', 0.15, 'kg'),
                                    (2,1, 'Carrot', 0.15, 'kg'),
                                    (2,1, 'Soy Sauce', 0.05, 'liters'),

                                    (3,1, 'Egg', 3, 'units'),
                                    (3,1, 'Spinach', 0.1, 'kg'),
                                    (3,1, 'Onion', 0.1, 'kg'),
                                    (3,1, 'Parsley', 0.02, 'kg'),

                                    (1,2, 'Potato', 0.5, 'kg'),
                                    (1,2, 'Onion', 0.2, 'kg'),
                                    (1,2, 'Cream', 0.2, 'liters'),
                                    (1,2, 'Garlic', 0.02, 'kg'),

                                    (2,2, 'Lettuce', 0.2, 'kg'),
                                    (2,2, 'Orange', 2, 'units'),
                                    (2,2, 'Olive Oil', 0.05, 'liters'),
                                    (2,2, 'Lemon', 1, 'units'),

                                    (1,3, 'Pasta', 0.2, 'kg'),
                                    (1,3, 'Butter', 0.05, 'kg'),
                                    (1,3, 'Garlic', 0.02, 'kg'),
                                    (1,3, 'Parsley', 0.01, 'kg'),
                                    (1,3, 'Salt', 0.01, 'kg'),
                                    (1,3, 'Pepper', 0.005, 'kg'),

                                    (2,3, 'Chicken Breast', 0.25, 'kg'),
                                    (2,3, 'Burger Bun', 2, 'pieces'),
                                    (2,3, 'Lettuce', 0.05, 'kg'),
                                    (2,3, 'Tomato', 0.08, 'kg'),
                                    (2,3, 'Mayonnaise', 0.03, 'kg'),
                                    (2,3, 'Oil', 0.02, 'liters'),

                                    (3,3, 'Carrot', 0.1, 'kg'),
                                    (3,3, 'Capsicum', 0.1, 'kg'),
                                    (3,3, 'Broccoli', 0.1, 'kg'),
                                    (3,3, 'Garlic', 0.02, 'kg'),
                                    (3,3, 'Soy Sauce', 0.03, 'liters'),
                                    (3,3, 'Oil', 0.02, 'liters');
                            """);

            conn.commit();

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            stmt.close();
        }
    }

    public static String getUrl() {
        return URL;
    }
}