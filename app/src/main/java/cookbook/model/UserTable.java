package cookbook.model;

import javafx.beans.property.SimpleStringProperty;

public class UserTable {

    private SimpleStringProperty name;
    private SimpleStringProperty username;
    private SimpleStringProperty isAdmin;
    private SimpleStringProperty edit;



    public UserTable(String name, String username, int isAdmin) {
        this.name = new SimpleStringProperty(name);
        this.username = new SimpleStringProperty(username);
        this.isAdmin = new SimpleStringProperty(isAdmin == 1 ? "Yes" : "No");
        System.out.println(isAdmin);
        this.edit = new SimpleStringProperty("Edit");
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

    public String getIsAdmin() {
        return isAdmin.get();
    }

    public void setIsAdmin(String isAdmin) {
        this.isAdmin = new SimpleStringProperty(isAdmin);
    }

    public String getEdit() {
        return edit.get();
    }

    public void setEdit(String edit) {
        this.edit = new SimpleStringProperty(edit);
    }
}