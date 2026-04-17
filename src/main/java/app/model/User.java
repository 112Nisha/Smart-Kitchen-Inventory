package app.model;

public class User {
    private final Long id;
    private final String username;
    private final String password;
    private final Long restaurantId;
    private final String role;

    public User(Long id, String username, String password, Long restaurantId, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.restaurantId = restaurantId;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public String getRole() {
        return role;
    }
}