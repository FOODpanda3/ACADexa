CREATE DATABASE IF NOT EXISTS admindashboard_db;
USE admindashboard_db;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    username VARCHAR(80) NOT NULL UNIQUE,
    password VARCHAR(120) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS students (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id_number VARCHAR(50) NOT NULL UNIQUE,
    class_name VARCHAR(100) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    pin VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exams (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    answer_key LONGTEXT,
    class_name VARCHAR(100),
    time_limit INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'Open',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam_scores (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    exam_name VARCHAR(200) NOT NULL,
    score VARCHAR(40) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_student_exam (student_id, exam_name),
    CONSTRAINT fk_exam_scores_student
        FOREIGN KEY (student_id) REFERENCES students(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS student_answers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    exam_id INT,
    exam_name VARCHAR(200),
    question_number INT NOT NULL,
    student_answer VARCHAR(50),
    correct_answer VARCHAR(50),
    is_correct TINYINT(1) DEFAULT 0,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_answers_student
        FOREIGN KEY (student_id) REFERENCES students(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_student_answers_exam
        FOREIGN KEY (exam_id) REFERENCES exams(id)
        ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS live_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id_number VARCHAR(50) NOT NULL,
    student_name VARCHAR(150),
    section VARCHAR(100),
    exam_title VARCHAR(200),
    device_token VARCHAR(255),
    status VARCHAR(40) DEFAULT 'Active',
    progress VARCHAR(80),
    last_ping TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_live_student_exam (student_id_number, exam_title)
);
