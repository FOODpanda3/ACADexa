package admindashboard;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.*;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;

public class DashboardController implements Initializable {

    private final String DB_URL = "jdbc:mysql://localhost:3306/admindashboard_db";
    private final String DB_USER = "root";
    private final String DB_PASS = "";
    
    private final String UPLOAD_DIR = "C:/xampp/htdocs/quiz_system/uploads/";

    @FXML private Label headerLabel, statusLabel, fileNameLabel, fileNameLabelKey;
    @FXML private VBox paneHome, paneManageClasses, paneUpload, paneViewScores;
    @FXML private VBox paneLiveMonitor;
    @FXML private ComboBox<String> classSelector;
    @FXML private ComboBox<String> cbScoreSectionFilter;
    @FXML private ComboBox<String> cbLiveExamFilter;
    @FXML private TextField txtLiveSearch;
    
    @FXML private ImageView qrView;
    
    @FXML private TextField txtClassName, txtExamTitle, txtTimeLimit;
    @FXML private TextArea txtAnswerKeyEditor;

    @FXML private TableView<Student> classTable;
    @FXML private TableColumn<Student, String> colStudentCode;
    @FXML private TableColumn<Student, String> colSection;
    @FXML private TableColumn<Student, String> colStudentName;
    @FXML private TableColumn<Student, String> colPin; 

    @FXML private TableView<Score> scoreTable;
    @FXML private TableColumn<Score, String> colScoreStudentName;
    @FXML private TableColumn<Score, String> colScoreSection;
    @FXML private TableColumn<Score, String> colScoreResult;

    @FXML private TableView<ExamItem> examTable;
    @FXML private TableColumn<ExamItem, String> colExamId;
    @FXML private TableColumn<ExamItem, String> colExamTitle;
    @FXML private TableColumn<ExamItem, String> colExamClass;
    @FXML private TableColumn<ExamItem, String> colExamAnswerKey;
    @FXML private TableColumn<ExamItem, String> colExamStatus;

    @FXML private TableView<LiveSession> liveTable;
    @FXML private TableColumn<LiveSession, String> colLiveName, colLiveSection, colLiveStatus, colLiveProgress, colLiveLastPing;
    @FXML private Button btnDashboard, btnStudents, btnExams, btnLiveMonitor, btnScores;
    
    private Timeline liveMonitorTimeline; 
    private File selectedFile;
    private File selectedAnswerKeyFile;
    private ExamItem selectedExam = null;

    @FXML private ComboBox<String> cbStudentSortDashboard;
    @FXML private TextField txtSearchStudentDashboard;
    @FXML private Label lblDashboardSelectedStudentInfo;

    private ObservableList<Student> studentList = FXCollections.observableArrayList();
    private FilteredList<Student> filteredDashboardStudents;
    private SortedList<Student> sortedDashboardStudents;

    private ObservableList<ExamItem> examList = FXCollections.observableArrayList();
    private ObservableList<Score> scoreList = FXCollections.observableArrayList();
    private final ObservableList<LiveSession> liveSessionList = FXCollections.observableArrayList();
    private FilteredList<LiveSession> filteredLiveSessionList;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (classTable != null) classTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (examTable != null) examTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (scoreTable != null) scoreTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (liveTable != null) liveTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        filteredLiveSessionList = new FilteredList<>(liveSessionList, item -> true);
        if (liveTable != null) liveTable.setItems(filteredLiveSessionList);

        populateDashboardSortDropdown();
        filteredDashboardStudents = new FilteredList<>(studentList, s -> true);
        sortedDashboardStudents = new SortedList<>(filteredDashboardStudents);
        sortedDashboardStudents.comparatorProperty().bind(classTable.comparatorProperty());
        if (classTable != null) classTable.setItems(sortedDashboardStudents);

        if (classTable != null) {
            classTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
                if (newSel != null) {
                    String pinText = (newSel.getPin() != null && !newSel.getPin().trim().isEmpty()) ? newSel.getPin() : "[No PIN]";
                    if (lblDashboardSelectedStudentInfo != null) {
                        lblDashboardSelectedStudentInfo.setText(String.format("Selected Student: %s   |   Student Code: %s   |   PIN: %s", 
                                newSel.getFullName(), newSel.getStudentCode(), pinText));
                    }
                } else {
                    if (lblDashboardSelectedStudentInfo != null) {
                        lblDashboardSelectedStudentInfo.setText("Select a student from the table to view and copy their Code & PIN.");
                    }
                }
            });
        }

        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) dir.mkdirs();

        if (colStudentCode != null) colStudentCode.setCellValueFactory(new PropertyValueFactory<>("studentCode"));
        if (colSection != null) colSection.setCellValueFactory(new PropertyValueFactory<>("section"));
        if (colStudentName != null) colStudentName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        if (colPin != null) colPin.setCellValueFactory(new PropertyValueFactory<>("pin"));

        if (colExamId != null) colExamId.setCellValueFactory(new PropertyValueFactory<>("id"));
        if (colExamTitle != null) colExamTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        if (colExamClass != null) colExamClass.setCellValueFactory(new PropertyValueFactory<>("className"));
        if (colExamAnswerKey != null) colExamAnswerKey.setCellValueFactory(new PropertyValueFactory<>("answerKey"));
        if (colExamStatus != null) colExamStatus.setCellValueFactory(new PropertyValueFactory<>("status")); 

        if (examTable != null) {
            examTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    selectedExam = newSelection;
                    if (fileNameLabel != null) fileNameLabel.setText(newSelection.getTitle());
                    if (txtExamTitle != null) txtExamTitle.setText(newSelection.getTitle());
                    if (txtTimeLimit != null) txtTimeLimit.setText(newSelection.getTimeLimit());
                    if (fileNameLabelKey != null) fileNameLabelKey.setText("Existing Key Saved (Upload new to replace)");
                    if (txtAnswerKeyEditor != null) txtAnswerKeyEditor.setText(newSelection.getAnswerKey());
                    if (classSelector != null) classSelector.setValue(newSelection.getClassName());
                    
                    selectedFile = null;
                    selectedAnswerKeyFile = null;
                    if (statusLabel != null) statusLabel.setText("Status: Editing Exam ID " + newSelection.getId());

                    int qCount = 0;
                    if (newSelection.getAnswerKey() != null) qCount = newSelection.getAnswerKey().length();
                    generateAndDisplayQRCode(newSelection.getTitle(), newSelection.getClassName(), qCount);
                }
            });
        }

        if (colScoreStudentName != null) colScoreStudentName.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        if (colScoreSection != null) colScoreSection.setCellValueFactory(new PropertyValueFactory<>("section"));
        if (colScoreResult != null) colScoreResult.setCellValueFactory(new PropertyValueFactory<>("score"));

        if (colLiveName != null) colLiveName.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        if (colLiveSection != null) colLiveSection.setCellValueFactory(new PropertyValueFactory<>("section"));
        if (colLiveStatus != null) colLiveStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        if (colLiveProgress != null) colLiveProgress.setCellValueFactory(new PropertyValueFactory<>("progress"));
        if (colLiveLastPing != null) colLiveLastPing.setCellValueFactory(new PropertyValueFactory<>("lastPing"));
        if (colLiveStatus != null) {
            colLiveStatus.setCellFactory(column -> new TableCell<LiveSession, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                        return;
                    }

                    setText(item);
                    String lower = item.toLowerCase();
                    if (lower.contains("blocked")) {
                        setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                    } else if (lower.contains("active")) {
                        setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                    } else if (lower.contains("tab left") || lower.contains("blur")) {
                        setStyle("-fx-text-fill: #ef6c00; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #1d2b53; -fx-font-weight: bold;");
                    }
                }
            });
        }

        if (cbLiveExamFilter != null) {
            cbLiveExamFilter.setOnAction(e -> loadLiveSessionsFromDatabase());
        }

        liveMonitorTimeline = new Timeline(new KeyFrame(Duration.seconds(3), ev -> {
            if (paneLiveMonitor != null && paneLiveMonitor.isVisible()) {
                loadLiveSessionsFromDatabase();
            }
        }));
        liveMonitorTimeline.setCycleCount(Timeline.INDEFINITE);
        liveMonitorTimeline.play();

        loadSectionsIntoComboBox();
        loadStudentsFromDatabase();
        showHome();
    }

    private void hideAllPanes() {
        setPaneVisible(paneHome, false);
        setPaneVisible(paneManageClasses, false);
        setPaneVisible(paneUpload, false);
        setPaneVisible(paneViewScores, false);
        setPaneVisible(paneLiveMonitor, false);
    }

    private void setPaneVisible(VBox pane, boolean visible) {
        if (pane != null) {
            pane.setVisible(visible);
            pane.setManaged(visible);
        }
    }

    private void setActiveSidebarButton(Button activeButton) {
        Button[] buttons = {btnDashboard, btnStudents, btnExams, btnLiveMonitor, btnScores};
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

    @FXML private void handleHome(ActionEvent e) {
        switchScene(e, "FXMLDocument.fxml");
    }

    private void showHome() {
        hideAllPanes();
        setPaneVisible(paneHome, true);
        if (headerLabel != null) headerLabel.setText("Welcome to the Dashboard!");
        if (statusLabel != null) statusLabel.setText("Status: Ready");
        setActiveSidebarButton(btnDashboard);
    }

    @FXML private void handleUploadAction(ActionEvent e) {
        showManageExams();
    }

    public void showManageExams() {
        hideAllPanes();
        setPaneVisible(paneUpload, true);
        if (headerLabel != null) headerLabel.setText("Upload Quiz / Manage Exams");
        setActiveSidebarButton(btnExams);
        loadSectionsIntoComboBox();
        loadExamsFromDatabase();
    }

    @FXML private void handleManageClasses(ActionEvent e) {
        hideAllPanes();
        setPaneVisible(paneManageClasses, true);
        if (headerLabel != null) headerLabel.setText("Manage Classes");
        setActiveSidebarButton(btnStudents);
        loadStudentsFromDatabase();
    }

    private void populateDashboardSortDropdown() {
        if (cbStudentSortDashboard != null) {
            cbStudentSortDashboard.getItems().clear();
            cbStudentSortDashboard.getItems().addAll(
                "Name (A - Z)",
                "Name (Z - A)",
                "Section / Class",
                "Student ID Code",
                "Missing PIN First"
            );
            cbStudentSortDashboard.getSelectionModel().select("Name (A - Z)");
        }
    }

    @FXML
    private void handleStudentSortDashboard(ActionEvent event) {
        applyDashboardStudentSorting();
    }

    private void applyDashboardStudentSorting() {
        String sortChoice = cbStudentSortDashboard != null && cbStudentSortDashboard.getValue() != null ? cbStudentSortDashboard.getValue() : "Name (A - Z)";
        java.util.Comparator<Student> comp;

        switch (sortChoice) {
            case "Name (Z - A)":
                comp = (a, b) -> b.getFullName().compareToIgnoreCase(a.getFullName());
                break;
            case "Section / Class":
                comp = (a, b) -> {
                    int res = a.getSection().compareToIgnoreCase(b.getSection());
                    return res != 0 ? res : a.getFullName().compareToIgnoreCase(b.getFullName());
                };
                break;
            case "Student ID Code":
                comp = (a, b) -> a.getStudentCode().compareToIgnoreCase(b.getStudentCode());
                break;
            case "Missing PIN First":
                comp = (a, b) -> {
                    boolean aEmpty = a.getPin() == null || a.getPin().trim().isEmpty();
                    boolean bEmpty = b.getPin() == null || b.getPin().trim().isEmpty();
                    if (aEmpty != bEmpty) return aEmpty ? -1 : 1;
                    return a.getFullName().compareToIgnoreCase(b.getFullName());
                };
                break;
            case "Name (A - Z)":
            default:
                comp = (a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName());
                break;
        }

        if (sortedDashboardStudents != null) {
            sortedDashboardStudents.setComparator(comp);
        }
    }

    @FXML
    private void handleStudentFilterDashboard(Event event) {
        String search = txtSearchStudentDashboard != null && txtSearchStudentDashboard.getText() != null
                ? txtSearchStudentDashboard.getText().trim().toLowerCase()
                : "";

        if (filteredDashboardStudents != null) {
            filteredDashboardStudents.setPredicate(student -> {
                if (search.isEmpty()) return true;
                String code = student.getStudentCode() == null ? "" : student.getStudentCode().toLowerCase();
                String name = student.getFullName() == null ? "" : student.getFullName().toLowerCase();
                String section = student.getSection() == null ? "" : student.getSection().toLowerCase();
                String pin = student.getPin() == null ? "" : student.getPin().toLowerCase();
                return code.contains(search) || name.contains(search) || section.contains(search) || pin.contains(search);
            });
        }
        applyDashboardStudentSorting();
    }

    @FXML
    private void handleDashboardCopyCode(ActionEvent event) {
        Student selected = classTable != null ? classTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showAlert(AlertType.WARNING, "No Selection", "Please select a student from the table first.");
            return;
        }

        String pinStr = (selected.getPin() != null && !selected.getPin().trim().isEmpty()) ? selected.getPin() : "[No PIN]";
        String copyText = String.format("Student: %s | Student Code: %s | PIN: %s", selected.getFullName(), selected.getStudentCode(), pinStr);

        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(copyText);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);

        showAlert(AlertType.INFORMATION, "Copied!", "Copied to Clipboard:\n" + copyText);
    }

    @FXML private void handleViewScores(ActionEvent e) {
        hideAllPanes();
        setPaneVisible(paneViewScores, true);
        if (headerLabel != null) headerLabel.setText("Live Student Results");
        setActiveSidebarButton(btnScores);
        
        if (cbScoreSectionFilter != null && cbScoreSectionFilter.getItems().isEmpty()) {
            cbScoreSectionFilter.getItems().add("All Sections");
            String sql = "SELECT DISTINCT class_name FROM students ORDER BY class_name ASC";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    cbScoreSectionFilter.getItems().add(rs.getString("class_name"));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
            
            cbScoreSectionFilter.setValue("All Sections");
            cbScoreSectionFilter.setOnAction(event -> loadScoresFromDatabase());
        }
        
        loadScoresFromDatabase();
    }

    @FXML private void handleLiveMonitor(ActionEvent e) {
        hideAllPanes();
        setPaneVisible(paneLiveMonitor, true);
        if (headerLabel != null) headerLabel.setText("📡 Live Exam Monitor");
        setActiveSidebarButton(btnLiveMonitor);
        loadExamsIntoLiveFilter();
    }

    @FXML
    private void handleLiveSearch(Event e) {
        applyLiveSearchFilter();
    }

    @FXML private void handleRefreshScores(ActionEvent e) {
        loadScoresFromDatabase();
        if (statusLabel != null) statusLabel.setText("Status: Scores Refreshed!");
    }

    @FXML private void handleLogout(ActionEvent e) { switchScene(e, "login.fxml"); }

    private void loadSectionsIntoComboBox() {
        if (classSelector == null) return;
        classSelector.getItems().clear();
        String sql = "SELECT DISTINCT class_name FROM students ORDER BY class_name ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                classSelector.getItems().add(rs.getString("class_name"));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadExamsIntoLiveFilter() {
        if (cbLiveExamFilter == null) return;
        String current = cbLiveExamFilter.getValue();
        cbLiveExamFilter.getItems().clear();
        String sql = "SELECT title FROM exams ORDER BY title ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                cbLiveExamFilter.getItems().add(rs.getString("title"));
            }
            if (current != null && cbLiveExamFilter.getItems().contains(current)) {
                cbLiveExamFilter.setValue(current);
            } else if (!cbLiveExamFilter.getItems().isEmpty()) {
                cbLiveExamFilter.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {}
    }

    private final java.util.Set<String> knownBlockedStudents = new java.util.HashSet<>();

    private void loadLiveSessionsFromDatabase() {
        if (liveTable == null || cbLiveExamFilter == null) return;
        String selectedExam = cbLiveExamFilter.getValue();
        if (selectedExam == null) return;

        liveSessionList.clear();
        String sql = "SELECT student_id_number, student_name, section, status, progress, last_ping FROM live_sessions WHERE exam_title = ? ORDER BY section ASC, student_name ASC, student_id_number ASC";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, selectedExam);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String sid = rs.getString("student_id_number");
                    String sname = rs.getString("student_name");
                    String sec = rs.getString("section");
                    String stat = rs.getString("status");
                    String prog = rs.getString("progress");
                    String lastPing = rs.getString("last_ping");

                    liveSessionList.add(new LiveSession(sid, sname, sec, stat, prog, lastPing));

                    // 🔊 AUDIO CHIME ALERT: Trigger system sound when a student gets blocked or tab-switches!
                    if (stat != null && (stat.toLowerCase().contains("blocked") || stat.toLowerCase().contains("tab left") || stat.toLowerCase().contains("terminated"))) {
                        String blockKey = selectedExam + "_" + sid + "_" + stat;
                        if (!knownBlockedStudents.contains(blockKey)) {
                            knownBlockedStudents.add(blockKey);
                            try {
                                java.awt.Toolkit.getDefaultToolkit().beep();
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            applyLiveSearchFilter();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleExtendTime(ActionEvent event) {
        String selectedExam = cbLiveExamFilter != null ? cbLiveExamFilter.getValue() : null;
        if (selectedExam == null || selectedExam.trim().isEmpty()) {
            if (statusLabel != null) statusLabel.setText("Status: Select an exam in Live Monitor first.");
            return;
        }

        String sql = "UPDATE exams SET time_limit = time_limit + 5 WHERE title = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, selectedExam);
            int updated = pstmt.executeUpdate();

            if (updated > 0) {
                if (statusLabel != null) {
                    statusLabel.setText("Status: Added +5 minutes to " + selectedExam + " time limit!");
                }
                showAlert(AlertType.INFORMATION, "Time Extended", "Granted +5 extra minutes to exam: " + selectedExam);
                loadExamsFromDatabase();
            } else {
                if (statusLabel != null) statusLabel.setText("Status: Could not find exam to extend time.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (statusLabel != null) statusLabel.setText("Status: Error extending time limit.");
        }
    }

    @FXML private void handleClearLiveSessions(ActionEvent event) {
        String sql = "TRUNCATE TABLE live_sessions";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            loadLiveSessionsFromDatabase();
            if (statusLabel != null) statusLabel.setText("Status: Offline students cleared!");
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleBlockLiveStudent(ActionEvent event) {
        updateLiveStudentStatus("Teacher Blocked", "Blocked by teacher");
    }

    @FXML
    private void handleUnblockLiveStudent(ActionEvent event) {
        updateLiveStudentStatus("Teacher Active", "Resume allowed");
    }

    private void updateLiveStudentStatus(String status, String progress) {
        LiveSession selectedLive = liveTable != null ? liveTable.getSelectionModel().getSelectedItem() : null;
        String selectedExam = cbLiveExamFilter != null ? cbLiveExamFilter.getValue() : null;

        if (selectedLive == null) {
            if (statusLabel != null) statusLabel.setText("Status: Select a student in Live Monitor first.");
            return;
        }
        if (selectedExam == null || selectedExam.trim().isEmpty()) {
            if (statusLabel != null) statusLabel.setText("Status: Select an exam first.");
            return;
        }

        String sql = "UPDATE live_sessions SET status = ?, progress = ?, last_ping = NOW() WHERE student_id_number = ? AND exam_title = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, progress);
            pstmt.setString(3, selectedLive.getStudentIdNumber());
            pstmt.setString(4, selectedExam);
            int updated = pstmt.executeUpdate();

            if (statusLabel != null) {
                statusLabel.setText(updated > 0
                        ? "Status: " + selectedLive.getStudentName() + " is now " + status + "."
                        : "Status: Could not update the selected student.");
            }
            loadLiveSessionsFromDatabase();
        } catch (Exception e) {
            if (statusLabel != null) statusLabel.setText("Status: Error updating live student.");
            e.printStackTrace();
        }
    }

    private void applyLiveSearchFilter() {
        if (filteredLiveSessionList == null) {
            return;
        }

        String search = txtLiveSearch != null && txtLiveSearch.getText() != null
                ? txtLiveSearch.getText().trim().toLowerCase()
                : "";

        filteredLiveSessionList.setPredicate(session -> {
            if (search.isEmpty()) {
                return true;
            }

            String id = session.getStudentIdNumber() == null ? "" : session.getStudentIdNumber().toLowerCase();
            String name = session.getStudentName() == null ? "" : session.getStudentName().toLowerCase();
            String section = session.getSection() == null ? "" : session.getSection().toLowerCase();
            String status = session.getStatus() == null ? "" : session.getStatus().toLowerCase();
            String progress = session.getProgress() == null ? "" : session.getProgress().toLowerCase();

            return id.contains(search)
                    || name.contains(search)
                    || section.contains(search)
                    || status.contains(search)
                    || progress.contains(search);
        });
    }

    private void loadStudentsFromDatabase() {
        studentList.clear();
        String sql = "SELECT student_id_number, class_name, full_name, pin FROM students ORDER BY class_name ASC, full_name ASC, student_id_number ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                studentList.add(new Student(
                    rs.getString("student_id_number"),
                    rs.getString("class_name"),
                    rs.getString("full_name"),
                    rs.getString("pin")
                ));
            }
            if (classTable != null && sortedDashboardStudents != null) {
                classTable.setItems(sortedDashboardStudents);
                applyDashboardStudentSorting();
            } else if (classTable != null) {
                classTable.setItems(studentList);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadExamsFromDatabase() {
        if (examTable == null) return;
        examList.clear();
        
        String sql = "SELECT id, title, class_name, answer_key, time_limit, status FROM exams ORDER BY title ASC, class_name ASC, id ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                examList.add(new ExamItem(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("answer_key"),
                    rs.getString("class_name"),
                    rs.getString("time_limit"),
                    rs.getString("status") 
                ));
            }
            examTable.setItems(examList);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadScoresFromDatabase() {
        if (scoreTable == null) return;
        scoreList.clear();
        
        String selectedSection = cbScoreSectionFilter != null ? cbScoreSectionFilter.getValue() : null;

        String sql = "SELECT s.full_name AS student_name, s.class_name AS section, e.score " +
                     "FROM exam_scores e JOIN students s ON e.student_id = s.id ";
                     
        if (selectedSection != null && !selectedSection.equals("All Sections")) {
            sql += " WHERE s.class_name = ? ";
        }
        sql += " ORDER BY s.class_name ASC, s.full_name ASC, e.exam_name ASC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            if (selectedSection != null && !selectedSection.equals("All Sections")) {
                pstmt.setString(1, selectedSection);
            }
             
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    scoreList.add(new Score(
                        rs.getString("student_name"),
                        rs.getString("section"),
                        rs.getString("score")
                    ));
                }
            }
            scoreTable.setItems(scoreList);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleUploadRoster(ActionEvent event) {
        String selectedClass = (txtClassName.getText() != null && !txtClassName.getText().trim().isEmpty())
                                ? txtClassName.getText().trim() : classSelector.getValue();
        if (selectedClass == null) {
            statusLabel.setText("Status: Select/Enter Section first!");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel/CSV Files", "*.xlsx", "*.csv"));
        File file = fc.showOpenDialog(((Node) event.getSource()).getScene().getWindow());

        if (file != null) {
            if (file.getName().endsWith(".csv")) importCSV(file, selectedClass);
            else importExcel(file, selectedClass);
            
            loadStudentsFromDatabase();
            loadSectionsIntoComboBox();
        }
    }

    private void importExcel(File file, String className) {
        String sql = "INSERT INTO students (student_id_number, class_name, full_name, pin) VALUES (?, ?, ?, ?)";
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis);
             Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            int currentYear = java.time.Year.now().getValue();
            int nextIdSequence = 1;

            String classPrefix = className.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            if (classPrefix.isEmpty()) classPrefix = "SEC";

            String getMaxIdSql = "SELECT student_id_number FROM students WHERE student_id_number LIKE ? AND class_name = ? ORDER BY student_id_number DESC LIMIT 1";
            try (PreparedStatement stmtMax = conn.prepareStatement(getMaxIdSql)) {
                stmtMax.setString(1, currentYear + "-" + classPrefix + "-%");
                stmtMax.setString(2, className);
                try (ResultSet rsMax = stmtMax.executeQuery()) {
                    if (rsMax.next()) {
                        String lastId = rsMax.getString("student_id_number");
                        String[] parts = lastId.split("-");
                        if (parts.length >= 2) {
                            try { nextIdSequence = Integer.parseInt(parts[parts.length - 1]) + 1; } 
                            catch (NumberFormatException ex) {}
                        }
                    }
                }
            } catch (Exception e) {}

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                Sheet sheet = workbook.getSheetAt(0);
                boolean firstRow = true;
                DataFormatter formatter = new DataFormatter();

                for (Row row : sheet) {
                    if (firstRow) { firstRow = false; continue; }
                    Cell nameCell = row.getCell(0);
                    String fullName = (nameCell == null) ? "" : formatter.formatCellValue(nameCell).trim();

                    if (fullName.isEmpty()) continue;

                    String studentId = String.format("%d-%s-%03d", currentYear, classPrefix, nextIdSequence);
                    nextIdSequence++;
                    
                    String generatedPin = String.format("%04d", new java.util.Random().nextInt(10000));

                    pstmt.setString(1, studentId);
                    pstmt.setString(2, className);
                    pstmt.setString(3, fullName);
                    pstmt.setString(4, generatedPin);
                    pstmt.executeUpdate();
                }
            }
            if (statusLabel != null) statusLabel.setText("Status: Excel imported successfully!");
            
        } catch (Exception e) {
            if (statusLabel != null) statusLabel.setText("Status: Error reading Excel file.");
            e.printStackTrace();
        }
    }

    private void importCSV(File file, String className) {
        String sql = "INSERT INTO students (student_id_number, class_name, full_name, pin) VALUES (?, ?, ?, ?)";
        try (BufferedReader br = new BufferedReader(new FileReader(file));
             Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            
            int currentYear = java.time.Year.now().getValue();
            int nextIdSequence = 1;

            String classPrefix = className.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            if (classPrefix.isEmpty()) classPrefix = "SEC";

            String getMaxIdSql = "SELECT student_id_number FROM students WHERE student_id_number LIKE ? AND class_name = ? ORDER BY student_id_number DESC LIMIT 1";
            try (PreparedStatement stmtMax = conn.prepareStatement(getMaxIdSql)) {
                stmtMax.setString(1, currentYear + "-" + classPrefix + "-%");
                stmtMax.setString(2, className);
                try (ResultSet rsMax = stmtMax.executeQuery()) {
                    if (rsMax.next()) {
                        String lastId = rsMax.getString("student_id_number");
                        String[] parts = lastId.split("-");
                        if (parts.length >= 2) {
                            try { nextIdSequence = Integer.parseInt(parts[parts.length - 1]) + 1; } 
                            catch (NumberFormatException ex) {}
                        }
                    }
                }
            } catch (Exception e) {}

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                String line;
                boolean firstLine = true;
                while ((line = br.readLine()) != null) {
                    if (firstLine) { firstLine = false; continue; }
                    String[] data = line.split(",");
                    if (data.length >= 1) {
                        String fullName = data[0].trim();
                        if (!fullName.isEmpty()) {
                            String studentId = String.format("%d-%s-%03d", currentYear, classPrefix, nextIdSequence);
                            nextIdSequence++;
                            
                            String generatedPin = String.format("%04d", new java.util.Random().nextInt(10000));

                            pstmt.setString(1, studentId);
                            pstmt.setString(2, className);
                            pstmt.setString(3, fullName);
                            pstmt.setString(4, generatedPin);
                            pstmt.executeUpdate();
                        }
                    }
                }
            }
            if (statusLabel != null) statusLabel.setText("Status: CSV imported successfully!");
        } catch (Exception e) {
            if (statusLabel != null) statusLabel.setText("Status: Error reading CSV file.");
            e.printStackTrace();
        }
    }

    @FXML private void handleAddClass(ActionEvent e) { statusLabel.setText("Status: Ready to add class."); }

    private void generateAndDisplayQRCode(String title, String section, int questionCount) {
        try {
            if (title == null) title = "Unknown";
            if (section == null) section = "Unknown";

            String timeLimit = (txtTimeLimit != null && !txtTimeLimit.getText().isEmpty()) ? txtTimeLimit.getText() : "60";

            String queryParams = String.format(
                "answer.php?class=%s&title=%s&pdf=%s&q=%d&timeLimit=%s",
                URLEncoder.encode(section, StandardCharsets.UTF_8.toString()),
                URLEncoder.encode(title, StandardCharsets.UTF_8.toString()),
                URLEncoder.encode(title, StandardCharsets.UTF_8.toString()),
                questionCount,
                URLEncoder.encode(timeLimit, StandardCharsets.UTF_8.toString())
            );

            String studentURL = NetworkConfig.buildStudentURL(queryParams);

            QRCodeWriter qr = new QRCodeWriter();
            BitMatrix matrix = qr.encode(studentURL, BarcodeFormat.QR_CODE, 200, 200);
            if(qrView != null) {
                qrView.setImage(SwingFXUtils.toFXImage(MatrixToImageWriter.toBufferedImage(matrix), null));
            }
        } catch (Exception e) {
            System.out.println("Error generating QR: " + e.getMessage());
        }
    }

    private String getLocalWiFiIP() {
        return NetworkConfig.getLocalWiFiIP();
    }

    @FXML
    private void handleConfigureNetwork(ActionEvent event) {
        NetworkConfig.showConfigDialog();
    }

    @FXML private void handleBrowseFile(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        selectedFile = fc.showOpenDialog(null);
        if(selectedFile != null) {
            if (fileNameLabel != null) fileNameLabel.setText(selectedFile.getName());
            if (txtExamTitle != null) txtExamTitle.setText(selectedFile.getName());
        }
    }

    @FXML private void handleBrowseKeyFile(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        selectedAnswerKeyFile = fc.showOpenDialog(null);
        if(selectedAnswerKeyFile != null) {
            if (fileNameLabelKey != null) fileNameLabelKey.setText(selectedAnswerKeyFile.getName());
            try {
                String ansKey = new String(Files.readAllBytes(selectedAnswerKeyFile.toPath())).trim().toUpperCase().replaceAll("[^A-Z]", "");
                if (txtAnswerKeyEditor != null) txtAnswerKeyEditor.setText(ansKey);
            } catch (Exception ex) {
                statusLabel.setText("Status: Error reading answer key file.");
            }
        }
    }

    @FXML private void handleGenerateQR(ActionEvent event) {
        if (selectedFile == null) {
            statusLabel.setText("Status: Select a PDF Exam first!");
            return;
        }
        String section = classSelector.getValue();
        if (section == null) {
            statusLabel.setText("Status: Please select a Section!");
            return;
        }

        try {
            String ansKey = resolveEditedAnswerKey(null);
            if (ansKey.isEmpty()) {
                statusLabel.setText("Status: Enter or upload an answer key first!");
                return;
            }
            int questionCount = ansKey.length();

            String customTitle = txtExamTitle != null ? txtExamTitle.getText().trim() : "";
            if (customTitle.isEmpty()) {
                customTitle = selectedFile.getName();
            } else if (!customTitle.toLowerCase().endsWith(".pdf")) {
                customTitle += ".pdf";
            }
            
            String timeLimitStr = (txtTimeLimit != null && !txtTimeLimit.getText().trim().isEmpty()) ? txtTimeLimit.getText().trim() : "60";
            int timeLimit = 60;
            try { timeLimit = Integer.parseInt(timeLimitStr); } catch (Exception ex) {}

            Path source = selectedFile.toPath();
            Path destination = Paths.get(UPLOAD_DIR + customTitle);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

            generateAndDisplayQRCode(customTitle, section, questionCount);

            String sql = "INSERT INTO exams (title, answer_key, class_name, time_limit, status) VALUES (?, ?, ?, ?, 'Open')";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, customTitle); 
                pstmt.setString(2, ansKey);
                pstmt.setString(3, section);
                pstmt.setInt(4, timeLimit);
                pstmt.executeUpdate();
            }

            statusLabel.setText("Status: QR Generated! Copied PDF to Server.");
            handleClearExamSelection(null);
            loadExamsFromDatabase();
        } catch (Exception e) {
            statusLabel.setText("Status: Error generating QR or copying file.");
            e.printStackTrace();
        }
    }

    @FXML private void handleUpdateExam(ActionEvent event) {
        if (selectedExam == null) {
            statusLabel.setText("Status: Select an exam from the table below first!");
            return;
        }
        String section = classSelector.getValue();
        if (section == null) {
            statusLabel.setText("Status: Select a Class Section!");
            return;
        }

        try {
            String originalTitle = selectedExam.getTitle();
            String examId = selectedExam.getId();
            String titleToSave = txtExamTitle != null ? txtExamTitle.getText().trim() : "";
            if (titleToSave.isEmpty()) {
                titleToSave = (selectedFile != null) ? selectedFile.getName() : selectedExam.getTitle();
            } else if (!titleToSave.toLowerCase().endsWith(".pdf")) {
                titleToSave += ".pdf";
            }

            String ansKeyToSave = resolveEditedAnswerKey(selectedExam.getAnswerKey());
            if (ansKeyToSave.isEmpty()) {
                statusLabel.setText("Status: Enter or upload an answer key first!");
                return;
            }
            
            int questionCount = ansKeyToSave.length();
            
            String timeLimitStr = (txtTimeLimit != null && !txtTimeLimit.getText().trim().isEmpty()) ? txtTimeLimit.getText().trim() : "60";
            int timeLimit = 60;
            try { timeLimit = Integer.parseInt(timeLimitStr); } catch (Exception ex) {}

            if (selectedFile != null) {
                Path source = selectedFile.toPath();
                Path destination = Paths.get(UPLOAD_DIR + titleToSave);
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } else if (!titleToSave.equals(selectedExam.getTitle())) {
                File oldFile = new File(UPLOAD_DIR + selectedExam.getTitle());
                File newFile = new File(UPLOAD_DIR + titleToSave);
                if (oldFile.exists()) {
                    oldFile.renameTo(newFile);
                }
            }
            
            generateAndDisplayQRCode(titleToSave, section, questionCount);

            String sql = "UPDATE exams SET title = ?, answer_key = ?, class_name = ?, time_limit = ? WHERE id = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);
                pstmt.setString(1, titleToSave);
                pstmt.setString(2, ansKeyToSave);
                pstmt.setString(3, section);
                pstmt.setInt(4, timeLimit);
                pstmt.setString(5, examId);
                pstmt.executeUpdate();

                updateExamTitleReferences(conn, originalTitle, titleToSave);
                RegradeResult regradeResult = regradeExamAnswersAndScores(conn, examId, titleToSave, ansKeyToSave);
                conn.commit();

                statusLabel.setText("Status: Exam Updated! Regraded " + regradeResult.updatedScores + " score(s).");
            }

            handleClearExamSelection(null);
            loadExamsFromDatabase();
            loadScoresFromDatabase();
        } catch (Exception e) {
            statusLabel.setText("Status: Error updating exam."); e.printStackTrace();
        }
    }

    @FXML 
    private void handleToggleExamStatus(ActionEvent event) {
        if (selectedExam == null) {
            statusLabel.setText("Status: Select an exam from the table to Lock/Unlock!");
            return;
        }

        String currentStatus = selectedExam.getStatus() == null ? "Open" : selectedExam.getStatus();
        String newStatus = currentStatus.equalsIgnoreCase("Open") ? "Locked" : "Open";

        String sql = "UPDATE exams SET status = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, newStatus);
            pstmt.setString(2, selectedExam.getId());
            pstmt.executeUpdate();
            
            statusLabel.setText("Status: Exam is now " + newStatus + "!");
            loadExamsFromDatabase(); 
            
        } catch (Exception e) {
            statusLabel.setText("Status: Error changing exam status.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRegradeExam(ActionEvent event) {
        if (selectedExam == null) {
            statusLabel.setText("Status: Select an exam from the table below first!");
            return;
        }

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Regrade Exam");
        confirm.setHeaderText("Regrade " + selectedExam.getTitle() + "?");
        confirm.setContentText("This will recalculate all submitted answers and overwrite saved scores for this exam.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            conn.setAutoCommit(false);

            String answerKey = resolveAnswerKeyForRegrade();
            if (answerKey.isEmpty()) {
                conn.rollback();
                statusLabel.setText("Status: No valid answer key found for regrading.");
                return;
            }

            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE exams SET answer_key = ? WHERE id = ?")) {
                pstmt.setString(1, answerKey);
                pstmt.setString(2, selectedExam.getId());
                pstmt.executeUpdate();
            }

            RegradeResult regradeResult = regradeExamAnswersAndScores(conn, selectedExam.getId(), selectedExam.getTitle(), answerKey);
            conn.commit();
            statusLabel.setText("Status: Regraded " + regradeResult.updatedScores + " student score(s) and " + regradeResult.updatedAnswers + " answer row(s).");
            loadExamsFromDatabase();
            loadScoresFromDatabase();
            showAlert(AlertType.INFORMATION, "Regrade Complete", "Updated " + regradeResult.updatedScores + " student score(s) and " + regradeResult.updatedAnswers + " answer row(s).");
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            statusLabel.setText("Status: Error regrading exam.");
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Regrade Error", "Could not finish regrading.\n" + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }

    @FXML private void handleClearExamSelection(ActionEvent event) {
        selectedExam = null;
        selectedFile = null;
        selectedAnswerKeyFile = null;
        
        if (fileNameLabel != null) fileNameLabel.setText("No file selected");
        if (fileNameLabelKey != null) fileNameLabelKey.setText("No file selected");
        if (classSelector != null) classSelector.getSelectionModel().clearSelection();
        if (examTable != null) examTable.getSelectionModel().clearSelection();
        if (qrView != null) qrView.setImage(null);
        
        if (txtExamTitle != null) txtExamTitle.clear();
        if (txtTimeLimit != null) txtTimeLimit.clear();
        if (txtAnswerKeyEditor != null) txtAnswerKeyEditor.clear();
        
        statusLabel.setText("Status: Ready for new upload.");
    }

    private String resolveAnswerKeyForRegrade() throws IOException {
        return resolveEditedAnswerKey(selectedExam != null ? selectedExam.getAnswerKey() : "");
    }

    private String resolveEditedAnswerKey(String fallbackValue) throws IOException {
        String manualKey = txtAnswerKeyEditor != null ? txtAnswerKeyEditor.getText() : "";
        manualKey = manualKey == null ? "" : manualKey.trim().toUpperCase().replaceAll("[^A-Z]", "");
        if (!manualKey.isEmpty()) {
            return manualKey;
        }
        if (selectedAnswerKeyFile != null) {
            return new String(Files.readAllBytes(selectedAnswerKeyFile.toPath())).trim().toUpperCase().replaceAll("[^A-Z]", "");
        }
        return fallbackValue != null ? fallbackValue.trim().toUpperCase().replaceAll("[^A-Z]", "") : "";
    }

    private void updateExamTitleReferences(Connection conn, String oldTitle, String newTitle) throws SQLException {
        if (oldTitle == null || newTitle == null || oldTitle.equals(newTitle)) {
            return;
        }

        try (PreparedStatement pstmt = conn.prepareStatement("UPDATE exam_scores SET exam_name = ? WHERE exam_name = ?")) {
            pstmt.setString(1, newTitle);
            pstmt.setString(2, oldTitle);
            pstmt.executeUpdate();
        }

        try (PreparedStatement pstmt = conn.prepareStatement("UPDATE live_sessions SET exam_title = ? WHERE exam_title = ?")) {
            pstmt.setString(1, newTitle);
            pstmt.setString(2, oldTitle);
            pstmt.executeUpdate();
        }

        String answerExamColumn = resolveExamReferenceColumn(conn, "student_answers");
        if (answerExamColumn != null && !"exam_id".equals(answerExamColumn)) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE student_answers SET " + answerExamColumn + " = ? WHERE " + answerExamColumn + " = ?")) {
                pstmt.setString(1, newTitle);
                pstmt.setString(2, oldTitle);
                pstmt.executeUpdate();
            }
        }
    }

    private RegradeResult regradeExamAnswersAndScores(Connection conn, String examId, String examTitle, String answerKey) throws SQLException {
        String answerExamColumn = resolveExamReferenceColumn(conn, "student_answers");
        if (answerExamColumn == null) {
            throw new SQLException("Could not find an exam reference column in student_answers.");
        }

        Map<Integer, Integer> scoreByStudent = new HashMap<>();
        Set<Integer> participantIds = new HashSet<>();

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT DISTINCT student_id FROM exam_scores WHERE exam_name = ?")) {
            pstmt.setString(1, examTitle);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int studentId = rs.getInt("student_id");
                    participantIds.add(studentId);
                    scoreByStudent.put(studentId, 0);
                }
            }
        }

        String selectAnswersSql = "SELECT student_id, question_number, student_answer FROM student_answers WHERE " + answerExamColumn + " = ? ORDER BY student_id, question_number";
        String updateAnswersSql = "UPDATE student_answers SET correct_answer = ?, is_correct = ? WHERE student_id = ? AND question_number = ? AND " + answerExamColumn + " = ?";
        int updatedAnswers = 0;

        try (PreparedStatement selectStmt = conn.prepareStatement(selectAnswersSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateAnswersSql)) {

            bindExamReference(selectStmt, 1, answerExamColumn, examId, examTitle);
            try (ResultSet rs = selectStmt.executeQuery()) {
                while (rs.next()) {
                    int studentId = rs.getInt("student_id");
                    int questionNumber = rs.getInt("question_number");
                    String studentAnswer = rs.getString("student_answer");
                    String correctAnswer = "";
                    boolean isCorrect = false;

                    participantIds.add(studentId);
                    scoreByStudent.putIfAbsent(studentId, 0);

                    if (questionNumber >= 1 && questionNumber <= answerKey.length()) {
                        correctAnswer = String.valueOf(answerKey.charAt(questionNumber - 1));
                        String normalizedStudentAnswer = studentAnswer == null ? "" : studentAnswer.trim().toUpperCase();
                        isCorrect = correctAnswer.equals(normalizedStudentAnswer);
                        if (isCorrect) {
                            scoreByStudent.put(studentId, scoreByStudent.get(studentId) + 1);
                        }
                    }

                    updateStmt.setString(1, correctAnswer);
                    updateStmt.setBoolean(2, isCorrect);
                    updateStmt.setInt(3, studentId);
                    updateStmt.setInt(4, questionNumber);
                    bindExamReference(updateStmt, 5, answerExamColumn, examId, examTitle);
                    updateStmt.executeUpdate();
                    updatedAnswers++;
                }
            }
        }

        String updateScoreSql = "UPDATE exam_scores SET score = ? WHERE student_id = ? AND exam_name = ?";
        String insertScoreSql = "INSERT INTO exam_scores (student_id, exam_name, score) VALUES (?, ?, ?)";
        int updatedScores = 0;

        for (Integer studentId : participantIds) {
            String score = scoreByStudent.getOrDefault(studentId, 0) + "/" + answerKey.length();
            try (PreparedStatement updateScoreStmt = conn.prepareStatement(updateScoreSql)) {
                updateScoreStmt.setString(1, score);
                updateScoreStmt.setInt(2, studentId);
                updateScoreStmt.setString(3, examTitle);

                int rows = updateScoreStmt.executeUpdate();
                if (rows == 0) {
                    try (PreparedStatement insertScoreStmt = conn.prepareStatement(insertScoreSql)) {
                        insertScoreStmt.setInt(1, studentId);
                        insertScoreStmt.setString(2, examTitle);
                        insertScoreStmt.setString(3, score);
                        insertScoreStmt.executeUpdate();
                    }
                }
                updatedScores++;
            }
        }

        return new RegradeResult(updatedScores, updatedAnswers);
    }

    private String resolveExamReferenceColumn(Connection conn, String tableName) throws SQLException {
        if (columnExists(conn, tableName, "exam_id")) return "exam_id";
        if (columnExists(conn, tableName, "exam_name")) return "exam_name";
        if (columnExists(conn, tableName, "exam_title")) return "exam_title";
        if (columnExists(conn, tableName, "title")) return "title";
        return null;
    }

    private void bindExamReference(PreparedStatement pstmt, int parameterIndex, String referenceColumn) throws SQLException {
        bindExamReference(pstmt, parameterIndex, referenceColumn, selectedExam.getId(), selectedExam.getTitle());
    }

    private void bindExamReference(PreparedStatement pstmt, int parameterIndex, String referenceColumn, String examId, String examTitle) throws SQLException {
        if ("exam_id".equals(referenceColumn)) {
            pstmt.setInt(parameterIndex, Integer.parseInt(examId));
        } else {
            pstmt.setString(parameterIndex, examTitle);
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }

    private void switchScene(ActionEvent event, String fxmlFile) {
        try {
            Scene currentScene = ((Node) event.getSource()).getScene();
            Stage stage = (Stage) currentScene.getWindow();
            boolean isMaximized = stage.isMaximized();

            Parent root = FXMLLoader.load(getClass().getResource(fxmlFile));
            currentScene.setRoot(root);
            stage.setMaximized(isMaximized);
            stage.show();

        } catch (IOException e) {
            System.err.println("Could not load: " + fxmlFile);
            e.printStackTrace();
        }
    }
   
    @FXML
    private void handleViewStudentAnswers(ActionEvent event) {
        Score selectedScore = scoreTable.getSelectionModel().getSelectedItem();
        
        if (selectedScore == null) {
            if (statusLabel != null) statusLabel.setText("Status: Please select a student score!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/admindashboard/viewanswers.fxml"));
            Parent root = loader.load();
            
            ViewAnswersController controller = loader.getController();
            controller.initData(selectedScore.getStudentName(), selectedScore.getSection());
            
            Stage stage = new Stage();
            stage.setTitle("Detailed Answers - " + selectedScore.getStudentName());
            stage.setScene(new Scene(root));
            stage.show();
            
        } catch (IOException e) {
            System.err.println("FXML Load Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
 @FXML
private void handleDeleteExam(ActionEvent event) {
    if (selectedExam == null) {
        showAlert(AlertType.WARNING, "No Selection", "Please select an exam from the table to delete.");
        return;
    }

    Alert confirm = new Alert(AlertType.CONFIRMATION);
    confirm.setTitle("Confirm Deletion");
    confirm.setHeaderText("Delete Exam: " + selectedExam.getTitle());
    confirm.setContentText("Are you sure you want to delete this exam? This will also delete all student scores for this exam.");

    Optional<ButtonType> result = confirm.showAndWait();
    if (result.isPresent() && result.get() == ButtonType.OK) {
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // 1. Delete student answers and scores tied to this exam first
            String deleteScores = "DELETE FROM exam_scores WHERE exam_name = ?";
            try (PreparedStatement ps1 = conn.prepareStatement(deleteScores)) {
                ps1.setString(1, selectedExam.getTitle());
                ps1.executeUpdate();
            }

            // 2. Now delete the actual exam
            String sqlDeleteExam = "DELETE FROM exams WHERE id = ?";
            try (PreparedStatement ps2 = conn.prepareStatement(sqlDeleteExam)) {
                ps2.setString(1, selectedExam.getId());
                ps2.executeUpdate();
            }
            
            // 3. Delete the PDF file
            File pdfFile = new File(UPLOAD_DIR + selectedExam.getTitle());
            if (pdfFile.exists()) {
                pdfFile.delete();
            }

            statusLabel.setText("Status: Exam '" + selectedExam.getTitle() + "' deleted.");
            handleClearExamSelection(null);
            loadExamsFromDatabase();
            
        } catch (Exception e) {
            statusLabel.setText("Status: Error deleting exam.");
            e.printStackTrace();
        }
    }
}

    @FXML
    private void handleClearUploadedExams(ActionEvent event) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Clear Uploaded Exams");
        confirm.setHeaderText("Delete ALL uploaded exams?");
        confirm.setContentText("This will remove every exam in the table, delete related scores/answers/live sessions, delete the uploaded PDF files, and reset the exam ID back to 1. This cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }

        Set<String> uploadedTitles = new HashSet<>();
        for (ExamItem exam : examList) {
            if (exam.getTitle() != null && !exam.getTitle().trim().isEmpty()) {
                uploadedTitles.add(exam.getTitle().trim());
            }
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DELETE FROM student_answers");
            stmt.executeUpdate("DELETE FROM exam_scores");
            stmt.executeUpdate("DELETE FROM live_sessions");
            stmt.executeUpdate("DELETE FROM exams");
            stmt.executeUpdate("ALTER TABLE exams AUTO_INCREMENT = 1");

            int deletedFiles = deleteUploadedExamFiles(uploadedTitles);

            handleClearExamSelection(null);
            loadExamsFromDatabase();
            loadExamsIntoLiveFilter();
            loadScoresFromDatabase();

            if (statusLabel != null) {
                statusLabel.setText("Status: Uploaded exams cleared. Exam IDs will start at 1. Deleted files: " + deletedFiles);
            }
        } catch (Exception e) {
            if (statusLabel != null) {
                statusLabel.setText("Status: Error clearing uploaded exams.");
            }
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Clear Uploaded Exams", "Could not clear uploaded exams.\n" + e.getMessage());
        }
    }

    private int deleteUploadedExamFiles(Set<String> titles) {
        int deleted = 0;
        Path uploadDir = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();

        for (String title : titles) {
            try {
                Path filePath = uploadDir.resolve(title).normalize();
                if (filePath.startsWith(uploadDir) && Files.isRegularFile(filePath) && Files.deleteIfExists(filePath)) {
                    deleted++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return deleted;
    }

    @FXML
    private void handleClearScores(ActionEvent event) {
        String selectedSection = cbScoreSectionFilter != null ? cbScoreSectionFilter.getValue() : "All Sections";
        
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Clear Scores");
        
        String sql;
        if (selectedSection == null || selectedSection.equals("All Sections")) {
            confirm.setHeaderText("Clear ALL Scores?");
            confirm.setContentText("WARNING: Are you sure you want to permanently delete ALL student scores in the database? This cannot be undone.");
            sql = "TRUNCATE TABLE exam_scores"; 
        } else {
            confirm.setHeaderText("Clear Scores for: " + selectedSection);
            confirm.setContentText("Are you sure you want to delete all scores for students in " + selectedSection + "?");
            sql = "DELETE e FROM exam_scores e JOIN students s ON e.student_id = s.id WHERE s.class_name = ?";
        }

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                 
                if (selectedSection != null && !selectedSection.equals("All Sections")) {
                    pstmt.setString(1, selectedSection);
                }
                
                int rowsAffected = pstmt.executeUpdate();
                statusLabel.setText("Status: " + (selectedSection.equals("All Sections") ? "All scores cleared." : rowsAffected + " scores cleared for " + selectedSection));
                loadScoresFromDatabase();
                
            } catch (Exception e) {
                statusLabel.setText("Status: Error clearing scores.");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleDownloadScores(ActionEvent event) {
        if (scoreList.isEmpty()) {
            showAlert(AlertType.WARNING, "No Data", "There are no scores currently displayed to download.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Scores to Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"));
        
        String sectionFilter = (cbScoreSectionFilter != null && cbScoreSectionFilter.getValue() != null) ? cbScoreSectionFilter.getValue() : "All_Sections";
        sectionFilter = sectionFilter.replaceAll("[^a-zA-Z0-9_\\-]", "_"); 
        fileChooser.setInitialFileName("Scores_" + sectionFilter + ".xlsx");
        
        File file = fileChooser.showSaveDialog(((Node) event.getSource()).getScene().getWindow());

        if (file != null) {
            try (Workbook workbook = new XSSFWorkbook();
                 FileOutputStream fileOut = new FileOutputStream(file)) {
                 
                Sheet sheet = workbook.createSheet("Scores");

                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Student Name");
                headerRow.createCell(1).setCellValue("Section");
                headerRow.createCell(2).setCellValue("Score");

                int rowNum = 1;
                for (Score s : scoreList) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(s.getStudentName());
                    row.createCell(1).setCellValue(s.getSection());
                    row.createCell(2).setCellValue(s.getScore());
                }

                sheet.autoSizeColumn(0);
                sheet.autoSizeColumn(1);
                sheet.autoSizeColumn(2);

                workbook.write(fileOut);
                
                statusLabel.setText("Status: Scores exported to " + file.getName());
                showAlert(AlertType.INFORMATION, "Export Successful", "Scores successfully saved to:\n" + file.getAbsolutePath());
                
            } catch (IOException e) {
                statusLabel.setText("Status: Error saving Excel file.");
                e.printStackTrace();
                showAlert(AlertType.ERROR, "Export Error", "Could not save the file.\n" + e.getMessage());
            }
        }
    }

   @FXML
    private void handleClearClassTable(ActionEvent event) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Clear All Classes");
        confirm.setHeaderText("🚨 WARNING: Delete ALL Students?");
        confirm.setContentText("Are you sure you want to permanently delete all student records? This cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            
            String sqlDelete = "DELETE FROM students";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate(sqlDelete);
                
                if (statusLabel != null) {
                    statusLabel.setText("Status: All student records cleared!");
                    statusLabel.setStyle("-fx-text-fill: #5cb85c;"); 
                }
                
                loadStudentsFromDatabase(); 
                loadSectionsIntoComboBox(); 

            } catch (Exception e) {
                if (statusLabel != null) {
                    statusLabel.setText("Status: Database Error. Check for linked scores.");
                    statusLabel.setStyle("-fx-text-fill: #d9534f;"); 
                }
                e.printStackTrace();
                
                Alert errorAlert = new Alert(AlertType.ERROR);
                errorAlert.setContentText("Could not clear table. Usually, this happens if these students already have scores recorded in the Scores table.");
                errorAlert.show();
            }
        }
    }
    @FXML
    private void handleDeleteScore(ActionEvent event) {
        // 1. Get the selected student from the table
        Score selectedScore = scoreTable.getSelectionModel().getSelectedItem();
        
        if (selectedScore == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText(null);
            alert.setContentText("Please click on a student's score in the table first.");
            alert.showAndWait();
            return;
        }

        // 2. Ask for confirmation
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Score for " + selectedScore.getStudentName());
        confirm.setContentText("Are you sure you want to permanently delete this score?\n\n(Note: If this student was blocked, deleting their score will allow them to retake the test).");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                
                String studentName = selectedScore.getStudentName();
                
                // 3. Delete their score from exam_scores
                String delScore = "DELETE FROM exam_scores WHERE student_id = (SELECT id FROM students WHERE full_name = ? LIMIT 1)";
                try (PreparedStatement ps = conn.prepareStatement(delScore)) {
                    ps.setString(1, studentName);
                    ps.executeUpdate();
                }
                
                // 4. Delete their blocked status from live_sessions
                String delLive = "DELETE FROM live_sessions WHERE student_name = ?";
                try (PreparedStatement ps = conn.prepareStatement(delLive)) {
                    ps.setString(1, studentName);
                    ps.executeUpdate();
                }

                // 5. Delete their old answers so they start fresh
                String delAns = "DELETE FROM student_answers WHERE student_id = (SELECT id FROM students WHERE full_name = ? LIMIT 1)";
                try (PreparedStatement ps = conn.prepareStatement(delAns)) {
                    ps.setString(1, studentName);
                    ps.executeUpdate();
                }

                // Show Success Message
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Deleted Successfully");
                info.setHeaderText(null);
                info.setContentText("✅ The score for " + studentName + " has been deleted.\nThey are now cleared to retake the exam.");
                info.showAndWait();
                
                // Refresh the table to show the score is gone
                handleRefreshScores(new ActionEvent());
                
            } catch (Exception e) {
                e.printStackTrace();
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Database Error");
                err.setHeaderText("Could not delete score");
                err.setContentText(e.getMessage());
                err.showAndWait();
            }
        }
    }
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static class LiveSession {
        private String studentIdNumber;
        private String studentName;
        private String section;
        private String status;
        private String progress;
        private String lastPing;

        public LiveSession(String studentIdNumber, String studentName, String section, String status, String progress, String lastPing) {
            this.studentIdNumber = studentIdNumber;
            this.studentName = studentName;
            this.section = section;
            this.status = status;
            this.progress = progress;
            this.lastPing = lastPing;
        }
        public String getStudentIdNumber() { return studentIdNumber; }
        public String getStudentName() { return studentName; }
        public String getSection() { return section; }
        public String getStatus() { return status; }
        public String getProgress() { return progress; }
        public String getLastPing() { return lastPing; }
    }

    public static class ExamItem {
        private String id;
        private String title;
        private String answerKey;
        private String className; 
        private String timeLimit; 
        private String status; 

        public ExamItem(String id, String title, String answerKey, String className, String timeLimit, String status) { 
            this.id = id; 
            this.title = title; 
            this.answerKey = answerKey;
            this.className = className;
            this.timeLimit = timeLimit;
            this.status = status; 
        }
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getAnswerKey() { return answerKey; }
        public String getClassName() { return className; } 
        public String getTimeLimit() { return timeLimit; } 
        public String getStatus() { return status; } 
    }

    private static class RegradeResult {
        private final int updatedScores;
        private final int updatedAnswers;

        private RegradeResult(int updatedScores, int updatedAnswers) {
            this.updatedScores = updatedScores;
            this.updatedAnswers = updatedAnswers;
        }
    }

    public static class Score {
        private String studentName;
        private String section;
        private String score;
        public Score(String studentName, String section, String score) { 
            this.studentName = studentName; 
            this.section = section; 
            this.score = score; 
        }
        public String getStudentName() { return studentName; }
        public String getSection() { return section; }
        public String getScore() { return score; }
    }

    public static class Student {
        private String studentCode;
        private String section;
        private String fullName;
        private String pin; 

        public Student(String studentCode, String section, String fullName, String pin) { 
            this.studentCode = studentCode; 
            this.section = section; 
            this.fullName = fullName; 
            this.pin = pin; 
        }
        public String getStudentCode() { return studentCode; }
        public String getSection() { return section; }
        public String getFullName() { return fullName; }
        public String getPin() { return pin; } 
    }
}
