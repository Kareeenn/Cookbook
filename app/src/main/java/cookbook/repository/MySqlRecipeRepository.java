package cookbook.repository;

import cookbook.DatabaseManager;
import cookbook.model.*;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.scene.text.Text;

import java.sql.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping;
import com.mysql.cj.xdevapi.SqlResultBuilder;

public class MySqlRecipeRepository implements RecipeRepository {

    private DatabaseManager dbManager;
    private UserDao userDao;
    private User currentUser; // Current user object
    private List<Recipe> allrecipes = new ArrayList<>();


    public MySqlRecipeRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.currentUser = currentUser; // Initialize with current user
        this.userDao = new UserDao(dbManager);

        // loadPredeterminedTags(); // Pre-load tags to optimize later checks
    }

    public MySqlRecipeRepository(DatabaseManager dbManager, User currentUser) {
        this.dbManager = dbManager;
        this.currentUser = currentUser; // Initialize with current user
        this.userDao = new UserDao(dbManager);

    }

    public MySqlRecipeRepository(DatabaseManager dbManager, User user, List<Recipe> recipeList) {
        this.dbManager = dbManager;
        this.currentUser = currentUser; // Initialize with current user
        this.userDao = new UserDao(dbManager);
        this.allrecipes = recipeList;

    }

//    @Override
//    public void addRecipeRepo(Long userID, String name, String shortDescription, String description, String imageUrl,
//            int servings, Long author, String ingredients, String tags) {
//        try (Connection connection = DriverManager.getConnection(dbManager.url)) {
//            // Prepare the SQL statement
//            String sql = "CALL AddNewRecipe(?, ?, ?, ?, ?, ?, ?, ?, ?)";
//            try (CallableStatement statement = connection.prepareCall(sql)) {
//                // Set the parameters for the stored procedure
//                statement.setLong(1, userID); // Pass the userID to the stored procedure
//                statement.setString(2, name);
//                statement.setString(3, shortDescription);
//                statement.setString(4, description);
//                statement.setString(5, imageUrl);
//                statement.setInt(6, servings);
//                statement.setInt(7, author.intValue());
//                statement.setString(8, ingredients); // Directly pass the ingredients string
//                statement.setString(9, tags); // Directly pass the tags string
//
//                System.out.println("statement: " + statement);
//                // Execute the stored procedure
//                statement.execute();
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//    }

    private String convertStepsToJson(String stepsText) {
        // Split steps text into an array of steps
        String[] steps = stepsText.split(";");
        // Convert array to JSON format
        String jsonSteps = Arrays.stream(steps)
                .map(step -> "\"" + step.trim().replaceAll("\"", "\\\"") + "\"") // Ensure to escape any double quotes in the step text itself.
                .collect(Collectors.joining(", ", "[", "]"));
        return "{\"steps\": " + jsonSteps + "}";
    }


    @Override
    public void addRecipeRepo(Long userID, String name, String shortDescription, String description, String imageUrl,
                              int servings, Long author, String ingredients, String tags) {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(dbManager.url);
            connection.setAutoCommit(false); // Start transaction

            // Convert description to JSON
            String jsonDescription = convertStepsToJson(description);

            // Insert into Recipe table
            String sqlRecipe = "INSERT INTO Recipe (name, sdecr, descr, imgUrl, servings, author) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmtRecipe = connection.prepareStatement(sqlRecipe, Statement.RETURN_GENERATED_KEYS)) {
                stmtRecipe.setString(1, name);
                stmtRecipe.setString(2, shortDescription);
                stmtRecipe.setString(3, jsonDescription); // Set JSON description
                stmtRecipe.setString(4, imageUrl);
                stmtRecipe.setInt(5, servings);
                stmtRecipe.setLong(6, author);
                stmtRecipe.executeUpdate();

                try (ResultSet generatedKeys = stmtRecipe.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long recipeID = generatedKeys.getLong(1);
                        // Insert ingredients
                        insertIngredients(connection, ingredients, recipeID);
                        // Insert tags
                        insertTags(connection, tags, recipeID, userID);
                    } else {
                        throw new SQLException("Creating recipe failed, no ID obtained.");
                    }
                }
            }
            connection.commit(); // Commit transaction
        } catch (SQLException ex) {
            try {
                if (connection != null) connection.rollback(); // Rollback transaction on error
            } catch (SQLException e) {
                System.out.println("Rollback failed: " + e.getMessage());
            }
            ex.printStackTrace();
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    private void insertIngredients(Connection connection, String ingredients, long recipeID) throws SQLException {
        String sqlRecipeIngredient = "INSERT INTO RecipeIngredient (Recipe_ID, Ingredient_ID, amount_int, amount_unit) VALUES (?, ?, ?, ?)";

        for (String ingredientDetails : ingredients.split(";")) {
            String[] details = ingredientDetails.split(",");
            String name = details[0].trim();
            int amount = Integer.parseInt(details[1].trim());
            String unit = details[2].trim();

            // Insert or find ingredient ID
            long ingredientID = getOrInsertIngredient(connection, name);

            try (PreparedStatement stmtRecipeIngredient = connection.prepareStatement(sqlRecipeIngredient)) {
                stmtRecipeIngredient.setLong(1, recipeID);
                stmtRecipeIngredient.setLong(2, ingredientID);
                stmtRecipeIngredient.setInt(3, amount);
                stmtRecipeIngredient.setString(4, unit);
                stmtRecipeIngredient.executeUpdate();
            }
        }
    }


    private long getOrInsertIngredient(Connection connection, String name) throws SQLException {
        String sqlCheck = "SELECT ID FROM Ingredient WHERE name = ?";
        PreparedStatement stmtCheck = connection.prepareStatement(sqlCheck);
        stmtCheck.setString(1, name);
        ResultSet rs = stmtCheck.executeQuery();
        if (rs.next()) {
            return rs.getLong("ID");
        } else {
            String sqlInsert = "INSERT INTO Ingredient (name) VALUES (?)";
            PreparedStatement stmtInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
            stmtInsert.setString(1, name);
            stmtInsert.executeUpdate();
            rs = stmtInsert.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            } else {
                throw new SQLException("Inserting ingredient failed, no ID obtained.");
            }
        }
    }

    private void insertTags(Connection connection, String tags, long recipeID, Long userID) throws SQLException {
        String sqlTag = "INSERT INTO RecipeTag (Recipe_ID, Tags_ID) VALUES (?, ?)";
        PreparedStatement stmtTag = connection.prepareStatement(sqlTag);

        for (String tag : tags.split(";")) {
            long tagID = getOrInsertTag(connection, tag.trim());

            stmtTag.setLong(1, recipeID);
            stmtTag.setLong(2, tagID);
            stmtTag.executeUpdate();
        }
    }

    private long getOrInsertTag(Connection connection, String tagName) throws SQLException {
        String sqlCheck = "SELECT ID FROM Tags WHERE tagname = ?";
        PreparedStatement stmtCheck = connection.prepareStatement(sqlCheck);
        stmtCheck.setString(1, tagName);
        ResultSet rs = stmtCheck.executeQuery();
        if (rs.next()) {
            return rs.getLong("ID");
        } else {
            String sqlInsert = "INSERT INTO Tags (tagname, ispredefined) VALUES (?, 0)";
            PreparedStatement stmtInsert = connection.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
            stmtInsert.setString(1, tagName);
            stmtInsert.executeUpdate();
            rs = stmtInsert.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            } else {
                throw new SQLException("Inserting tag failed, no ID obtained.");
            }
        }
    }



    public void addCustomTagsRepo(String tags, Long userId, Recipe recipe) {
        if (tags == null || tags.isEmpty()) {
            System.out.println("No tags provided.");
            return;
        }

        // Split tags string divided by ';' and make List<String>
        List<String> tagList = Arrays.stream(tags.split(";"))
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());

        if (tagList.isEmpty()) {
            System.out.println("No valid tags to process.");
            return;
        }

        String insertTagSql = "INSERT INTO Tags (tagname, ispredefined) VALUES (?, 0) ON DUPLICATE KEY UPDATE ID=LAST_INSERT_ID(ID)";
        String fetchTagIdSql = "SELECT ID FROM Tags WHERE tagname = ?";
        String insertRecipeTagSql = "INSERT INTO RecipeCustomTag (Recipe_ID, User_ID, Tags_ID) VALUES (?, ?, ?)";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
                PreparedStatement insertTagStmt = connection.prepareStatement(insertTagSql,
                        Statement.RETURN_GENERATED_KEYS);
                PreparedStatement fetchTagIdStmt = connection.prepareStatement(fetchTagIdSql);
                PreparedStatement insertRecipeTagStmt = connection.prepareStatement(insertRecipeTagSql)) {

            for (String tag : tagList) {
                // Insert the tag or update it to get the last insert ID
                insertTagStmt.setString(1, tag);
                insertTagStmt.executeUpdate();
                ResultSet tagIds = insertTagStmt.getGeneratedKeys();

                long tagId;
                if (tagIds.next()) {
                    tagId = tagIds.getLong(1); // Get the generated tag ID
                } else {
                    // Fetch existing tag ID
                    fetchTagIdStmt.setString(1, tag);
                    ResultSet rs = fetchTagIdStmt.executeQuery();
                    rs.next();
                    tagId = rs.getLong("ID");
                }

                // Associate tag with user and recipe in RecipeCustomTag table
                Long recipeId = recipe.getId();

                insertRecipeTagStmt.setLong(1, recipeId);
                insertRecipeTagStmt.setLong(2, userId);
                insertRecipeTagStmt.setLong(3, tagId);
                insertRecipeTagStmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SQL Error: " + e.getMessage());
        }
    }

    public void fetchRecipeDetails(Recipe rec) {

        // Fetch comments for the recipe
        // List<String> comments = fetchComments(rec.getId());
        // rec.setComments(comments);
    }

    @Override
    public void getAllRecipes(List<Recipe> recipes, boolean useThread) {
        // Define an anonymous Callable to perform database query and return the list of
        // recipes
        Runnable dbOperationTask = new Runnable() {

            @Override
            public void run() {
                long startTime = System.currentTimeMillis(); // Start timing

                //String sql = "SELECT Recipe_ID, Recipe_Name, Short_Description, Description, Ingredients_JSON, Predefined_Tags_JSON, Servings FROM FullRecipeView";
                String sql = "SELECT Recipe_ID, Recipe_Name, Short_Description, Description, Ingredients_JSON, Predefined_Tags_JSON, Servings, Image_URL, Comments_JSON, Average_Rating FROM FullRecipeView";

                try (Connection connection = DriverManager.getConnection(dbManager.url);
                     PreparedStatement pstmt = connection.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {
                    List<Recipe> fetchedRecipes = new ArrayList<>();
                    while (rs.next()) {
                        Recipe recipe = new Recipe();
                        recipe.setId(rs.getLong("Recipe_ID"));
                        recipe.setName(rs.getString("Recipe_Name"));
                        recipe.setShortDescription(rs.getString("Short_Description"));
                        recipe.setProcessSteps(parseProcessSteps(rs.getString("Description"))); // Assuming process steps are in Description
                        recipe.setIngredients(parseIngredients(rs.getString("Ingredients_JSON")));
                        recipe.setTags(FXCollections.observableArrayList(parseTags(rs.getString("Predefined_Tags_JSON"))));
                        recipe.setNumberOfPersons(rs.getInt("Servings"));
                        recipe.setImagePath(rs.getString("Image_URL"));
                        recipe.setComments(parseComments(rs.getString("Comments_JSON"))); // Parse comments JSON
                        recipe.setAverageRating(rs.getInt("Average_Rating"));

                        // Extract and parse process steps from JSON
                        String jsonProcessSteps = rs.getString("Description");
                        List<String> processSteps = parseProcessSteps(jsonProcessSteps);
                        recipe.setProcessSteps(processSteps);

                        // Fetch tags for the recipe
                        String jsonTags = rs.getString("Predefined_Tags_JSON");
                        List<String> tags = parseTags(jsonTags);
                        recipe.setTags(tags);

                        // Fetch ingredients for the recipe
                        String jsonIngredients = rs.getString("Ingredients_JSON");
                        List<Ingredient> ingredients = parseIngredients(jsonIngredients);
                        recipe.setIngredients(ingredients);

                        recipes.add(recipe);
                        System.out.println("recipes"+recipes);
                        allrecipes.add(recipe);
                    }
                    long endTime = System.currentTimeMillis(); // End timing
                    long duration = endTime - startTime;
                    System.out.println("Data loading completed in " + duration + " milliseconds.");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        };

        // Start a new thread to execute the database operation
        Thread dbThread = new Thread(dbOperationTask);
        dbThread.start();
        System.out.println("Thread is executing for data loading.");
        if(!useThread){
            try {
                dbThread.join();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void getFavorites(List<Recipe> recipes, User user) {
        Runnable dbOperationTask = new Runnable() {
            @Override
            public void run() {
                // add Information for favourite recipes
                List<Long> favourites = fetchFavourites(user);
                for (Recipe recipe : recipes) {
                    if (favourites.contains(recipe.getId())) {
                        recipe.setIsFavourite(true);
                    } else {
                        recipe.setIsFavourite(false);
                    }
                }
            }
        };

        // Start a new thread to execute the database operation
        Thread dbThread = new Thread(dbOperationTask);
        dbThread.start();
    }

    @Override
    public void updateRecipe(Long id, String name, String shortDescription, String description, String imageUrl,
            int servings, Long author, String ingredientsText, String tagsText) {
        try (Connection connection = DriverManager.getConnection(dbManager.url)) {
            // String sql = "SELECT Recipe_ID FROM UserRecipe " +
            // "WHERE User_ID = ? AND Recipe_ID IS NOT NULL AND isstar = true";
            String sql = "CALL UpdateRecipe(?, ?, ?, ?, ?, ?, ?, ?)";
            try (CallableStatement statement = connection.prepareCall(sql)) {
                statement.setLong(1, id);
                statement.setString(2, name);
                statement.setString(3, shortDescription);
                statement.setString(4, description);
                statement.setString(5, imageUrl);
                statement.setInt(6, servings);
                statement.setInt(7, author.intValue());
                statement.setString(8, ingredientsText);
                statement.setString(9, tagsText);

                statement.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteRecipe(Long id) {

    }

    @Override
    public List<Recipe> findByName(String name) {
        return List.of();
    }

    @Override
    public List<Recipe> findByIngredient(String ingredient) {
        return List.of();
    }

    @Override
    public List<Recipe> findByTag(String tag) {
        return List.of();
    }

    @Override
    public void saveToFavorites(Recipe recipe, User user) {
        String sql = "{CALL UpdateFavoriteRecipe(?, ?)}";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
                PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // set values
            pstmt.setLong(1, user.getId());
            pstmt.setLong(2, recipe.getId());

            pstmt.executeUpdate();

            pstmt.close();
            connection.close();
            recipe.setIsFavourite(true);

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("problem while adding favourite");

        }
    }

    @Override
    public void removeFromFavorites(Recipe recipe, User user) {
        String sql = "UPDATE UserRecipeStar " +
                "SET isstar = 0 " +
                "WHERE User_ID = ? AND Recipe_ID = ?";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
                PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // set values
            pstmt.setLong(1, user.getId());
            pstmt.setLong(2, recipe.getId());

            pstmt.executeUpdate();

            pstmt.close();
            connection.close();
            recipe.setIsFavourite(false);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addToWeekPlan(Recipe recipe, User user, Date date, int servings, Text text) {
        Runnable dbOperationTask = new Runnable() {
            @Override
            public void run() {
                String sql = "INSERT INTO UserRecipeWeeklyList (User_ID, Recipe_ID, date, w_servings) " +
                        "VALUES (?, ?, ?, ?)";

                try (Connection connection = DriverManager.getConnection(dbManager.url);
                        PreparedStatement pstmt = connection.prepareStatement(sql)) {

                    // set values
                    pstmt.setLong(1, user.getId());
                    pstmt.setLong(2, recipe.getId());
                    pstmt.setDate(3, date);
                    pstmt.setInt(4, servings);

                    pstmt.executeUpdate();

                    pstmt.close();
                    connection.close();
                    Map<Date, Integer> dates = recipe.getWeeklyDates();
                    if (dates == null) {
                        dates = new HashMap<Date, Integer>();
                        recipe.setWeeklyDates(dates);
                    }
                    dates.put(date, servings);

                } catch (SQLException e) {
                    e.printStackTrace();
                    text.setFill(javafx.scene.paint.Color.RED);
                    text.setText("you can not assign a Recipe for a day twice");
                    text.setVisible(true);

                }
            }
        };
        // Start a new thread to execute the database operation
        Thread dbThread = new Thread(dbOperationTask);
        dbThread.start();
    }

    @Override
    public void removeFromWeekPlan(Recipe recipe, User user, Date date) {
        Runnable dbOperationTask = new Runnable() {
            @Override
            public void run() {
                String sql = "DELETE FROM UserRecipeWeeklyList " +
                        "WHERE User_ID = ? AND Recipe_ID = ? AND date = ?";

                try (Connection connection = DriverManager.getConnection(dbManager.url);
                        PreparedStatement pstmt = connection.prepareStatement(sql)) {

                    // set values
                    pstmt.setLong(1, user.getId());
                    pstmt.setLong(2, recipe.getId());
                    pstmt.setDate(3, date);

                    pstmt.executeUpdate();

                    // remove the element from the list
                    recipe.getWeeklyDates().remove(date);

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        };

        // Start a new thread to execute the database operation
        Thread dbThread = new Thread(dbOperationTask);
        dbThread.start();
    }

    @Override
    public void getRecipeWeeklyDates(List<Recipe> recipes, User user) {
        Runnable dbOperationTask = new Runnable() {
            @Override
            public void run() {
                // add Information for favourite recipes
                Map<Long, Map<Date, Integer>> map = fetchWeeklyRecipes(user);
                for (Recipe recipe : recipes) {
                    if (map.containsKey(recipe.getId())) {
                        recipe.setWeeklyDates(map.get(recipe.getId()));
                    } else {
                        recipe.setWeeklyDates(null);
                    }
                }
                System.out.println(recipes);
            }
        };

        // Start a new thread to execute the database operation
        Thread dbThread = new Thread(dbOperationTask);
        dbThread.start();
    }

    public void sharingRecipe(User sender, Recipe recipe, User receiverUser, String message) {
        String sql = "INSERT INTO UserMessage (message, recipe_ID, sender_ID, receiver_ID) VALUES (?, ?, ?, ?)";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Set the parameters for the PreparedStatement
            pstmt.setString(1, message);
            pstmt.setInt(2, recipe.getId().intValue()); // Recipe ID that is being shared, ensure the type matches (int vs long)
            pstmt.setInt(3, sender.getId().intValue());
            pstmt.setInt(4, receiverUser.getId().intValue());

            // Execute the update and retrieve the generated primary key
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating user message failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long messageId = generatedKeys.getLong(1);
                    System.out.println("Shared message was created with ID: " + messageId);
                } else {
                    throw new SQLException("Creating user message failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SQL Error during sharing a recipe: " + e.getMessage());
        }
    }

    public List<Message> showAllMessage(User user) {
        // SQL query with JOINs to fetch sender names and recipe titles along with message details
        String sql = "SELECT um.ID, um.message, um.sender_ID, um.receiver_ID, um.recipe_ID, " +
                "u.name AS senderName, r.name AS recipeTitle " +
                "FROM UserMessage um " +
                "JOIN User u ON um.sender_ID = u.ID " +
                "JOIN Recipe r ON um.recipe_ID = r.ID " +
                "WHERE um.receiver_ID = ?";

        List<Message> allMessages = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, user.getId()); // Set the receiver ID to the current user's ID

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Message message = new Message();
                    message.setId(rs.getLong("ID"));
                    message.setMessage(rs.getString("message"));
                    message.setSenderId(rs.getLong("sender_ID"));
                    message.setReceiverId(rs.getLong("receiver_ID"));
                    message.setRecipeId(rs.getLong("recipe_ID"));
                    message.setSenderName(rs.getString("senderName"));
                    message.setRecipeTitle(rs.getString("recipeTitle"));
                    allMessages.add(message);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SQL Error: " + e.getMessage());
        }
        return allMessages;
    }


    public void setUserRating(User user, Recipe recipe, int rating){
        Runnable dbOperationTask = new Runnable() {
            @Override
            public void run() {
                String sql = "INSERT INTO Ratings (User_ID, Recipe_ID, rating) " +
                             "VALUES (?, ?, ?)";

                try (Connection connection = DriverManager.getConnection(dbManager.url);
                        PreparedStatement pstmt = connection.prepareStatement(sql)) {

                    // set values
                    pstmt.setLong(1, user.getId());
                    pstmt.setLong(2, recipe.getId());
                    pstmt.setInt(3, rating);

                    pstmt.executeUpdate();

                    pstmt.close();
                    connection.close();

                } catch (SQLException e) {
                    e.printStackTrace();

                }
            }
        };
        // Start a new thread to execute the database operation
        Thread dbThread = new Thread(dbOperationTask);
        dbThread.start();
        try{
            dbThread.join();
        }catch(InterruptedException e){
            e.printStackTrace();
        }
        dbOperationTask = new Runnable() {
        @Override
        public void run() {

            //String sql = "SELECT Recipe_ID, Recipe_Name, Short_Description, Description, Ingredients_JSON, Predefined_Tags_JSON, Servings FROM FullRecipeView";
            String sql = " SELECT Average_Rating FROM FullRecipeView "+
                         " WHERE Recipe_ID = ?";

            try (Connection connection = DriverManager.getConnection(dbManager.url);
                    PreparedStatement pstmt = connection.prepareStatement(sql)){
                
                // set values
                pstmt.setLong(1, recipe.getId());


                // Execute the query
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        recipe.setAverageRating(rs.getInt("Average_Rating"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            }
        };
        // Start a new thread to execute the database operation
        dbThread = new Thread(dbOperationTask);
        dbThread.start();
        try{
            dbThread.join();
        }catch(InterruptedException e){
            e.printStackTrace();
        }

    }

    public int getUserRating(User user, Recipe recipe){
        int rating = -1;
        String sql = "SELECT rating " +
                     "FROM Ratings " +
                     "WHERE User_ID = ? AND Recipe_ID = ?";

        List<Message> allMessages = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, user.getId()); 
            pstmt.setLong(2, recipe.getId());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    rating = rs.getInt("rating");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SQL Error: " + e.getMessage());
        }
        return rating;
    }

    @Override
    public List<Ingredient> fetchIngredients(Long id) {
        List<Ingredient> ingredients = new ArrayList<>();

        String sql = "SELECT i.name, ri.amount_int, ri.amount_unit FROM Ingredient i " +
                "INNER JOIN RecipeIngredient ri ON i.ID = ri.Ingredient_ID " +
                "WHERE ri.Recipe_ID = ?";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
                PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // Set the recipe ID parameter
            pstmt.setLong(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Ingredient ingredient = new Ingredient();
                    ingredient.setName(rs.getString("name"));
                    ingredient.setAmount(rs.getInt("amount_int"));
                    ingredient.setUnit(rs.getString("amount_unit"));
                    ingredients.add(ingredient);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return ingredients;
    }

    @Override
    public List<String> fetchTags(Long id) {
        List<String> tags = new ArrayList<>();

        String sql = "SELECT t.tagname FROM Tags t " +
                "INNER JOIN RecipeTag rt ON t.ID = rt.Tags_ID " +
                "WHERE rt.Recipe_ID = ?";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
                PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // Set the recipe ID parameter
            pstmt.setLong(1, id);

            // Execute the query
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String ingredientName = rs.getString("tagname");
                    tags.add(ingredientName);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return tags;
    }
    public void addComment(Long recipeId, Long userId, String commentText) {
        String sql = "INSERT INTO Comments (Recipe_ID, User_ID, Comment) VALUES (?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, recipeId);
            pstmt.setLong(2, userId);
            pstmt.setString(3, commentText);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void editComment(Long commentId, String newCommentText) {
        String sql = "UPDATE Comments SET Comment = ? WHERE Comment_ID = ?";
        System.out.println("editCommentsql:"+sql);
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newCommentText);
            pstmt.setLong(2, commentId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteComment(Long commentId) {
        String sql = "DELETE FROM Comments WHERE Comment_ID = ?";
        System.out.println("deleteCommentsql:"+sql);
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, commentId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @Override
    public List<Comment> fetchComments(Long recipeId) {
        List<Comment> comments = new ArrayList<>();
        System.out.println("comments:"+comments);
        String sql = "SELECT Comment_ID, Comment, User_ID, User_Name FROM CommentsView WHERE Recipe_ID = ?";
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, recipeId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Comment comment = new Comment();
                comment.setCommentID(rs.getLong("Comment_ID"));
                comment.setComment(rs.getString("Comment"));
                comment.setUserID(rs.getLong("User_ID"));
                comment.setUserName(rs.getString("User_Name"));
                comments.add(comment);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return comments;
    }


    public List<Long> fetchFavourites(User user) {
        List<Long> favourites = new ArrayList<>();

        String sql = "SELECT Recipe_ID FROM UserRecipeStar " +
                "WHERE User_ID = ? AND Recipe_ID IS NOT NULL AND isstar = true";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
                PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // Set the recipe ID parameter
            pstmt.setLong(1, user.getId());

            // Execute the query
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Long id = rs.getLong("Recipe_ID");
                    favourites.add(id);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return favourites;
    }

    private Map<Long, Map<Date, Integer>> fetchWeeklyRecipes(User user) {
        Map<Long, Map<Date, Integer>> weekly = new HashMap<Long, Map<Date, Integer>>();

        String sql = "SELECT Recipe_ID, date, w_servings FROM UserRecipeWeeklyList " +
                "WHERE User_ID = ? AND Recipe_ID IS NOT NULL";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
                PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // Set the recipe ID parameter
            pstmt.setLong(1, user.getId());

            // Execute the query
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Long id = rs.getLong("Recipe_ID");
                    Date date = rs.getDate("date");
                    int servings = rs.getInt("w_servings");
                    Map<Date, Integer> dates = weekly.get(id);
                    if (dates == null) {
                        dates = new HashMap<>();
                        weekly.put(id, dates);
                    }
                    dates.put(date, servings);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return weekly;
    }

    @Override
    public List<String> parseProcessSteps(String json) {
        List<String> processSteps = new ArrayList<>();

        // Use regex to extract process steps from JSON string
        Pattern pattern = Pattern.compile("\"steps\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            String steps = matcher.group(1);
            // Split the steps string by comma and trim each step
            String[] stepsArray = steps.split("\", \"");
            for (String step : stepsArray) {
                processSteps.add(step.trim().replaceAll("\"", ""));
            }
        }

        return processSteps;
    }

    public List<String> parseTags(String json) {
        List<String> processSteps = new ArrayList<>();

        // Use regex to extract process steps from JSON string
        Pattern pattern = Pattern.compile("\\{\\s*\"tagname\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            String tag = matcher.group(1);
            // Split the steps string by comma and trim each step
            String[] tags = tag.split(",");
            for (String t : tags) {
                processSteps.add(t.trim().replaceAll("\"", ""));
            }
        }

        return processSteps;
    }

    public List<Ingredient> parseIngredients(String json) {
        List<Ingredient> ingredients = new ArrayList<>();

        List<String> jsonElementsList = new ArrayList<>();

        // Use regex to match each JSON element in the array
        Pattern pat = Pattern.compile("\\{[^\\}]+\\}");
        Matcher mat = pat.matcher(json);

        while (mat.find()) {
            String jsonElement = mat.group();
            jsonElementsList.add(jsonElement);
        }
        for (String element : jsonElementsList) {

            // Use regex to extract process steps from JSON string
            Pattern pattern = Pattern.compile(
                    "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\",\\s*\"amount\"\\s*:\\s*\"([^\"]+)\",\\s*\"unit\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");
            Matcher matcher = pattern.matcher(element);

            if (matcher.find()) {
                String name = matcher.group(1).replaceAll("\"", "").trim();
                String amount = matcher.group(2).replaceAll("\"", "").trim();
                String unit = matcher.group(3).replaceAll("\"", "").trim();

                Ingredient ingredient = new Ingredient();
                ingredient.setName(name);
                ingredient.setAmount(Integer.parseInt(amount));
                ingredient.setUnit(unit);
                ingredients.add(ingredient);
            }
        }

        return ingredients;
    }

    public List<Comment> parseComments(String json) {
        List<Comment> comments = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            System.out.println("Empty or null JSON data for comments. JSON: '" + json + "'");
            return comments;  // Return an empty list if JSON is null or empty.
        }

        // Updated regex pattern to match the new JSON structure
        Pattern pat = Pattern.compile("\\{\"comment_id\": \"(\\d+)\", \"comment\": \"([^\"]+)\", \"user_id\": \"(\\d+)\", \"user_name\": \"([^\"]+)\"\\}");
        Matcher mat = pat.matcher(json);

        while (mat.find()) {
            long commentId = Long.parseLong(mat.group(1));
            String commentText = mat.group(2).replaceAll("\\\\\"", "\"");  // Unescape JSON encoded quotes
            long userId = Long.parseLong(mat.group(3));
            String userName = mat.group(4);

            Comment comment = new Comment();
            comment.setCommentID(commentId);
            comment.setComment(commentText);
            comment.setUserID(userId);
            comment.setUserName(userName);
            comments.add(comment);
        }

        return comments;
    }

    public List<String> getAllPredeterminedTags() {
        String sql = "SELECT TagName FROM Tags WHERE ispredefined = 1;";
        List<String> tags = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(dbManager.url);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tags.add(rs.getString("TagName"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return tags;
    }


    //method for getting whole customTags for that recipe and for that user
    public void getAllCustomTags(List<Recipe> recipes, User user) {
        // SQL to get all Tags_ID for a given Recipe_ID
        String sql = "SELECT rt.Recipe_ID, t.TagName " +
                "FROM RecipeCustomTag rt " +
                "JOIN Tags t ON rt.Tags_ID = t.ID " +
                "WHERE rt.User_ID = ?;";

        Map<Long, List<String>> customTagsMap = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(dbManager.url);
                PreparedStatement pstmt = connection.prepareStatement(sql);) {

            // Set User_ID parameter for the first query
            pstmt.setLong(1, user.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long recipeId = rs.getLong("Recipe_ID");
                    String tagname = rs.getString("TagName");
                    List<String> tagList = customTagsMap.get(recipeId);
                    if(tagList == null){
                        tagList = new ArrayList<>();
                        customTagsMap.put(recipeId, tagList);
                    }
                    tagList.add(tagname);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (Recipe recipe : recipes) {
            if (customTagsMap.containsKey(recipe.getId())) {
                recipe.getCustomTags().addAll(customTagsMap.get(recipe.getId()));
            }
        }
    }

    public void getCustomTags(Recipe recipe, User user) {
        // SQL to get Tags_ID for a given Recipe_ID
        String sql = "SELECT t.TagName "+
                    "FROM RecipeCustomTag rt "+
                    "JOIN Tags t ON rt.Tags_ID = t.ID "+
                    "WHERE rt.Recipe_ID = ? AND rt.User_ID = ?;";

        List<String> customTags = new ArrayList<String>();

        try (Connection connection = DriverManager.getConnection(dbManager.url);
            PreparedStatement pstmt = connection.prepareStatement(sql);) {

            // Set User_ID parameter for the first query
            pstmt.setLong(1, recipe.getId());
            pstmt.setLong(2, user.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String tagname = rs.getString("TagName");
                    customTags.add(tagname);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        recipe.getCustomTags().addAll(customTags);

    }

    public void writeShoppingList(List<Ingredient> ingredients, int week, User user){
        Runnable dbOperationTask = new Runnable() {
            @Override
            public void run() {
                String sql = "INSERT INTO ShoppingLists (User_ID, week, shoppingList) " +
                            "VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE shoppingList = ?";

                String jsonIngredients = ingredientsToJson(ingredients);

                try (Connection connection = DriverManager.getConnection(dbManager.url);
                    PreparedStatement pstmt = connection.prepareStatement(sql)) {

                    // set values
                    pstmt.setLong(1, user.getId());
                    pstmt.setInt(2, week);
                    pstmt.setString(3, jsonIngredients);
                    pstmt.setString(4, jsonIngredients);

                    pstmt.executeUpdate();

                    pstmt.close();
                    connection.close();

                } catch (SQLException e) {
                    e.printStackTrace();

                }
            }
        };
        // Start a new thread to execute the database operation
        Thread dbThread = new Thread(dbOperationTask);
        dbThread.start();

    }

    public List<Ingredient> fetchShoppingList(List<Ingredient> ingredients, int week, User user ){
        // SQL to get Tags_ID for a given Recipe_ID
        String sql = "SELECT shoppinglist "+
                     "FROM ShoppingLists "+
                     "WHERE User_ID = ? AND week = ?;";

        String jsonList = "";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
            PreparedStatement pstmt = connection.prepareStatement(sql);) {

            // Set User_ID parameter for the first query
            pstmt.setLong(1,user.getId() );
            pstmt.setInt(2, week);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    jsonList = rs.getString("shoppingList");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (jsonList != ""){
            // parse jsonList to ingredients and
            return jsonToIngredients(jsonList);
        }
        else{
            return ingredients;
        }


    }

    public void deleteShoppingList(int week, User user){
        Runnable dbOperationTask = new Runnable() {
            @Override
            public void run() {
                String sql = "DELETE FROM ShoppingLists " +
                            "WHERE User_ID = ? AND week = ?";

                try (Connection connection = DriverManager.getConnection(dbManager.url);
                    PreparedStatement pstmt = connection.prepareStatement(sql)) {

                    // set values
                    pstmt.setLong(1, user.getId());
                    pstmt.setInt(2, week);

                    pstmt.executeUpdate();

                    pstmt.close();
                    connection.close();

                } catch (SQLException e) {
                    e.printStackTrace();

                }
            }
        };
        // Start a new thread to execute the database operation
        Thread dbThread = new Thread(dbOperationTask);
        dbThread.start();
    }


    private String ingredientsToJson(List<Ingredient> ingredients) {
        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            json.append("{")
                .append("\"name\":\"").append(ingredient.getName()).append("\",")
                .append("\"amount\":").append(ingredient.getAmount()).append(",")
                .append("\"unit\":\"").append(ingredient.getUnit()).append("\"")
                .append("}");

            if (i < ingredients.size() - 1) {
                json.append(",");
            }
        }

        json.append("]");
        return json.toString();
    }


    private List<Ingredient> jsonToIngredients(String json) {
        List<Ingredient> ingredients = new ArrayList<>();

        // Remove the square brackets
        json = json.substring(1, json.length() - 1);

        // Split the string into individual ingredient JSON strings
        String[] ingredientJsons = json.split("(?<=\\}),\\s*(?=\\{)");

        for (String ingredientJson : ingredientJsons) {
            Ingredient ingredient = new Ingredient();

            // Remove the curly braces
            ingredientJson = ingredientJson.substring(1, ingredientJson.length() - 1);

            // Split the key-value pairs
            String[] keyValuePairs = ingredientJson.split(",");

            for (String pair : keyValuePairs) {
                String[] keyValue = pair.split(":");
                String key = keyValue[0].replace("\"", "").trim();
                String value = keyValue[1].replace("\"", "").trim();

                switch (key) {
                    case "name":
                        ingredient.setName(value);
                        break;
                    case "amount":
                        ingredient.setAmount(Integer.parseInt(value));
                        break;
                    case "unit":
                        ingredient.setUnit(value);
                        break;
                }
            }

            ingredients.add(ingredient);
        }

        return ingredients;
    }

    public Recipe fetchRecipeById(Long recipeId) {
        System.out.println("start fetchingRecipeById");
        final int maxAttempts = 100;  // Maximum number of attempts
        int attempts = 0;

        // Poll every half second until recipes are loaded or max attempts reached
        while (allrecipes.isEmpty() && attempts < maxAttempts) {
            try {
                Thread.sleep(1000);  // Wait for 1000 milliseconds
                attempts++;
                System.out.print("attempt " + attempts+"\t");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;  // If interrupted, exit
            }
        }

        // After waiting, try to find the recipe
        if (!allrecipes.isEmpty()) {
            return allrecipes.stream()
                    .filter(recipe -> recipe.getId().equals(recipeId))
                    .findFirst()
                    .orElse(null);
        } else {
            return null;  // Return null if recipes are still not loaded or not found
        }
    }




//    public Recipe fetchRecipeById(Long recipeId) {
//        // Ensure that the recipes list is updated or initialized
//        if (allrecipes == null || allrecipes.isEmpty()) {
//            System.out.println("recipes is null, so I am filling up with getAllRecipes");
//            getAllRecipes(allrecipes); // Load recipes if not already loaded
//        }
//
//        // Search for the recipe with the given ID
//        for (Recipe recipe : allrecipes) {
//            System.out.println("recipe id: " + recipe.getId());
//            if (recipe.getId().equals(recipeId)) {
//                return recipe;
//            }
//        }
//
//        System.out.println("I can't find any matching recipe with id " + recipeId);
//        return null; // Return null if no recipe matches
//    }

}
