package admindashboard;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LoginController implements Initializable {

    private static final double DASHBOARD_MIN_WIDTH = 900.0;
    private static final double DASHBOARD_MIN_HEIGHT = 600.0;

    @FXML private VBox loginBox, registerBox;
    @FXML private TextField usernameField, regFullName, regUsername, passwordTextVisible;
    @FXML private PasswordField passwordField, regPassword;
    @FXML private CheckBox showPasswordCheckbox;
    @FXML private ImageView imgLogo;

    private final String DB_URL = "jdbc:mysql://localhost:3306/admindashboard_db";
    private final String DB_USER = "root";
    private final String DB_PASS = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // 1. SAFE LOGO LOAD
        try {
            // Updated to use the correct path: /admindashboard/image/
            var imageStream = getClass().getResourceAsStream("/admindashboard/image/logo.png");
            if (imageStream != null && imgLogo != null) {
                imgLogo.setImage(new Image(imageStream));
            } else {
                System.out.println("Resource not found: /admindashboard/image/logo.png");
            }
        } catch (Exception e) {
            System.err.println("Logo error: " + e.getMessage());
        }

        // 2. SAFE KEYBOARD LISTENERS
        setupSafeListeners();
    }

    private void setupSafeListeners() {
        // Login listeners: Triggers login when 'Enter' is pressed
        if (usernameField != null) usernameField.setOnKeyPressed(this::triggerLoginOnEnter);
        if (passwordField != null) passwordField.setOnKeyPressed(this::triggerLoginOnEnter);
        if (passwordTextVisible != null) passwordTextVisible.setOnKeyPressed(this::triggerLoginOnEnter);

        // Registration listeners: Triggers registration when 'Enter' is pressed
        if (regFullName != null) regFullName.setOnKeyPressed(this::triggerRegisterOnEnter);
        if (regUsername != null) regUsername.setOnKeyPressed(this::triggerRegisterOnEnter);
        if (regPassword != null) regPassword.setOnKeyPressed(this::triggerRegisterOnEnter);
    }

    private void triggerLoginOnEnter(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) handleLogin(null);
    }

    private void triggerRegisterOnEnter(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) handleRegistration(null);
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String user = usernameField.getText();
        String pass = showPasswordCheckbox.isSelected() ? passwordTextVisible.getText() : passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            showError("Input Error", "Please fill in all fields.");
            return;
        }

        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                loadDashboard();
            } else {
                showError("Login Failed", "Invalid credentials.");
            }
        } catch (Exception e) {
            showError("Database Error", "Connection failed: " + e.getMessage());
        }
    }

    private void loadDashboard() {
        try {
            Stage stage = (Stage) loginBox.getScene().getWindow();
            boolean isMaximized = stage.isMaximized();
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            Parent root = FXMLLoader.load(getClass().getResource("FXMLDocument.fxml"));
            loginBox.getScene().setRoot(root);
            stage.setTitle("ACADexa - Dashboard");
            stage.setMinWidth(DASHBOARD_MIN_WIDTH);
            stage.setMinHeight(DASHBOARD_MIN_HEIGHT);
            
            if (isMaximized) {
                stage.setMaximized(true);
            } else {
                stage.setWidth(Math.max(currentWidth, DASHBOARD_MIN_WIDTH));
                stage.setHeight(Math.max(currentHeight, DASHBOARD_MIN_HEIGHT));
            }
            
            stage.show();
        } catch (Exception e) {
            showError("Navigation Error", "Could not load the dashboard.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleShowPassword(ActionEvent event) {
        if (showPasswordCheckbox.isSelected()) {
            passwordTextVisible.setText(passwordField.getText());
            passwordTextVisible.setVisible(true);
            passwordTextVisible.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
        } else {
            passwordField.setText(passwordTextVisible.getText());
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordTextVisible.setVisible(false);
            passwordTextVisible.setManaged(false);
        }
    }

    @FXML
    private void handleRegistration(ActionEvent event) {
        String name = regFullName.getText();
        String user = regUsername.getText();
        String pass = regPassword.getText();

        if (name.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showError("Registration Error", "Please complete all fields.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "INSERT INTO users (full_name, username, password) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);
            pstmt.setString(2, user);
            pstmt.setString(3, pass);

            pstmt.executeUpdate();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            alert.setContentText("Account created successfully! You can now login.");
            alert.showAndWait();

            showLogin(null); // Switch back to login view
        } catch (Exception e) {
            showError("Database Error", "Registration failed: " + e.getMessage());
        }
    }

    @FXML 
    private void showRegistration(ActionEvent e) { 
        loginBox.setVisible(false); 
        registerBox.setVisible(true); 
    }

    @FXML 
    private void showLogin(ActionEvent e) { 
        registerBox.setVisible(false); 
        loginBox.setVisible(true); 
    }

    @FXML 
    private void handleForgotPassword(ActionEvent event) { 
        // 1. Get the username they typed in the login box
        String user = usernameField.getText();

        // 2. Check if they actually typed a username
        if (user == null || user.trim().isEmpty()) {
            showError("Forgot Password", "Please type your Username in the field first, then click Forgot Password.");
            return;
        }

        // 3. Look up their password in the database
        String sql = "SELECT password FROM users WHERE username = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.trim());
            ResultSet rs = pstmt.executeQuery();

            // 4. If the username exists, show the password
            if (rs.next()) {
                String recoveredPassword = rs.getString("password");
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Password Recovered");
                info.setHeaderText("Account Found!");
                info.setContentText("Your password is: " + recoveredPassword);
                info.showAndWait();
            } else {
                // If username is wrong/doesn't exist
                showError("Forgot Password", "That Username was not found in the database.");
            }
            
        } catch (Exception e) {
            showError("Database Error", "Could not retrieve password: " + e.getMessage());
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
