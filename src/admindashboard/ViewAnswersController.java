package admindashboard;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.util.Callback;

public class ViewAnswersController {

    @FXML private Label lblName;
    @FXML private Label lblSection;
    @FXML private ListView<String> answerListView;

    private final String DB_URL = "jdbc:mysql://localhost:3306/admindashboard_db";
    private final String DB_USER = "root";
    private final String DB_PASS = "";

    public void initData(String studentName, String section) {
        lblName.setText(studentName);
        lblSection.setText(section);

        answerListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> param) {
                return new ListCell<String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                            setStyle("");
                        } else {
                            setText(item);

                            if (item.contains("Correct")) {
                                setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                            } else if (item.contains("Wrong")) {
                                setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                            } else {
                                setStyle("-fx-text-fill: black;");
                            }
                        }
                    }
                };
            }
        });

        fetchStudentAnswers(studentName);
    }

    private void fetchStudentAnswers(String studentName) {
        answerListView.getItems().clear();

        String sql = "SELECT sa.question_number, sa.student_answer, sa.correct_answer, sa.is_correct " +
                     "FROM student_answers sa " +
                     "JOIN students s ON sa.student_id = s.id " +
                     "WHERE s.full_name = ? " +
                     "ORDER BY sa.question_number ASC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, studentName);
            ResultSet rs = pstmt.executeQuery();

            boolean hasData = false;

            while (rs.next()) {
                hasData = true;
                int qNum = rs.getInt("question_number");
                String studentAns = rs.getString("student_answer");
                String correctAns = rs.getString("correct_answer");
                boolean isCorrect = rs.getBoolean("is_correct");

                String status = isCorrect ? "Correct" : "Wrong";
                String rowText = String.format("Question %d: %s  (Correct Answer: %s) -> %s",
                        qNum, studentAns, correctAns, status);

                answerListView.getItems().add(rowText);
            }

            if (!hasData) {
                answerListView.getItems().add("No answers found in the database for this student.");
                answerListView.getItems().add("Check PHPMyAdmin to verify if the web app is saving answers properly.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            answerListView.getItems().add("Database Error: Could not load answers.");
            answerListView.getItems().add("Details: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}
