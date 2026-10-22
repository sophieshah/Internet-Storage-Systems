import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.ServerSocket;
import java.net.Socket;

public class server {          // define the server class used to facilitate communication with the client
    int port = 4857;           // the port that the server uses on its machine
    ServerSocket serverSocket; // serversocket used to listen on the port
    Socket connection = null;  // socket for the connection with the client
    String messageFromClient;  // message received from the client
    ObjectOutputStream out;    // stream for writing to the socket
    ObjectInputStream in;      // stream for reading from the socket

    // Messages that don't change
    static final String DISCONNECTED_MESSAGE = "disconnected"; // message sent to the client when the client and the server disconnect from one another
    static final String HELLO_MESSAGE = "Hello!";              // message sent to the client when the client first connects to the server
    static final String ERROR_MESSAGE = "File not found";      // message sent to the client when the name of a nonexistent file is received from the client

    public server() {} // constructor for the server class

    // helper function to reduce the code needed to print something
    void print(String s) { System.out.println(s); }

    void run() {  // used to start the server after its port has been initialized in main
        try {
            // Create server socket
            this.print("Creating server socket on port " + this.port + "...");  // server-side-only message indicating creation of server socket
            this.serverSocket = new ServerSocket(this.port, 10);                // creates the server socket on the specified port using a backlog of size 10
            this.print("Server socket created. Awaiting client connection..."); // server-side-only message indicating the socket is ready to accept a connection

            // Accept connection from client
            this.connection = this.serverSocket.accept();                                             // accept the connection
            this.print("Connection received from " + this.connection.getInetAddress().getHostName()); // print the name of the machine connecting to the server

            // Create input/output streams
            this.out = new ObjectOutputStream(this.connection.getOutputStream()); // create the output stream used to send messages to the client
            this.out.flush();                                                     // flush the output stream to ensure it is empty and ready to send a new message
            this.in = new ObjectInputStream(this.connection.getInputStream());    // create the input stream used to read messages from the client

            // Begin serving
            this.print("Serving start!");    // server-side-only message indicating server is ready
            this.SendMessage(HELLO_MESSAGE); // send the hello message to the client
            try {                            // used for preventing problems with errors
                while (true) {               // run until a manually-defined break occurs
                    // Receive message
                    this.ReceiveMessage(); // receive a message from the client 

                    if (this.messageFromClient.equals("bye")) { // the client is disconnecting
                        // Disconnect server
                        this.SendMessage(DISCONNECTED_MESSAGE); // send the disconnected message to the client
                        break;                                  // exit the serving loop
                    } 
                    
                    if (this.messageFromClient.matches("sample0[1-5].bmp")) {                    // the message from the client is asking for is 'sample01.bmp' through 'sample05.bmp'
                        Path filePath = Paths.get("./" + this.messageFromClient);                // get the path of the file the user seeks
                        if (Files.exists(filePath)) {                                            // if the file exists
                            this.SendMessage("Sending file " + this.messageFromClient + "...");  // tell the client the file is coming
                            this.SendFile(filePath.toString(), this.out);                        // send the file to client
                            continue;                                                            // repeat the serving loop
                        }
                    }
                    this.SendMessage(ERROR_MESSAGE);        // if file not found, send the error message and repeat the serving loop
                }

            } catch (ClassNotFoundException e) {                       // catch the error if the message from the client is not a string
                System.err.println("Data received in unknown format"); // server-side-only error message indicating the client sent a message of the wrong format, then close the server
            }
        } catch (IOException ioException) {  // catch the standard IOExceptionError for problems using input/output streams
            ioException.printStackTrace();   // print the stack trace and close the server
        } finally {
            // Close the input/output streams and the socket.
            try {
                if (this.in != null) this.in.close();                      // if the server input stream exists, close it
                if (this.out != null) this.out.close();                    // if the server output stream exists, close it
                if (this.connection != null) this.connection.close();      // if the server's client connection exists, close it
                if (this.serverSocket != null) this.serverSocket.close();  // if the server's socket exists, close it

                this.print("All connections closed, exiting in 3s.");      // server-side-only message indicating the server is closing in 3 seconds
                Thread.sleep(3000);                                        // pause this thread of execution for three seconds
            } catch (IOException ioException) {                            // catch any IO errors when closing streams, the connection, or the socket
                ioException.printStackTrace();                             // print the IO error's stack trace
            } catch (InterruptedException e) {                             // catch any Interruption errors when closing streams, the connection, or the socket
                e.printStackTrace();                                        // print the Interruption error's stack trace
            }
        }
    }

    // Send the file to the client
    void SendFile(String filePath, ObjectOutputStream output_stream) {
        try (BufferedInputStream input_stream = new BufferedInputStream(new FileInputStream(filePath))) {
            long fileSize = new File(filePath).length();    // calculate the length of the file
            output_stream.writeLong(fileSize);              // send the length of the file to the client so it knows when to stop reading
            output_stream.flush();                          // flush the output stream to get rid of any extra data
            
            long lastPercent = -1;                            // stores the last value of the send status as a percent

            byte[] buffer = new byte[1000];                 // declare buffer of 1,000 bytes
            int bytes;                                      // store file information in integer
            
            while ((bytes = input_stream.read(buffer)) != -1) {  // while end of file has not been reached, read 1000 bytes
                output_stream.write(buffer, 0, bytes);      // write the buffer's content to the output stream (to client)
                output_stream.flush();                      // flush stream in case data left waiting in there
                
                long currentPercent = 100 * (fileSize - input_stream.available()) / fileSize; // calculate the current integer-valued send percentage
                if (currentPercent % 10 == 0 && currentPercent > lastPercent)                 // if the number of bytes as a percentage has exceeded the previously calculated status percentage,      
                {
                    lastPercent = currentPercent;                                             // update the last integer status
                    this.print("File sent " + currentPercent + "%");                          // print the current send percentage only when it increases by an integer amount
                }
            }
            input_stream.close();                                   // close the input stream, ensuring the file is no longer being accessed.
            this.print("File " + filePath + " sent to client successfully."); // server-side-only message indicating the file has finished sending
        } catch (IOException ex) {                      // Catch exception for opening input stream
            System.out.println(ex.getMessage());        // Send message to console
        }
    }

    // Receive the message and print it to the server terminal window.
    void ReceiveMessage() throws IOException, ClassNotFoundException { // define the helper function used to receive a message from the client out of the server's input stream. can throw an IO error or a ClassNotFoundException
        this.messageFromClient = (String)this.in.readObject();         // read the message from the server's input stream and attempt to cast it to a String, which is the expected type of message from the client
        this.print("\n[CLIENT]: " + this.messageFromClient);           // server-side-only message indicating what the client sent to the server, if the String cast succeeds
    }

    // Send the message to the client.
    void SendMessage(String msg) throws IOException {                  // define the helper function used to send a message to the client through the server's output stream. can throw an IOException
        this.out.writeObject(msg);                                     // write the message String to the server's output stream
        this.out.flush();                                              // send the message to the client by flushing the server's output stream
        this.print("Sent to client: " + msg);                          // server-side-only message indicating what was sent to the client
    }

    // Set the port number.
    public static void main(String[] args) {                                    // main function, used as the entry point to the server program
        server s = new server();                                                // create a new object of our server class
        if (args.length == 1) {                                                 // verify that only one argument (port) was used when starting the server program 
            s.port = Integer.parseInt(args[0]);                                 // parse the port number from the command-line argument, and set the new server object's port number to that integer value
            s.run();                                                            // start the server
        } else {                                                                // the command-line start sequence for the server was not correct
            s.print("Invalid server parameters. Usage: server <port_number>");  // server-side-only message indicating the server was started incorrectly and provides instructions for proper use
            return;                                                             // end the server program
        }
    }
}