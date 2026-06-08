package admindashboard;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class StudentsscoresController implements Initializable {

    @FXML private ComboBox<String> cbSection, cbExamName;
    @FXML private TableView<ScoreData> scoreTable;
    @FXML private TableColumn<ScoreData, String> colID, colName, colScore;
    @FXML private Label lblSelectedStudent;
    @FXML private TextField txtScoreInput;

    private final String DB_URL = "jdbc:mysql://localhost:3306/admindashboard_db";
    private final String DB_USER = "root";
    private final String DB_PASS = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colID.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        scoreTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loadInitialData();
    }

    private void loadInitialData() {
        cbSection.getItems().clear();
        cbExamName.getItems().clear();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rsSec = stmt.executeQuery("SELECT DISTINCT class_name FROM students ORDER BY class_name ASC")) {
                while (rsSec.next()) {
                    cbSection.getItems().add(rsSec.getString("class_name"));
                }
            }

            try (ResultSet rsEx = stmt.executeQuery("SELECT title FROM exams ORDER BY title ASC")) {
                while (rsEx.next()) {
                    cbExamName.getItems().add(rsEx.getString("title"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to load sections and exams: " + e.getMessage());
        }
    }

    @FXML
    private void loadStudents(ActionEvent event) {
        String section = cbSection.getValue();
        if (section == null) {
            return;
        }

        ObservableList<ScoreData> data = FXCollections.observableArrayList();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement("SELECT student_id_number, full_name FROM students WHERE class_name = ? ORDER BY full_name ASC, student_id_number ASC")) {
            pstmt.setString(1, section);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    data.add(new ScoreData(rs.getString("student_id_number"), rs.getString("full_name"), "Not Taken"));
                }
            }
            scoreTable.setItems(data);
            clearSelectionState();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to load students: " + e.getMessage());
        }
    }

    @FXML
    private void selectStudent() {
        ScoreData selected = scoreTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lblSelectedStudent.setText(selected.getName());
            txtScoreInput.setText("Not Taken".equals(selected.getScore()) ? "" : selected.getScore());
        }
    }

    @FXML
    private void saveScore(ActionEvent event) {
        ScoreData selected = scoreTable.getSelectionModel().getSelectedItem();
        String examName = cbExamName.getValue();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a student first.");
            return;
        }
        if (examName == null || examName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Exam", "Please select an exam first.");
            return;
        }

        String scoreText = txtScoreInput.getText() == null ? "" : txtScoreInput.getText().trim();
        if (scoreText.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Score", "Enter a score before saving.");
            return;
        }

        int scoreValue;
        try {
            scoreValue = Integer.parseInt(scoreText);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Score", "Score must be a whole number from 0 to 100.");
            return;
        }

        if (scoreValue < 0 || scoreValue > 100) {
            showAlert(Alert.AlertType.ERROR, "Invalid Score", "Score must be between 0 and 100.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            Integer studentId = getStudentDbId(conn, selected.getId());
            if (studentId == null) {
                showAlert(Alert.AlertType.ERROR, "Save Failed", "The selected student was not found.");
                return;
            }

            if (scoreExists(conn, studentId, examName)) {
                try (PreparedStatement pstmt = conn.prepareStatement("UPDATE exam_scores SET score = ? WHERE student_id = ? AND exam_name = ?")) {
                    pstmt.setInt(1, scoreValue);
                    pstmt.setInt(2, studentId);
                    pstmt.setString(3, examName);
                    pstmt.executeUpdate();
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO exam_scores (student_id, exam_name, score) VALUES (?, ?, ?)")) {
                    pstmt.setInt(1, studentId);
                    pstmt.setString(2, examName);
                    pstmt.setInt(3, scoreValue);
                    pstmt.executeUpdate();
                }
            }

            selected.setScore(String.valueOf(scoreValue));
            scoreTable.refresh();
            showAlert(Alert.AlertType.INFORMATION, "Score Saved", "The score for " + selected.getName() + " was saved.");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save score: " + e.getMessage());
        }
    }

    @FXML
    private void loadScores(ActionEvent event) {
        String section = cbSection.getValue();
        String examName = cbExamName.getValue();

        if (section == null || examName == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Selection", "Please select both a section and an exam to view scores.");
            return;
        }

        ObservableList<ScoreData> data = FXCollections.observableArrayList();
        String sql = "SELECT s.student_id_number, s.full_name, IFNULL(es.score, 'Not Taken') AS actual_score " +
                     "FROM students s " +
                     "LEFT JOIN exam_scores es ON s.id = es.student_id AND es.exam_name = ? " +
                     "WHERE s.class_name = ? " +
                     "ORDER BY s.full_name ASC, s.student_id_number ASC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, examName);
            pstmt.setString(2, section);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    data.add(new ScoreData(rs.getString("student_id_number"), rs.getString("full_name"), rs.getString("actual_score")));
                }
            }

            scoreTable.setItems(data);
            clearSelectionState();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to load scores: " + e.getMessage());
        }
    }

    @FXML
    private void deleteScore(ActionEvent event) {
        ScoreData selected = scoreTable.getSelectionModel().getSelectedItem();
        String examName = cbExamName.getValue();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a score to delete.");
            return;
        }
        if (examName == null || examName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Exam", "Please select the exam for the score you want to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Score");
        confirm.setHeaderText("Delete score for " + selected.getName() + "?");
        confirm.setContentText("This will remove the saved score and reset the selected student for this exam.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            Integer studentId = getStudentDbId(conn, selected.getId());
            if (studentId == null) {
                showAlert(Alert.AlertType.ERROR, "Delete Failed", "The selected student was not found.");
                return;
            }

            deleteAnswersForExam(conn, studentId, examName);

            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM exam_scores WHERE student_id = ? AND exam_name = ?")) {
                pstmt.setInt(1, studentId);
                pstmt.setString(2, examName);
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM live_sessions WHERE student_id_number = ? AND exam_title = ?")) {
                pstmt.setString(1, selected.getId());
                pstmt.setString(2, examName);
                pstmt.executeUpdate();
            }

            selected.setScore("Not Taken");
            scoreTable.refresh();
            clearSelectionState();
            showAlert(Alert.AlertType.INFORMATION, "Score Deleted", "The score for " + selected.getName() + " was deleted.");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to delete score: " + e.getMessage());
        }
    }

    @FXML
    private void clearScores(ActionEvent event) {
        String section = cbSection.getValue();
        String examName = cbExamName.getValue();

        if (section == null || section.trim().isEmpty() || examName == null || examName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Selection", "Select both a section and an exam before clearing scores.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear Scores");
        confirm.setHeaderText("Clear all scores for " + section + " / " + examName + "?");
        confirm.setContentText("This will delete all saved scores for the selected section and exam.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            deleteAnswersForSectionExam(conn, section, examName);

            try (PreparedStatement pstmt = conn.prepareStatement("DELETE es FROM exam_scores es JOIN students s ON es.student_id = s.id WHERE s.class_name = ? AND es.exam_name = ?")) {
                pstmt.setString(1, section);
                pstmt.setString(2, examName);
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM live_sessions WHERE section = ? AND exam_title = ?")) {
                pstmt.setString(1, section);
                pstmt.setString(2, examName);
                pstmt.executeUpdate();
            }

            loadScores(event);
            clearSelectionState();
            showAlert(Alert.AlertType.INFORMATION, "Scores Cleared", "Scores for the selected section and exam were cleared.");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to clear scores: " + e.getMessage());
        }
    }

    @FXML
    private void goBack(ActionEvent event) {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Integer getStudentDbId(Connection conn, String studentCode) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM students WHERE student_id_number = ? LIMIT 1")) {
            pstmt.setString(1, studentCode);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }

    private boolean scoreExists(Connection conn, int studentId, String examName) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM exam_scores WHERE student_id = ? AND exam_name = ? LIMIT 1")) {
            pstmt.setInt(1, studentId);
            pstmt.setString(2, examName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void deleteAnswersForExam(Connection conn, int studentId, String examName) {
        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM student_answers WHERE student_id = ? AND exam_name = ?")) {
            pstmt.setInt(1, studentId);
            pstmt.setString(2, examName);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void deleteAnswersForSectionExam(Connection conn, String section, String examName) {
        try (PreparedStatement pstmt = conn.prepareStatement("DELETE sa FROM student_answers sa JOIN students s ON sa.student_id = s.id WHERE s.class_name = ? AND sa.exam_name = ?")) {
            pstmt.setString(1, section);
            pstmt.setString(2, examName);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void clearSelectionState() {
        scoreTable.getSelectionModel().clearSelection();
        lblSelectedStudent.setText("No student selected");
        txtScoreInput.clear();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static class ScoreData {
        private final SimpleStringProperty id;
        private final SimpleStringProperty name;
        private final SimpleStringProperty score;

        public ScoreData(String id, String name, String score) {
            this.id = new SimpleStringProperty(id);
            this.name = new SimpleStringProperty(name);
            this.score = new SimpleStringProperty(score);
        }

        public String getId() { return id.get(); }
        public String getName() { return name.get(); }
        public String getScore() { return score.get(); }
        public void setScore(String value) { score.set(value); }
    }
}
