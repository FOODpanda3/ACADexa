Project Title: ACADexa
Developer Name: [Your Name]

Description:
ACADexa is a JavaFX desktop application for managing school quizzes/exams, students, live monitoring, submitted answers, and scores. It uses a local MySQL database and a small PHP support page for student answer submission.

Folder contents:
Install.bat - easy installer for copying web files, setting up the database, and creating a shortcut

XAMPPInstaller/
  xampp-windows-x64-8.2.12-0-VS16-installer.exe - bundled XAMPP installer for PCs that do not have XAMPP yet

Application/
  Admindashboard.exe - main Windows launcher for ACADexa
  Admindashboard.jar - Java application package used by the launcher
  Run_Admindashboard.bat - fallback launcher script
  lib/ - required Java dependency files
  quiz_system/ - PHP student answer page and PDF support files

Database/
  setup_database.sql - creates the required local database and tables

Documentation/
  Installation_Guide.txt - setup and run instructions
  User_Manual.txt - basic usage guide

Run command:
For easiest setup, run Install.bat first.
After installation, use the ACADexa desktop shortcut or open Application/Admindashboard.exe.

Note:
Install.bat requests Administrator permission so it can copy quiz_system into C:\xampp\htdocs and create/update the desktop shortcut. Use Run_Admindashboard.bat only as a fallback if the executable is blocked.

Clean system test status:
The deployment folder is organized and ready for clean-system testing. Java is not available on the current command PATH, so final launch validation must be performed on a computer with Java/JavaFX and XAMPP installed.
