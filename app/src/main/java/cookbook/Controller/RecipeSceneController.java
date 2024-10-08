package cookbook.Controller;
//handles the detailed view of a recipe

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import cookbook.DatabaseManager;
import cookbook.model.Comment;
import cookbook.model.Ingredient;
import cookbook.model.Recipe;
import cookbook.model.User;
import cookbook.repository.MySqlRecipeRepository;
import cookbook.repository.ThemesRepository;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

public class RecipeSceneController implements Initializable {

    @FXML
    private Text recipeNameText;

    @FXML
    private Text recipeDescriptionText;

    @FXML
    private Text averageRatingText;
    
    @FXML
    private VBox recipeDetailsVBox;

    @FXML
    private VBox vBoxProcessSteps;

    @FXML
    private HBox ratingHbox;

    @FXML
    private FlowPane ingredientsFlowPane;

    @FXML
    private Text IngredientHeadlineText;

    @FXML
    private ImageView recipeImageView;

    @FXML
    private FontAwesomeIconView star;

    @FXML
    private ComboBox<Integer> servingsComboBox;

    @FXML
    private FlowPane tagsFlowPane;

    @FXML
    private StackPane addTagsButton;

    @FXML
    private AddTagsButtonController addTagsButtonController;

    @FXML
    private Button removeFromWeeklyListButton;

    @FXML
    private VBox commentDisplayArea; // VBox to dynamically load comment views
    @FXML
    public TextField commentInputField; // TextField for entering new comments

    // @FXML
    // private Button addCommentButton; // Button to submit new comments

    private MySqlRecipeRepository recipeRepos;
    private Recipe recipe;
    private User user;
    private MySqlRecipeRepository sqlRepos = new MySqlRecipeRepository(new DatabaseManager());
    private boolean tagsListenerAdded = false;
    private boolean canSetRating;

    public void setUser(User user) {
        this.user = user;
        this.recipeRepos = new MySqlRecipeRepository(new DatabaseManager(), user);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        recipeNameText.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                System.out.println("Scene is now set.");
                ThemesRepository.applyTheme(recipeNameText.getScene());
            }
        });
    }

    public void setRecipeData(Recipe recipe, int numberOfServings) {
        this.recipe = recipe;

        recipeRepos = new MySqlRecipeRepository(new DatabaseManager(), user);
        initializeServings(numberOfServings);

        // Fetch tags and initialize tags view
        initializeTagsView();

        initializeUIFromRecipe();
    }

    private void initializeTagsView() {
        System.out.println("initializeTagsView");
        tagsFlowPane.getChildren().clear(); // Clear existing tags
        System.out.println("removed existing tags by initializeTagsView method");

        // Add all current tags from the recipe's observable list
        recipe.getTags().forEach(this::addTagToPane);
        recipe.getCustomTags().forEach(this::addCustomTagToPane);

        // Add the button for adding new tags at the end
        addAddTagsButton();

        // Setup listener for future updates
        setupTagChangeListener();
    }

    private void setupTagChangeListener() {
        if (!tagsListenerAdded) {
            // Take an initial snapshot of the tags list
            final List<String> oldTags = new ArrayList<>(recipe.getCustomTags());

            recipe.getCustomTags().addListener((ListChangeListener.Change<? extends String> change) -> {
                Platform.runLater(() -> {
                    // Take a new snapshot after changes
                    List<String> newTags = new ArrayList<>(recipe.getCustomTags());

                    // Determine removed tags by checking what's in oldTags but not in newTags
                    List<String> removedTags = oldTags.stream()
                            .filter(tag -> !newTags.contains(tag))
                            .collect(Collectors.toList());

                    // Determine added tags by checking what's in newTags but not in oldTags
                    List<String> addedTags = newTags.stream()
                            .filter(tag -> !oldTags.contains(tag))
                            .collect(Collectors.toList());

                    // Log changes for debugging
                    System.out.println("Removed Tags: " + removedTags);
                    System.out.println("Added Tags: " + addedTags);

                    // Update the UI for removed tags
                    removedTags.forEach(this::removeCustomTagFromPane);

                    // Update the UI for added tags
                    addedTags.forEach(this::addCustomTagToPane);

                    // Update oldTags to reflect the current state
                    oldTags.clear();
                    oldTags.addAll(newTags);
                });
            });
            tagsListenerAdded = true;
        }
    }

    private void addTagToPane(String tag) {
        System.out.println("addTagToPane");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/cookbook.view/Tag.fxml"));
            Node tagNode = loader.load(); // Load the FXML
            TagController tagController = loader.getController(); // Get the controller
            tagController.setTagName(tag); // Set the tag name
            tagNode.setUserData(tagController); // Store the controller in userData for later retrieval
            tagsFlowPane.getChildren().add(0, tagNode); // Add to the FlowPane
        } catch (IOException e) {
            System.out.println("Error loading tag view: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addCustomTagToPane(String tag) {
        System.out.println("addTagToPane");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/cookbook.view/Tag.fxml"));
            Node tagNode = loader.load(); // Load the FXML
            TagController tagController = loader.getController(); // Get the controller
            tagController.setCustom();
            tagController.setTagName(tag); // Set the tag name
            tagNode.setUserData(tagController); // Store the controller in userData for later retrieval
            tagsFlowPane.getChildren().add(0, tagNode); // Add to the FlowPane
        } catch (IOException e) {
            System.out.println("Error loading tag view: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeCustomTagFromPane(String tag) {
        System.out.println("removeTagFromPane");
        tagsFlowPane.getChildren().removeIf(node -> {
            TagController controller = (TagController) node.getUserData(); // Retrieve the controller from userData
            return controller != null && controller.getTagName().equals(tag); // Check if this is the tag to remove
        });
    }

    private void addAddTagsButton() {

        addTagsButtonController.initializeData(user, recipe);
    }

    private void initializeServings(int numberOfServings) {
        servingsComboBox.getItems().clear();
        Integer[] baseServings = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        ObservableList<Integer> adjustedServings = FXCollections.observableArrayList();
        for (int base : baseServings) {
            adjustedServings.add(base * recipe.getNumberOfPersons());
        }
        servingsComboBox.setItems(adjustedServings);
        servingsComboBox.setValue(numberOfServings);
        changeNumberOfServings();
    }

    private void initializeUIFromRecipe() {
        averageRatingText.setText("( " + Integer.toString(this.recipe.getAverageRating()) + " )");
        int rating = recipeRepos.getUserRating(user, recipe);
        if(rating == -1){
            canSetRating = true;
        }
        else{
            setRatingStars(rating);
            canSetRating = false;

        }

        recipeNameText.setText(recipe.getName());
        recipeDescriptionText.setText(recipe.getShortDescription());
        recipeImageView.setImage(new Image(
                recipe.getImagePath() != null ? recipe.getImagePath()
                        : "https://images.pexels.com/photos/1109197/pexels-photo-1109197.jpeg",
                true));
        vBoxProcessSteps.getChildren().setAll(
                recipe.getProcessSteps().stream().map(step -> new TextFlow(new Text(step)))
                        .collect(Collectors.toList()));
        setFavouriteStar();
        refreshComments();
    }

    public void editRecipeScene(ActionEvent event) {
        try {

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/cookbook.view/ChangeRecipe.fxml"));

            // create new stage for new window of the recipe
            Stage stage = new Stage();
            stage.setResizable(false);
            stage.setScene(new Scene(fxmlLoader.load()));

            // get the controller to call the method to set the data
            ChangeRecipeController controller = fxmlLoader.getController();
            controller.setRecipe(this.recipe);
            controller.setUser(this.user);

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void addToWeeklyList(ActionEvent event) {
        try {

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/cookbook.view/AddToWeeklyList_new.fxml"));

            // create new stage for new window of the recipe
            Stage stage = new Stage();
            stage.setResizable(false);
            stage.setScene(new Scene(fxmlLoader.load()));

            // get the controller to call the method to set the data
            AddToWeeklyListController controller = fxmlLoader.getController();
            controller.setRecipe(this.recipe);
            controller.setUser(user);
            controller.setServings(servingsComboBox.getValue());

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void changeServings(ActionEvent event) {
        changeNumberOfServings();
        // Refresh the scene to reflect the changes
    }

    @FXML
    void addToFavouritesClicked(MouseEvent event) {
        if (!recipe.getIsFavourite()) {
            recipe.setIsFavourite(true);
            setFavouriteStar();
            recipeRepos.saveToFavorites(recipe, user);
        } else {
            recipe.setIsFavourite(false);
            setFavouriteStar();
            recipeRepos.removeFromFavorites(recipe, user);
        }
    }

    @FXML
    void ratingOneStar(MouseEvent event) {
        setRating(1);
    }
    @FXML
    void ratingTwoStar(MouseEvent event) {
        setRating(2);
    }
    @FXML
    void ratingThreeStar(MouseEvent event) {
        setRating(3);
    }
    @FXML
    void ratingFourStar(MouseEvent event) {
        setRating(4);
    }
    @FXML
    void ratingFiveStar(MouseEvent event) {
        setRating(5);
    }

    private void setFavouriteStar() {
        if (recipe.getIsFavourite()) {
            star.setFill(Paint.valueOf("#ffbd00"));
        } else {
            star.setFill(Color.TRANSPARENT);

        }
    }

    private void setRatingStars(int rating){
        ObservableList<Node> stars = ratingHbox.getChildren();
        for(int i = 0; i < rating; i++){
            ((FontAwesomeIconView)stars.get(i)).setFill(Paint.valueOf("#ffbd00"));
        }
        
    }

    private void setRating(int rating){
        if(canSetRating){
            setRatingStars(rating);
            System.out.println("set");
            canSetRating = false;
            recipeRepos.setUserRating(user, recipe, rating);
            averageRatingText.setText("( " + Integer.toString(this.recipe.getAverageRating()) + " )");
        }
    }


    private void changeNumberOfServings() {
        Integer ogServings = this.recipe.getNumberOfPersons();
        // Read the new servings from the dropdown menu
        Integer newServings = servingsComboBox.getValue();
        IngredientHeadlineText.setText("Ingredients (" + newServings + " servings)");
        // Code to read the new servings from the dropdown menu
        // Calculate the ratio of new servings to original servings
        double scaleFactor = (double) newServings / (double) ogServings;
        // Create a new ingredients list and multiply ingredients
        List<Ingredient> ingredients = this.recipe.getIngredients();
        List<Ingredient> newIngredients = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            double newAmount = ingredient.getAmount() * scaleFactor;
            Ingredient newIngredient = new Ingredient();
            newIngredient.setName(ingredient.getName());
            newIngredient.setAmount((int) newAmount);
            newIngredient.setUnit(ingredient.getUnit());
            newIngredients.add(newIngredient);
        }
        // Set new servings and ingredients
        ingredientsFlowPane.getChildren().clear();
        for (Ingredient ingredient : newIngredients) {
            ingredientsFlowPane.getChildren().add(
                    new TextFlow(new Text(
                            ingredient.getName() + " (" + ingredient.getAmount() + " " + ingredient.getUnit() + ")")));
        }
    }

    @FXML
    private void addComment(ActionEvent event) {
        String commentText = commentInputField.getText().trim();
        System.out.println("Adding comment:" + commentText);
        if (!commentText.isEmpty()) {
            sqlRepos.addComment(recipe.getId(), user.getId(), commentText);
            refreshComments(); // Refresh comments after adding a new one
            commentInputField.clear();
//            Comment newComment = new Comment(); // Assuming you have a way to create a comment object here
//            // Populate your newComment object appropriately
//            displayComment(newComment); // This will add the new comment at the beginning
//            commentInputField.clear();
        }
    }

    public void refreshComments() {
        try {
            List<Comment> comments = sqlRepos.fetchComments(recipe.getId());
            commentDisplayArea.getChildren().clear();
            Collections.reverse(comments); // Reverse the list to add the newest first
            for (Comment comment : comments) {
                displayComment(comment);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to refresh comments: " + e.getMessage());
        }
    }

    private void displayComment(Comment comment) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/cookbook.view/Comment.fxml"));
            HBox commentBox = loader.load();
            CommentController controller = loader.getController();
            controller.setComment(comment);

            // Setting up the action handlers and visibility based on user permission
            Text editButton = (Text) controller.getEditButton();
            Text deleteButton = (Text) controller.getDeleteButton();

            // Check if the current user is the author of the comment
            if (user.getId().equals(comment.getUserID())) {
                editButton.setVisible(true);
                editButton.setDisable(false);
                editButton.setOnMouseClicked(e -> {
                    handleEditComment(comment);
                });

                deleteButton.setVisible(true);
                deleteButton.setDisable(false);
                deleteButton.setOnMouseClicked(e -> {
                    handleDeleteComment(comment);
                });
            } else {
                editButton.setVisible(false);
                editButton.setDisable(true);

                deleteButton.setVisible(false);
                deleteButton.setDisable(true);
            }

            // Optionally set up delete button actions here, or manage in the
            // CommentController
            commentDisplayArea.getChildren().add(commentBox);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleEditComment(Comment comment) {
        System.out.println("handleEditComment");
        TextInputDialog dialog = new TextInputDialog(comment.getComment());
        dialog.setTitle("Edit Comment");
        dialog.setHeaderText("Edit your comment:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newComment -> {
            sqlRepos.editComment(comment.getCommentID(), newComment);
            refreshComments();
        });
    }

    private void handleDeleteComment(Comment comment) {
        System.out.println("handleDeleteComment");
        if (showConfirmationDialog("Are you sure you want to delete this comment?")) {
            sqlRepos.deleteComment(comment.getCommentID());
            refreshComments();
        }
    }

    public boolean showConfirmationDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.showAndWait();
        return alert.getResult() == ButtonType.YES;
    }

    @FXML
    public void shareRecipeClicked(ActionEvent actionEvent) {
        System.out.println("Share Recipe button is clicked");
        try {
            // Correct the path if necessary and ensure it's accurate
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/cookbook.view/ShareRecipeScene.fxml"));
            // Create new stage for new window of the recipe
            Stage stage = new Stage();
            stage.setResizable(false);
            stage.setScene(new Scene(fxmlLoader.load()));

            // Get the controller to call the method to set the data
            ShareRecipeSceneController controller = fxmlLoader.getController();
            controller.setRecipe(this.recipe);
            controller.setUser(this.user);

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
