package app.config;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    private static final String DB_FOLDER = "data";
    private static final String DB_FILE = "recipes.db";
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
        String createRecipesTable = """
                CREATE TABLE IF NOT EXISTS dish_recipes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    instructions TEXT NOT NULL
                );
                """;

        String createIngredientsTable = """
                CREATE TABLE IF NOT EXISTS recipe_ingredients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    recipe_id INTEGER NOT NULL,
                    ingredient_name TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unit TEXT NOT NULL,
                    FOREIGN KEY (recipe_id) REFERENCES dish_recipes(id) ON DELETE CASCADE,
                    UNIQUE(recipe_id, ingredient_name)
                );
                """;

        try (Connection conn = DriverManager.getConnection(URL);
                Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute(createRecipesTable);
            stmt.execute(createIngredientsTable);

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
                        INSERT OR IGNORE INTO dish_recipes (id, name, instructions) VALUES
                        (1, 'Tomato Basil Pasta',
                        '1. Boil water and cook the pasta until al dente.
                    2. Heat olive oil in a pan and sauté garlic for 1 minute.
                    3. Add chopped tomatoes and cook until soft.
                    4. Stir in basil and cook briefly.
                    5. Mix the sauce with pasta and serve warm.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, name, instructions) VALUES
                        (2, 'Veggie Stir Fry',
                        '1. Wash and chop all vegetables.
                    2. Heat oil in a wok or pan.
                    3. Stir fry onion, carrot, and bell pepper for 4–5 minutes.
                    4. Add soy sauce and mix well.
                    5. Serve hot.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, name, instructions) VALUES
                        (3, 'Herb Omelette',
                        '1. Crack eggs into a bowl and whisk well.
                    2. Add chopped spinach, onion, and parsley.
                    3. Heat butter or oil in a pan.
                    4. Pour in the egg mixture and cook until set.
                    5. Fold and serve.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, name, instructions) VALUES
                        (4, 'Potato Soup',
                        '1. Peel and chop potatoes and onion.
                    2. Sauté onion and garlic in a pot.
                    3. Add potatoes and enough water or stock to cover.
                    4. Simmer until potatoes are soft.
                    5. Blend lightly, stir in cream, and serve.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, name, instructions) VALUES
                        (5, 'Citrus Salad',
                        '1. Wash and chop lettuce.
                    2. Peel and segment the orange.
                    3. Mix olive oil and lemon juice to make dressing.
                    4. Toss lettuce and orange together.
                    5. Drizzle dressing on top and serve fresh.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO dish_recipes (id, name, instructions) VALUES
                        (6, 'Garlic Basil Spaghetti',
                        '1. Boil spaghetti until al dente.
                    2. Warm olive oil and sauté garlic.
                    3. Toss spaghetti with basil and season to taste.
                    4. Serve immediately.'),
                        (7, 'Creamy Tomato Pasta',
                        '1. Cook pasta and set aside.
                    2. Simmer tomatoes with garlic.
                    3. Add cream and stir until smooth.
                    4. Toss in pasta and finish with basil.'),
                        (8, 'Onion Garlic Saute',
                        '1. Slice onions and mince garlic.
                    2. Heat olive oil in a skillet.
                    3. Saute onion until golden, then add garlic.
                    4. Serve as a side or topping.'),
                        (9, 'Spinach Garlic Egg Scramble',
                        '1. Whisk eggs in a bowl.
                    2. Saute spinach and garlic quickly.
                    3. Pour eggs and scramble gently.
                    4. Serve warm.'),
                        (10, 'Tomato Onion Base Sauce',
                        '1. Saute onion and garlic in olive oil.
                    2. Add chopped tomatoes.
                    3. Simmer until thick and glossy.
                    4. Use as sauce base for other dishes.'),
                        (11, 'Creamy Spinach Pasta',
                        '1. Cook pasta.
                    2. Saute spinach and garlic.
                    3. Add cream and simmer.
                    4. Mix with pasta and serve.'),
                        (12, 'Simple Pasta Aglio e Olio',
                        '1. Cook pasta.
                    2. Warm olive oil with garlic.
                    3. Toss with cooked pasta.
                    4. Adjust seasoning and serve.');
                    """);

            stmt.executeUpdate("""
                        INSERT OR IGNORE INTO recipe_ingredients (recipe_id, ingredient_name, quantity, unit) VALUES
                        (1, 'Tomato', 0.3, 'kg'),
                        (1, 'Basil', 0.05, 'kg'),
                        (1, 'Garlic', 0.02, 'kg'),
                        (1, 'Pasta', 0.2, 'kg'),

                        (2, 'Bell Pepper', 0.2, 'kg'),
                        (2, 'Onion', 0.15, 'kg'),
                        (2, 'Carrot', 0.15, 'kg'),
                        (2, 'Soy Sauce', 0.05, 'liters'),

                        (3, 'Egg', 3, 'kg'),
                        (3, 'Spinach', 0.1, 'kg'),
                        (3, 'Onion', 0.1, 'kg'),
                        (3, 'Parsley', 0.02, 'kg'),

                        (4, 'Potato', 0.5, 'kg'),
                        (4, 'Onion', 0.2, 'kg'),
                        (4, 'Cream', 0.2, 'liters'),
                        (4, 'Garlic', 0.02, 'kg'),

                        (5, 'Lettuce', 0.2, 'kg'),
                        (5, 'Orange', 2, 'kg'),
                        (5, 'Olive Oil', 0.05, 'liters'),
                        (5, 'Lemon', 1, 'kg'),

                        (6, 'Pasta', 0.25, 'kg'),
                        (6, 'Garlic', 0.03, 'kg'),
                        (6, 'Basil', 0.04, 'kg'),
                        (6, 'Olive Oil', 0.03, 'liters'),

                        (7, 'Pasta', 0.25, 'kg'),
                        (7, 'Tomato', 0.25, 'kg'),
                        (7, 'Cream', 0.12, 'liters'),
                        (7, 'Garlic', 0.02, 'kg'),
                        (7, 'Basil', 0.02, 'kg'),

                        (8, 'Onion', 0.3, 'kg'),
                        (8, 'Garlic', 0.03, 'kg'),
                        (8, 'Olive Oil', 0.02, 'liters'),

                        (9, 'Egg', 4, 'kg'),
                        (9, 'Spinach', 0.15, 'kg'),
                        (9, 'Garlic', 0.01, 'kg'),

                        (10, 'Tomato', 0.4, 'kg'),
                        (10, 'Onion', 0.15, 'kg'),
                        (10, 'Garlic', 0.02, 'kg'),
                        (10, 'Olive Oil', 0.02, 'liters'),

                        (11, 'Pasta', 0.2, 'kg'),
                        (11, 'Spinach', 0.12, 'kg'),
                        (11, 'Cream', 0.1, 'liters'),
                        (11, 'Garlic', 0.02, 'kg'),

                        (12, 'Pasta', 0.2, 'kg'),
                        (12, 'Garlic', 0.025, 'kg'),
                        (12, 'Olive Oil', 0.03, 'liters');
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