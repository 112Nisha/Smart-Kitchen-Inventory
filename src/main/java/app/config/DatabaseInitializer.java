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
        String countSql = "SELECT COUNT(*) FROM dish_recipes";

        try (Connection conn = DriverManager.getConnection(URL);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(countSql)) {

            if (rs.next() && rs.getInt(1) == 0) {
                insertSampleData(conn);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed database", e);
        }
    }

    private static void insertSampleData(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("PRAGMA foreign_keys = ON;");
        conn.setAutoCommit(false);

        try {
            stmt.executeUpdate("""
                        INSERT INTO dish_recipes (id, name, instructions) VALUES
                        (1, 'Tomato Basil Pasta',
                        '1. Boil water and cook the pasta until al dente.
                    2. Heat olive oil in a pan and sauté garlic for 1 minute.
                    3. Add chopped tomatoes and cook until soft.
                    4. Stir in basil and cook briefly.
                    5. Mix the sauce with pasta and serve warm.');
                    """);

            stmt.executeUpdate("""
                        INSERT INTO dish_recipes (id, name, instructions) VALUES
                        (2, 'Veggie Stir Fry',
                        '1. Wash and chop all vegetables.
                    2. Heat oil in a wok or pan.
                    3. Stir fry onion, carrot, and bell pepper for 4–5 minutes.
                    4. Add soy sauce and mix well.
                    5. Serve hot.');
                    """);

            stmt.executeUpdate("""
                        INSERT INTO dish_recipes (id, name, instructions) VALUES
                        (3, 'Herb Omelette',
                        '1. Crack eggs into a bowl and whisk well.
                    2. Add chopped spinach, onion, and parsley.
                    3. Heat butter or oil in a pan.
                    4. Pour in the egg mixture and cook until set.
                    5. Fold and serve.');
                    """);

            stmt.executeUpdate("""
                        INSERT INTO dish_recipes (id, name, instructions) VALUES
                        (4, 'Potato Soup',
                        '1. Peel and chop potatoes and onion.
                    2. Sauté onion and garlic in a pot.
                    3. Add potatoes and enough water or stock to cover.
                    4. Simmer until potatoes are soft.
                    5. Blend lightly, stir in cream, and serve.');
                    """);

            stmt.executeUpdate("""
                        INSERT INTO dish_recipes (id, name, instructions) VALUES
                        (5, 'Citrus Salad',
                        '1. Wash and chop lettuce.
                    2. Peel and segment the orange.
                    3. Mix olive oil and lemon juice to make dressing.
                    4. Toss lettuce and orange together.
                    5. Drizzle dressing on top and serve fresh.');
                    """);

            stmt.executeUpdate("""
                        INSERT INTO recipe_ingredients (recipe_id, ingredient_name, quantity, unit) VALUES
                        (1, 'Tomato', 0.3, 'kg'),
                        (1, 'Basil', 0.05, 'kg'),
                        (1, 'Garlic', 0.02, 'kg'),
                        (1, 'Pasta', 0.2, 'kg'),

                        (2, 'Bell Pepper', 0.2, 'kg'),
                        (2, 'Onion', 0.15, 'kg'),
                        (2, 'Carrot', 0.15, 'kg'),
                        (2, 'Soy Sauce', 0.05, 'liters'),

                        (3, 'Egg', 3, 'units'),
                        (3, 'Spinach', 0.1, 'kg'),
                        (3, 'Onion', 0.1, 'kg'),
                        (3, 'Parsley', 0.02, 'kg'),

                        (4, 'Potato', 0.5, 'kg'),
                        (4, 'Onion', 0.2, 'kg'),
                        (4, 'Cream', 0.2, 'liters'),
                        (4, 'Garlic', 0.02, 'kg'),

                        (5, 'Lettuce', 0.2, 'kg'),
                        (5, 'Orange', 2, 'units'),
                        (5, 'Olive Oil', 0.05, 'liters'),
                        (5, 'Lemon', 1, 'units');
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