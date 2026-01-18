import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;

public class ChatServer {
    private static final int PORT = 12345;
    private static Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static Map<String, Set<String>> groups = new ConcurrentHashMap<>();
    
    // Database of ALL users
    private static Map<String, String> userDatabase = new ConcurrentHashMap<>(); 
    private static final String DB_FILE = "users.txt";
    private static final String HISTORY_FILE = "server_history.txt";

    // History Storage
    private static Map<String, LinkedList<String>> historyStorage = new ConcurrentHashMap<>();
    private static Map<String, Stack<String>> commandHistory = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Chat Server running on Port " + PORT);
        loadDatabase();
        loadHistoryFromFile();

        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void loadHistoryFromFile() {
        File f = new File(HISTORY_FILE);
        if(!f.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
            
                int splitIndex = line.indexOf("|");
                if (splitIndex != -1) {
                    String roomKey = line.substring(0, splitIndex);
                    String message = line.substring(splitIndex + 1);
                    
                    
                    historyStorage.putIfAbsent(roomKey, new LinkedList<>());
                    LinkedList<String> list = historyStorage.get(roomKey);
                    list.add(message);
                    
                    
                    if(list.size() > 100) list.removeFirst();
                }
            }
            System.out.println("History loaded from " + HISTORY_FILE);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static synchronized void saveToHistoryFile(String key, String msg) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(HISTORY_FILE, true))) {
            pw.println(key + "|" + msg);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static synchronized void deleteFromHistoryFile(String key, String fullMsg) {
        File inputFile = new File(HISTORY_FILE);
        File tempFile = new File("history_temp.txt");

        
        String lineToRemove = key + "|" + fullMsg;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {

            String currentLine;
            boolean deleted = false;

            while ((currentLine = reader.readLine()) != null) {
                
                if (!deleted && currentLine.equals(lineToRemove)) {
                    deleted = true; 
                    continue;
                }
                writer.println(currentLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Replace old file with new file
        if (inputFile.delete()) {
            tempFile.renameTo(inputFile);
        }
    }

    // SECURITY: PASSWORD HASHING
    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) { return null; }
    }

    private static void loadDatabase() {
        try (BufferedReader br = new BufferedReader(new FileReader(DB_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) userDatabase.put(parts[0], parts[1]);
            }
        } catch (IOException e) { }
    }

    private static synchronized boolean registerUser(String user, String pass) {
        if (userDatabase.containsKey(user)) return false;
        String hashedPass = hashPassword(pass);
        userDatabase.put(user, hashedPass);
        try (PrintWriter pw = new PrintWriter(new FileWriter(DB_FILE, true))) {
            pw.println(user + ":" + hashedPass);
        } catch (IOException e) {}
        return true;
    }

    private static boolean checkLogin(String user, String pass) {
        if (!userDatabase.containsKey(user)) return false;
        return userDatabase.get(user).equals(hashPassword(pass));
    }

    // HISTORY SYNC
    private static void syncHistory(String user, PrintWriter out) {
        if (historyStorage.containsKey("Global")) {
            for (String msg : historyStorage.get("Global")) out.println("[Global Chat]: " + msg);
        }

        for (String groupName : groups.keySet()) {
            if (groups.get(groupName).contains(user)) {
                if (historyStorage.containsKey(groupName)) {
                    for (String msg : historyStorage.get(groupName)) out.println("[Group " + groupName + "] " + msg);
                }
            }
        }

        for (String key : historyStorage.keySet()) {
            if (key.contains("_")) { 
                String[] parts = key.split("_");
                if (parts.length == 2 && (parts[0].equals(user) || parts[1].equals(user))) {
                    String otherUser = parts[0].equals(user) ? parts[1] : parts[0];
                    for (String fullMsg : historyStorage.get(key)) {
                        String[] mParts = fullMsg.split(": ", 2);
                        if (mParts.length < 2) continue;
                        String sender = mParts[0];
                        String content = mParts[1];

                        if (sender.equals(user)) out.println("[Private to " + otherUser + "]: " + content);
                        else out.println("[Private from " + otherUser + "]: " + content);
                    }
                }
            }
        }
    }

    // utilities
    private static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USERS:");
        
        List<String> allUsers = new ArrayList<>(userDatabase.keySet());
        
        Collections.sort(allUsers);
    
        for (String name : allUsers) {
            sb.append(name).append(",");
        }

        for (PrintWriter writer : clients.values()) {
            writer.println(sb.toString());
        }
    }

    private static String getPrivateChatKey(String u1, String u2) {
        return (u1.compareTo(u2) < 0) ? u1 + "_" + u2 : u2 + "_" + u1;
    }

    private static void saveMessage(String k, String m) {
        historyStorage.putIfAbsent(k, new LinkedList<>());
        LinkedList<String> l = historyStorage.get(k); 
        l.add(m);
        if(l.size()>100) l.removeFirst(); 
        saveToHistoryFile(k, m);
    }

    private static void saveCommand(String u, String c) {
        commandHistory.putIfAbsent(u, new Stack<>());
        Stack<String> s = commandHistory.get(u); s.push(c); if(s.size()>50) s.remove(0);
    }

    private static String undoLastCommand(String user, String roomKey) {
        if (!commandHistory.containsKey(user) || commandHistory.get(user).isEmpty()) return null;
        
        Stack<String> stack = commandHistory.get(user);
        
        for (int i = stack.size() - 1; i >= 0; i--) {
            String command = stack.get(i);
            
            if (command.startsWith(roomKey + ":")) {
                stack.remove(i); 
                return command;
            }
        }
        return null; 
    }

    private static void broadcastMessageRemoval(String roomKey, String sender, String content) {
        if (roomKey.equals("Global")) {
            for (PrintWriter w : clients.values()) 
                w.println("REMOVE_MESSAGE:Global Chat:" + sender + ":" + content);
        } else if (groups.containsKey(roomKey)) {
            for (String m : groups.get(roomKey)) 
                if (clients.containsKey(m)) 
                    clients.get(m).println("REMOVE_MESSAGE:" + roomKey + ":" + sender + ":" + content);
        } else {
            String[] users = roomKey.split("_");
            for (String u : users) {
                if (clients.containsKey(u)) {
                    String targetRoom = u.equals(users[0]) ? users[1] : users[0];
                    clients.get(u).println("REMOVE_MESSAGE:" + targetRoom + ":" + sender + ":" + content);
                }
            }
        }
    }

    //CLIENT HANDLER
    private static class ClientHandler extends Thread {
        private Socket socket;
        private String name;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                while (true) {
                    String req = in.readLine();
                    if (req == null) return;
                    if (req.startsWith("AUTH:")) {
                        String[] parts = req.split(":", 4);
                        if (parts[1].equals("LOGIN")) {
                            if (checkLogin(parts[2], parts[3])) {
                                if (clients.containsKey(parts[2])) out.println("AUTH_FAIL:Already logged in");
                                else { 
                                    out.println("AUTH_SUCCESS"); 
                                    name = parts[2]; 
                                    syncHistory(name, out);
                                    break; 
                                }
                            } else out.println("AUTH_FAIL:Invalid credentials");
                        } else if (parts[1].equals("REGISTER")) {
                            if (registerUser(parts[2], parts[3])) out.println("REG_SUCCESS"); 
                            else out.println("AUTH_FAIL:Username taken");
                        }
                    }
                }

                clients.put(name, out);
                broadcastUserList();
                for (Map.Entry<String, Set<String>> e : groups.entrySet()) if (e.getValue().contains(name)) out.println("NEW_GROUP:" + e.getKey());

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("CREATE_GROUP:")) handleCreateGroup(msg);
                    else if (msg.startsWith("UNDO:")) handleUndo(msg);
                    else if (msg.startsWith("@")) handleRouting(msg);
                    else handleGlobalChat(msg);
                }
            } catch (IOException e) {
            } finally {
                if (name != null) { clients.remove(name); broadcastUserList(); }
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private void handleCreateGroup(String c) {
            String[] p = c.split(":");
            Set<String> s = new HashSet<>(); s.add(name); Collections.addAll(s, p[2].split(","));
            groups.put(p[1], s);
            for(String m:s) if(clients.containsKey(m)) { clients.get(m).println("NEW_GROUP:"+p[1]); clients.get(m).println("[Group "+p[1]+"] You added."); }
        }


        private void handleUndo(String cmd) {
        String targetName = cmd.substring(5);
        
        String roomKey;
        if (targetName.equals("Global Chat")) {
            roomKey = "Global";
        } else if (groups.containsKey(targetName)) {
            roomKey = targetName;
        } else {
            roomKey = getPrivateChatKey(name, targetName);
        }

        String last = undoLastCommand(name, roomKey);
        
        if (last != null) {
            String[] p = last.split(":", 3);
            if (p.length == 3) {
                String k = p[0];       
                String sender = p[1];  
                String content = p[2];
                
                String fullMsg = sender + ": " + content;
                
                
                if (historyStorage.containsKey(k)) {
                    historyStorage.get(k).remove(fullMsg);
                }
                    
                deleteFromHistoryFile(k, fullMsg);

                broadcastMessageRemoval(k, sender, content);
            }
        }
    }

        private void handleGlobalChat(String m) {
            String fullMsg = name+": "+m; 
            saveMessage("Global", fullMsg); 
            saveCommand(name, "Global:"+name+":"+m);
            for(PrintWriter w:clients.values()) w.println("[Global Chat]: "+fullMsg);
        }

        
        private void handleRouting(String m) {  
            int sp = m.indexOf(" "); 
            if (sp == -1) return;
            
            String t = m.substring(1, sp); 
            String msg = m.substring(sp+1); 
            t = t.trim();
            
            String f = name+": "+msg;      
            
            // Check Groups
            if(groups.containsKey(t)) {
                saveMessage(t, f); 
                saveCommand(name, t+":"+name+":"+msg);
                for(String mem:groups.get(t)) if(clients.containsKey(mem)) clients.get(mem).println("[Group "+t+"] "+(mem.equals(name)?"Me":name)+": "+msg);
                return;
            }

            // Check Private Users
            if (userDatabase.containsKey(t)) {
                String k = getPrivateChatKey(name, t);
                saveMessage(k, f);
                saveCommand(name, k+":"+name+":"+msg);

                out.println("[Private to "+t+"]: "+msg);

                if (clients.containsKey(t)) {
                    clients.get(t).println("[Private from "+name+"]: "+msg); 
                }
            } else {
                out.println("[System]: User '" + t + "' not found.");
            }
        }
    }
}