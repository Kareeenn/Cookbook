package cookbook.Controller;

import java.util.List;

import cookbook.model.Ingredient;
import cookbook.model.Recipe;
import cookbook.model.User;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;

public class ShoppingListItemController {

    @FXML
    private TextField ingredientAmount;

    @FXML
    private TextField ingredientName;

    @FXML
    private TextField ingredientUnit;

    private Ingredient ingredient;
    private User user;


    public void setUser(User user){
        this.user = user;
    }

    public void setIngredients(Ingredient ingredient){
        ingredientName.setText(ingredient.getName());
        ingredientAmount.setText(Integer.toString(ingredient.getAmount()));
        ingredientUnit.setText(ingredient.getUnit());
        this.ingredient = ingredient;
    }
    @FXML
    void ingredientClicked(MouseEvent event) {
    }

    public void updateIngredient(){
        this.ingredient.setName(ingredientName.getText());
        this.ingredient.setAmount(Integer.parseInt(ingredientAmount.getText()));
        this.ingredient.setUnit(ingredientUnit.getText());
    }
}
