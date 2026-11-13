package mailserver;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class MailServerGUI extends JFrame {
    private final JTextArea logArea = new JTextArea();
    private final JButton btnStart = new JButton("Khởi động Server");
    private final JButton btnStop = new JButton("Dừng Server");
    private volatile boolean isRunning;
    private ServerSocket pop3Socket, smtpSocket;

    private static final String DB_URL =
        "jdbc:sqlserver://localhost:1433;databaseName=my_mail_db;"
        + "integratedSecurity=true;encrypt=true;trustServerCertificate=true;";

    public MailServerGUI() {
        setTitle("Java Mail Server (POP3: 1100 | SMTP: 2525)");
        setSize(650, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        JPanel controls = new JPanel();
        controls.add(btnStart);
        controls.add(btnStop);
        add(controls, BorderLayout.SOUTH);
        btnStop.setEnabled(false);
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
    }

    private void startServer() {
        if (isRunning) return;
        isRunning = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        appendLog("[HỆ THỐNG] Đang khởi động Mail Server...");

        new Thread(() -> {
            try {
                pop3Socket = new ServerSocket(1100);
                appendLog("[POP3] Server đang chạy tại cổng 1100");
                while (isRunning) {
                    Socket s = pop3Socket.accept();
                    appendLog("[POP3 KẾT NỐI] " + s.getRemoteSocketAddress());
                    new POP3Handler(s, this).start();
                }
            } catch (IOException e) {
                if (isRunning) appendLog("[LỖI POP3] " + e.getMessage());
            }
        }, "POP3-Server").start();

        new Thread(() -> {
            try {
                smtpSocket = new ServerSocket(2525);
                appendLog("[SMTP] Server đang chạy tại cổng 2525");
                while (isRunning) {
                    Socket s = smtpSocket.accept();
                    appendLog("[SMTP KẾT NỐI] " + s.getRemoteSocketAddress());
                    new SMTPHandler(s, this).start();
                }
            } catch (IOException e) {
                if (isRunning) appendLog("[LỖI SMTP] " + e.getMessage());
            }
        }, "SMTP-Server").start();
    }

    private void stopServer() {
        isRunning = false;
        try {
            if (pop3Socket != null) pop3Socket.close();
            if (smtpSocket != null) smtpSocket.close();
            appendLog("[HỆ THỐNG] Đã dừng Server.");
        } catch (IOException e) {
            appendLog("[LỖI DỪNG SERVER] " + e.getMessage());
        }
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    public void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    public void saveUserToFile(String email, String password) {
        File dir = new File("G:/LTM/MailServer");
        if (!dir.exists() && !dir.mkdirs()) {
            appendLog("[LỖI] Không tạo được thư mục " + dir.getAbsolutePath());
            return;
        }
        File file = new File(dir, email.replaceAll("[^a-zA-Z0-9]", "_") + ".txt");
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("Email: " + email);
            w.println("Password: " + password);
            w.println("Timestamp: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            appendLog("[FILE] Đã tạo: " + file.getAbsolutePath());
        } catch (IOException e) {
            appendLog("[LỖI TẠO FILE] " + e.getMessage());
        }
    }

    /** Tạo một file riêng cho email sau khi email đã được lưu thành công vào SQL Server. */
    public void saveEmailToFile(String sender, String receiver, String subject, String body) {
        File dir = new File("G:/LTM/MailServer/Emails");
        if (!dir.exists() && !dir.mkdirs()) {
            appendLog("[LỖI] Không tạo được thư mục lưu email: " + dir.getAbsolutePath());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String timestampForName = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String timestampForText = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String unique = java.util.UUID.randomUUID().toString().substring(0, 8);
        File file = new File(dir, "email_" + timestampForName + "_" + unique + ".txt");

        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.println("========== THÔNG TIN EMAIL ==========");
            w.println("Người gửi: " + safeFileText(sender));
            w.println("Người nhận: " + safeFileText(receiver));
            w.println("Thời gian gửi: " + timestampForText);
            w.println("Tiêu đề: " + safeFileText(subject));
            w.println();
            w.println("========== NỘI DUNG ==========");
            w.println(body == null ? "" : body);
            w.println();
            w.println("====================================");
            appendLog("[FILE EMAIL] Đã tạo: " + file.getAbsolutePath());
        } catch (IOException e) {
            appendLog("[LỖI TẠO FILE EMAIL] " + e.getMessage());
        }
    }

    private static String safeFileText(String value) {
        return value == null ? "" : value;
    }

    public static String getDbUrl() { return DB_URL; }

    public static void main(String[] args) {
        System.out.println("Java arch: " + System.getProperty("os.arch"));
        System.out.println("DLL path: " + System.getProperty("java.library.path"));
        SwingUtilities.invokeLater(() -> new MailServerGUI().setVisible(true));
    }
}

class POP3Handler extends Thread {
    private final Socket socket;
    private final MailServerGUI gui;

    POP3Handler(Socket socket, MailServerGUI gui) {
        this.socket = socket;
        this.gui = gui;
    }

    @Override
    public void run() {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(
                 s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(
                 s.getOutputStream(), StandardCharsets.UTF_8), true)) {
            out.println("+OK POP3 server ready");
            String currentUser = "";
            boolean authenticated = false;
            String line;

            while ((line = in.readLine()) != null) {
                String[] p = line.trim().split("\\s+", 2);
                String cmd = p[0].toUpperCase();
                String arg = p.length > 1 ? p[1] : "";

                switch (cmd) {
                    case "USER":
                        currentUser = arg.trim();
                        authenticated = false;
                        out.println("+OK User accepted");
                        break;
                    case "PASS":
                        authenticated = checkLogin(currentUser, arg);
                        if (authenticated) {
                            out.println("+OK Logged in successfully");
                            gui.appendLog("[ĐĂNG NHẬP] " + currentUser);
                        } else out.println("-ERR Invalid credentials");
                        break;
                    case "REGISTER":
                        authenticated = register(currentUser, arg);
                        if (authenticated) {
                            out.println("+OK Registered successfully");
                            gui.appendLog("[ĐĂNG KÝ] " + currentUser);
                        } else out.println("-ERR Registration failed (email may exist)");
                        break;
                    case "LIST":
                        if (!authenticated) {
                            out.println("-ERR Please login first");
                        } else {
                            out.println("+OK List follows");
                            sendInboxList(currentUser, out);
                            out.println(".");
                        }
                        break;
                    case "READ":
                        if (!authenticated) {
                            out.println("-ERR Please login first");
                        } else {
                            try { sendEmail(currentUser, Integer.parseInt(arg.trim()), out); }
                            catch (NumberFormatException e) { out.println("-ERR Invalid mail ID"); }
                        }
                        break;
                    case "QUIT":
                        out.println("+OK Bye");
                        return;
                    default:
                        out.println("-ERR Unknown command");
                }
            }
        } catch (IOException e) {
            gui.appendLog("[LỖI POP3] " + e.getMessage());
        }
    }

    private boolean checkLogin(String email, String pass) {
        String sql = "SELECT 1 FROM mail_users WHERE email=? AND password=?";
        try (Connection c = DriverManager.getConnection(MailServerGUI.getDbUrl());
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    gui.saveUserToFile(email, pass);
                    return true;
                }
            }
        } catch (Exception e) { gui.appendLog("[SQL LOGIN] " + e.getMessage()); }
        return false;
    }

    private boolean register(String email, String pass) {
        if (email == null || email.trim().isEmpty() || pass == null || pass.isEmpty()) return false;
        String sql = "INSERT INTO mail_users(email,password) VALUES(?,?)";
        try (Connection c = DriverManager.getConnection(MailServerGUI.getDbUrl());
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            ps.setString(2, pass);
            ps.executeUpdate();
            gui.saveUserToFile(email.trim(), pass);
            return true;
        } catch (Exception e) {
            gui.appendLog("[SQL REGISTER] " + e.getMessage());
            return false;
        }
    }

    private void sendInboxList(String user, PrintWriter out) {
        String sql = "SELECT id,sender,subject FROM emails WHERE receiver=? ORDER BY id DESC";
        try (Connection c = DriverManager.getConnection(MailServerGUI.getDbUrl());
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sender = safe(rs.getString("sender"));
                    String subject = encode(safe(rs.getString("subject")));
                    out.println(rs.getInt("id") + " | " + sender + " | " + subject);
                }
            }
        } catch (Exception e) { gui.appendLog("[SQL LIST] " + e.getMessage()); }
    }

    private void sendEmail(String user, int id, PrintWriter out) {
        String sql = "SELECT sender,receiver,subject,body FROM emails WHERE id=? AND receiver=?";
        try (Connection c = DriverManager.getConnection(MailServerGUI.getDbUrl());
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { out.println("-ERR Email not found"); return; }
                out.println("+OK");
                out.println(encode(safe(rs.getString("sender"))));
                out.println(encode(safe(rs.getString("receiver"))));
                out.println(encode(safe(rs.getString("subject"))));
                out.println(encode(safe(rs.getString("body"))));
                out.println(".");
            }
        } catch (Exception e) {
            gui.appendLog("[LỖI ĐỌC THƯ] " + e.getMessage());
            out.println("-ERR Cannot read email");
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}

class SMTPHandler extends Thread {
    private final Socket socket;
    private final MailServerGUI gui;

    SMTPHandler(Socket socket, MailServerGUI gui) {
        this.socket = socket;
        this.gui = gui;
    }

    @Override
    public void run() {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(
                 s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(
                 s.getOutputStream(), StandardCharsets.UTF_8), true)) {
            out.println("220 Mail Server Ready");
            String sender = "", receiver = "", subject = "", line;

            while ((line = in.readLine()) != null) {
                if (line.startsWith("MAIL FROM:")) {
                    sender = line.substring("MAIL FROM:".length()).trim();
                    out.println("250 OK");
                } else if (line.startsWith("RCPT TO:")) {
                    receiver = line.substring("RCPT TO:".length()).trim();
                    out.println("250 OK");
                } else if (line.startsWith("SUBJECT:")) {
                    subject = line.substring("SUBJECT:".length()).trim();
                    out.println("250 OK");
                } else if (line.equals("DATA")) {
                    out.println("354 Start mail input; end with <CRLF>.<CRLF>");
                    StringBuilder body = new StringBuilder();
                    while ((line = in.readLine()) != null && !line.equals(".")) {
                        if (body.length() > 0) body.append('\n');
                        body.append(line);
                    }
                    if (saveEmail(sender, receiver, subject, body.toString())) {
                        out.println("250 OK: Message accepted for delivery");
                        gui.appendLog("[SMTP GỬI THƯ] " + sender + " -> " + receiver);
                    } else out.println("550 Failed to save message");
                } else if (line.equals("QUIT")) {
                    out.println("221 Bye");
                    return;
                } else out.println("500 Unknown command");
            }
        } catch (IOException e) { gui.appendLog("[LỖI SMTP] " + e.getMessage()); }
    }

    private boolean saveEmail(String sender, String receiver, String subject, String body) {
        String sql = "INSERT INTO emails(sender,receiver,subject,body) VALUES(?,?,?,?)";
        try (Connection c = DriverManager.getConnection(MailServerGUI.getDbUrl());
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setString(3, subject);
            ps.setString(4, body);
            ps.executeUpdate();
            // Chỉ tạo file khi email đã được lưu thành công vào cơ sở dữ liệu.
            gui.saveEmailToFile(sender, receiver, subject, body);
            return true;
        } catch (Exception e) {
            gui.appendLog("[SQL EMAIL] " + e.getMessage());
            return false;
        }
    }
}
