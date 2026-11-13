package mailclient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MailClientGUI extends JFrame {
    private String loggedInUser = "";
    private String currentPassword = "";

    private final JPanel loginPanel = new JPanel(new GridBagLayout());
    private final JTextField txtEmail = new JTextField(20);
    private final JPasswordField txtPass = new JPasswordField(20);
    // Thông tin kết nối có thể nhập trực tiếp trên giao diện Client.
    private final JTextField txtServerIp = new JTextField("127.0.0.1", 20);
    private final JTextField txtPop3Port = new JTextField("1100", 20);
    private final JTextField txtSmtpPort = new JTextField("2525", 20);
    private final JButton btnLogin = new JButton("Đăng nhập");
    private final JButton btnRegister = new JButton("Đăng ký tài khoản");

    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final DefaultListModel<String> inboxModel = new DefaultListModel<>();
    private final JList<String> inboxList = new JList<>(inboxModel);
    private final JButton btnCompose = new JButton("Soạn thư (Compose)");
    private final JButton btnRefresh = new JButton("Làm mới (Refresh)");

    public MailClientGUI() {
        setTitle("Java Mail Client");
        setSize(700, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0; gbc.gridy = 0;
        loginPanel.add(new JLabel("Email tài khoản:"), gbc);
        gbc.gridx = 1; loginPanel.add(txtEmail, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        loginPanel.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1; loginPanel.add(txtPass, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        loginPanel.add(new JLabel("IP máy chủ:"), gbc);
        gbc.gridx = 1; loginPanel.add(txtServerIp, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        loginPanel.add(new JLabel("Port nhận/đọc thư (POP3):"), gbc);
        gbc.gridx = 1; loginPanel.add(txtPop3Port, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        loginPanel.add(new JLabel("Port gửi thư (SMTP):"), gbc);
        gbc.gridx = 1; loginPanel.add(txtSmtpPort, gbc);

        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(btnLogin);
        buttons.add(btnRegister);
        gbc.gridx = 1; gbc.gridy = 5;
        loginPanel.add(buttons, gbc);
        add(loginPanel, BorderLayout.CENTER);

        btnLogin.addActionListener(e -> {
            String email = txtEmail.getText().trim();
            String pass = new String(txtPass.getPassword());
            if (authenticate(email, pass, false)) {
                loggedInUser = email;
                currentPassword = pass;
                initMainUI();
            } else JOptionPane.showMessageDialog(this,
                "Đăng nhập thất bại! Kiểm tra email hoặc mật khẩu.");
        });

        btnRegister.addActionListener(e -> {
            String email = txtEmail.getText().trim();
            String pass = new String(txtPass.getPassword());
            if (email.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập email và mật khẩu.");
                return;
            }
            if (authenticate(email, pass, true)) {
                loggedInUser = email;
                currentPassword = pass;
                JOptionPane.showMessageDialog(this, "Đăng ký thành công!");
                initMainUI();
            } else JOptionPane.showMessageDialog(this,
                "Đăng ký thất bại! Email có thể đã tồn tại.");
        });

        inboxList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = inboxList.getSelectedValue();
                    if (selected != null) openReadDialog(getMailId(selected));
                }
            }
        });
    }

    private String getServerIp() {
        String ip = txtServerIp.getText().trim();
        if (ip.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập IP máy chủ.");
        return ip;
    }

    private int getPop3Port() {
        return parsePort(txtPop3Port.getText().trim(), "POP3");
    }

    private int getSmtpPort() {
        return parsePort(txtSmtpPort.getText().trim(), "SMTP");
    }

    private int parsePort(String value, String protocol) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port " + protocol + " phải là số từ 1 đến 65535.");
        }
    }

    private boolean authenticate(String email, String pass, boolean register) {
        if (email == null || email.trim().isEmpty() || pass == null || pass.isEmpty()) return false;
        try (Socket s = new Socket(getServerIp(), getPop3Port());
             BufferedReader in = reader(s);
             PrintWriter out = writer(s)) {
            in.readLine();
            out.println("USER " + email.trim());
            String r = in.readLine();
            if (r == null || !r.startsWith("+OK")) return false;
            out.println((register ? "REGISTER " : "PASS ") + pass);
            r = in.readLine();
            if (r != null && r.startsWith("+OK")) {
                out.println("QUIT");
                in.readLine();
                return true;
            }
            return false;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối Server: " + e.getMessage());
            return false;
        }
    }

    private void initMainUI() {
        getContentPane().removeAll();
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Xin chào: " + loggedInUser));
        top.add(btnCompose);
        top.add(btnRefresh);

        inboxList.setBorder(BorderFactory.createTitledBorder("Hộp thư đến (Inbox)"));
        mainPanel.removeAll();
        mainPanel.add(top, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(inboxList), BorderLayout.CENTER);
        getContentPane().add(mainPanel, BorderLayout.CENTER);

        // Tránh gắn listener lặp nhiều lần khi init lại giao diện.
        for (java.awt.event.ActionListener a : btnRefresh.getActionListeners()) btnRefresh.removeActionListener(a);
        for (java.awt.event.ActionListener a : btnCompose.getActionListeners()) btnCompose.removeActionListener(a);
        btnRefresh.addActionListener(e -> loadInbox());
        btnCompose.addActionListener(e -> openComposeDialog());

        revalidate();
        repaint();
        loadInbox();
    }

    private void loadInbox() {
        inboxModel.clear();
        try (Socket s = new Socket(getServerIp(), getPop3Port());
             BufferedReader in = reader(s);
             PrintWriter out = writer(s)) {
            in.readLine();
            out.println("USER " + loggedInUser);
            in.readLine();
            out.println("PASS " + currentPassword);
            String auth = in.readLine();
            if (auth == null || !auth.startsWith("+OK")) {
                JOptionPane.showMessageDialog(this, "Không xác thực được để tải thư.");
                return;
            }

            out.println("LIST");
            String response = in.readLine();
            if (response == null || !response.startsWith("+OK")) {
                JOptionPane.showMessageDialog(this, "Lỗi lấy danh sách thư: " + response);
                return;
            }

            String line;
            while ((line = in.readLine()) != null && !line.equals(".")) {
                String[] p = line.split("\\s*\\|\\s*", 3);
                if (p.length == 3) {
                    String subject = decode(p[2]);
                    inboxModel.addElement(p[0].trim() + " | Từ: " + p[1].trim()
                        + " | Tiêu đề: " + subject);
                } else inboxModel.addElement(line);
            }
            out.println("QUIT");
            in.readLine();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi tải hộp thư: " + e.getMessage());
        }
    }

    private int getMailId(String row) {
        try { return Integer.parseInt(row.split("\\s*\\|", 2)[0].trim()); }
        catch (Exception e) { return -1; }
    }

    private void openReadDialog(int mailId) {
        if (mailId < 0) {
            JOptionPane.showMessageDialog(this, "Mã thư không hợp lệ.");
            return;
        }
        try (Socket s = new Socket(getServerIp(), getPop3Port());
             BufferedReader in = reader(s);
             PrintWriter out = writer(s)) {
            in.readLine();
            out.println("USER " + loggedInUser);
            in.readLine();
            out.println("PASS " + currentPassword);
            String auth = in.readLine();
            if (auth == null || !auth.startsWith("+OK")) {
                JOptionPane.showMessageDialog(this, "Không xác thực được để đọc thư.");
                return;
            }

            out.println("READ " + mailId);
            String response = in.readLine();
            if (response == null || !response.startsWith("+OK")) {
                JOptionPane.showMessageDialog(this, "Không đọc được thư: " + response);
                return;
            }

            String from = decode(in.readLine());
            String to = decode(in.readLine());
            String subject = decode(in.readLine());
            String body = decode(in.readLine());
            in.readLine(); // dấu kết thúc .

            out.println("QUIT");
            in.readLine();

            JTextArea info = new JTextArea(
                "Người gửi: " + from + "\nNgười nhận: " + to
                + "\nTiêu đề: " + subject);
            info.setEditable(false);
            info.setBackground(UIManager.getColor("Panel.background"));

            JTextArea bodyArea = new JTextArea(body, 12, 45);
            bodyArea.setEditable(false);
            bodyArea.setLineWrap(true);
            bodyArea.setWrapStyleWord(true);

            JPanel panel = new JPanel(new BorderLayout(5, 8));
            panel.add(info, BorderLayout.NORTH);
            panel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);
            JOptionPane.showMessageDialog(this, panel, "Đọc thư",
                JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi đọc thư: " + e.getMessage());
        }
    }

    private void openComposeDialog() {
        JDialog dialog = new JDialog(this, "Soạn thư mới (SMTP)", true);
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel fields = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField toField = new JTextField();
        JTextField subjectField = new JTextField();
        JTextArea bodyField = new JTextArea(8, 30);
        bodyField.setLineWrap(true);
        bodyField.setWrapStyleWord(true);

        fields.add(new JLabel("Đến (Receiver):")); fields.add(toField);
        fields.add(new JLabel("Tiêu đề (Subject):")); fields.add(subjectField);
        fields.add(new JLabel("Nội dung (Body):")); fields.add(new JScrollPane(bodyField));

        JButton send = new JButton("Gửi thư");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(send);
        dialog.add(fields, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);

        send.addActionListener(e -> {
            String to = toField.getText().trim();
            String subject = subjectField.getText().trim();
            String body = bodyField.getText();
            if (to.isEmpty() || subject.isEmpty() || body.trim().isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập đủ người nhận, tiêu đề và nội dung.");
                return;
            }
            if (sendSmtpMail(loggedInUser, to, subject, body)) {
                JOptionPane.showMessageDialog(dialog, "Gửi thư thành công!");
                dialog.dispose();
                loadInbox();
            } else JOptionPane.showMessageDialog(dialog,
                "Gửi thư thất bại! Kiểm tra Server và database.");
        });
        dialog.setVisible(true);
    }

    private boolean sendSmtpMail(String from, String to, String subject, String body) {
        try (Socket s = new Socket(getServerIp(), getSmtpPort());
             BufferedReader in = reader(s);
             PrintWriter out = writer(s)) {
            in.readLine();
            out.println("MAIL FROM: " + from);
            String r = in.readLine();
            if (r == null || !r.startsWith("250")) return false;

            out.println("RCPT TO: " + to);
            r = in.readLine();
            if (r == null || !r.startsWith("250")) return false;

            out.println("SUBJECT: " + subject);
            r = in.readLine();
            if (r == null || !r.startsWith("250")) return false;

            out.println("DATA");
            r = in.readLine();
            if (r == null || !r.startsWith("354")) return false;

            // Gửi từng dòng; dấu chấm đơn là dấu kết thúc theo giao thức hiện tại.
            String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
            for (String line : normalized.split("\n", -1)) {
                out.println(line.equals(".") ? ".." : line);
            }
            out.println(".");
            r = in.readLine();
            out.println("QUIT");
            in.readLine();
            return r != null && r.startsWith("250");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi gửi thư: " + e.getMessage());
            return false;
        }
    }

    private static BufferedReader reader(Socket s) throws IOException {
        return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
    }

    private static PrintWriter writer(Socket s) throws IOException {
        return new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private static String decode(String s) {
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s == null ? "" : s;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MailClientGUI().setVisible(true));
    }
}
