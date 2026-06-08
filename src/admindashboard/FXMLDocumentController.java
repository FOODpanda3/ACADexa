package admindashboard;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ResourceBundle;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javax.imageio.ImageIO;

public class FXMLDocumentController implements Initializable {

    @FXML private Label label;
    @FXML private Label statusLabel;
    @FXML private AnchorPane contentArea;
    @FXML private VBox dashboardPane;
    @FXML private VBox wheelPane;
    
    @FXML private Label lblTotalStudents;
    @FXML private Label lblActiveExams;
    @FXML private Label lblLiveSessions;
    @FXML private Label lblSubmittedScores;
    
    @FXML private BarChart<String, Number> scoreChart;
    @FXML private ComboBox<String> wheelSectionSelector;
    @FXML private HBox wheelClassControls;
    @FXML private Button btnSpinWheelNow;
    @FXML private Canvas wheelCanvas;
    @FXML private Label wheelStatusLabel;
    @FXML private Label wheelWinnerLabel;
    @FXML private Label wheelWinnerCodeLabel;
    @FXML private ImageView wheelQrView;
    @FXML private Button btnDashboard;
    @FXML private Button btnstudents;
    @FXML private Button btnExams;
    @FXML private Button btnScores;
    private Node embeddedContentView;

    // Database Credentials
    private final String DB_URL = "jdbc:mysql://localhost:3306/admindashboard_db";
    private final String DB_USER = "root";
    private final String DB_PASS = "";
    private final List<WheelStudent> wheelStudents = new ArrayList<>();
    private final Random random = new Random();
    private AnimationTimer wheelAnimation;
    private double wheelRotation = 0.0;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Automatically update the dashboard stats and the chart on startup
        updateDashboardStats();
        loadChartData(); 
        if (wheelCanvas != null) {
            wheelCanvas.setOnMouseClicked(event -> handleSpinWheelNow(null));
        }
        showDashboardPane();
        drawWheel();
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
    private void handleSpinWheel(ActionEvent event) {
        if (dashboardPane != null) {
            dashboardPane.setVisible(false);
            dashboardPane.setManaged(false);
        }
        if (wheelPane != null) {
            wheelPane.setVisible(true);
            wheelPane.setManaged(true);
        }
        if (label != null) label.setText("Spin the Wheel");
        if (statusLabel != null) statusLabel.setText("Pick students fairly by section.");
        loadWheelSections();
        showWheelClassControls(false);
        wheelStudents.clear();
        if (btnSpinWheelNow != null) btnSpinWheelNow.setDisable(true);
        if (wheelStatusLabel != null) wheelStatusLabel.setText("Choose Select Class or Scan QR Code to start.");
        drawWheel();
    }

    @FXML
    private void handleWheelSelectClass(ActionEvent event) {
        showWheelClassControls(true);
        loadWheelSections();
        loadWheelStudents();
        if (btnSpinWheelNow != null) btnSpinWheelNow.setDisable(wheelStudents.isEmpty());
    }

    @FXML
    private void handleWheelScanQr(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Scan QR Code Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        File imageFile = chooser.showOpenDialog(((Node) event.getSource()).getScene().getWindow());
        if (imageFile == null) {
            return;
        }

        try {
            String qrText = decodeQrImage(imageFile);
            String section = extractQrValue(qrText, "Section");
            String studentId = extractQrValue(qrText, "ID");

            if (section == null) {
                section = extractQueryParameter(qrText, "class");
            }
            if (section == null && studentId != null) {
                section = findStudentSection(studentId);
            }

            if (section == null || section.trim().isEmpty()) {
                if (wheelStatusLabel != null) wheelStatusLabel.setText("QR scanned, but no class/section was found.");
                return;
            }

            loadWheelSections();
            showWheelClassControls(true);
            if (!wheelSectionSelector.getItems().contains(section)) {
                wheelSectionSelector.getItems().add(section);
            }
            wheelSectionSelector.setValue(section);
            loadWheelStudents();
            drawWheel();
            if (btnSpinWheelNow != null) btnSpinWheelNow.setDisable(wheelStudents.isEmpty());
            if (wheelStatusLabel != null) wheelStatusLabel.setText("QR loaded " + section + ". Ready to spin.");
        } catch (Exception e) {
            if (wheelStatusLabel != null) wheelStatusLabel.setText("Could not scan QR code: " + e.getMessage());
        }
    }

    @FXML
    private void handleWheelSectionChanged(ActionEvent event) {
        loadWheelStudents();
        drawWheel();
        if (btnSpinWheelNow != null) btnSpinWheelNow.setDisable(wheelStudents.isEmpty());
    }

    @FXML
    private void handleWheelRefresh(ActionEvent event) {
        loadWheelSections();
        if (wheelClassControls == null || wheelClassControls.isVisible()) {
            loadWheelStudents();
        }
        drawWheel();
        if (btnSpinWheelNow != null) btnSpinWheelNow.setDisable(wheelStudents.isEmpty());
    }

    @FXML
    private void handleSpinWheelNow(ActionEvent event) {
        if (wheelStudents.isEmpty()) {
            if (wheelStatusLabel != null) wheelStatusLabel.setText("No students found for this section.");
            return;
        }
        if (wheelAnimation != null) {
            wheelAnimation.stop();
        }

        int winnerIndex = random.nextInt(wheelStudents.size());
        WheelStudent winner = wheelStudents.get(winnerIndex);
        double segment = 360.0 / wheelStudents.size();
        double winnerCenter = winnerIndex * segment + segment / 2.0;
        double targetRotation = 90.0 - winnerCenter + 360.0 * (4 + random.nextInt(3));
        double startRotation = wheelRotation;
        double delta = targetRotation - startRotation;
        final long durationNanos = 2_400_000_000L;
        final long[] startNanos = {0};

        if (btnSpinWheelNow != null) btnSpinWheelNow.setDisable(true);
        if (wheelWinnerLabel != null) wheelWinnerLabel.setText("...");
        if (wheelWinnerCodeLabel != null) wheelWinnerCodeLabel.setText("");
        if (wheelQrView != null) wheelQrView.setImage(null);
        if (wheelStatusLabel != null) wheelStatusLabel.setText("Spinning...");

        wheelAnimation = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (startNanos[0] == 0) {
                    startNanos[0] = now;
                }
                double progress = Math.min(1.0, (now - startNanos[0]) / (double) durationNanos);
            double eased = 1.0 - Math.pow(1.0 - progress, 3);
            wheelRotation = startRotation + delta * eased;
            drawWheel();

            if (progress >= 1.0) {
                    stop();
                wheelRotation = targetRotation % 360.0;
                drawWheel();
                if (wheelWinnerLabel != null) wheelWinnerLabel.setText(winner.name);
                if (wheelWinnerCodeLabel != null) wheelWinnerCodeLabel.setText(winner.code + " | " + winner.section);
                generateStudentQrCode(winner);
                if (wheelStatusLabel != null) wheelStatusLabel.setText("Winner selected from " + winner.section + ".");
                if (btnSpinWheelNow != null) btnSpinWheelNow.setDisable(false);
            }
            }
        };
        wheelAnimation.start();
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
        if (wheelAnimation != null) {
            wheelAnimation.stop();
        }
        clearEmbeddedContent();
        if (wheelPane != null) {
            wheelPane.setVisible(false);
            wheelPane.setManaged(false);
        }
        showWheelClassControls(false);
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
            if (wheelAnimation != null) {
                wheelAnimation.stop();
            }
            if (dashboardPane != null) {
                dashboardPane.setVisible(false);
                dashboardPane.setManaged(false);
            }
            if (wheelPane != null) {
                wheelPane.setVisible(false);
                wheelPane.setManaged(false);
            }
            showWheelClassControls(false);
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

    private void loadWheelSections() {
        if (wheelSectionSelector == null) return;
        String selected = wheelSectionSelector.getValue();
        wheelSectionSelector.getItems().clear();
        wheelSectionSelector.getItems().add("All Sections");

        String sql = "SELECT DISTINCT class_name FROM students WHERE class_name IS NOT NULL AND TRIM(class_name) <> '' ORDER BY class_name ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                wheelSectionSelector.getItems().add(rs.getString("class_name"));
            }
        } catch (Exception e) {
            if (wheelStatusLabel != null) wheelStatusLabel.setText("Could not load sections: " + e.getMessage());
        }

        if (selected != null && wheelSectionSelector.getItems().contains(selected)) {
            wheelSectionSelector.setValue(selected);
        } else {
            wheelSectionSelector.setValue("All Sections");
        }
    }

    private void loadWheelStudents() {
        wheelStudents.clear();
        String section = wheelSectionSelector != null ? wheelSectionSelector.getValue() : "All Sections";
        boolean allSections = section == null || section.equals("All Sections");
        String sql = allSections
                ? "SELECT student_id_number, class_name, full_name, pin FROM students ORDER BY class_name ASC, full_name ASC"
                : "SELECT student_id_number, class_name, full_name, pin FROM students WHERE class_name = ? ORDER BY full_name ASC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (!allSections) {
                pstmt.setString(1, section);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    wheelStudents.add(new WheelStudent(
                            rs.getString("student_id_number"),
                            rs.getString("class_name"),
                            rs.getString("full_name"),
                            rs.getString("pin")
                    ));
                }
            }
            if (wheelStatusLabel != null) {
                wheelStatusLabel.setText(wheelStudents.size() + " student(s) loaded.");
            }
        } catch (Exception e) {
            if (wheelStatusLabel != null) wheelStatusLabel.setText("Could not load students: " + e.getMessage());
        }

        if (wheelWinnerLabel != null) wheelWinnerLabel.setText("-");
        if (wheelWinnerCodeLabel != null) wheelWinnerCodeLabel.setText("");
        if (wheelQrView != null) wheelQrView.setImage(null);
    }

    private void showWheelClassControls(boolean visible) {
        if (wheelClassControls != null) {
            wheelClassControls.setVisible(visible);
            wheelClassControls.setManaged(visible);
        }
    }

    private String decodeQrImage(File imageFile) throws Exception {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("Unsupported image file.");
        }

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result result = new MultiFormatReader().decode(bitmap);
        return result.getText();
    }

    private String extractQrValue(String qrText, String label) {
        if (qrText == null || label == null) {
            return null;
        }

        String prefix = label + ":";
        String[] lines = qrText.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String value = trimmed.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private String extractQueryParameter(String text, String key) {
        if (text == null || key == null) {
            return null;
        }

        int queryStart = text.indexOf('?');
        String query = queryStart >= 0 ? text.substring(queryStart + 1) : text;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = decodeUrl(pair.substring(0, equals));
            if (key.equalsIgnoreCase(name)) {
                String value = decodeUrl(pair.substring(equals + 1));
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private String decodeUrl(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String findStudentSection(String studentId) {
        String sql = "SELECT class_name FROM students WHERE student_id_number = ? LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, studentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("class_name");
                }
            }
        } catch (Exception e) {
            if (wheelStatusLabel != null) wheelStatusLabel.setText("Could not find student section: " + e.getMessage());
        }
        return null;
    }

    private void drawWheel() {
        if (wheelCanvas == null) return;
        GraphicsContext gc = wheelCanvas.getGraphicsContext2D();
        double width = wheelCanvas.getWidth();
        double height = wheelCanvas.getHeight();
        double size = Math.min(width, height) - 20.0;
        double x = (width - size) / 2.0;
        double y = (height - size) / 2.0;
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        String[] colors = {"#1d2b53", "#ff9a56", "#5cb85c", "#8a2be2", "#17a2b8", "#f0ad4e", "#d9534f", "#2d9cdb"};

        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.WHITE);
        gc.fillOval(x, y, size, size);

        if (wheelStudents.isEmpty()) {
            gc.setStroke(Color.web("#d8dde6"));
            gc.setLineWidth(3);
            gc.strokeOval(x, y, size, size);
            gc.setFill(Color.web("#1d2b53"));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText("No students loaded", centerX, centerY);
            return;
        }

        double angle = 360.0 / wheelStudents.size();
        for (int i = 0; i < wheelStudents.size(); i++) {
            double start = wheelRotation + i * angle;
            gc.setFill(Color.web(colors[i % colors.length]));
            gc.fillArc(x, y, size, size, start, angle, javafx.scene.shape.ArcType.ROUND);

            gc.save();
            gc.translate(centerX, centerY);
            gc.rotate(-(start + angle / 2.0));
            gc.setFill(Color.WHITE);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setTextBaseline(VPos.CENTER);
            String name = wheelStudents.get(i).name;
            if (name.length() > 18) {
                name = name.substring(0, 17) + ".";
            }
            gc.fillText(name, size / 2.0 - 14.0, 0.0);
            gc.restore();
        }

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(4);
        gc.strokeOval(x, y, size, size);
        gc.setFill(Color.WHITE);
        gc.fillOval(centerX - 28.0, centerY - 28.0, 56.0, 56.0);
        gc.setFill(Color.web("#1d2b53"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("SPIN", centerX, centerY);

        drawWheelPointer(gc, centerX, y);
    }

    private void drawWheelPointer(GraphicsContext gc, double centerX, double wheelTop) {
        double tipY = wheelTop + 8.0;
        gc.setFill(Color.web("#d9534f"));
        gc.fillPolygon(
                new double[] {centerX - 18.0, centerX + 18.0, centerX},
                new double[] {wheelTop - 22.0, wheelTop - 22.0, tipY},
                3
        );
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2.0);
        gc.strokePolygon(
                new double[] {centerX - 18.0, centerX + 18.0, centerX},
                new double[] {wheelTop - 22.0, wheelTop - 22.0, tipY},
                3
        );
    }

    private void generateStudentQrCode(WheelStudent student) {
        if (wheelQrView == null || student == null) {
            return;
        }

        String qrText = "ACADexa Student\n"
                + "ID: " + student.code + "\n"
                + "Name: " + student.name + "\n"
                + "Section: " + student.section + "\n"
                + "PIN: " + student.pin;

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        try {
            int size = 160;
            BitMatrix bitMatrix = qrCodeWriter.encode(qrText, BarcodeFormat.QR_CODE, size, size);
            WritableImage image = new WritableImage(size, size);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    image.getPixelWriter().setColor(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            wheelQrView.setImage(image);
        } catch (WriterException e) {
            if (wheelStatusLabel != null) {
                wheelStatusLabel.setText("Winner selected, but QR could not be generated.");
            }
        }
    }

    /**
     * Optimized Scene Switcher: Transfers window size and state seamlessly.
     */
    private void switchScene(ActionEvent event, String fxmlFile) {
        try {
            if (wheelAnimation != null) {
                wheelAnimation.stop();
            }
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

    private static class WheelStudent {
        private final String code;
        private final String section;
        private final String name;
        private final String pin;

        private WheelStudent(String code, String section, String name, String pin) {
            this.code = code == null ? "" : code;
            this.section = section == null ? "" : section;
            this.name = name == null || name.trim().isEmpty() ? this.code : name.trim();
            this.pin = pin == null ? "" : pin;
        }
    }
}
