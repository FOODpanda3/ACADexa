package admindashboard;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder; // Added for safe URL generation
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;
import java.util.Scanner;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ManageExamsController implements Initializable {

    // --- QUIZ VARIABLES ---
    @FXML private TextField txtQuizTitle;
    @FXML private TextField txtQuizTimeLimit; // Added time limit variable
    @FXML private ComboBox<String> cbQuizClass;
    @FXML private Label lblQuizFile;
    @FXML private Label lblQuizAnsFile; 
    @FXML private ImageView imgQuizQR;
    private File selectedQuizFile;
    private File selectedQuizAnsFile; 

    // --- EXAM VARIABLES ---
    @FXML private TextField txtExamTitle;
    @FXML private TextField txtExamTimeLimit; // Added time limit variable
    @FXML private ComboBox<String> cbExamClass;
    @FXML private Label lblExamFile;
    @FXML private Label lblExamAnsFile; 
    @FXML private ImageView imgExamQR;
    private File selectedExamFile;
    private File selectedExamAnsFile; 

    private final String DB_URL = "jdbc:mysql://localhost:3306/admindashboard_db";
    private final String DB_USER = "root";
    private final String DB_PASS = ""; 

    private final String UPLOAD_DIR = "C:/xampp/htdocs/quiz_system/uploads/";

    @FXML private javafx.scene.control.ToolBar manageExamsToolbar;
    private Runnable backHandler;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadSectionsFromManageStudents();
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    private void loadSectionsFromManageStudents() {
        ObservableList<String> sections = FXCollections.observableArrayList();
        String sql = "SELECT DISTINCT class_name FROM students WHERE class_name IS NOT NULL ORDER BY class_name ASC"; 

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                sections.add(rs.getString("class_name"));
            }
            cbQuizClass.setItems(sections);
            cbExamClass.setItems(sections);
        } catch (Exception e) {
            System.err.println("Database Error: " + e.getMessage());
        }
    }

    @FXML private void handleUploadQuiz(ActionEvent event) {
        processMobileUpload("Quiz", txtQuizTitle, txtQuizTimeLimit, cbQuizClass, selectedQuizFile, selectedQuizAnsFile, imgQuizQR);
    }

    @FXML private void handleUploadExam(ActionEvent event) {
        processMobileUpload("Major Exam", txtExamTitle, txtExamTimeLimit, cbExamClass, selectedExamFile, selectedExamAnsFile, imgExamQR);
    }

    // Updated to accept the timeLimitField
    private void processMobileUpload(String type, TextField titleField, TextField timeLimitField, ComboBox<String> classBox, File pdfFile, File ansFile, ImageView qrView) {
        String title = titleField.getText().trim();
        String timeLimitStr = timeLimitField.getText().trim(); // Get time limit text
        String targetClass = classBox.getValue();
        
        if (title.isEmpty() || targetClass == null || pdfFile == null || ansFile == null) {
            showAlert("Missing Info", "Please provide a title, class, PDF, and Answer Key (.txt).", Alert.AlertType.WARNING);
            return;
        }

        // Validate time limit input
        int timeLimit = 0;
        if (!timeLimitStr.isEmpty()) {
            try {
                timeLimit = Integer.parseInt(timeLimitStr);
                if (timeLimit <= 0) {
                    showAlert("Invalid Input", "Time limit must be a positive number.", Alert.AlertType.WARNING);
                    return;
                }
            } catch (NumberFormatException e) {
                showAlert("Invalid Input", "Time limit must be a valid number.", Alert.AlertType.WARNING);
                return;
            }
        }

        String answerKeyString = "";
        try (Scanner scanner = new Scanner(ansFile)) {
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine().toUpperCase());
            }
            answerKeyString = sb.toString().replaceAll("[^A-Z]", "");
        } catch (Exception e) {
            showAlert("File Error", "Could not read the answer key file.", Alert.AlertType.ERROR);
            return;
        }

        int validQuestionCount = answerKeyString.length();
        
        if (validQuestionCount == 0) {
            showAlert("Warning", "The text file has no valid answers (A-Z).", Alert.AlertType.WARNING);
            return;
        }

        try {
            Path source = pdfFile.toPath();
            Path destination = Paths.get(UPLOAD_DIR + pdfFile.getName());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

            // Encode parameters to handle spaces and special characters safely
            String encodedClass = URLEncoder.encode(targetClass, StandardCharsets.UTF_8.toString());
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString());
            String encodedPdf = URLEncoder.encode(pdfFile.getName(), StandardCharsets.UTF_8.toString());

            String queryParams = String.format("answer.php?class=%s&title=%s&pdf=%s&q=%d&timeLimit=%d",
                                encodedClass, encodedTitle, encodedPdf, validQuestionCount, timeLimit);

            String studentURL = NetworkConfig.buildStudentURL(queryParams);

            generateQRCode(studentURL, qrView);
            
            // Pass the time limit to the log method
            saveExamLog(title, "Type: " + type, answerKeyString, targetClass, timeLimit);
            
            showAlert("Success", "QR Code Generated for " + validQuestionCount + " automatic questions.", Alert.AlertType.INFORMATION);

        } catch (IOException e) {
            showAlert("Error", "Failed to copy PDF. Check path: " + UPLOAD_DIR, Alert.AlertType.ERROR);
        } catch (Exception e) {
            showAlert("Connection Error", "Network error or encoding failed.", Alert.AlertType.ERROR);
        }
    }

    // Updated to handle timeLimit logic
    private void saveExamLog(String title, String description, String answerKey, String className, int timeLimit) {
        String sql = "INSERT INTO exams (title, description, answer_key, class_name, time_limit) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, description);
            pstmt.setString(3, answerKey);
            pstmt.setString(4, className);
            pstmt.setInt(5, timeLimit);
            pstmt.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void generateQRCode(String data, ImageView targetImageView) {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        try {
            int width = 250;
            int height = 250;
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height);
            WritableImage writableImage = new WritableImage(width, height);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    writableImage.getPixelWriter().setColor(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            targetImageView.setImage(writableImage);
        } catch (WriterException e) { e.printStackTrace(); }
    }

    @FXML private void handleBrowseQuiz(ActionEvent e) { browsePDF(true, e); }
    @FXML private void handleBrowseExam(ActionEvent e) { browsePDF(false, e); }

    private void browsePDF(boolean isQuiz, ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File f = fc.showOpenDialog(((Node) event.getSource()).getScene().getWindow());
        if (f != null) {
            if (isQuiz) { selectedQuizFile = f; lblQuizFile.setText(f.getName()); }
            else { selectedExamFile = f; lblExamFile.setText(f.getName()); }
        }
    }

    @FXML private void handleBrowseQuizAns(ActionEvent e) { browseTXT(true, e); }
    @FXML private void handleBrowseExamAns(ActionEvent e) { browseTXT(false, e); }

    private void browseTXT(boolean isQuiz, ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File f = fc.showOpenDialog(((Node) event.getSource()).getScene().getWindow());
        if (f != null) {
            if (isQuiz) { selectedQuizAnsFile = f; if(lblQuizAnsFile != null) lblQuizAnsFile.setText(f.getName()); }
            else { selectedExamAnsFile = f; if(lblExamAnsFile != null) lblExamAnsFile.setText(f.getName()); }
        }
    }

    public void configureEmbedded(Runnable onBack) {
        backHandler = onBack;
        if (manageExamsToolbar != null) {
            manageExamsToolbar.setVisible(false);
            manageExamsToolbar.setManaged(false);
        }
    }

    @FXML private void handleBack(ActionEvent event) {
        if (backHandler != null) {
            backHandler.run();
            return;
        }

        try {
            Scene currentScene = ((Node) event.getSource()).getScene();
            Stage stage = (Stage) currentScene.getWindow();
            boolean isMaximized = stage.isMaximized();

            Parent root = FXMLLoader.load(getClass().getResource("FXMLDocument.fxml"));
            currentScene.setRoot(root);
            
            if (isMaximized) {
                stage.setMaximized(true);
            }
            
            stage.show();
            
        } catch (IOException e) { 
            e.printStackTrace(); 
        }
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    @FXML
    private void handleConfigureNetwork(ActionEvent event) {
        NetworkConfig.showConfigDialog();
    }

    private String getLocalWiFiIP() {
        return NetworkConfig.getLocalWiFiIP();
    }}
