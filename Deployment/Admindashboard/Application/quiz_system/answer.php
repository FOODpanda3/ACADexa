<?php
// Display errors to prevent white screens
error_reporting(E_ALL);
ini_set('display_errors', 1);

// --- NEW: REMOTE UNBLOCK CHECKER (Listens to the Java App) ---
if (isset($_GET['check_block_status'])) {
    $conn_check = new mysqli("localhost", "root", "", "admindashboard_db");
    $sid = trim($_GET['sid']);
    $exam_title = trim($_GET['title']);

    $stmt = $conn_check->prepare("SELECT id FROM students WHERE student_id_number = ? OR full_name = ?");
    $stmt->bind_param("ss", $sid, $sid);
    $stmt->execute();
    $res = $stmt->get_result();
    
    if ($row = $res->fetch_assoc()) {
        $student_db_id = $row['id'];
        
        // If the teacher deleted their score in Java, they are CLEARED!
        $check_stmt = $conn_check->prepare("SELECT id FROM exam_scores WHERE student_id = ? AND exam_name = ?");
        $check_stmt->bind_param("is", $student_db_id, $exam_title);
        $check_stmt->execute();
        $check_res = $check_stmt->get_result();
        
        if ($check_res->num_rows > 0) {
            echo "BLOCKED";
        } else {
            echo "CLEARED";
        }
    }
    exit();
}
// --------------------------------------------------------------

// --- NEW: AJAX TEACHER OVERRIDE UNBLOCK ---
if (isset($_POST['teacher_override_unblock'])) {
    $conn_override = new mysqli("localhost", "root", "", "admindashboard_db");
    $sid = trim($_POST['sid']);
    $exam_title = trim($_POST['title']);
    $password = trim($_POST['password']);

    if ($password !== 'admin') {
        echo "WRONG_PASSWORD";
        exit();
    }

    $stmt = $conn_override->prepare("SELECT id, student_id_number, full_name FROM students WHERE student_id_number = ? OR full_name = ?");
    $stmt->bind_param("ss", $sid, $sid);
    $stmt->execute();
    $res = $stmt->get_result();

    if ($row = $res->fetch_assoc()) {
        $student_db_id = $row['id'];
        $db_sid = $row['student_id_number'];

        $score_stmt = $conn_override->prepare("DELETE FROM exam_scores WHERE student_id = ? AND exam_name = ?");
        $score_stmt->bind_param("is", $student_db_id, $exam_title);
        $score_stmt->execute();

        $ans_stmt = $conn_override->prepare("DELETE FROM student_answers WHERE student_id = ? AND exam_name = ?");
        $ans_stmt->bind_param("is", $student_db_id, $exam_title);
        $ans_stmt->execute();

        $live_stmt = $conn_override->prepare("UPDATE live_sessions SET status = 'Active', progress = 'Resumed' WHERE student_id_number = ? AND exam_title = ?");
        $live_stmt->bind_param("ss", $db_sid, $exam_title);
        $live_stmt->execute();

        echo "OK";
    } else {
        echo "STUDENT_NOT_FOUND";
    }
    exit();
}

// --- NEW: AJAX PIN VERIFICATION (Runs when they click submit) ---
if (isset($_POST['check_pin_ajax'])) {
    $conn_pin = new mysqli("localhost", "root", "", "admindashboard_db");
    $sid = trim($_POST['sid']);
    $pin = trim($_POST['pin']);
    $stmt = $conn_pin->prepare("SELECT pin FROM students WHERE student_id_number = ? OR full_name = ?");
    $stmt->bind_param("ss", $sid, $sid);
    $stmt->execute();
    $res = $stmt->get_result();
    if ($row = $res->fetch_assoc()) {
        if ($row['pin'] === $pin) {
            echo "OK";
        } else {
            echo "WRONG";
        }
    } else {
        echo "NOT_FOUND";
    }
    exit();
}
// ----------------------------------------------------------------

// --- NEW: EXAM ACCESS LOCK CHECKER ---
if (isset($_GET['check_exam_access'])) {
    $conn_access = new mysqli("localhost", "root", "", "admindashboard_db");
    $sid = trim($_GET['sid'] ?? '');
    $exam_title = trim($_GET['title'] ?? '');
    $device_token = trim($_GET['device_token'] ?? '');

    if ($conn_access->connect_error) {
        echo "ERROR";
        exit();
    }

    if ($sid === '' || $exam_title === '' || $device_token === '') {
        echo "MISSING";
        exit();
    }

    $stmt = $conn_access->prepare("SELECT ls.device_token, ls.last_ping
        FROM live_sessions ls
        JOIN students s ON s.student_id_number = ls.student_id_number
        WHERE (s.student_id_number = ? OR s.full_name = ?)
          AND ls.exam_title = ?
        LIMIT 1");
    $stmt->bind_param("sss", $sid, $sid, $exam_title);
    $stmt->execute();
    $res = $stmt->get_result();

    if ($row = $res->fetch_assoc()) {
        $existing_token = trim($row['device_token'] ?? '');
        $last_ping = strtotime($row['last_ping'] ?? '');
        $is_recent = $last_ping && (time() - $last_ping <= 15);

        if ($existing_token !== '' && $existing_token !== $device_token && $is_recent) {
            echo "IN_USE";
            exit();
        }
    }

    echo "OK";
    exit();
}
// -------------------------------------

// --- NEW: TEACHER LIVE BLOCK STATUS CHECKER ---
if (isset($_GET['check_teacher_block'])) {
    $conn_block = new mysqli("localhost", "root", "", "admindashboard_db");
    $sid = trim($_GET['sid'] ?? '');
    $exam_title = trim($_GET['title'] ?? '');

    if ($conn_block->connect_error || $sid === '' || $exam_title === '') {
        echo "ERROR";
        exit();
    }

    $stmt = $conn_block->prepare("SELECT ls.status
        FROM live_sessions ls
        JOIN students s ON s.student_id_number = ls.student_id_number
        WHERE (s.student_id_number = ? OR s.full_name = ?)
          AND ls.exam_title = ?
        LIMIT 1");
    $stmt->bind_param("sss", $sid, $sid, $exam_title);
    $stmt->execute();
    $res = $stmt->get_result();

    if ($row = $res->fetch_assoc()) {
        echo (trim($row['status'] ?? '') === 'Teacher Blocked') ? 'BLOCKED' : 'ACTIVE';
    } else {
        echo "ACTIVE";
    }
    exit();
}
// ------------------------------------------

// --- NEW: LIVE MONITOR UPDATE HANDLER ---
// Catches the background ping to update the teacher's dashboard
if (isset($_GET['live_ping'])) {
    $conn_live = new mysqli("localhost", "root", "", "admindashboard_db");
    if (!$conn_live->connect_error) {
        $sid = $_GET['sid'] ?? '';
        $title = $_GET['title'] ?? '';
        $prog = $_GET['progress'] ?? '0/0';
        $status = $_GET['status'] ?? 'Active';
        
        $device_token = $_GET['device_token'] ?? '';
        if (!empty($sid) && !empty($device_token)) {
            $lock_stmt = $conn_live->prepare("SELECT device_token, last_ping
                FROM live_sessions
                WHERE student_id_number = (SELECT student_id_number FROM students WHERE student_id_number = ? OR full_name = ? LIMIT 1)
                  AND exam_title = ?
                LIMIT 1");
            $lock_stmt->bind_param("sss", $sid, $sid, $title);
            $lock_stmt->execute();
            $lock_res = $lock_stmt->get_result();

            if ($lock_row = $lock_res->fetch_assoc()) {
                $existing_token = trim($lock_row['device_token'] ?? '');
                $last_ping = strtotime($lock_row['last_ping'] ?? '');
                $is_recent = $last_ping && (time() - $last_ping <= 15);

                if ($existing_token !== '' && $existing_token !== $device_token && $is_recent) {
                    echo "IN_USE";
                    exit();
                }
            }

            $live_stmt = $conn_live->prepare("INSERT INTO live_sessions (student_id_number, student_name, section, exam_title, device_token, status, progress) 
                SELECT student_id_number, full_name, class_name, ?, ?, ?, ? 
                FROM students WHERE student_id_number = ? OR full_name = ?
                ON DUPLICATE KEY UPDATE last_ping = NOW(), device_token = VALUES(device_token), progress = VALUES(progress), status = IF(status IN ('Teacher Blocked', 'Blocked'), status, VALUES(status))");
            $live_stmt->bind_param("ssssss", $title, $device_token, $status, $prog, $sid, $sid);
            $live_stmt->execute();
        }
    }
    exit();
}
// ----------------------------------------

// --- BACKGROUND IP CHECKER ---
if (isset($_GET['check_ip'])) {
    echo $_SERVER['REMOTE_ADDR'];
    exit();
}

// 1. DATABASE CONNECTION
$servername = "localhost";
$username = "root";
$password = ""; 
$dbname = "admindashboard_db";

$conn = new mysqli($servername, $username, $password, $dbname);
if ($conn->connect_error) { 
    die("Database Connection Failed: " . $conn->connect_error); 
}

// 2. GET DATA FROM URL
$title = $_GET['title'] ?? "Quiz/Exam";
$class = $_GET['class'] ?? "";
$pdf_file = $_GET['pdf'] ?? "";

function resolve_uploaded_pdf_file($requested_file, $exam_title) {
    $upload_dir = __DIR__ . DIRECTORY_SEPARATOR . "uploads";
    $candidates = [];

    $requested_file = trim(urldecode((string)$requested_file));
    $exam_title = trim(urldecode((string)$exam_title));

    if ($requested_file !== '') {
        $safe_requested = basename($requested_file);
        $candidates[] = $safe_requested;
        if (!preg_match('/\.pdf$/i', $safe_requested)) {
            $candidates[] = $safe_requested . ".pdf";
        }
    }

    if ($exam_title !== '') {
        $safe_title = basename($exam_title);
        $candidates[] = $safe_title;
        if (!preg_match('/\.pdf$/i', $safe_title)) {
            $candidates[] = $safe_title . ".pdf";
        }
    }

    foreach (array_unique($candidates) as $candidate) {
        if ($candidate !== '' && is_file($upload_dir . DIRECTORY_SEPARATOR . $candidate)) {
            return $candidate;
        }
    }

    if (is_dir($upload_dir)) {
        $files = scandir($upload_dir);
        foreach (array_unique($candidates) as $candidate) {
            foreach ($files as $file) {
                if (strcasecmp($file, $candidate) === 0 && is_file($upload_dir . DIRECTORY_SEPARATOR . $file)) {
                    return $file;
                }
            }
        }
    }

    return $requested_file;
}

// 3. FETCH EXAM DETAILS, ANSWER KEY, TIME LIMIT, STATUS, & FORMAT
$exam_stmt = $conn->prepare("SELECT * FROM exams WHERE title = ? ORDER BY id DESC LIMIT 1");
$exam_stmt->bind_param("s", $title);
$exam_stmt->execute();
$exam_result = $exam_stmt->get_result();

$answer_key_array = [];
$exam_row = null; 
$time_limit_minutes = 60; 
$exam_format = 'Standard'; // Default Format

if ($exam_row = $exam_result->fetch_assoc()) {

    $exam_format = $exam_row['exam_format'] ?? 'Standard';

    // ðŸŒŸ EXAM LOCKOUT SECURITY CHECK ðŸŒŸ
    $status = $exam_row['status'] ?? 'Open';
    if (strcasecmp($status, 'Locked') === 0) {
        echo "<div style='text-align:center; padding:50px; font-family:sans-serif; background:#f4f4f9; min-height:100vh; display:flex; align-items:center; justify-content:center;'>
                <div style='background:white; max-width:500px; padding:40px; border-radius:10px; box-shadow:0 4px 15px rgba(0,0,0,0.1); border-top: 5px solid #ff4757;'>
                    <h1 style='font-size: 60px; margin: 0;'>LOCKED</h1>
                    <h1 style='color:#333; margin-top:10px;'>Exam Locked</h1>
                    <p style='color:#666; font-size: 18px;'>This exam is currently closed by your teacher. Please wait until they unlock it, or ask for assistance.</p>
                </div>
              </div>";
        exit(); // STOP EXECUTION
    }

    if (!empty($exam_row['time_limit'])) {
        $time_limit_minutes = (int)$exam_row['time_limit']; 
    }
    if ($time_limit_minutes <= 0) {
        $time_limit_minutes = 60; 
    }

    $db_key = trim($exam_row['answer_key']);
    if (!empty($db_key)){
        $answer_key_array = str_split($db_key); 
    }
}

$pdf_file = resolve_uploaded_pdf_file($pdf_file, $title);

$q_count = 0;
if (is_array($answer_key_array)) {
    $q_count = count($answer_key_array);
}

if ($q_count == 0) {
    $q_count = 10; 
}

// ðŸŒŸ SMART SCAN: Does this exam HAVE Matching Type (E-J) anywhere in the key? ðŸŒŸ
$exam_has_matching = false;
foreach ($answer_key_array as $char) {
    $c = strtoupper(trim($char));
    if ($c >= 'E' && $c <= 'J') {
        $exam_has_matching = true;
        break;
    }
}

// --- FETCH ALL STUDENTS FOR THE NAME AUTOCOMPLETE ---
$student_datalist = "";
$stu_query = "SELECT student_id_number, full_name FROM students ORDER BY full_name ASC";
if ($stu_res = $conn->query($stu_query)) {
    while ($s = $stu_res->fetch_assoc()) {
        $student_datalist .= "<option value='" . htmlspecialchars($s['student_id_number']) . "'>" . htmlspecialchars($s['full_name']) . "</option>\n";
    }
}

// 4. HANDLE SUBMISSION, AUTO-GRADING, & REVIEW SHEET
if ($_SERVER["REQUEST_METHOD"] == "POST" && !isset($_POST['check_pin_ajax'])) {
    $sid_number = trim($_POST['sid']); 
    $student_pin = trim($_POST['pin'] ?? ''); 
    $is_cheater = trim($_POST['is_cheater'] ?? '0');
    
    $find_stmt = $conn->prepare("SELECT id, student_id_number, full_name, pin FROM students WHERE student_id_number = ? OR full_name = ?");
    $find_stmt->bind_param("ss", $sid_number, $sid_number);
    $find_stmt->execute();
    $result = $find_stmt->get_result();
    
    if ($row = $result->fetch_assoc()) {
        $student_db_id = $row['id'];
        $actual_student_id_num = $row['student_id_number']; 
        $actual_student_name = $row['full_name'];
        $actual_pin = $row['pin'];

        if ($is_cheater !== '1' && $student_pin !== $actual_pin) {
            echo "<script>alert('Incorrect PIN! Please try again or ask your teacher for your PIN.'); window.history.back();</script>";
            exit();
        }
        
        $check_stmt = $conn->prepare("SELECT id FROM exam_scores WHERE student_id = ? AND exam_name = ?");
        $check_stmt->bind_param("is", $student_db_id, $title);
        $check_stmt->execute();
        $check_res = $check_stmt->get_result();
        
        if ($check_res->num_rows > 0) {
            echo "<script>alert('You have already submitted this exam. Multiple submissions are not allowed.'); window.history.back();</script>";
            exit();
        }

        $exam_db_id = $exam_row['id'] ?? 0; 
        $score = 0;
        
        $ans_stmt = $conn->prepare("INSERT INTO student_answers (student_id, exam_id, question_number, student_answer, correct_answer, is_correct) VALUES (?, ?, ?, ?, ?, ?)");
        
        $review_html = "<div id='review-section' style='display:none; text-align:left; margin-top:20px; padding:20px; border:1px solid #ddd; background:#fff; border-radius:8px; box-shadow: inset 0 2px 4px rgba(0,0,0,0.05);'>
                            <h3 style='margin-top:0; color:#2b5876; border-bottom:2px solid #eee; padding-bottom:10px;'>Your Answers</h3>
                            <div style='max-height: 400px; overflow-y: auto; padding-right: 10px;'>";

        for ($i = 0; $i < $q_count; $i++) {
            $q_num = $i + 1;
            $student_ans = trim($_POST['q'.$q_num] ?? "-"); 
            $correct_ans = trim($answer_key_array[$i] ?? "");
            
            $is_correct = 0;
            if (strtoupper($student_ans) === strtoupper($correct_ans)) {
                $score++; 
                $is_correct = 1;
                $review_html .= "<div style='margin-bottom:10px; padding:8px; background:#e8f5e9; border-left:4px solid #4caf50; border-radius:4px;'>
                                    <b>Question $q_num:</b> You answered <b style='color:#2e7d32;'>$student_ans</b> ✔ Correct 
                                 </div>";
            } else {
                $review_html .= "<div style='margin-bottom:10px; padding:8px; background:#ffebee; border-left:4px solid #f44336; border-radius:4px;'>
                                    <b>Question $q_num:</b> You answered <b style='color:#c62828;'>$student_ans</b> ❌ Wrong
                                 </div>";
            }
            
            $ans_stmt->bind_param("iiissi", $student_db_id, $exam_db_id, $q_num, $student_ans, $correct_ans, $is_correct);
            $ans_stmt->execute();
        }
        
        $review_html .= "</div></div>";

        $final_score_string = $score . "/" . $q_count;
        
        $ins = $conn->prepare("INSERT INTO exam_scores (student_id, exam_name, score) VALUES (?, ?, ?)");
        $ins->bind_param("iss", $student_db_id, $title, $final_score_string);
        
        if ($ins->execute()) {

            // ðŸŒŸ BLOCKED TRACKER UPDATE: Don't delete them if they are a cheater!
            if ($is_cheater === '1') {
                // Change their status to Blocked on the dashboard
                $conn->query("UPDATE live_sessions SET status = 'Blocked', progress = 'Terminated' WHERE student_id_number = '$actual_student_id_num' OR student_name = '$actual_student_name'");

                echo "<div style='text-align:center; padding:50px; font-family:sans-serif; background:#111; min-height:100vh; display:flex; align-items:center; justify-content:center;'>
                        <div style='background:white; max-width:600px; padding:40px; border-radius:10px; box-shadow:0 0 30px rgba(255,0,0,0.8); border: 5px solid red;'>
                            <h1 style='font-size: 80px; margin: 0;'>BLOCKED</h1>
                            <h1 style='color:red; margin-top:10px;'>EXAM TERMINATED</h1>
                            <h3 style='color:#333;'>You have been permanently blocked for violating exam rules.</h3>
                            <p style='color:#666; font-size:18px;'>Your incomplete test has been submitted.</p>
                            <h2>Score Logged: <span style='color:red;'>$final_score_string</span></h2>
                        </div>
                      </div>";
            } else {
                // Normal submission -> Remove them from Live Monitor
                $conn->query("DELETE FROM live_sessions WHERE student_id_number = '$actual_student_id_num' OR student_name = '$actual_student_name'");

                echo "<div style='min-height:100vh; display:flex; align-items:center; justify-content:center; padding:24px; background:linear-gradient(180deg, #eef4ff 0%, #f8fafc 100%); font-family:sans-serif;'>
                        <div style='background:white; width:100%; max-width:680px; margin:0 auto; padding:36px; border-radius:22px; box-shadow:0 22px 60px rgba(29,43,83,0.12); border:1px solid rgba(29,43,83,0.08); text-align:center;'>
                            <div style='width:74px; height:74px; margin:0 auto 18px auto; border-radius:50%; background:#e8f7ee; color:#1f8f4d; display:flex; align-items:center; justify-content:center; font-size:18px; font-weight:bold;'>DONE</div>
                            <h1 style='color:green; margin-top:0;'>Submitted & Graded Successfully!</h1>
                            <h3 style='color:#555; margin:0 0 12px 0;'>Great job, " . htmlspecialchars($actual_student_name) . ".</h3>
                            <h2 style='margin:0 0 10px 0;'>Your Score: <span style='color:#2b5876; font-size:40px;'>$final_score_string</span></h2>
                            <p style='color:#666; font-size:17px; margin-bottom:22px;'>Your teacher has received your score.</p>
                            <button onclick='document.getElementById(\"review-section\").style.display=\"block\"; this.style.display=\"none\";' 
                                    style='padding:12px 24px; background:#1d2b53; color:white; border:none; border-radius:999px; font-size:15px; font-weight:bold; cursor:pointer;'>
                                View My Answers
                            </button>
                            $review_html
                        </div>
                      </div>";
            }
            exit();
        } else {
            echo "<script>alert('Error saving score: " . addslashes($conn->error) . "'); window.history.back();</script>";
            exit();
        }

    } else {
        echo "<script>alert('Student Not Found! Please make sure you typed your exact Name or ID correctly.'); window.history.back();</script>";
        exit();
    }
}
?>
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Student Portal - Exam</title>
    
    <script src="pdf.min.js"></script>
    
    <style>
        body { 
            font-family: sans-serif; background: #f4f4f9; margin: 0; padding: 10px; 
            -webkit-user-select: none; 
            -moz-user-select: none; 
            -ms-user-select: none; 
            user-select: none; 
        }
        .card { background: white; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; overflow: hidden; }
        
        #pdf-viewer-container { width: 100%; height: 55vh; overflow-y: auto; background-color: #525659; text-align: center; position: relative; }
        
        #pdf-viewer-container::after {
            content: "";
            position: absolute;
            top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0);
            z-index: 10;
        }

        canvas { max-width: 100%; height: auto; margin: 10px auto; box-shadow: 0px 4px 6px rgba(0,0,0,0.3); display: block; }
        
        .q-row { display: flex; flex-direction: column; padding: 15px; border-bottom: 1px solid #eee; }
        .q-title { font-size: 18px; margin-bottom: 8px; }
        .question-text {
            color: #222;
            font-size: 16px;
            line-height: 1.45;
            margin: 0 0 8px 0;
            white-space: pre-wrap;
        }
        .question-text.pending {
            color: #777;
            font-style: italic;
        }
        
        /* ðŸŒŸ 2x2 GRID FIX: Changed to CSS Grid ðŸŒŸ */
        .options { 
            display: grid; 
            /* Columns will now be handled dynamically via inline style, but this is the fallback */
            grid-template-columns: 1fr 1fr; 
            gap: 10px; 
            width: 100%; 
            margin-top: 10px;
        }

        .options label { 
            cursor: pointer; display: flex; align-items: center; justify-content: flex-start; gap: 10px;
            background: #fff; padding: 12px 15px; border: 2px solid #ddd; 
            border-radius: 8px; font-size: 16px; font-weight: bold;
            transition: 0.2s ease-in-out;
        }
        .option-text { overflow-wrap: anywhere; text-align: left; }
        @media (max-width: 640px) {
            .options { grid-template-columns: 1fr !important; }
        }
        .options label:has(input[type="radio"]:checked) {
            background: #e3f2fd; border-color: #2b5876; color: #2b5876;
        }
        input[type="radio"] { transform: scale(1.5); margin:0; }
        
        #timer-banner {
            background: #ff4757; color: white; padding: 15px; text-align: center; 
            font-weight: bold; font-size: 18px; border-radius: 5px; margin-bottom: 20px; 
            position: sticky; top: 0; z-index: 1000; box-shadow: 0 4px 6px rgba(0,0,0,0.2);
        }

        .nav-btn {
            padding: 12px 20px; background: #2b5876; color: white; border: none; 
            border-radius: 5px; font-weight: bold; cursor: pointer; font-size: 14px;
        }
        .nav-btn:hover { background: #1a364b; }
        .nav-btn:disabled { background: #ccc; cursor: not-allowed; }

        #force-submit-overlay {
            position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(255, 255, 255, 0.98); z-index: 9999;
            display: none; flex-direction: column; justify-content: center; align-items: center;
            text-align: center; font-family: sans-serif; padding: 20px;
            box-sizing: border-box;
        }

        @media print {
            body * { visibility: hidden !important; }
            body::before {
                content: "Printing and screenshots are not allowed during this exam.";
                visibility: visible !important;
                display: block;
                padding: 40px;
                color: #c0392b;
                font: bold 22px sans-serif;
                text-align: center;
            }
        }

        /* Modal Styles for PIN Input */
        #pin-modal {
            position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0, 0, 0, 0.7); z-index: 10000;
            display: none; flex-direction: column; justify-content: center; align-items: center;
        }
        .modal-content {
            background: white; padding: 30px; border-radius: 10px; text-align: center;
            width: 90%; max-width: 400px; box-shadow: 0 4px 15px rgba(0,0,0,0.2);
        }
    </style>
</head>
<body>

    <datalist id="student_list_datalist">
        <?php echo $student_datalist; ?>
    </datalist>

    <div id="start-screen" style="max-width: 500px; margin: 40px auto;">
        <div class="card" style="padding: 40px; text-align: center;">
            <img src="logo.png" alt="School Logo" style="max-width: 120px; margin-bottom: 15px; border-radius: 8px;">
            <h2 style="color: #2b5876;">Welcome</h2>
            <p style="color: #666; margin-bottom: 20px;">Please enter your Name or Student ID to begin.</p>
            
            <input type="text" id="initial-sid" list="student_list_datalist" placeholder="Search Name or ID" autocomplete="off"
                   style="width:100%; padding:12px; margin-bottom:20px; border:2px solid #2b5876; border-radius:5px; box-sizing:border-box; font-size:16px;">

            <input type="password" id="initial-pin" placeholder="Enter PIN" autocomplete="off"
                   style="width:100%; padding:12px; margin-bottom:20px; border:2px solid #2b5876; border-radius:5px; box-sizing:border-box; font-size:16px; text-align:center; letter-spacing:4px;">
            
            <button onclick="startExam()" style="width: 100%; padding: 15px; background: #2b5876; color: white; border: none; font-size: 18px; font-weight: bold; cursor: pointer; border-radius: 5px;">
                Open
            </button>
        </div>
    </div>

    <div id="exam-container" style="display: none;">

        <div id="force-submit-overlay">
            <h1 id="overlay-icon" style="color:red; font-size: 50px; margin-bottom: 5px;">WARNING</h1>
            <h2 id="overlay-message" style="color:#333;">Action Triggered</h2>
            <p id="overlay-sub" style="font-size:18px;">Your exam answers are being automatically submitted to the server right now. Please wait...</p>
        </div>

        <div id="timer-banner">
            Time Remaining: <span id="timer-display">Loading...</span>
        </div>

        <div class="card" style="display:none;">
            <div style="background:#2b5876; color:white; padding:10px; text-align:center; font-weight:bold;">
                EXAM/QUIZ FILE (Page <span id="pdf-page-num">1</span>)
            </div>
            <div id="pdf-viewer-container"></div>
        </div>

        <div class="card" style="padding: 20px;">
            
            <h2 style="text-align:center; color:#2b5876; margin-top:0;"><?php echo htmlspecialchars($title); ?></h2>
            <?php if ($class): ?><p style="text-align:center; color:#666; margin-top:-10px;">Section: <?php echo htmlspecialchars($class); ?></p><?php endif; ?>
            
            <form id="examForm" method="POST">
                
                <input type="hidden" name="sid" id="final-sid">
                <input type="hidden" name="pin" id="final-pin">
                <input type="hidden" name="is_cheater" id="is-cheater" value="0">
                
                <div style="background:#eee; padding:10px; text-align:center; font-weight:bold; border-radius:5px; margin-bottom:10px;">
                    Answer Sheet (Questions 1-<?php echo $q_count; ?>)
                </div>
                
                <?php 
                    $questions_per_page = 5; 
                    $total_pages = ceil($q_count / $questions_per_page);
                    
                    for ($p = 1; $p <= $total_pages; $p++): 
                        $start_q = (($p - 1) * $questions_per_page) + 1;
                        $end_q = min($p * $questions_per_page, $q_count);
                ?>
                    <div class="question-page" id="page-<?php echo $p; ?>" style="display: <?php echo ($p == 1) ? 'block' : 'none'; ?>;">
                        <?php for ($i = $start_q; $i <= $end_q; $i++): ?>
                            <div class="q-row">
                                <b class="q-title">Question #<?php echo $i; ?></b>
                                <p class="question-text pending" id="question-text-<?php echo $i; ?>">Loading question from exam file...</p>
                                
                                    <?php 
                                        // ðŸŒŸ DYNAMIC FORMAT SELECTOR (A-J logic) ðŸŒŸ
                                        $q_index = $i - 1;
                                        $expected_answer = isset($answer_key_array[$q_index]) ? strtoupper(trim($answer_key_array[$q_index])) : '';
                                        
                                        $format_lower = strtolower($exam_format);
                                        
                                        // 1. Is it explicitly True/False?
                                        if ($expected_answer === 'T' || $expected_answer === 'F' || strpos($format_lower, 'true') !== false || strpos($format_lower, 't/f') !== false) {
                                            $choices = ['T', 'F'];
                                            $grid_cols = "1fr 1fr";
                                        } 
                                        // 2. Is it part of a Matching set (Answer E or higher), OR past Q10 in a matching exam, OR explicitly Extended/Completion?
                                        elseif ($expected_answer >= 'E' || ($exam_has_matching && $i > 10) || strpos($format_lower, 'extended') !== false || strpos($format_lower, 'completion') !== false || strpos($format_lower, 'matching') !== false) {
                                            $choices = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'];
                                            $grid_cols = "1fr 1fr 1fr 1fr 1fr"; // 5 columns for A-J
                                        } 
                                        // 3. Fallback to Standard (A-D)
                                        else {
                                            $choices = ['A', 'B', 'C', 'D'];
                                            $grid_cols = "1fr 1fr";
                                        }
                                    ?>
                                    
                                <div class="options" style="grid-template-columns: <?php echo $grid_cols; ?>;">    
                                    <?php foreach($choices as $opt): ?>
                                        <label>
                                            <input type="radio" name="q<?php echo $i; ?>" value="<?php echo $opt; ?>"> 
                                            <span class="option-text" data-question="<?php echo $i; ?>" data-option="<?php echo $opt; ?>"><?php 
                                                if ($opt === 'T') echo "True";
                                                elseif ($opt === 'F') echo "False";
                                                else echo $opt; 
                                            ?></span>
                                        </label>
                                    <?php endforeach; ?>
                                </div>
                            </div>
                        <?php endfor; ?>
                        <p style="text-align:center; font-size:12px; color:#888;">Page <?php echo $p; ?> of <?php echo $total_pages; ?></p>
                    </div>
                <?php endfor; ?>

                <div style="display:flex; justify-content:space-between; margin-top:20px;">
                    <button type="button" id="prevBtn" class="nav-btn" onclick="changePage(-1)" style="display:none;">Previous</button>
                    <button type="button" id="nextBtn" class="nav-btn" onclick="changePage(1)" style="<?php echo ($total_pages > 1) ? 'display:block;' : 'display:none;'; ?> margin-left:auto;">Next</button>
                </div>
                
                <button type="button" onclick="showPinModal()" id="submitBtn" style="<?php echo ($total_pages > 1) ? 'display:none;' : 'display:block;'; ?> width:100%; padding:15px; background:#2b5876; color:white; border:none; border-radius:5px; font-weight:bold; font-size:16px; margin-top:20px; cursor:pointer;">
                    SUBMIT ALL ANSWERS
                </button>
            </form>
        </div>
    </div>

    <div id="pin-modal">
        <div class="modal-content">
            <h2 style="color: #2b5876; margin-top: 0;">Confirm Submission</h2>
            <p style="color: #666;">Please enter your 4-digit PIN to securely submit your exam.</p>
            
            <input type="password" id="modal-pin-input" placeholder="Enter PIN" autocomplete="off"
                   style="width:100%; padding:12px; margin-bottom:15px; border:2px solid #2b5876; border-radius:5px; box-sizing:border-box; text-align:center; font-size: 20px; letter-spacing: 5px;">
            
            <p id="pin-error-msg" style="color: red; font-weight: bold; display: none; margin-bottom: 10px;"></p>
            
            <button onclick="verifyPinAndSubmit()" id="confirm-btn" style="width: 100%; padding: 15px; background: #28a745; color: white; border: none; font-size: 16px; font-weight: bold; cursor: pointer; border-radius: 5px; margin-bottom: 10px;">
                Verify & Submit
            </button>
            <button onclick="hidePinModal()" style="width: 100%; padding: 10px; background: #dc3545; color: white; border: none; font-size: 16px; font-weight: bold; cursor: pointer; border-radius: 5px;">
                Cancel
            </button>
        </div>
    </div>

    <script>
        let isExamStarted = false;
        let currentPage = 1;
        const totalPages = <?php echo $total_pages; ?>;
        const examForm = document.getElementById('examForm');
        let isExamLocked = false;
        let isTeacherBlocked = false;
        let timerInterval;
        let remoteUnblockInterval = null;

        
        const examTitle = <?php echo json_encode($title); ?>;
        const questionsPerPage = <?php echo $questions_per_page; ?>;
        const allRadioButtons = document.querySelectorAll('input[type="radio"]');
        let currentDeviceToken = localStorage.getItem('exam_device_token');
        if (!currentDeviceToken) {
            currentDeviceToken = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : ('device-' + Date.now() + '-' + Math.random().toString(16).slice(2));
            localStorage.setItem('exam_device_token', currentDeviceToken);
        }
        
        // Anti-Cheat Variables
        let cheatWarnings = 0;
        const maxWarnings = 3;
        let lastActiveQ = 1;
        let lastResizeWarningAt = 0;
        const resizeWarningCooldownMs = 8000;
        const minExamWidthRatio = 0.88;
        const minExamHeightRatio = 0.78;
        let fullscreenWasActive = false;

        function isMobileLikeDevice() {
            return window.matchMedia('(pointer: coarse)').matches || Math.min(screen.width || 0, screen.height || 0) <= 768;
        }

        function getExamStoragePrefix(sid) {
            return 'exam_' + examTitle + '_' + sid;
        }

        function getAnswerStorageKey(sid, questionName) {
            return getExamStoragePrefix(sid) + '_' + questionName;
        }

        function getProgressStorageKey(sid) {
            return getExamStoragePrefix(sid) + '_progress';
        }

        function getActiveStudentId() {
            return document.getElementById('final-sid').value.trim() || document.getElementById('initial-sid').value.trim();
        }

        function recordCheatWarning(reason) {
            if (isExamLocked || !isExamStarted) return;

            let sid = document.getElementById('final-sid').value.trim();
            cheatWarnings++;
            localStorage.setItem('cheat_warnings_' + examTitle + '_' + sid, cheatWarnings);
            sendLivePing(reason || "Anti-Cheat Warning");

            if (cheatWarnings >= maxWarnings) {
                forceSubmitExam("Cheating Limit Reached", true);
            } else {
                alert("WARNING (" + cheatWarnings + "/" + maxWarnings + ")\n" + reason + "\nIf you reach 3 warnings, you will be permanently blocked.");
            }
        }

        function isFullscreenActive() {
            const active = !!(document.fullscreenElement || document.webkitFullscreenElement || document.msFullscreenElement);
            if (active) fullscreenWasActive = true;
            return active;
        }

        function isFullscreenSupported() {
            const target = document.documentElement;
            return !!(target.requestFullscreen || target.webkitRequestFullscreen || target.msRequestFullscreen);
        }

        function enterFullscreenMode() {
            const target = document.documentElement;
            const requestFullscreen = target.requestFullscreen || target.webkitRequestFullscreen || target.msRequestFullscreen;

            if (!requestFullscreen) {
                return Promise.resolve();
            }

            const fullscreenResult = requestFullscreen.call(target);
            if (fullscreenResult && typeof fullscreenResult.catch === 'function') {
                return fullscreenResult.catch(() => Promise.resolve());
            }

            return Promise.resolve();
        }

        function isExamWindowSuspiciouslySmall() {
            const availableWidth = screen.availWidth || screen.width || window.innerWidth;
            const availableHeight = screen.availHeight || screen.height || window.innerHeight;
            return window.innerWidth < (availableWidth * minExamWidthRatio) || window.innerHeight < (availableHeight * minExamHeightRatio);
        }

        function checkSplitScreenRisk() {
            if (isExamLocked || !isExamStarted || isTeacherBlocked) return;
            if (isMobileLikeDevice()) return;

            const now = Date.now();
            if (now - lastResizeWarningAt < resizeWarningCooldownMs) return;

            const fullscreenProblem = isFullscreenSupported() && fullscreenWasActive && !isFullscreenActive();
            if (fullscreenProblem || isExamWindowSuspiciouslySmall()) {
                lastResizeWarningAt = now;
                recordCheatWarning("Split-screen or window resize detected. Keep the exam as the main active window.");
            }
        }

        function saveProgressState() {
            const sid = getActiveStudentId();
            if (!sid) return;

            const state = {
                currentPage: currentPage,
                lastActiveQ: lastActiveQ,
                timestamp: Date.now()
            };
            localStorage.setItem(getProgressStorageKey(sid), JSON.stringify(state));
        }

        function restoreSavedProgress(sid) {
            allRadioButtons.forEach(radio => {
                const savedAnswer = localStorage.getItem(getAnswerStorageKey(sid, radio.name));
                radio.checked = savedAnswer === radio.value;
            });

            const rawState = localStorage.getItem(getProgressStorageKey(sid));
            let targetPage = 1;

            if (rawState) {
                try {
                    const savedState = JSON.parse(rawState);
                    if (savedState && Number.isInteger(savedState.currentPage) && savedState.currentPage >= 1 && savedState.currentPage <= totalPages) {
                        targetPage = savedState.currentPage;
                    }
                    if (savedState && Number.isInteger(savedState.lastActiveQ) && savedState.lastActiveQ >= 1) {
                        lastActiveQ = savedState.lastActiveQ;
                    }
                } catch (e) {}
            }

            goToPage(targetPage, false);
            sendLivePing();
        }

        document.addEventListener("change", function(e) {
            if (e.target.type === "radio") {
                const sid = getActiveStudentId();
                lastActiveQ = parseInt(e.target.name.replace('q', ''), 10) || lastActiveQ;
                if (sid) {
                    localStorage.setItem(getAnswerStorageKey(sid, e.target.name), e.target.value);
                }
                saveProgressState();
                sendLivePing();
            }
        });

        // --- STEP 1: OPEN EXAM WITH NAME ---
        function startExam() {
            let sid = document.getElementById('initial-sid').value.trim();
            let pin = document.getElementById('initial-pin').value.trim();
            if (!sid) {
                alert("Please enter your Name or Student ID first.");
                return;
            }
            if (!pin) {
                alert("Please enter your PIN first.");
                return;
            }

            enterFullscreenMode();

            fetch(`?check_exam_access=1&sid=${encodeURIComponent(sid)}&title=${encodeURIComponent(examTitle)}&device_token=${encodeURIComponent(currentDeviceToken)}`)
                .then(res => res.text())
                .then(result => {
                    let access = result.trim();
                    if (access === "IN_USE") {
                        alert("This student is already taking this exam on another device.");
                        return;
                    }
                    if (access !== "OK") {
                        alert("Could not verify exam access right now. Please try again.");
                        return;
                    }

                    let formData = new FormData();
                    formData.append('check_pin_ajax', '1');
                    formData.append('sid', sid);
                    formData.append('pin', pin);

                    fetch('', { method: 'POST', body: formData })
                        .then(res => res.text())
                        .then(txt => {
                            let pinResult = txt.trim();
                            if (pinResult === 'OK') {
                                document.getElementById('final-sid').value = sid;
                                document.getElementById('final-pin').value = pin;

                                if (localStorage.getItem('exam_blocked_' + examTitle + '_' + sid) === 'true') {
                                    showPermanentBlockScreen();
                                    return;
                                }

                                cheatWarnings = parseInt(localStorage.getItem('cheat_warnings_' + examTitle + '_' + sid) || '0');
                                document.getElementById('start-screen').style.display = 'none';
                                document.getElementById('exam-container').style.display = 'block';
                                isExamStarted = true;
                                checkSplitScreenRisk();
                                startTimer();

                                try {
                                    if (pdfDoc === null) {
                                        loadPDF();
                                    }
                                } catch (error) {
                                    console.log("PDF Viewer Error: ", error);
                                }

                                restoreSavedProgress(sid);
                            } else if (pinResult === 'NOT_FOUND') {
                                alert("Student record not found. Check the Name/ID.");
                            } else {
                                alert("Incorrect PIN. Please try again.");
                            }
                        })
                        .catch(() => {
                            alert("Network error while verifying the PIN.");
                        });
                })
                .catch(() => {
                    alert("Network error while checking if this student is already in the exam.");
                });
            return;
            
            document.getElementById('final-sid').value = sid;
            
            // ðŸŒŸ Check if this student is already PERMANENTLY BLOCKED ðŸŒŸ
            if (localStorage.getItem('exam_blocked_' + examTitle + '_' + sid) === 'true') {
                showPermanentBlockScreen();
                return;
            }

            // Restore warnings if they refreshed
            cheatWarnings = parseInt(localStorage.getItem('cheat_warnings_' + examTitle + '_' + sid) || '0');
            
            document.getElementById('start-screen').style.display = 'none';
            document.getElementById('exam-container').style.display = 'block';
            
            isExamStarted = true;
            checkSplitScreenRisk();
            
            // ðŸŒŸ FIX: Start the timer first so it never breaks! ðŸŒŸ
            startTimer();
            
            // ðŸŒŸ FIX: Catch any PDF errors so they don't break the page ðŸŒŸ
            try {
                if (pdfDoc === null) {
                    loadPDF();
                }
            } catch (error) {
                console.log("PDF Viewer Error: ", error);
            }
            
            restoreSavedProgress(sid);
        }

        // --- STEP 1b: PERMANENT BLOCK SCREEN ---
        function showPermanentBlockScreen() {
            document.getElementById('start-screen').style.display = 'none';
            document.getElementById('exam-container').style.display = 'block';
            
            let overlay = document.getElementById('force-submit-overlay');
            overlay.style.display = 'flex';
            overlay.style.background = 'rgba(255, 0, 0, 0.95)'; // Deep red background
            
            overlay.innerHTML = `
                <h1 style='color:white; font-size: 50px; margin-bottom: 5px;'>BLOCKED</h1>
                <h2 style='color:white;'>You have been locked out of this exam.</h2>
                <p style='color:white; font-size:18px; margin-bottom: 30px;'>Please wait for your teacher to unlock you from the dashboard.</p>
                <div style='color:#f0ad4e; font-weight:bold;'>Checking for unlock signal...</div>
                
                <button onclick="teacherUnblock()" style="margin-top:20px; padding: 10px 20px; background: #222; color: white; border: 2px solid #555; border-radius: 5px; cursor: pointer;">
                    Manual Teacher Override
                </button>
            `;

            // ðŸŒŸ BLOCKED TRACKER: Ping the server immediately so the teacher sees they are blocked!
            let sid = document.getElementById('final-sid').value.trim();
            if (sid) {
                fetch(`?live_ping=1&sid=${encodeURIComponent(sid)}&title=${encodeURIComponent(examTitle)}&device_token=${encodeURIComponent(currentDeviceToken)}&status=Blocked&progress=Terminated`).catch(e=>{});
                
                // ðŸŒŸ THE MAGIC: Checks database every 5 seconds to see if teacher unblocked them!
                remoteUnblockInterval = setInterval(() => {
                    fetch(`?check_block_status=1&sid=${encodeURIComponent(sid)}&title=${encodeURIComponent(examTitle)}`)
                    .then(res => res.text())
                    .then(txt => {
                        if (txt.trim() === "CLEARED") {
                            clearInterval(remoteUnblockInterval);
                            localStorage.removeItem('exam_blocked_' + examTitle + '_' + sid);
                            localStorage.removeItem('cheat_warnings_' + examTitle + '_' + sid);
                            alert("Your teacher has remotely unlocked you! You may now continue.");
                            window.location.reload();
                        }
                    }).catch(e=>{});
                }, 5000);
            }
        }

        function showTeacherBlockedOverlay() {
            let overlay = document.getElementById('force-submit-overlay');
            overlay.style.display = 'flex';
            overlay.style.background = 'rgba(255, 140, 0, 0.96)';
            overlay.innerHTML = `
                <h1 style='color:white; font-size: 48px; margin-bottom: 8px;'>Teacher Blocked</h1>
                <h2 style='color:white;'>Your teacher has paused your exam.</h2>
                <p style='color:white; font-size:18px; margin-bottom: 20px;'>Please wait until you are unblocked to continue.</p>
                <button onclick="teacherUnblock()" style="padding: 10px 20px; background: #222; color: white; border: 2px solid #555; border-radius: 5px; cursor: pointer;">
                    Manual Teacher Override
                </button>
            `;
            document.body.style.filter = "blur(15px)";
        }

        function hideTeacherBlockedOverlay() {
            let overlay = document.getElementById('force-submit-overlay');
            overlay.style.display = 'none';
            overlay.style.background = 'rgba(255, 255, 255, 0.98)';
            overlay.innerHTML = `
                <h1 id="overlay-icon" style="color:red; font-size: 50px; margin-bottom: 5px;">WARNING</h1>
                <h2 id="overlay-message" style="color:#333;">Action Triggered</h2>
                <p id="overlay-sub" style="font-size:18px;">Your exam answers are being automatically submitted to the server right now. Please wait...</p>
            `;
            document.body.style.filter = "none";
        }

        function pollTeacherBlockStatus() {
            if (!isExamStarted || isExamLocked) return;

            let sid = document.getElementById('final-sid').value.trim();
            if (!sid) return;

            fetch(`?check_teacher_block=1&sid=${encodeURIComponent(sid)}&title=${encodeURIComponent(examTitle)}`)
                .then(res => res.text())
                .then(txt => {
                    let state = txt.trim();
                    if (state === "BLOCKED" && !isTeacherBlocked) {
                        isTeacherBlocked = true;
                        showTeacherBlockedOverlay();
                    } else if (state === "ACTIVE" && isTeacherBlocked) {
                        isTeacherBlocked = false;
                        hideTeacherBlockedOverlay();
                    }
                })
                .catch(() => {});
        }

        // --- NEW: TEACHER UNBLOCK FUNCTION ---
        function teacherUnblock() {
            let attempt = prompt("Enter Teacher Password:");
            if (attempt === null) return;

            let sid = getActiveStudentId();
            if (!sid) {
                alert("Student ID/Name not found.");
                return;
            }

            let formData = new FormData();
            formData.append('teacher_override_unblock', '1');
            formData.append('sid', sid);
            formData.append('title', examTitle);
            formData.append('password', attempt);

            fetch('', { method: 'POST', body: formData })
            .then(res => res.text())
            .then(txt => {
                let result = txt.trim();
                if (result === 'OK') {
                    localStorage.removeItem('exam_blocked_' + examTitle + '_' + sid);
                    localStorage.removeItem('cheat_warnings_' + examTitle + '_' + sid);
                    alert("Override successful. Reloading...");
                    window.location.reload();
                } else if (result === 'WRONG_PASSWORD') {
                    alert("Incorrect Password.");
                } else {
                    alert("Unblock failed. Student record not found.");
                }
            })
            .catch(() => {
                alert("Network error. Could not connect to server.");
            });
        }

        // --- STEP 2: SHOW PIN MODAL ON SUBMIT ---
        function showPinModal() {
            if (isTeacherBlocked) {
                alert("Your teacher has blocked this exam session.");
                return;
            }
            if (!validateAllPages()) {
                return;
            }
            if (document.getElementById('final-pin').value.trim()) {
                clearLocalStorageAnswers();
                isExamLocked = true;
                examForm.submit();
                return;
            }
            document.getElementById('pin-modal').style.display = 'flex';
        }

        function hidePinModal() {
            document.getElementById('pin-modal').style.display = 'none';
            document.getElementById('pin-error-msg').style.display = 'none';
            document.getElementById('modal-pin-input').value = '';
        }

        // --- STEP 3: VERIFY PIN VIA AJAX THEN SUBMIT ---
        function verifyPinAndSubmit() {
            let pin = document.getElementById('modal-pin-input').value.trim();
            let sid = document.getElementById('final-sid').value.trim();
            let errorText = document.getElementById('pin-error-msg');
            let btn = document.getElementById('confirm-btn');

            if (!pin) {
                errorText.innerText = "Please enter your PIN.";
                errorText.style.display = 'block';
                return;
            }

            btn.innerText = "Verifying...";
            btn.disabled = true;

            let formData = new FormData();
            formData.append('check_pin_ajax', '1');
            formData.append('sid', sid);
            formData.append('pin', pin);

            fetch('', { method: 'POST', body: formData })
            .then(res => res.text())
            .then(txt => {
                let result = txt.trim();
                if (result === 'OK') {
                    document.getElementById('final-pin').value = pin;
                    clearLocalStorageAnswers();
                    isExamLocked = true;
                    examForm.submit();
                } else if (result === 'NOT_FOUND') {
                    errorText.innerText = "Student record not found! Check Name/ID.";
                    errorText.style.display = 'block';
                    btn.innerText = "Verify & Submit";
                    btn.disabled = false;
                } else {
                    errorText.innerText = "Incorrect PIN! Please try again.";
                    errorText.style.display = 'block';
                    btn.innerText = "Verify & Submit";
                    btn.disabled = false;
                }
            }).catch(e => {
                errorText.innerText = "Network error. Try again.";
                errorText.style.display = 'block';
                btn.innerText = "Verify & Submit";
                btn.disabled = false;
            });
        }

        function goToPage(targetPageNum, shouldPersist = true) {
            if (targetPageNum < 1 || targetPageNum > totalPages) return;

            let targetPageEl = document.getElementById('page-' + targetPageNum);
            if (!targetPageEl) return;

            let currentPageEl = document.getElementById('page-' + currentPage);
            if (currentPageEl) {
                currentPageEl.style.display = 'none';
            }

            currentPage = targetPageNum;
            targetPageEl.style.display = 'block';

            let checkedOnPage = targetPageEl.querySelector('input[type="radio"]:checked');
            if (checkedOnPage) {
                lastActiveQ = parseInt(checkedOnPage.name.replace('q', ''), 10) || lastActiveQ;
            } else {
                lastActiveQ = ((currentPage - 1) * questionsPerPage) + 1;
            }

            if (pdfDoc) {
                let targetPdfPage = currentPage > pdfDoc.numPages ? pdfDoc.numPages : currentPage;
                renderPdfPage(targetPdfPage);
            }

            document.getElementById('prevBtn').style.display = (currentPage === 1) ? 'none' : 'block';

            if (currentPage === totalPages) {
                document.getElementById('nextBtn').style.display = 'none';
                document.getElementById('submitBtn').style.display = 'block';
            } else {
                document.getElementById('nextBtn').style.display = 'block';
                document.getElementById('submitBtn').style.display = 'none';
            }

            if (shouldPersist) {
                saveProgressState();
                sendLivePing();
            }
        }

        function changePage(direction) {
            if (isTeacherBlocked) return;
            goToPage(currentPage + direction);
        }

        function validateAllPages() {
            const allRows = document.querySelectorAll('.q-row');
            
            for (let row of allRows) {
                const isChecked = row.querySelector('input[type="radio"]:checked');
                if (!isChecked) {
                    alert("Warning: You have unanswered questions. Please go back and check all pages.");
                    return false;
                }
            }
            return true;
        }

        function clearLocalStorageAnswers() {
            const sid = document.getElementById('final-sid').value.trim();
            if (!sid) return;

            allRadioButtons.forEach(radio => {
                localStorage.removeItem(getAnswerStorageKey(sid, radio.name));
            });
            localStorage.removeItem(getProgressStorageKey(sid));
        }

        let pdfDoc = null;

        function buildPdfLines(textItems) {
            const rows = [];
            textItems
                .filter(item => item.str && item.str.trim() !== '')
                .sort((a, b) => {
                    const yDiff = b.transform[5] - a.transform[5];
                    return Math.abs(yDiff) > 3 ? yDiff : a.transform[4] - b.transform[4];
                })
                .forEach(item => {
                    const y = item.transform[5];
                    let row = rows.find(existing => Math.abs(existing.y - y) <= 3);
                    if (!row) {
                        row = { y: y, items: [] };
                        rows.push(row);
                    }
                    row.items.push(item);
                });

            return rows
                .sort((a, b) => b.y - a.y)
                .map(row => row.items
                    .sort((a, b) => a.transform[4] - b.transform[4])
                    .map(item => item.str.trim())
                    .join(' ')
                    .replace(/\s+/g, ' ')
                    .trim())
                .filter(Boolean);
        }

        function parseQuestionsFromLines(lines) {
            const questions = {};
            let current = null;

            function saveCurrent() {
                if (!current) return;
                const text = current.lines.join(' ').replace(/\s+/g, ' ').trim();
                if (text || Object.keys(current.options).length > 0) {
                    questions[current.number] = {
                        text: text || ('Question #' + current.number),
                        options: current.options
                    };
                }
            }

            lines.forEach(line => {
                const questionMatch = line.match(/^(\d{1,3})[\.\)]\s+(.+)$/);
                if (questionMatch) {
                    saveCurrent();
                    current = {
                        number: parseInt(questionMatch[1], 10),
                        lines: [questionMatch[2].trim()],
                        options: {}
                    };
                    return;
                }

                if (!current || /^part\s+/i.test(line)) return;

                const optionMatch = line.match(/^([a-jA-J])[\.\)]\s*(.+)$/);
                if (optionMatch) {
                    current.options[optionMatch[1].toUpperCase()] = optionMatch[2].trim();
                    return;
                }

                current.lines.push(line);
            });

            saveCurrent();
            return questions;
        }

        function applyExtractedQuestions(questions) {
            document.querySelectorAll('.question-text').forEach(el => {
                const qNum = parseInt(el.id.replace('question-text-', ''), 10);
                const question = questions[qNum];

                if (!question) {
                    el.textContent = 'Question text is not available from the uploaded file.';
                    return;
                }

                el.textContent = question.text;
                el.classList.remove('pending');

                Object.entries(question.options).forEach(([letter, text]) => {
                    const optionEl = document.querySelector(`.option-text[data-question="${qNum}"][data-option="${letter}"]`);
                    if (optionEl && text) {
                        optionEl.textContent = letter + '. ' + text;
                    }
                });
            });
        }

        function extractQuestionsFromPdf(pdf) {
            const pageJobs = [];
            for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
                pageJobs.push(
                    pdf.getPage(pageNum)
                        .then(page => page.getTextContent())
                        .then(content => buildPdfLines(content.items))
                );
            }

            Promise.all(pageJobs)
                .then(pageLines => applyExtractedQuestions(parseQuestionsFromLines(pageLines.flat())))
                .catch(error => {
                    console.error(error);
                    document.querySelectorAll('.question-text').forEach(el => {
                        el.textContent = 'Question text could not be read from the uploaded file.';
                    });
                });
        }
        
        // ðŸŒŸ 100% OFFLINE LOCAL PDF VIEWER CODE ðŸŒŸ
        function loadPDF() {
            if (typeof pdfjsLib === 'undefined') {
                document.getElementById('pdf-viewer-container').innerHTML = 
                    "<div style='color:white; padding:20px; text-align:center;'>" +
                    "<h3>PDF Engine Missing</h3>" +
                    "<p>Make sure 'pdf.min.js' and 'pdf.worker.min.js' are present in your quiz_system folder.</p>" +
                    "</div>";
                return;
            }

            // POINT TO LOCAL WORKER FILE
            pdfjsLib.GlobalWorkerOptions.workerSrc = 'pdf.worker.min.js';
            
            const rawUrl = <?php echo json_encode($pdf_file); ?>;
            const encodedUrl = rawUrl ? 'uploads/' + encodeURIComponent(rawUrl) : '';

            if (encodedUrl && encodedUrl !== "uploads/") {
                const lowerUrl = encodedUrl.toLowerCase();
                
                if (lowerUrl.endsWith('.pdf')) {
                    pdfjsLib.getDocument(encodedUrl).promise.then(function(pdf) {
                        pdfDoc = pdf;
                        renderPdfPage(1);
                        extractQuestionsFromPdf(pdf);
                    }).catch(function(error) {
                        console.error(error);
                        document.getElementById('pdf-viewer-container').innerHTML = 
                            "<div style='color:white; padding:20px; text-align:center;'>" +
                            "<h3>Cannot Load Exam Paper</h3>" +
                            "<p>Error loading: <b>" + rawUrl + "</b></p>" +
                            "<p>Ensure it is saved in the 'uploads' folder.</p>" +
                            "</div>";
                    });
                } else if (lowerUrl.endsWith('.doc') || lowerUrl.endsWith('.docx')) {
                    // BLOCK GOOGLE DOCS (It crashes offline)
                    document.getElementById('pdf-viewer-container').innerHTML = 
                        "<div style='color:white; padding:20px; text-align:center; margin-top: 50px;'>" +
                        "<h3>Word Documents (.docx) Require Internet</h3>" +
                        "<p style='color:#ccc; font-size: 14px;'>Because you are running this system on a local offline network, Google Docs cannot load this file.</p>" +
                        "<p style='color:#f0ad4e; font-weight: bold;'>Please convert your exam to a PDF and upload it again!</p>" +
                        "</div>";
                } else {
                    document.getElementById('pdf-viewer-container').innerHTML = "<p style='color:white; padding: 20px;'>Unsupported file format.</p>";
                }
            } else {
                document.getElementById('pdf-viewer-container').innerHTML = "<p style='color:white; padding: 20px;'>No file attached to this exam.</p>";
            }
        }

        function renderPdfPage(pageNum) {
            if (!pdfDoc) return;
            document.getElementById('pdf-page-num').innerText = pageNum;
            const targetPage = pageNum > pdfDoc.numPages ? pdfDoc.numPages : pageNum;

            pdfDoc.getPage(targetPage).then(function(page) {
                const scale = 1.5;
                const viewport = page.getViewport({ scale: scale });
                
                const container = document.getElementById('pdf-viewer-container');
                container.innerHTML = ''; 
                
                const canvas = document.createElement('canvas');
                const context = canvas.getContext('2d');
                canvas.height = viewport.height;
                canvas.width = viewport.width;
                container.appendChild(canvas);
                
                page.render({ canvasContext: context, viewport: viewport });
            });
        }

        // ðŸŒŸ FORCE SUBMIT (UPDATED TO HANDLE BLOCKS) ðŸŒŸ
        function forceSubmitExam(message, isCheatBlock = false) {
            if (isExamLocked) return;
            isExamLocked = true;
            
            let sid = document.getElementById('final-sid').value.trim();

            if (isCheatBlock) {
                // Permanently ban them in memory
                localStorage.setItem('exam_blocked_' + examTitle + '_' + sid, 'true');
                // Tell PHP to show the Red Blocked page
                document.getElementById('is-cheater').value = '1';
                showPermanentBlockScreen();
            } else {
                document.getElementById('overlay-message').innerText = message;
                document.getElementById('force-submit-overlay').style.display = 'flex';
            }
            
            // Bypass PIN requirement when forcibly submitted via timeout/anti-cheat
            clearLocalStorageAnswers();
            examForm.noValidate = true;
            examForm.submit();
        }


        window.addEventListener('beforeunload', function (e) {
            if (!isExamLocked && isExamStarted) {
                e.preventDefault();
                saveProgressState();
                e.returnValue = ''; 
            }
        });

        function startTimer() {
            // ðŸŒŸ NaN JS FIX: Use raw PHP echo to inject the number natively ðŸŒŸ
            let totalSeconds = <?php echo $time_limit_minutes; ?> * 60; 

            const timerDisplay = document.getElementById('timer-display');

            timerInterval = setInterval(() => {
                if (isExamLocked) {
                    clearInterval(timerInterval);
                    return;
                }

                totalSeconds--;
                let minutes = Math.floor(totalSeconds / 60);
                let seconds = totalSeconds % 60;
                if (seconds < 10) seconds = "0" + seconds; 
                
                timerDisplay.innerText = minutes + ":" + seconds;

                if (totalSeconds <= 0) {
                    clearInterval(timerInterval);
                    forceSubmitExam("Time is up!"); 
                }
            }, 1000);
        }

        // ðŸŒŸ ANTI-CHEAT LISTENERS (UPDATED TO RECORD 3 STRIKES) ðŸŒŸ
        document.addEventListener("visibilitychange", () => {
            if (document.hidden && !isExamLocked && isExamStarted) {
                recordCheatWarning("Please do not leave the exam tab.");
            }
        });

        window.addEventListener('blur', function() {
            if (!isExamLocked && isExamStarted) { document.body.style.filter = "blur(15px)"; }
        });
        window.addEventListener('focus', function() {
            if (!isExamLocked && isExamStarted) { document.body.style.filter = "none"; }
            checkSplitScreenRisk();
        });

        document.addEventListener('fullscreenchange', function() {
            if (isExamStarted && !isExamLocked && isFullscreenSupported() && fullscreenWasActive && !isFullscreenActive()) {
                recordCheatWarning("Fullscreen was closed. Keep the exam as the main active window.");
            }
        });
        document.addEventListener('webkitfullscreenchange', function() {
            if (isExamStarted && !isExamLocked && isFullscreenSupported() && fullscreenWasActive && !isFullscreenActive()) {
                recordCheatWarning("Fullscreen was closed. Keep the exam as the main active window.");
            }
        });

        window.addEventListener('resize', checkSplitScreenRisk);
        setInterval(checkSplitScreenRisk, 2000);

        document.addEventListener('contextmenu', event => event.preventDefault());

        document.addEventListener('keydown', function(e) {
            const key = (e.key || '').toLowerCase();
            const isPrintScreen = key === "printscreen";
            const isBrowserPrint = e.ctrlKey && key === 'p';
            const isSavePage = e.ctrlKey && key === 's';
            const isViewSource = e.ctrlKey && key === 'u';
            const isDevTools = key === "f12" || (e.ctrlKey && e.shiftKey && (key === "i" || key === "j" || key === "c"));
            const isWindowsSnip = e.shiftKey && e.metaKey && key === 's';
            const isCopyCutPaste = e.ctrlKey && (key === 'c' || key === 'x' || key === 'v');

            if (isPrintScreen || isWindowsSnip) {
                e.preventDefault();
                document.body.style.filter = "blur(15px)";
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText("").catch(() => {});
                }
                recordCheatWarning("Screenshot attempt detected. Screenshots are not allowed during this exam.");
                return false;
            }

            if (isBrowserPrint || isSavePage || isViewSource || isDevTools || isCopyCutPaste) {
                e.preventDefault();
                alert("This action is disabled during the exam.");
                return false;
            }
        });

        window.addEventListener('beforeprint', function(e) {
            if (!isExamLocked && isExamStarted) {
                document.body.style.filter = "blur(15px)";
                recordCheatWarning("Print/screenshot attempt detected. Printing is not allowed during this exam.");
            }
        });

        window.addEventListener("orientationchange", function() {
            if (window.orientation === 90 || window.orientation === -90) {
                alert("Orientation change detected. Please keep your device in Portrait mode.");
            }
        });

        const initialIP = "<?php echo $_SERVER['REMOTE_ADDR']; ?>";
        setInterval(() => {
            if (isExamLocked || !isExamStarted) return;
            fetch('?check_ip=1')
                .then(response => response.text())
                .then(currentIP => {
                    if (currentIP.trim() !== "" && currentIP.trim() !== initialIP) {
                        forceSubmitExam("NETWORK CHANGE DETECTED\nYou changed your Wi-Fi or Mobile Data network.");
                    }
                }).catch(e => {  });
        }, 5000); 

        // --- NEW: LIVE MONITOR HEARTBEAT ---
        function sendLivePing(extraStatus) {
            if (isExamLocked || !isExamStarted || isTeacherBlocked) return;
            
            let sidInput = document.getElementById('final-sid').value;
            if (!sidInput) return;

            let currentStatus = "Active (On Q" + lastActiveQ + ")";
            if (extraStatus) currentStatus = extraStatus + " (At Q" + lastActiveQ + ")";
            else if (document.hidden) currentStatus = "Tab Left (At Q" + lastActiveQ + ")";
            else if (document.body.style.filter.includes("blur")) currentStatus = "Window Blurred (At Q" + lastActiveQ + ")";
            else if (!isMobileLikeDevice() && ((isFullscreenSupported() && fullscreenWasActive && !isFullscreenActive()) || isExamWindowSuspiciouslySmall())) currentStatus = "Split Screen / Not Fullscreen (At Q" + lastActiveQ + ")";

            let answeredCount = document.querySelectorAll('input[type="radio"]:checked').length;
            let progressStr = answeredCount + "/" + <?php echo $q_count; ?> + " | P" + currentPage + "/" + totalPages + " | Q" + lastActiveQ;

            let url = `?live_ping=1&sid=${encodeURIComponent(sidInput.trim())}&title=${encodeURIComponent(examTitle)}&device_token=${encodeURIComponent(currentDeviceToken)}&status=${encodeURIComponent(currentStatus)}&progress=${encodeURIComponent(progressStr)}`;
            
            fetch(url).catch(e => {});
        }

        setInterval(sendLivePing, 3000); // Pings database every 3 seconds
        setInterval(pollTeacherBlockStatus, 3000);
        document.addEventListener("visibilitychange", sendLivePing);
        window.addEventListener('blur', sendLivePing);
        window.addEventListener('focus', sendLivePing);
        // -----------------------------------
        
    </script>
</body>
</html>

