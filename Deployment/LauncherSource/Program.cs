using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Windows.Forms;

namespace AdmindashboardLauncher
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            string appDirectory = AppDomain.CurrentDomain.BaseDirectory;
            string jarPath = Path.Combine(appDirectory, "Admindashboard.jar");

            if (!File.Exists(jarPath))
            {
                ShowError("ACADexa application file was not found beside this launcher.\n\nExpected location:\n" + jarPath);
                return;
            }

            try
            {
                string javaPath = FindJavaExecutable();
                ProcessStartInfo startInfo = new ProcessStartInfo();
                startInfo.FileName = javaPath;
                startInfo.Arguments = "-Xms64m -Xmx512m -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 --enable-native-access=javafx.graphics --add-modules javafx.controls,javafx.fxml,javafx.swing -cp \"Admindashboard.jar;lib/*\" admindashboard.Admindashboard";
                startInfo.WorkingDirectory = appDirectory;
                startInfo.UseShellExecute = false;
                startInfo.CreateNoWindow = true;
                startInfo.RedirectStandardError = false;

                Process.Start(startInfo);
            }
            catch (Win32Exception)
            {
                ShowError("Java was not found on this computer.\n\nInstall Java/JDK with JavaFX support, then run ACADexa again.");
            }
            catch (Exception ex)
            {
                ShowError("Unexpected launcher error:\n\n" + ex.Message);
            }
        }

        private static void ShowError(string message)
        {
            MessageBox.Show(message, "ACADexa Launcher", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }

        private static string FindJavaExecutable()
        {
            string[] candidates =
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "BellSoft", "LibericaJDK-25-Full", "bin", "javaw.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "BellSoft", "LibericaJDK-21-Full", "bin", "javaw.exe"),
                "javaw"
            };

            foreach (string candidate in candidates)
            {
                if (candidate == "javaw" || File.Exists(candidate))
                {
                    return candidate;
                }
            }

            return "javaw";
        }

        private static string TrimForMessage(string text)
        {
            const int maxLength = 1800;
            string clean = text.Replace("\r\n", "\n").Replace("\n", Environment.NewLine).Trim();
            if (clean.Length <= maxLength)
            {
                return clean;
            }

            StringBuilder builder = new StringBuilder();
            builder.Append(clean.Substring(0, maxLength));
            builder.Append(Environment.NewLine);
            builder.Append("...");
            return builder.ToString();
        }
    }
}
