package app.repository;

import app.config.DatabaseInitializer;
import app.model.User;

import java.sql.*;
import java.util.Optional;

public class UserRepository {

    /** registers a new restaurant */
    public void register(String restaurantName, String username, String password) {
        String insertRestaurantSql = "INSERT INTO restaurants (name) VALUES (?)";
        String findRestaurantSql = "SELECT id FROM restaurants WHERE name = ?";
        String insertUserSql = "INSERT INTO users (username, password, restaurant_id) VALUES (?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl())) {
            conn.setAutoCommit(false);

            try {
                Long restaurantId;

                try (PreparedStatement stmt = conn.prepareStatement(insertRestaurantSql)) {
                    stmt.setString(1, restaurantName);
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = conn.prepareStatement(findRestaurantSql)) {
                    stmt.setString(1, restaurantName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Failed to create restaurant.");
                        }
                        restaurantId = rs.getLong("id");
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(insertUserSql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, password);
                    stmt.setLong(3, restaurantId);
                    stmt.executeUpdate();
                }

                conn.commit();

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to register user", e);
        }
    }

    /** logs in a user */
    public Optional<LoginResult> login(String username, String password) {
        String sql = """
                SELECT u.id AS user_id, u.username, u.password, u.restaurant_id, r.name AS restaurant_name
                FROM users u
                JOIN restaurants r ON u.restaurant_id = r.id
                WHERE u.username = ? AND u.password = ?
                """;

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                            rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getLong("restaurant_id")
                    );

                    String restaurantName = rs.getString("restaurant_name");
                    return Optional.of(new LoginResult(user, restaurantName));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to login", e);
        }

        return Optional.empty();
    }

    /** checks if a username already exists */
    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check username", e);
        }
    }

    /** checks if a restaurant already exists */
    public boolean restaurantExists(String restaurantName) {
        String sql = "SELECT 1 FROM restaurants WHERE name = ?";

        try (Connection conn = DriverManager.getConnection(DatabaseInitializer.getUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, restaurantName);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check restaurant", e);
        }
    }

    public record LoginResult(User user, String restaurantName) {}
}