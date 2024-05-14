package cookbook.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class UserTable {

    private SimpleStringProperty name;
    private SimpleStringProperty username;
    private SimpleIntegerProperty isAdmin;


    public UserTable(String name, String username, int isAdmin) {
        this.name = new SimpleStringProperty(name);
        this.username = new SimpleStringProperty(username);
        this.isAdmin = new SimpleIntegerProperty(isAdmin);
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name = new SimpleStringProperty(name);
    }

    public String getUsername() {
        return username.get();
    }

    public void setUsername(String username) {
        this.username = new SimpleStringProperty(username);
    }

    public int getIsAdmin() {
        return isAdmin.get();
    }

    public void setIsAdmin(int isAdmin) {
        this.isAdmin = new SimpleIntegerProperty(isAdmin);
    }
}