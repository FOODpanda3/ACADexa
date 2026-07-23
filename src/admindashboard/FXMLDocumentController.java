package admindashboard;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class FXMLDocumentController implements Initializable {

    @FXML private Label label;
    @FXML private Label statusLabel;
    @FXML private AnchorPane contentArea;
    @FXML private VBox dashboardPane;
    
    @FXML private Label lblTotalStudents;
    @FXML private Label lblActiveExams;
    @FXML private Label lblLiveSessions;
    @FXML private Label lblSubmittedScores;
    
    @FXML private BarChart<String, Number> scoreChart;
    @FXML private Button btnDashboard;
    @FXML private Button btnstudents;
    @FXML private Button btnExams;
    @FXML private Button btnScores;
    private Node embeddedContentView;

    // Database Credentials
    private final String DB_URL = "jdbc:mysql://localhost:3306/admindashboard_db";
    private final String DB_USER = "root";
    private final String DB_PASS = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Automatically update the dashboard stats and the chart on startup
        updateDashboardStats();
        loadChartData(); 
        showDashboardPane();
    }

    /**
     * Fetches real-time counts from the database to display on the dashboard cards.
     */
    private void updateDashboardStats() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            
            // Count total students
            ResultSet rsStudents = stmt.executeQuery("SELECT COUNT(*) AS total FROM students");
            if (rsStudents.next()) {
                lblTotalStudents.setText(String.valueOf(rsStudents.getInt("total")));
            }

            // Count total exams/quizzes uploaded
            ResultSet rsExams = stmt.executeQuery("SELECT COUNT(*) AS total FROM exams");
            if (rsExams.next()) {
                lblActiveExams.setText(String.valueOf(rsExams.getInt("total")));
            }

            ResultSet rsLive = stmt.executeQuery("SELECT COUNT(*) AS total FROM live_sessions");
            if (rsLive.next() && lblLiveSessions != null) {
                lblLiveSessions.setText(String.valueOf(rsLive.getInt("total")));
            }

            ResultSet rsScores = stmt.executeQuery("SELECT COUNT(*) AS total FROM exam_scores");
            if (rsScores.next() && lblSubmittedScores != null) {
                lblSubmittedScores.setText(String.valueOf(rsScores.getInt("total")));
            }

        } catch (Exception e) {
            System.err.println("Dashboard Stats Error: " + e.getMessage());
            lblTotalStudents.setText("0");
            lblActiveExams.setText("0");
            if (lblLiveSessions != null) lblLiveSessions.setText("0");
            if (lblSubmittedScores != null) lblSubmittedScores.setText("0");
        }
    }

    /**
     * Calculates average scores mathematically from strings like "8/10" and plots them with CUSTOM COLORS!
     */
  /**
     * Calculates average scores mathematically and plots them.
     * Fix: Uses a listener to ensure bars are colored correctly once rendered.
     */
    private void loadChartData() {
        if (scoreChart == null) return;
        scoreChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Avg %");

        String sql = "SELECT s.class_name, e.score FROM exam_scores e JOIN students s ON e.student_id = s.id";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            Map<String, Double> totalScoresMap = new HashMap<>();
            Map<String, Integer> studentCountMap = new HashMap<>();

            while (rs.next()) {
                String section = rs.getString("class_name");
                String scoreStr = rs.getString("score"); 

                if (section == null || scoreStr == null) continue;

                String[] parts = scoreStr.split("/");
                if (parts.length == 2) {
                    try {
                        double earned = Double.parseDouble(parts[0]);
                        double max = Double.parseDouble(parts[1]);
                        double percentage = (max > 0) ? (earned / max) * 100.0 : 0;

                        totalScoresMap.put(section, totalScoresMap.getOrDefault(section, 0.0) + percentage);
                        studentCountMap.put(section, studentCountMap.getOrDefault(section, 0) + 1);
                    } catch (NumberFormatException ex) { }
                }
            }

            for (String section : totalScoresMap.keySet()) {
                double avgPercentage = totalScoresMap.get(section) / studentCountMap.get(section);
                series.getData().add(new XYChart.Data<>(section, Math.round(avgPercentage * 10.0) / 10.0));
            }

            scoreChart.getData().add(series);

            // 🌟 FIXED COLORING LOGIC 🌟
            // We loop through the data and apply styles. 
            // If the node is null (common error), we wait until the chart renders it.
            String[] colors = {"#1d2b53", "#ff9a56", "#5cb85c", "#d9534f", "#8a2be2", "#00bcd4", "#f0ad4e", "#e84393"};
            
            for (int i = 0; i < series.getData().size(); i++) {
                XYChart.Data<String, Number> data = series.getData().get(i);
                String color = colors[i % colors.length];
                
                Node node = data.getNode();
                if (node != null) {
                    node.setStyle("-fx-bar-fill: " + color + ";");
                } else {
                    // If the bar hasn't appeared yet, this listener catches it when it does
                    int finalI = i;
                    data.nodeProperty().addListener((ov, oldNode, newNode) -> {
                        if (newNode != null) {
                            newNode.setStyle("-fx-bar-fill: " + color + ";");
                        }
                    });
                }
            }

        } catch (Exception e) {
            System.err.println("Chart Error: " + e.getMessage());
        }
    }

    @FXML
    private void goToDashboard(ActionEvent event) {
        switchScene(event, "Dashboard.fxml");
    }



    @FXML
    private void handleUsers(ActionEvent event) {
        switchScene(event, "managestudent.fxml"); 
    }

    @FXML
    private void handleManageExams(ActionEvent event) {
        switchScene(event, "ManageExams.fxml");
    }

    @FXML
    private void handleScores(ActionEvent event) {
        switchScene(event, "studentsscores.fxml"); 
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        switchScene(event, "login.fxml");
    }

    private void showDashboardPane() {
        clearEmbeddedContent();
        if (dashboardPane != null) {
            dashboardPane.setVisible(true);
            dashboardPane.setManaged(true);
        }
        if (label != null) label.setText("School Exam Dashboard");
        if (statusLabel != null) statusLabel.setText("Track students, exams, live activity, and score performance from one place.");
        setActiveSidebarButton(btnDashboard);
        updateDashboardStats();
        loadChartData();
    }

    private void showEmbeddedContent(String fxmlFile, Button activeButton) {
        try {
            if (dashboardPane != null) {
                dashboardPane.setVisible(false);
                dashboardPane.setManaged(false);
            }
            clearEmbeddedContent();

            Parent view = FXMLLoader.load(getClass().getResource(fxmlFile));
            embeddedContentView = view;
            contentArea.getChildren().add(view);
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            setActiveSidebarButton(activeButton);
        } catch (IOException e) {
            System.err.println("Could not load: " + fxmlFile);
            e.printStackTrace();
        }
    }

    private void clearEmbeddedContent() {
        if (embeddedContentView != null && contentArea != null) {
            contentArea.getChildren().remove(embeddedContentView);
            embeddedContentView = null;
        }
    }

    private void setActiveSidebarButton(Button activeButton) {
        Button[] buttons = {btnDashboard, btnstudents, btnExams, btnScores};
        for (Button button : buttons) {
            if (button == null) {
                continue;
            }
            if (button == activeButton) {
                button.setStyle("-fx-background-color: #8a2be2; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
            } else {
                button.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-cursor: hand;");
            }
        }
    }

    /**
     * Optimized Scene Switcher: Transfers window size and state seamlessly.
     */
    private void switchScene(ActionEvent event, String fxmlFile) {
        try {
            Scene currentScene = ((Node) event.getSource()).getScene();
            Stage stage = (Stage) currentScene.getWindow();
            boolean isMaximized = stage.isMaximized();
            Parent root = FXMLLoader.load(getClass().getResource(fxmlFile));
            currentScene.setRoot(root);
            
            if (isMaximized) {
                stage.setMaximized(true);
            }
            
            stage.show();

        } catch (IOException e) {
            System.err.println("Could not load: " + fxmlFile);
            e.printStackTrace();
        }
    }

    private void switchToDashboardManageExams(ActionEvent event) {
        try {
            Scene currentScene = ((Node) event.getSource()).getScene();
            Stage stage = (Stage) currentScene.getWindow();
            boolean isMaximized = stage.isMaximized();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("Dashboard.fxml"));
            Parent root = loader.load();
            DashboardController controller = loader.getController();
            controller.showManageExams();

            currentScene.setRoot(root);
            if (isMaximized) {
                stage.setMaximized(true);
            }
            stage.show();
        } catch (IOException e) {
            System.err.println("Could not load Dashboard.fxml");
            e.printStackTrace();
        }
    }
}
