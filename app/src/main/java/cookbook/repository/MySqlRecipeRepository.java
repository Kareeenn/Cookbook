package cookbook.repository;

import cookbook.DatabaseManager;
import cookbook.model.Ingredient;
import cookbook.model.Recipe;
import java.sql.*;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mysql.cj.protocol.Resultset;

public class MySqlRecipeRepository implements RecipeRepository{

    private DatabaseManager dbManager;

    public MySqlRecipeRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        loadPredeterminedTags();  // Pre-load tags to optimize later checks
    }


    private List<String> cachedPredeterminedTags;

    //Cache Predetermined Tags
    private void loadPredeterminedTags() {
        cachedPredeterminedTags = getAllPredeterminedTags();
    }


    @Override
    public int addRecipeRepo(String name, String shortDescription, String description, String imageUrl, int servings, String ingredients, List <String> tags) {
        int recipeId = -1;
        String sql = "CALL AddNewRecipe(?, ?, ?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             CallableStatement statement = connection.prepareCall(sql)) {
            statement.setString(1, name);
            statement.setString(2, shortDescription);
            statement.setString(3, description);
            statement.setString(4, imageUrl);
            statement.setInt(5, servings);
            statement.setString(6, ingredients);
            statement.setString(7, convertTagsToString(tags)); // Ensure this matches the stored procedure's requirements

            boolean hadResults = statement.execute();
            if (hadResults) {
                try (ResultSet rs = statement.getGeneratedKeys()) {
                    if (rs.next()) {
                        recipeId = rs.getInt(1);  // Retrieve the ID of the newly inserted recipe
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return recipeId;  // Return the recipe ID

    }

    private String convertTagsToString(List<String> tags) {
        StringBuilder sb = new StringBuilder();
        for (String tag : tags) {
            sb.append(tag).append(",");
        }
        // Remove the last comma
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }


    @Override
    public Recipe getRecipeById(Long id) {
        Recipe recipe = new Recipe();

        // Fetch ingredients for the recipe
        List<Ingredient> ingredients = fetchIngredients(id);
        recipe.setIngredients(ingredients);

        // Fetch tags for the recipe
        List<String> tags = fetchTags(id);
        recipe.setTags(tags);

        // Fetch comments for the recipe
        List<String> comments = fetchComments(id);
        recipe.setComments(comments);
        return recipe;
    }

    public void fetchRecipeDetails(Recipe rec) {

        // Fetch comments for the recipe
        List<String> comments = fetchComments(rec.getId());
        rec.setComments(comments);
    }

    @Override
    public List<Recipe> getAllRecipes() {
        List<Recipe> recipes = new ArrayList<>();

        //String sql = "SELECT Recipe_ID, Recipe_Name, Short_Description, Description, Ingredients_JSON, Tags_JSON, Servings FROM FullRecipeView";
        String sql = "SELECT Recipe_ID, Recipe_Name, Short_Description, Description, Ingredients_JSON, Tags_JSON, Servings, Image_URL FROM FullRecipeView";


        try (Connection connection = DriverManager.getConnection(dbManager.url);
        PreparedStatement pstmt = connection.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Recipe recipe = new Recipe();
                recipe.setId(rs.getLong("Recipe_ID"));
                recipe.setName(rs.getString("Recipe_Name"));
                recipe.setShortDescription(rs.getString("Short_Description"));
                recipe.setNumberOfPersons(rs.getInt("Servings"));
                recipe.setImagePath(rs.getString("Image_URL"));


                // Extract and parse process steps from JSON
                String jsonProcessSteps = rs.getString("Description");
                List<String> processSteps = parseProcessSteps(jsonProcessSteps);
                recipe.setProcessSteps(processSteps);

                // Fetch tags for the recipe
                String jsonTags = rs.getString("Tags_JSON");
                List<String> tags = parseTags(jsonTags);
                recipe.setTags(tags);

                // Fetch ingredients for the recipe
                String jsonIngredients = rs.getString("Ingredients_JSON");
                List<Ingredient> ingredients = parseIngredients(jsonIngredients);
                recipe.setIngredients(ingredients);

                recipes.add(recipe);
            }


            pstmt.close();
            connection.close();



        } catch (SQLException e) {
            e.printStackTrace();
        }
        return recipes;
    }

    @Override
    public void updateRecipe(Long id, String name, String shortDescription, String description, String imageUrl, int servings, String ingredientsText, String tagsText) {
        try (Connection connection = DriverManager.getConnection(dbManager.url)) {
            String sql = "CALL UpdateRecipe(?, ?, ?, ?, ?, ?, ?, ?)";
            try (CallableStatement statement = connection.prepareCall(sql)) {
                statement.setLong(1, id);
                statement.setString(2, name);
                statement.setString(3, shortDescription);
                statement.setString(4, description);
                statement.setString(5, imageUrl);
                statement.setInt(6, servings);
                statement.setString(7, ingredientsText);
                statement.setString(8, tagsText);
    
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
    public void saveToFavorites(Recipe recipe) {

    }

    @Override
    public void removeFromFavorites(Recipe recipe) {

    }

    @Override
    public List<Recipe> getFavorites() {
        return List.of();
    }

    @Override
    public void addToWeekPlan(Recipe recipe, String week) {

    }

    @Override
    public void removeFromWeekPlan(Recipe recipe, String week) {

    }

    @Override
    public List<Recipe> getWeekPlan(String week) {
        return List.of();
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

            // Execute the query
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

    @Override
    public List<String> fetchComments(Long id) {
        List<String> comments = new ArrayList<>();

        String sql = "SELECT comment FROM Comments " +
                     "WHERE Recipe_ID = ? AND comment IS NOT NULL";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            // Set the recipe ID parameter
            pstmt.setLong(1, id);

            // Execute the query
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String ingredientName = rs.getString("name");
                    comments.add(ingredientName);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return comments;
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
        for (String element : jsonElementsList){

            // Use regex to extract process steps from JSON string
            Pattern pattern = Pattern.compile("\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\",\\s*\"amount\"\\s*:\\s*\"([^\"]+)\",\\s*\"unit\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");
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

    //method for getting whole predeterminedTags
    public List<String> getAllPredeterminedTags() {
        List<String> tags = new ArrayList<>();

        String sql = "SELECT ID, tagname FROM Tags WHERE ispredefined = 1";

        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String tag = rs.getString("tagname");
                tags.add(tag);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return tags;
    }

    private boolean tagExists(String tag) {
        // Check in cached predetermined tags first
        if (cachedPredeterminedTags.contains(tag)) {
            return true;
        }

        // Check in the database if not found in the cache (for non-predetermined tags)
        String sql = "SELECT COUNT(*) FROM Tags WHERE tagname = ?";
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tag);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Integer> ensureTagsExist(List<String> tags, boolean isPredefined) {
        List<Integer> tagIds = new ArrayList<>();
        for (String tag : tags) {
            int tagId = getTagId(tag);  // Check if the tag exists and get its ID
            if (tagId == -1) {  // Tag does not exist
                addTag(tag, isPredefined ? 1 : 0);  // Add the tag as predetermined or custom based on the parameter
            }
            tagIds.add(tagId);  // Collect tag ID
        }
        return tagIds;
    }

    private int getTagId(String tag) {
        String sql = "SELECT ID FROM Tags WHERE tagname = ?";
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tag);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");  // Return the existing ID
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;  // Return -1 if the tag does not exist
    }

    // Returning addTag Method
    private int addTag(String tag, int isPredefined) {
        String sql = "INSERT INTO Tags (tagname, ispredefined) VALUES (?, ?)";
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, tag);
            pstmt.setInt(2, isPredefined);
            pstmt.executeUpdate();
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);  // Return the new ID
                } else {
                    throw new SQLException("Creating tag failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;  // Return -1 if insertion fails
    }

    public void linkTagsToRecipe(int recipeId, List<Integer> tagIds) {
        String sql = "INSERT INTO RecipeTag (Recipe_ID, Tags_ID) VALUES (?, ?)";
        try (Connection connection = DriverManager.getConnection(dbManager.url);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (int tagId : tagIds) {
                pstmt.setInt(1, recipeId);
                pstmt.setInt(2, tagId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }





}
