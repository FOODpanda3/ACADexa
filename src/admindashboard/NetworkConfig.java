package admindashboard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Properties;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * Utility for managing server host IP, automatic Ngrok background execution & tunnel detection,
 * custom domain configuration, and generating student answer URLs for QR code creation.
 */
public class NetworkConfig {

    private static final String CONFIG_FILE = "server_config.properties";
    private static Properties properties = new Properties();

    private static Process ngrokProcess = null;
    private static boolean attemptedAutoStart = false;

    static {
        loadConfig();
        // Asynchronously check/launch background Ngrok process on startup
        new Thread(NetworkConfig::ensureNgrokRunning).start();
    }

    private static synchronized void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
            } catch (IOException e) {
                System.out.println("Failed to load server_config.properties: " + e.getMessage());
            }
        }
    }

    public static synchronized void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "Admin Dashboard Network & Server Configurations");
        } catch (IOException e) {
            System.out.println("Failed to save server_config.properties: " + e.getMessage());
        }
    }

    public static String getCustomDomain() {
        return properties.getProperty("custom_domain", "").trim();
    }

    public static void setCustomDomain(String domain) {
        properties.setProperty("custom_domain", domain != null ? domain.trim() : "");
        saveConfig();
    }

    public static boolean isUseCustomDomain() {
        return Boolean.parseBoolean(properties.getProperty("use_custom_domain", "false"));
    }

    public static void setUseCustomDomain(boolean use) {
        properties.setProperty("use_custom_domain", Boolean.toString(use));
        saveConfig();
    }

    /**
     * ⚡ AUTOMATIC NGROK BACKGROUND LAUNCHER
     * Automatically starts 'ngrok http 80' silently in the background if ngrok is available
     * and not already running. Automatically kills ngrok when ACADexa exits.
     */
    public static synchronized void ensureNgrokRunning() {
        if (attemptedAutoStart) return;
        attemptedAutoStart = true;

        if (checkNgrokApi() != null) {
            System.out.println("⚡ Live Ngrok tunnel is already active.");
            return;
        }

        try {
            String ngrokCmd = "ngrok";
            File localNgrok = new File("ngrok.exe");
            if (localNgrok.exists()) {
                ngrokCmd = localNgrok.getAbsolutePath();
            }

            ProcessBuilder pb = new ProcessBuilder(ngrokCmd, "http", "80");
            pb.redirectErrorStream(true);
            ngrokProcess = pb.start();

            System.out.println("⚡ Auto-started background Ngrok tunnel (" + ngrokCmd + ")");

            // Register shutdown hook to clean up Ngrok process when application exits
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (ngrokProcess != null && ngrokProcess.isAlive()) {
                    ngrokProcess.destroyForcibly();
                    System.out.println("⚡ Closed background Ngrok tunnel.");
                }
            }));

            // Pause briefly to allow Ngrok tunnel to initialize its REST API
            Thread.sleep(1200);

        } catch (Exception e) {
            System.out.println("ℹ️ Ngrok executable not found or failed to auto-start. Using Local Wi-Fi IP.");
        }
    }

    /**
     * Helper to query local Ngrok REST API.
     */
    private static String checkNgrokApi() {
        try {
            URL url = new URL("http://127.0.0.1:4040/api/tunnels");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(400); // fast local check
            conn.setReadTimeout(400);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String json = sb.toString();

                    int idx = json.indexOf("\"public_url\":\"https://");
                    if (idx != -1) {
                        int start = idx + 14;
                        int end = json.indexOf("\"", start);
                        if (end != -1) {
                            return json.substring(start, end);
                        }
                    }

                    idx = json.indexOf("\"public_url\":\"http://");
                    if (idx != -1) {
                        int start = idx + 14;
                        int end = json.indexOf("\"", start);
                        if (end != -1) {
                            return json.substring(start, end);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * ⚡ AUTOMATIC NGROK DETECTOR
     * Queries the local Ngrok client REST API on port 4040.
     * Returns the active public URL (e.g. "https://xxxx.ngrok-free.app") if Ngrok is running,
     * or null if Ngrok is not active.
     */
    public static String getAutoDetectedNgrokUrl() {
        String liveUrl = checkNgrokApi();
        if (liveUrl == null && !attemptedAutoStart) {
            ensureNgrokRunning();
            liveUrl = checkNgrokApi();
        }
        return liveUrl;
    }

    /**
     * Automatic smart IP finder for local Wi-Fi / Router connection.
     */
    public static String getLocalWiFiIP() {
        // Attempt 1: Try internet route
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            if (ip != null && !ip.equals("0.0.0.0")) {
                return ip;
            }
        } catch (Exception ignored) {}

        // Attempt 2: Try local router gateway route
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            socket.connect(InetAddress.getByName("192.168.1.1"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            if (ip != null && !ip.equals("0.0.0.0") && !ip.startsWith("127.")) {
                return ip;
            }
        } catch (Exception ignored) {}

        // Attempt 3: Hardware Deep Scan
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtualbox") || name.contains("hyper-v")
                        || name.contains("wsl") || name.contains("vpn")) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }

    /**
     * Constructs the full student answer URL given the query parameters.
     * 100% AUTOMATIC:
     * 1. Auto-launches & detects running Ngrok tunnel (http://127.0.0.1:4040).
     * 2. Falls back to saved custom domain if enabled.
     * 3. Falls back to local Wi-Fi IP (http://192.168.x.x).
     */
    public static String buildStudentURL(String queryParams) {
        String base;

        // Priority 1: ⚡ Automatic Live Ngrok Tunnel Detection
        String liveNgrokUrl = getAutoDetectedNgrokUrl();
        if (liveNgrokUrl != null && !liveNgrokUrl.isEmpty()) {
            base = liveNgrokUrl;
            System.out.println("⚡ [AUTO-DETECT] Found active Ngrok tunnel: " + base);
        }
        // Priority 2: Saved Custom Domain (if manual override is enabled)
        else if (isUseCustomDomain() && !getCustomDomain().isEmpty()) {
            String domain = getCustomDomain();
            if (domain.startsWith("http://") || domain.startsWith("https://")) {
                base = domain;
            } else {
                base = "https://" + domain;
            }
            System.out.println("🌐 Using saved Custom Domain: " + base);
        }
        // Priority 3: Automatic Local Wi-Fi IP
        else {
            base = "http://" + getLocalWiFiIP();
            System.out.println("📶 Using Local Wi-Fi IP: " + base);
        }

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        if (queryParams.startsWith("/")) {
            queryParams = queryParams.substring(1);
        }

        return base + "/quiz_system/" + queryParams;
    }

    /**
     * Displays a JavaFX configuration dialog to view live connection status or set manual domain overrides.
     */
    public static void showConfigDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Network & Server Configuration");
        dialog.setHeaderText("Server Host & Ngrok Status");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 100, 10, 10));

        String liveNgrok = getAutoDetectedNgrokUrl();
        Label lblNgrokStatus = new Label("Live Ngrok Tunnel: " + 
            (liveNgrok != null ? "⚡ ACTIVE (" + liveNgrok + ")" : "❌ Not Running (Local Wi-Fi Active)"));
        lblNgrokStatus.setStyle(liveNgrok != null ? "-fx-text-fill: green; -fx-font-weight: bold;" : "-fx-text-fill: #666;");

        Label lblLocalIP = new Label("Local Wi-Fi IP: http://" + getLocalWiFiIP());

        CheckBox chkUseCustom = new CheckBox("Manual Custom Domain Override");
        chkUseCustom.setSelected(isUseCustomDomain());

        TextField txtCustomDomain = new TextField();
        txtCustomDomain.setPromptText("e.g. funny-cat-123.ngrok-free.app");
        txtCustomDomain.setText(getCustomDomain());
        txtCustomDomain.setDisable(!chkUseCustom.isSelected());

        chkUseCustom.setOnAction(e -> txtCustomDomain.setDisable(!chkUseCustom.isSelected()));

        grid.add(lblNgrokStatus, 0, 0, 2, 1);
        grid.add(lblLocalIP, 0, 1, 2, 1);
        grid.add(chkUseCustom, 0, 2, 2, 1);
        grid.add(new Label("Manual Domain:"), 0, 3);
        grid.add(txtCustomDomain, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            setUseCustomDomain(chkUseCustom.isSelected());
            setCustomDomain(txtCustomDomain.getText());

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Configuration Saved");
            alert.setHeaderText(null);
            alert.setContentText("Current Active URL: " + buildStudentURL("answer.php"));
            alert.showAndWait();
        }
    }
}
