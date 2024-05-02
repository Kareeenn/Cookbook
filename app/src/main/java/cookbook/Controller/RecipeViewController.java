package cookbook.Controller;

//searching

import java.io.IOException;
import java.lang.String;
import java.net.URL;

import java.util.List;
import java.util.ResourceBundle;
import java.util.ArrayList;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import cookbook.DatabaseManager;
import cookbook.SceneModifier;
import cookbook.model.Ingredient;
import cookbook.model.Recipe;
import cookbook.model.User;
import cookbook.repository.MySqlRecipeRepository;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import cookbook.Controller.AddRecipeController;

public class RecipeViewController implements Initializable {

    @FXML
    private GridPane recipeContainer;

    @FXML
    private MenuItem changeProfiles;

    @FXML
    private VBox vBox;

    @FXML
    private TextField searchBar;

    @FXML
    private Label greetingText;

    @FXML
    private FontAwesomeIconView star;

    @FXML
    private Button searchButton;

    private List<Recipe> recipeList;
    private MySqlRecipeRepository recipeRepos;
    private User user;
    private boolean favoritesShowing = false;

    public void setUserName(User user) {
        this.user = user;
        greetingText.setText("Hi, " + user.getUserName() + "!");

        // get information about favourite recipes
        recipeList = recipeRepos.getFavorites(recipeList, user);
        int number = 0;
        for (Recipe recipe : recipeList) {
            displayRecipeItem(recipe, number++, "");

        }
        
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // has to be the first to initialize the repository
        recipeRepos = new MySqlRecipeRepository(new DatabaseManager());

        recipeList = recipeRepos.getAllRecipes();

    }

    @FXML
    void changeProfileClicked(ActionEvent event) throws IOException {
        SceneModifier.change_scene(FXMLLoader.load(getClass().getResource("/cookbook.view/LoginScene.fxml")),
                (Stage) vBox.getScene().getWindow());
    }

    @FXML
    void searchButtonClicked(ActionEvent event) {
        filterRecipes();
    }

    @FXML
    void addButtonClicked(ActionEvent event) throws IOException {
        try {

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/cookbook.view/NewRecipe.fxml"));

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setOnCloseRequest(closeEvent -> {
                try {
                    // this.recipeList = recipeRepos.getAllRecipes(); 
                    FXMLLoader fxmlLoader2 = new FXMLLoader(getClass().getResource("/cookbook.view/RecipeView.fxml"));
                    Stage s = new Stage();
                    s.setScene(new Scene(fxmlLoader2.load()));
                    RecipeViewController controller = fxmlLoader2.getController();
                    controller.setUserName(this.user);
                    s.show();
                } catch (Exception e) {
                    // TODO: handle exception
                    System.out.println(e.getMessage());
                }
            });
            stage.setTitle("add new recipe");
            stage.setResizable(false);
            stage.setScene(new Scene(fxmlLoader.load()));

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void searchBarKeyTyped(ActionEvent event) {
        filterRecipes();
    }

    private void filterRecipes() {
        String[] searchWords = searchBar.getText().split(", ");
        int number = 0;
        // clear all displayed elements
        recipeContainer.getChildren().clear();
        // iterate through all recipes
        for (Recipe recipe : recipeList) {
            String searchHits = "";
            int numberOfHits = 0;
            
            for (String searchWord : searchWords){
                boolean hit = false;
                // check if the search word is in the recipe name
                if (recipe.getName().toLowerCase().contains(searchWord.toLowerCase())) {
                    hit = true;
                    numberOfHits += 1;
                }
                // check if the search word is in the recipe tags
                for (String tag : recipe.getTags()) {
                    if (tag.toLowerCase().contains(searchWord.toLowerCase())) {
                        if (hit != true)
                            numberOfHits += 1;
                        hit = true;
                        if (!searchWord.isEmpty() && !searchHits.contains(tag)) {
                            searchHits += tag + ", ";
                        }
                    }
                }
                // check if the search word is in the recipe ingredients
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.getName().toLowerCase().contains(searchWord.toLowerCase())) {
                        if (hit != true)
                            numberOfHits += 1;
                        hit = true;
                        if (!searchWord.isEmpty() && !searchHits.contains(ingredient.getName())){
                            searchHits += ingredient.getName() + ", ";
                        }
                    }
                }
            }
            // add the recipe if found
            if (numberOfHits >= searchWords.length) {
                // cut the last ", "
                if (searchHits.length() >= 3)
                    searchHits = searchHits.substring(0, searchHits.length() - 2);
                // show selected Item
                displayRecipeItem(recipe, number++, searchHits);
            }
        }
    }
    
    @FXML
    void favouritesButtonClicked() {
        if(this.favoritesShowing == true){
            this.favoritesShowing = false;
            star.setFill(Paint.valueOf("#ffbb0000"));
            searchBar.setText("");
            filterRecipes();
            
        } else {
            this.favoritesShowing = true;
            star.setFill(Paint.valueOf("#ffbd00"));
            int number = 0;
            // clear all displayed elements
            recipeContainer.getChildren().clear();
            // iterate through all recipes
            for (Recipe recipe : recipeList){
                if(recipe.getIsFavourite()){
                    displayRecipeItem(recipe, number++, "");
                }
            }
        }
    }

    private void displayRecipeItem(Recipe recipe, int number, String searchHits) {
        try {

            FXMLLoader fxmlLoader = new FXMLLoader();
            fxmlLoader.setLocation(getClass().getResource("/cookbook.view/RecipeItem.fxml"));
            StackPane recipeBox = fxmlLoader.load();
            RecipeItemController controller = fxmlLoader.getController();
            controller.setRecipeData(recipe, searchHits);
            controller.setUser(user);
            int column = number % 5;
            int row = number / 5 + 1;
            recipeContainer.add(recipeBox, column, row);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
