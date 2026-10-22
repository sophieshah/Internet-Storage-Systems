import java.io.*;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class server {          // define the server class used to facilitate communication with the client
    int port = 4857;           // the port that the server uses on its machine
    boolean initialized = false; // flag noting whether this server has ever had a handler created
    List<server.Handler> runningHandlers; // list containing currently-running handlers
    public server() {                           // constructor for the server class
        runningHandlers = new ArrayList<>();    // set the list of handlers as being an empty list
    } 

    // creates a handler for this server instance
    public void InitializeHandler(Socket connection, int clientNum) {
        if (!this.initialized) this.initialized = true;                          // if the server isn't initialized, set the server as being initialized
        Handler h = new Handler(this, connection, clientNum);   // create a new handler 
        runningHandlers.add(h);                                 // and add it to the list
        h.start();                                              // and start it
        System.out.println("Client " + clientNum + " is connected!"); // print a message saying the client is connected
    }

    // helper function for removing a handler from the running list
    public void RemoveHandler(Handler h) {
        this.runningHandlers.removeIf(runningH -> runningH.clientNum == h.clientNum); // remove the handler if its clientNum equals that of the one to delete
    }

    // helper function for checking if the program should end
    public boolean HasNoConnections() {
        return (this.initialized && this.runningHandlers.isEmpty()); // program should end if this server has been initialized (at least one handler has been created) and if there are no running handlers
    }

    // a handler thread class. Handlers are spawned from the listening loop and are responsible for dealing with a single client's requests
    private static class Handler extends Thread {
        private server parentServer;      // reference to parent server so it can delete itself from the server's list
        private String messageFromClient; // message received from the client
        private Socket connection;        // socket used to connect to client
        private ObjectInputStream in;     // stream read from the socket
        private ObjectOutputStream out;   // stream write to socket
        private int clientNum;            // index number of the client
        private int batchSize = 1;        // batch size for the next images to be sent

        public Handler(server parentServer, Socket connection, int clientNum) {  // constructor for spawning a new handler
            this.parentServer = parentServer;               // give it a reference to its parent server so it can delete itself from its list
            this.connection = connection;                   // assigns the connection for the handler thread
            this.clientNum = clientNum;                     // assigns the client number for this handler thread
        }

        // Messages that don't change
        static final String DISCONNECTED_MESSAGE = "disconnected"; // message sent to the client when the client and the server disconnect from one another
        static final String HELLO_MESSAGE = "Hello!";              // message sent to the client when the client first connects to the server
        static final String ERROR_MESSAGE = "Please type a different command";      // message sent to the client when the name of a nonexistent file is received from the client
        static final String ASK_FOR_NUMS_MESSAGE = "sendnums";     // message sent to the client when the server is ready to receive the numbers
        
        
        // helper function to reduce the code needed to print something
        void print(String s) { System.out.println("[HANDLER #" + this.clientNum + "]: " + s); }

        // helper function to increase the batch size and have it wrap around from 3 -> 1
        private void IncreaseBatchSize() {
            if (this.batchSize == 3) this.batchSize = 1; // if the batch size is already 3 when we increase, wrap around to 1
            else this.batchSize++;                       // otherwise, increase the batch size
        }
        
        public void run() {  // used to start the handler after its connection has been initialized in main
            try {
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
                        } else if (this.messageFromClient.equals("SEND")) {             // the client is asking for the image files
                            this.SendMessage(ASK_FOR_NUMS_MESSAGE);                       // tell the client we are ready to receive the ordering
                            List<String> fileNames = this.ReceiveRandomImageOrderList();  // get the filenames from the client since it should be sending them
                            this.SendBatched(fileNames, batchSize);                       // send the files with the current batch size
                            this.IncreaseBatchSize();                                     // set to the next batch size for this handler
                        } else
                            this.SendMessage(ERROR_MESSAGE);        // if no command found, send the error message and repeat the serving loop
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

                    parentServer.RemoveHandler(this);                            // remove this handler from its parent server's list of handlers
                    this.print("Client disconnected and connections are closed. Terminating this handler.");      // server-side-only message indicating the server is closing in 3 seconds
                    this.join();                                                          // terminate this handler
                } catch (Exception ex) {                            // catch any IO errors when closing streams, the connection, or the socket
                    ex.printStackTrace();                             // print the IO error's stack trace
                }
            }
        }

        // send batched images to the client, throws IOException if read/write fails and FileNotFoundException if the file doesn't exist
        void SendBatched(List<String> files, int batchSize) throws IOException, FileNotFoundException {
            this.print("Sending to client with batch size: " + batchSize);          // server-side-only message indicating how big the batches are
            int numBatches = Math.ceilDiv(files.size(), batchSize);                 // calculate the total number of batches to be sent
            for (int batchIndex = 0; batchIndex < numBatches; batchIndex++)         // iterate over all the batches
            {
                List<ImageFileBatch.ImageFile> imgs = new ArrayList<>();            // create a new empty list of ImageFile objects
                int currentIndex = batchIndex * batchSize;                          // determine the current position in the list of filenames
                for (var i : files.subList(currentIndex, Math.min(files.size(), currentIndex + batchSize))) // iterate over the next (batchSize) number of filenames, getting either the last index or the furthest possible based on batch size
                imgs.add(this.GetImageFile(i));                                 // create and add the ImageFile to the batch
                ImageFileBatch batch = new ImageFileBatch(imgs);                    // create the ImageFileBatch from the list of ImageFile objects
                this.out.writeObject(batch);                                        // write the ImageFileBatch to the output stream
                this.out.flush();                                                   // flush the output stream
                //this.print("Batch " + String.valueOf(batchIndex + 1) + " of " + String.valueOf(numBatches) + " successfully sent to client: " + batch.GetFileNamesString()); // server-side console message illustrating progress
            }
            this.print("Successfully sent " + numBatches + " of " + numBatches + " batches to client");                // server-side-only message indicating all batches have been sent to client
        }

        // create an ImageFile object to be sent in an ImageFileBatch to a client. throws IOException if read/write fails and FileNotFoundException if the file doesn't exist
        ImageFileBatch.ImageFile GetImageFile(String filename) throws IOException, FileNotFoundException
        {
            Path filePath = Paths.get("./" + filename);                 // get the path for the specified filename
            if (!Files.exists(filePath))                                // determine if a file exists at the specified path
            {
                this.SendMessage(ERROR_MESSAGE + ": " + filename);      // if no file exists, send an error message to the client
                throw new FileNotFoundException(filename);              // throw a new FileNotFoundException
            }
            
            long size = -1;                                             // create a default size value
            byte[] data = null;                                         // create default byte data
            try (BufferedInputStream input_stream = new BufferedInputStream(new FileInputStream(filePath.toString()))) {    // create a new buffered input stream for reading image file byte data
                size = new File(filePath.toString()).length();                          // get the size of the file by creating a File object and getting its length
                data = input_stream.readAllBytes();                                     // read the image file's byte data
            } catch (IOException ex) {                                                  // if reading the image file's byte data fails by throwing an IOException,
                this.print(ex.getMessage());                                            // print the error to the client console
                throw ex;                                                               // rethrow
            }
            return new ImageFileBatch.ImageFile(filePath.toString(), size, data);       // create and return a new ImageFile object from the gathered fields
            
        }

        // Receive the message and print it to the server terminal window.
        void ReceiveMessage() throws IOException, ClassNotFoundException { // define the helper function used to receive a message from the client out of the server's input stream. can throw an IO error or a ClassNotFoundException
            try {
                this.messageFromClient = (String)this.in.readObject();         // read the message from the server's input stream and attempt to cast it to a String, which is the expected type of message from the client
                this.print("Client Message: " + this.messageFromClient);           // server-side-only message indicating what the client sent to the server, if the String cast succeeds
            } catch (EOFException eofException) {                                              // catch a ^C disconnect error from client
                this.print("Client disconnected unexpectedly: " + eofException.getMessage());  // server-side-only message indicating the client disconnected unexpectedly
            }
        }
        
        // Send the message to the client.
        void SendMessage(String msg) throws IOException {                  // define the helper function used to send a message to the client through the server's output stream. can throw an IOException
            this.out.writeObject(msg);                                     // write the message String to the server's output stream
            this.out.flush();                                              // send the message to the client by flushing the server's output stream
            this.print("Sent to client: " + msg);                          // server-side-only message indicating what was sent to the client
        }

        // Receive the list from the client, throwing an IOException if there's a read error and a ClassNotFoundException if the read object is not an Integer.
        List<String> ReceiveRandomImageOrderList() throws IOException, ClassNotFoundException {
            List<Integer> nums = new ArrayList<>();     // create a new empty list
            for (int i = 0; i < 10; i++)        // read a number ten times
                nums.add(this.in.readInt());    // add each number to the list
            List<String> names = new ArrayList<>();     // create a new list for the filenames
            for (Integer i : nums)              // for each number in the numbers list
                names.add("sample" + (i == 10 ? "" : "0") + i.intValue() + ".bmp"); // convert it to the appropriate filename, accounting for not using a zero if it is the tenth image
                this.print("Image order to send: " + names.toString()); // print the image order to the client console
            return names;                                             // return the list of filenames
        }
    }

    // Set the port number.
    public static void main(String[] args) {            
        server s = new server();                                                // create a new object of our server class
        if (args.length == 1) {                                                 // verify that only one argument (port) was used when starting the server program 
            s.port = Integer.parseInt(args[0]);                                     // start the server
        } else {                                                                // the command-line start sequence for the server was not correct
            System.out.println("Invalid server parameters. Usage: server <port_number>");  // server-side-only message indicating the server was started incorrectly and provides instructions for proper use
            return;                                                             // end the server program
        }
        
        // wait and create handlers
        ServerSocket listener = null;         // create a serversocket to allow connections to be made and closed
        try {
            System.out.println("Server ready to accept connections and spawn threads on " + InetAddress.getLocalHost().getHostAddress() + ":" + s.port); // server-side only message indicating the server is ready to accept connections.
            listener = new ServerSocket(s.port); // create a new serversocket for receiving connections
            while (!s.HasNoConnections()) {                                            // run as long as the server has been initialized and has at least one connection
                listener.setSoTimeout(100);                                   // set a timeout so we don't just sit here waiting and the exit condition can be checked again
                try {
                    s.InitializeHandler(listener.accept(), s.runningHandlers.size() + 1);  // create a new handler by accepting an incoming connection and start the handler
                } catch (SocketTimeoutException ex) {}                                     // allow the loop to re-run
            }
            if (listener != null) listener.close();                            // if the server's socket exists, close it
            System.out.println("All handlers have been terminated and all connections have been closed. Terminating this server in 3s."); // server-side-only message indicating server will close in three seconds
            Thread.sleep(3000);                                        // pause this thread of execution for three seconds
        } catch (Exception e) {                                                // if an exception is thrown, catch it
            System.out.println(e);                                             // print the exception
        }
        System.exit(0);                                                 // ensure the program exits
    }
}