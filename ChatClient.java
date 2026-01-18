import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class ChatClient {
    private static final String SECRET_KEY="1234567890123456";
    private String serverAddress;
    private int serverPort;
    private String myName;
    private PrintWriter out;
    private BufferedReader in;

    // Chat Data
    private Map<String, JPanel> chatPanels = new HashMap<>();
    private Map<String, ArrayList<String>> messageMemory= new HashMap<>();
    private java.util.List<String> onlineUsers = new ArrayList<>();
    private String currentRecipient = "Global Chat"; 
    
    // UI Components
    private JFrame frame;
    private JPanel cardPanel; 
    private CardLayout cardLayout;
    private DefaultListModel<String> contactListModel;
    private JLabel currentChatLabel;

    // Search Window
    private JFrame searchWindow;
    private JTextArea searchArea;

    // Colors
    private Color COL_BG = new Color(11, 20, 26);
    private Color COL_SIDEBAR = new Color(32, 44, 51);
    private Color COL_HEADER = new Color(32, 44, 51);
    private Color COL_MSG_ME = new Color(0, 92, 75);
    private Color COL_MSG_THEM = new Color(50, 50, 50);

    public ChatClient() { setupLogin(); }

    private String encrypt(String strToEncrypt) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes("UTF-8")));
        } catch (Exception e) { e.printStackTrace(); return null; }
    } 

    private String decrypt(String strToDecrypt) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
        } catch (Exception e) { return strToDecrypt; }
    }

    private void setupLogin() {
        // IDENTITY VERIFICATION
        JPanel panel1 = new JPanel(new GridLayout(0, 1));
        JTextField userField = new JTextField("");
        JPasswordField passField = new JPasswordField(""); 
        
        panel1.add(new JLabel("Username:")); panel1.add(userField);
        panel1.add(new JLabel("Password:")); panel1.add(passField);

        Object[] options = {"Login", "Register", "Cancel"};
        int choice = JOptionPane.showOptionDialog(null, panel1, "Identity Verification",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        if (choice == 2 || choice == -1) System.exit(0);

        this.myName = userField.getText().trim();
        String pass = new String(passField.getPassword());
        String action = (choice == 0) ? "LOGIN" : "REGISTER";

        // Port Connection
        JPanel panel2 = new JPanel(new GridLayout(0, 1));
        String defaultIP = (this.serverAddress != null) ? this.serverAddress : "0.tcp.ap.ngrok.io";
        String defaultPort = (this.serverPort > 0) ? String.valueOf(this.serverPort) : "";
        JTextField ipField = new JTextField(defaultIP); 
        JTextField portField = new JTextField(defaultPort);
        
        panel2.add(new JLabel("Server Address:")); panel2.add(ipField);
        panel2.add(new JLabel("Port:")); panel2.add(portField);

        int result = JOptionPane.showConfirmDialog(null, panel2, "Port Connection", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) System.exit(0);

        //Execute Connection
        try {
            this.serverAddress = ipField.getText().trim();
            this.serverPort = Integer.parseInt(portField.getText().trim());

            Socket socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("AUTH:" + action + ":" + myName + ":" + pass);
            String response = in.readLine();

            if (action.equals("REGISTER")) {
                if ("REG_SUCCESS".equals(response)) {
                    JOptionPane.showMessageDialog(null, "Account Created Successfully!\nPlease Log In now.");
                    socket.close(); setupLogin(); return;
                } else {
                    JOptionPane.showMessageDialog(null, "Registration Failed: " + response);
                    socket.close(); setupLogin(); return;
                }
            }

            if ("AUTH_SUCCESS".equals(response)) {
                buildGUI();
                new Thread(() -> {
                    try { String line; while ((line = in.readLine()) != null) process(line); } catch (Exception e) {}
                }).start();
            } else {
                JOptionPane.showMessageDialog(null, "Login Failed: " + response);
                socket.close(); setupLogin();
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Connection Error: " + e.getMessage());
            setupLogin();
        }
    }

    private void buildGUI() {
        frame = new JFrame("Chatting App - " + myName);
        frame.setSize(1000, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Sidebar
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(COL_SIDEBAR);
        JButton newGroupBtn = new JButton("+ New Group");
        newGroupBtn.setBackground(new Color(0, 168, 132));
        newGroupBtn.setForeground(Color.WHITE);
        newGroupBtn.addActionListener(e -> showCreateGroup());
        sidebar.add(newGroupBtn, BorderLayout.NORTH);

        contactListModel = new DefaultListModel<>();
        contactListModel.addElement("Global Chat");
        JList<String> list = new JList<>(contactListModel);
        list.setBackground(COL_SIDEBAR);
        list.setForeground(Color.WHITE);
        list.setFont(new Font("SansSerif", Font.PLAIN, 16));
        list.setFixedCellHeight(50);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                currentRecipient = list.getSelectedValue();
                currentChatLabel.setText(currentRecipient);
                createChatPanel(currentRecipient);
                cardLayout.show(cardPanel, currentRecipient);
            }
        });
        sidebar.add(new JScrollPane(list), BorderLayout.CENTER);
        sidebar.setPreferredSize(new Dimension(250, 0));
        frame.add(sidebar, BorderLayout.WEST);

        // Header
        JPanel right = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COL_HEADER);
        header.setPreferredSize(new Dimension(0, 60));
        currentChatLabel = new JLabel("  Global Chat");
        currentChatLabel.setForeground(Color.WHITE);
        currentChatLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        JButton searchBtn = new JButton("🔍 Search");
        searchBtn.addActionListener(e -> performSearch());
        header.add(currentChatLabel, BorderLayout.WEST);
        header.add(searchBtn, BorderLayout.EAST);
        right.add(header, BorderLayout.NORTH);

        // Chat Area
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(COL_BG);
        createChatPanel("Global Chat");
        right.add(cardPanel, BorderLayout.CENTER);

        // Input & Undo
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField input = new JTextField();
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0,0));
        JButton undoBtn = new JButton("⎌ Undo");
        undoBtn.setBackground(new Color(200, 50, 50));
        undoBtn.setForeground(Color.WHITE);
        undoBtn.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(frame, "Undo last message?", "Confirm", JOptionPane.YES_NO_OPTION);
            if(c == JOptionPane.YES_OPTION) out.println("UNDO:" + currentRecipient  );
        });
        JButton sendBtn = new JButton("➤");
        ActionListener sendAct = e -> { if(!input.getText().isEmpty()) { sendMessage(input.getText()); input.setText(""); }};
        input.addActionListener(sendAct);
        sendBtn.addActionListener(sendAct);
        btnPanel.add(undoBtn);
        btnPanel.add(sendBtn);
        inputPanel.add(input, BorderLayout.CENTER);
        inputPanel.add(btnPanel, BorderLayout.EAST);
        right.add(inputPanel, BorderLayout.SOUTH);

        frame.add(right, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private String formatForBubble(String text) {
        if (text == null) return "";
        return text.replaceAll("(\\S{30})", "$1 ");
    }

    private void sendMessage(String msg) {
        String encryptedMsg = encrypt(msg);
        
        if (currentRecipient.equals("Global Chat")) {
            out.println(encryptedMsg);
        } else {
            out.println("@" + currentRecipient + " " + encryptedMsg);
        }
    }

    private void createChatPanel(String id) {
        if (!chatPanels.containsKey(id)) {
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBackground(COL_BG);
            
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setBackground(COL_BG);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; gbc.gridy = 0; 
            gbc.weightx = 1.0; gbc.weighty = 1.0; 
            gbc.anchor = GridBagConstraints.SOUTH; 
            gbc.fill = GridBagConstraints.HORIZONTAL;
            wrapper.add(content, gbc);

            JScrollPane s = new JScrollPane(wrapper);
            s.setBorder(null);
            s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            
            chatPanels.put(id, content);
            cardPanel.add(s, id);
        }
    }

    private void addBubble(String id, String txt, boolean me, String customTime) {
        createChatPanel(id);

        messageMemory.putIfAbsent(id, new ArrayList<>());
        messageMemory.get(id).add(txt);

        JPanel p = chatPanels.get(id);
        String safeTxt = formatForBubble(txt);
        
        String time = (customTime != null) ? customTime : LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));

        JPanel row = new JPanel(new FlowLayout(me ? FlowLayout.RIGHT : FlowLayout.LEFT));
        row.setOpaque(false); 
        row.setBorder(new EmptyBorder(2, 10, 2, 10)); 

        Color bubbleColor = me ? COL_MSG_ME : COL_MSG_THEM;
        RoundedPanel bubble = new RoundedPanel(new BorderLayout(), 25, bubbleColor);
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12)); 
        bubble.setName(me ? myName : id);

        JLabel lbl = new JLabel("<html><body style='width: 220px; word-wrap: break-word'>" 
                                + safeTxt 
                                + "<br><div style='text-align:right; font-size:9px; color:#cccccc; margin-top:4px'>" 
                                + time + "</div></body></html>");
        lbl.setForeground(Color.WHITE);
        
        bubble.add(lbl, BorderLayout.CENTER);
        row.add(bubble); 

        p.add(row);
        p.revalidate();
        
        SwingUtilities.invokeLater(() -> {
            Container parent = p.getParent(); 
            while (parent != null && !(parent instanceof JScrollPane)) {
                parent = parent.getParent();
            }
            if (parent != null) {
                JScrollPane sc = (JScrollPane) parent;
                JScrollBar bar = sc.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    private void removeBubble(String room, String sender, String content) {
        String decryptedContent = decrypt(content);
        String target = room.equals("Global") ? "Global Chat" : room;
        String formattedTxt = formatForBubble(decryptedContent).trim();

        if (chatPanels.containsKey(target)) {
            JPanel p = chatPanels.get(target);
            Component[] comps = p.getComponents();

            for (int i = comps.length - 1; i >= 0; i--) {
                if (comps[i] instanceof JPanel) {
                    JPanel row = (JPanel) comps[i];
                    if (row.getComponentCount() > 0) {
                        Component bubbleComp = row.getComponent(0);
                        if (bubbleComp instanceof JPanel) {
                            JPanel bubble = (JPanel) bubbleComp;

                            if (bubble.getName() != null && bubble.getName().equals(sender)) {

                                if (bubble.getComponentCount() > 0 && bubble.getComponent(0) instanceof JLabel) {
                                    JLabel l = (JLabel) bubble.getComponent(0);
                                    String bubbleHtml = l.getText();

                                    if (bubbleHtml.contains(decryptedContent) || bubbleHtml.contains(formattedTxt)) {
                                        p.remove(i);
                                        p.revalidate();
                                        p.repaint();
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        String searchText;
        if (sender.equals(myName)) {
            searchText = decryptedContent;
        } else {
            searchText = sender + ": " + decryptedContent;
        }

        // Remove from memory
        if(messageMemory.containsKey(target)) {
            messageMemory.get(target).remove(searchText);
        }

        if(chatPanels.containsKey(target)) {    
            JPanel p = chatPanels.get(target);
            Component[] comps = p.getComponents();
            
            for(int i = comps.length - 1; i >= 0; i--) {
                if(comps[i] instanceof JPanel) {
                    JPanel row = (JPanel)comps[i];
                    if(row.getComponentCount() > 0) {
                        Component bubbleComp = row.getComponent(0); 
                        if (bubbleComp instanceof JPanel) {
                            JPanel bubble = (JPanel) bubbleComp;
                            if (bubble.getComponentCount() > 0) {
                                Component lblComp = bubble.getComponent(0);
                                if (lblComp instanceof JLabel) {
                                    JLabel l = (JLabel) lblComp;
                                    String bubbleHtml = l.getText(); // This is raw HTML
                        
                                    if (bubbleHtml.contains("SEARCH_RESULT")) continue;
                                
                                    if(bubbleHtml.contains(searchText) || bubbleHtml.contains(formatForBubble(searchText).trim())) { 
                                        p.remove(i); 
                                        p.revalidate(); 
                                        p.repaint(); 
                                            return; 
                                        }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void performSearch() {
        String k = JOptionPane.showInputDialog(frame, "Search in " + currentRecipient + ":");
        if (k == null || k.isEmpty()) return;

        if (searchWindow == null) {
            searchWindow = new JFrame("Search Results");
            searchWindow.setSize(400, 500);
            searchWindow.setLayout(new BorderLayout());
            searchArea = new JTextArea();
            searchArea.setEditable(false);
            searchArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
            searchWindow.add(new JScrollPane(searchArea), BorderLayout.CENTER);
        }

        searchArea.setText(""); 
        
        
        if (messageMemory.containsKey(currentRecipient)) {
            ArrayList<String> messages = messageMemory.get(currentRecipient);
            int count = 0;
            for (String msg : messages) {
                if (msg.toLowerCase().contains(k.toLowerCase())) {
                    searchArea.append(" -> " + msg + "\n\n");
                    count++;
                }
            }
            searchArea.append("Found " + count + " matches.\n");
        } else {
            searchArea.append("No messages found in this chat.\n");
        }
        
        searchWindow.setVisible(true);
        searchWindow.toFront();
    }
    
    private void showCreateGroup() {
        if(onlineUsers.isEmpty()) return;
        JList<String> u = new JList<>(onlineUsers.toArray(new String[0]));
        u.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JTextField n = new JTextField();
        Object[] m = {"Name:", n, "Members:", new JScrollPane(u)};
        int r = JOptionPane.showConfirmDialog(frame, m, "Group", JOptionPane.OK_CANCEL_OPTION);
        if(r==JOptionPane.OK_OPTION) out.println("CREATE_GROUP:"+n.getText()+":"+String.join(",", u.getSelectedValuesList()));
    }

    private void validateContact(String name) {
        SwingUtilities.invokeLater(() -> {
            if (!contactListModel.contains(name)) {
                contactListModel.addElement(name);
            }
        });
    }

   private void process(String line) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));

        try {
            if (line.startsWith("USERS:")) {
                String[] u = line.substring(6).split(",");
                onlineUsers.clear();
                SwingUtilities.invokeLater(() -> {
                    for (String x : u) { if (!x.isEmpty() && !x.equals(myName)) { onlineUsers.add(x); if (!contactListModel.contains(x)) contactListModel.addElement(x); }}
                });
            }
            else if (line.startsWith("REMOVE_MESSAGE:")) {
                String[] p = line.split(":", 4);
                if (p.length >= 4) {
                    String room = p[1];
                    String sender = p[2];
                    String content = p[3];
                SwingUtilities.invokeLater(() -> removeBubble(room, sender, content));
                }
            }
            else if (line.startsWith("NEW_GROUP:")) { 
                String g=line.substring(10); 
                SwingUtilities.invokeLater(()->{if(!contactListModel.contains(g)) contactListModel.addElement(g);}); 
            }
            
            
            else if (line.startsWith("[Private from")) {
                
                String sender = line.substring(14, line.indexOf("]"));
                
                
                int splitPoint = line.indexOf("]: ");
                if (splitPoint != -1) {
                    String encryptedMsg = line.substring(splitPoint + 3); 
                    String decryptedMsg = decrypt(encryptedMsg); 
                    validateContact(sender); 
                    SwingUtilities.invokeLater(() -> addBubble(sender, decryptedMsg, false, time));
                }
            }
            else if (line.startsWith("[Private to")) {
               
                String recipient = line.substring(12, line.indexOf("]"));
            
                int splitPoint = line.indexOf("]: ");
                if (splitPoint != -1) {
                    String encryptedMsg = line.substring(splitPoint + 3);
                    String decryptedMsg = decrypt(encryptedMsg); 
                    validateContact(recipient); 
                    SwingUtilities.invokeLater(() -> addBubble(recipient, decryptedMsg, true, time));
                }
            }
            else if (line.startsWith("[Group")) { 
                int end = line.indexOf("]");
                String groupName = line.substring(7, end);
                String content = line.substring(end+2);
                boolean isMe = content.startsWith("Me:");
                
                
                String encryptedMsg;
                if (isMe) {
                    encryptedMsg = content.substring(4); // Skip "Me: "
                } else {
                    int msgStart = content.indexOf(": ");
                    encryptedMsg = (msgStart != -1) ? content.substring(msgStart + 2) : content;
                }
                
                String decryptedMsg = decrypt(encryptedMsg); 
                String displayMsg = isMe ? decryptedMsg : (content.split(":")[0] + ": " + decryptedMsg);

                validateContact(groupName); 
                SwingUtilities.invokeLater(() -> addBubble(groupName, displayMsg, isMe, time)); 
            }
            else if (line.startsWith("[Global")) {
                
                String content = line.substring(15); 
                boolean isMe = content.contains(myName+":");
                
                String sender = isMe ? myName : content.split(":")[0];
                
                
                int msgStart = content.indexOf(": ");
                if (msgStart != -1) {
                    String encryptedMsg = content.substring(msgStart + 2);
                    String decryptedMsg = decrypt(encryptedMsg); 

                    String displayMsg = isMe ? decryptedMsg : sender + ": " + decryptedMsg;
                    SwingUtilities.invokeLater(() -> addBubble("Global Chat", displayMsg, isMe, time));
                }
            }
        } catch (Exception e) {
            System.out.println("Error processing line: " + line);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(ChatClient::new); }
}

class RoundedPanel extends JPanel {
    private Color backgroundColor;
    private int cornerRadius;

    public RoundedPanel(LayoutManager layout, int radius, Color bgColor) {
        super(layout);
        this.cornerRadius = radius;
        this.backgroundColor = bgColor;
        setOpaque(false); 
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Dimension arcs = new Dimension(cornerRadius, cornerRadius);
        int width = getWidth();
        int height = getHeight();
        Graphics2D graphics = (Graphics2D) g;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(backgroundColor);
        graphics.fillRoundRect(0, 0, width - 1, height - 1, arcs.width, arcs.height);
    }
}