package admindashboard;

import javafx.beans.property.SimpleStringProperty;

public class Student {
    private final SimpleStringProperty studentCode;
    private final SimpleStringProperty section;
    private final SimpleStringProperty fullName;

    public Student(String code, String section, String name) {
        this.studentCode = new SimpleStringProperty(code);
        this.section = new SimpleStringProperty(section);
        this.fullName = new SimpleStringProperty(name);
    }

    // PropertyValueFactory uses these exact getter names
    public String getStudentCode() { return studentCode.get(); }
    public String getSection() { return section.get(); }
    public String getFullName() { return fullName.get(); }
}