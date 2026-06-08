package admindashboard;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * @author Acer Nitro
 */
public class Admindashboard extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // 1. Load the Login FXML
        Parent root = FXMLLoader.load(getClass().getResource("login.fxml"));

        // 2. Set the Scene
        Scene scene = new Scene(root);

        // 3. Set the Window Title
        stage.setTitle("ACADexa - Login");

        // 4. ADDED: Set the Taskbar/Window Icon
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/admindashboard/image/logo.png")));
        } catch (Exception e) {
            System.out.println("Could not load window icon: " + e.getMessage());
        }

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
