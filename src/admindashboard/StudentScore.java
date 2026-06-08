package admindashboard;

import javafx.beans.property.SimpleStringProperty;

public class StudentScore {
    private final SimpleStringProperty studentCode;
    private final SimpleStringProperty fullName;
    private final SimpleStringProperty score;

    public StudentScore(String studentCode, String fullName, String score) {
        this.studentCode = new SimpleStringProperty(studentCode);
        this.fullName = new SimpleStringProperty(fullName);
        this.score = new SimpleStringProperty(score);
    }

    public String getStudentCode() { return studentCode.get(); }
    public String getFullName() { return fullName.get(); }
    public String getScore() { return score.get(); }
    
    public void setScore(String newScore) { this.score.set(newScore); }
}