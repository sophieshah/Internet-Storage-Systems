import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class server {
    int port = 4857;
    ServerSocket serverSocket;
    Socket connection = null;
    String messageFromClient;
    ObjectOutputStream out;
    ObjectInputStream in;

    // Messages that don't change
    static final String DISCONNECTED_MESSAGE = "disconnected";
    static final String HELLO_MESSAGE = "Hello!";
    static final String ERROR_MESSAGE = "Invalid message, please try again.";

    public server() {}

    void print(String s) { System.out.println(s); }

    void run() {
        try {
            // Create server socket
            this.print("Creating server socket on port " + this.port + "...");
            this.serverSocket = new ServerSocket(this.port, 10);
            this.print("Server socket created. Awaiting client connection...");

            // Accept connection from client
            this.connection = this.serverSocket.accept();
            this.print("Connection received from " + this.connection.getInetAddress().getHostName());

            // Create input/output streams
            this.out = new ObjectOutputStream(this.connection.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(this.connection.getInputStream());

            // Begin serving
            this.print("Serving start!");
            this.SendMessage(HELLO_MESSAGE);
            try {
                while (true) {
                    // Receive message
                    this.ReceiveMessage();

                    if (this.messageFromClient.equals("bye")) {
                        // Disconnect server
                        this.SendMessage(DISCONNECTED_MESSAGE);
                        break;
                    } else if (IsLowercaseAndAlphabetic(this.messageFromClient))
                        // Capitalize and return the message
                        this.SendMessage(this.messageFromClient.toUpperCase());
                    else
                        this.SendMessage(ERROR_MESSAGE);
                }

            } catch (ClassNotFoundException e) {
                System.err.println("Data received in unknown format");
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } finally {
            // Close the input/output streams and the socket.
            try {
                if (this.in != null) {
                    this.in.close();
                }
                if (this.out != null) {
                    this.out.close();
                }
                if (this.connection != null) {
                    this.connection.close();
                }
                if (this.serverSocket != null) {
                    this.serverSocket.close();
                }
                this.print("All connections closed, exiting in 3s.");
                Thread.sleep(3000);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
        }
    }

    // Receive the message and print it to the server terminal window.
    void ReceiveMessage() throws IOException, ClassNotFoundException {
        this.messageFromClient = (String)this.in.readObject();
        this.print("\n[CLIENT]: " + this.messageFromClient);
    }

    // Send the message to the client.
    void SendMessage(String msg) throws IOException {
        this.out.writeObject(msg);
        this.out.flush();
        this.print("Sent to client: " + msg);
    }

    // Check whether the message is lowercase and alphabetic.
    boolean IsLowercaseAndAlphabetic(String s) {
        for (char c : s.toCharArray())
            // check if the character is not a lowercase letter
            if (!(c >= 'a' && c <= 'z')) { return false; }
        return true;
    }

    // Set the port number.
    public static void main(String[] args) {
        server s = new server();
        if (args.length == 1) {
            s.port = Integer.parseInt(args[0]);
            s.run();
        } else {
            s.print("Invalid server parameters. Usage: server <port_number>");
            return;
        }
    }
}