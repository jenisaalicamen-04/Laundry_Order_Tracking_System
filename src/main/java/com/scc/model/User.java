package com.scc.model;

/**
 * A user account (record = a short way to write an unchangeable data class).
 * role:   CUSTOMER, STAFF, or ADMIN
 * status: PENDING (new account, waiting for approval), ACTIVE, or INACTIVE
 */
public record User(int userId, String fullName, String contactNumber, String address, String username,
                   String passwordHash, String role, String accountStatus, String createdAt) {

    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String ROLE_STAFF = "STAFF";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    public boolean isCustomer() { return role.equals(ROLE_CUSTOMER); }
    public boolean isStaff() { return role.equals(ROLE_STAFF); }
    public boolean isAdmin() { return role.equals(ROLE_ADMIN); }
    public boolean isActive() { return accountStatus.equals(STATUS_ACTIVE); }
    public boolean isPending() { return accountStatus.equals(STATUS_PENDING); }

    // A friendly role name for screens.
    public String roleLabel() {
        switch (role) {
            case ROLE_ADMIN:
                return "Admin";
            case ROLE_STAFF:
                return "Laundry Staff";
            default:
                return "Customer";
        }
    }

    // Records cannot be changed, so these methods return a NEW User with one changed value.
    public User withContactNumber(String value) {
        return new User(userId, fullName, value, address, username, passwordHash, role, accountStatus, createdAt);
    }

    public User withAddress(String value) {
        return new User(userId, fullName, contactNumber, value, username, passwordHash, role, accountStatus, createdAt);
    }

    public User withPasswordHash(String value) {
        return new User(userId, fullName, contactNumber, address, username, value, role, accountStatus, createdAt);
    }

    // The password hash is never printed.
    @Override
    public String toString() {
        return "User[" + userId + ", " + username + ", " + role + ", " + accountStatus + "]";
    }
}