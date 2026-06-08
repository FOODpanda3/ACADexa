package admindashboard;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class ManagestudentController implements Initializable {

    @FXML private TableView<DashboardController.Student> studentTable;
    @FXML private TableColumn<DashboardController.Student, String> colID, colName, colSection, colPin;
    @FXML private ComboBox<String> cbSectionFilter;
    @FXML private TextField txtStudentName;
    @FXML private TextField txtStudentSection;
    @FXML private TextField txtSearchStudent;

    private final ObservableList<DashboardController.Student> allStudents = FXCollections.observableArrayList();
    private FilteredList<DashboardController.Student> filteredStudents;

    private final String DB_URL = "jdbc:mysql://localhost:3306/admindashboard_db";
    private final String DB_USER = "root";
    private final String DB_PASS = "";
    private final Random random = new Random();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        studentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colID.setCellValueFactory(new PropertyValueFactory<>("studentCode"));
        colName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colSection.setCellValueFactory(new PropertyValueFactory<>("section"));
        colPin.setCellValueFactory(new PropertyValueFactory<>("pin"));

        loadDataFromDatabase();
        populateFilter();
        filteredStudents = new FilteredList<>(allStudents, s -> true);
        studentTable.setItems(filteredStudents);
    }

    private void loadDataFromDatabase() {
        allStudents.clear();
        String sql = "SELECT student_id_number, class_name, full_name, pin FROM students ORDER BY class_name ASC, full_name ASC, student_id_number ASC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                allStudents.add(new DashboardController.Student(
                        rs.getString("student_id_number"),
                        rs.getString("class_name"),
                        rs.getString("full_name"),
                        rs.getString("pin")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to load students: " + e.getMessage());
        }
    }

    private void populateFilter() {
        cbSectionFilter.getItems().clear();
        cbSectionFilter.getItems().add("All Sections");
        List<String> uniqueSections = allStudents.stream()
                .map(DashboardController.Student::getSection)
                .filter(section -> section != null && !section.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        cbSectionFilter.getItems().addAll(uniqueSections);
        cbSectionFilter.getSelectionModel().select("All Sections");
    }

    @FXML
    private void handleAddStudent(ActionEvent event) {
        String fullName = txtStudentName.getText() == null ? "" : txtStudentName.getText().trim();
        String section = txtStudentSection.getText() == null ? "" : txtStudentSection.getText().trim();

        if (fullName.isEmpty() || section.isEmpty()) {
            showError("Missing Information", "Enter both the student's full name and section.");
            return;
        }

        String sql = "INSERT INTO students (student_id_number, class_name, full_name, pin) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String studentId = generateStudentId(conn, section);
            String pin = generatePinValue();

            pstmt.setString(1, studentId);
            pstmt.setString(2, section);
            pstmt.setString(3, fullName);
            pstmt.setString(4, pin);
            pstmt.executeUpdate();

            refreshStudentView();
            clearStudentInputs();
            showInfo("Student Added", fullName + " was added successfully.\nStudent ID: " + studentId + "\nPIN: " + pin);
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to add student: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteStudent(ActionEvent event) {
        DashboardController.Student selectedStudent = studentTable.getSelectionModel().getSelectedItem();
        if (selectedStudent == null) {
            showError("No Selection", "Please select a student to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Student");
        confirm.setHeaderText("Delete " + selectedStudent.getFullName() + "?");
        confirm.setContentText("This will delete the student record and remove linked scores, answers, and live session data.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);
            Integer studentDbId = getStudentDbId(conn, selectedStudent.getStudentCode());
            if (studentDbId == null) {
                conn.rollback();
                showError("Delete Failed", "The selected student could not be found.");
                return;
            }

            deleteStudentDependencies(conn, studentDbId, selectedStudent.getStudentCode(), selectedStudent.getFullName());
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM students WHERE student_id_number = ?")) {
                pstmt.setString(1, selectedStudent.getStudentCode());
                pstmt.executeUpdate();
            }

            conn.commit();
            refreshStudentView();
            showInfo("Student Deleted", selectedStudent.getFullName() + " was deleted successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to delete student: " + e.getMessage());
        }
    }

    @FXML
    private void handleClearStudents(ActionEvent event) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear Students");
        confirm.setHeaderText("Delete all students?");
        confirm.setContentText("This will remove all students and clear linked scores, answers, and live sessions.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            stmt.executeUpdate("DELETE FROM student_answers");
            stmt.executeUpdate("DELETE FROM exam_scores");
            stmt.executeUpdate("DELETE FROM live_sessions");
            stmt.executeUpdate("DELETE FROM students");
            conn.commit();

            refreshStudentView();
            clearStudentInputs();
            showInfo("Students Cleared", "All student records were deleted.");
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to clear students: " + e.getMessage());
        }
    }

    @FXML
    private void handleGeneratePin(ActionEvent event) {
        DashboardController.Student selectedStudent = studentTable.getSelectionModel().getSelectedItem();
        if (selectedStudent == null) {
            showError("No Selection", "Please select a student first.");
            return;
        }

        String sql = "UPDATE students SET pin = ? WHERE student_id_number = ?";
        String newPin = generatePinValue();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newPin);
            pstmt.setString(2, selectedStudent.getStudentCode());
            pstmt.executeUpdate();

            refreshStudentView();
            showInfo("PIN Created", "New PIN for " + selectedStudent.getFullName() + ": " + newPin);
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to generate PIN: " + e.getMessage());
        }
    }

    @FXML
    private void handleGenerateMissingPins(ActionEvent event) {
        String sql = "SELECT student_id_number FROM students WHERE pin IS NULL OR TRIM(pin) = '' ORDER BY student_id_number ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int updated = 0;
            while (rs.next()) {
                updateStudentPin(conn, rs.getString("student_id_number"), generatePinValue());
                updated++;
            }

            refreshStudentView();
            showInfo("PIN Batch Complete", updated == 0 ? "All students already have PINs." : updated + " student PIN(s) created.");
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to generate missing PINs: " + e.getMessage());
        }
    }

    @FXML
    private void handleFilter(Event event) {
        String selected = cbSectionFilter.getValue();
        String search = txtSearchStudent != null && txtSearchStudent.getText() != null
                ? txtSearchStudent.getText().trim().toLowerCase()
                : "";

        filteredStudents.setPredicate(student -> {
            boolean matchesSection = selected == null || selected.equals("All Sections") || student.getSection().equals(selected);
            if (!matchesSection) {
                return false;
            }

            if (search.isEmpty()) {
                return true;
            }

            String code = student.getStudentCode() == null ? "" : student.getStudentCode().toLowerCase();
            String name = student.getFullName() == null ? "" : student.getFullName().toLowerCase();
            String section = student.getSection() == null ? "" : student.getSection().toLowerCase();
            return code.contains(search) || name.contains(search) || section.contains(search);
        });
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
            showError("Navigation Error", "Make sure FXMLDocument.fxml is the exact name of the file.");
        }
    }

    private String generatePinValue() {
        return String.format("%04d", random.nextInt(10000));
    }

    private String generateStudentId(Connection conn, String section) throws SQLException {
        int currentYear = Year.now().getValue();
        int nextIdSequence = 1;
        String classPrefix = section.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (classPrefix.isEmpty()) {
            classPrefix = "SEC";
        }

        String sql = "SELECT student_id_number FROM students WHERE student_id_number LIKE ? AND class_name = ? ORDER BY student_id_number DESC LIMIT 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, currentYear + "-" + classPrefix + "-%");
            pstmt.setString(2, section);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String lastId = rs.getString("student_id_number");
                    String[] parts = lastId.split("-");
                    if (parts.length > 0) {
                        try {
                            nextIdSequence = Integer.parseInt(parts[parts.length - 1]) + 1;
                        } catch (NumberFormatException ignored) {
                            nextIdSequence = 1;
                        }
                    }
                }
            }
        }
        return String.format("%d-%s-%03d", currentYear, classPrefix, nextIdSequence);
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

    private void deleteStudentDependencies(Connection conn, int studentDbId, String studentCode, String studentName) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM student_answers WHERE student_id = ?")) {
            pstmt.setInt(1, studentDbId);
            pstmt.executeUpdate();
        }
        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM exam_scores WHERE student_id = ?")) {
            pstmt.setInt(1, studentDbId);
            pstmt.executeUpdate();
        }
        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM live_sessions WHERE student_id_number = ? OR student_name = ?")) {
            pstmt.setString(1, studentCode);
            pstmt.setString(2, studentName);
            pstmt.executeUpdate();
        }
    }

    private void updateStudentPin(Connection conn, String studentCode, String pin) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement("UPDATE students SET pin = ? WHERE student_id_number = ?")) {
            pstmt.setString(1, pin);
            pstmt.setString(2, studentCode);
            pstmt.executeUpdate();
        }
    }

    private void refreshStudentView() {
        loadDataFromDatabase();
        populateFilter();
        handleFilter(null);
    }

    private void clearStudentInputs() {
        txtStudentName.clear();
        txtStudentSection.clear();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
