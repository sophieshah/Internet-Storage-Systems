import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

public class client {
    String URL;             // the URL for the network location of the server
    int port;               // the port that the server is hosting its socket on
    Socket requestSocket;   // the socket used to connect to the server
    ObjectOutputStream out; // the output stream used to send messages to the server
    ObjectInputStream in;   // the input stream used to read messages from the server
    String message;         // variable used to store the message to be sent to the server
    String response;        // variable used to store the message received from the server
    
    static final String RECEIVED_FILES_PATH = "./saved client files/";

    long[] times = new long[5];  // array of longs used to store the communication times between the client and the server
    int timeCounter = 0;         // counter used to store how many communication times have been captured

    public client() {}

    void print(String s) { System.out.println(s); }  // helper function used to eliminate verbose message logging on client-side

    void run() throws InterruptedException { // the function used to initate client loop. can throw an interrupted exception due to thread.sleep
        Scanner s = null;  // define the scanner used to read messages from the server
        try {
            try {
                Files.createDirectories(Paths.get(client.RECEIVED_FILES_PATH));                               // Create folder from client's parameters for saving files if it does not already exist
                this.print("Created folder " + client.RECEIVED_FILES_PATH + " for saving files.");            // client-side-only message indicating the folder was successfully created
            } catch (FileAlreadyExistsException faex) {                                                       // handles the case that the folder already exists
                this.print("Folder " + client.RECEIVED_FILES_PATH + " already exists. Proceeding normally."); // client-side-only message indicating the folder already exists and that the program will proceed as normal
            }

            // Create request socket
            this.requestSocket = new Socket(this.URL, this.port);                     // create the socket useed for connecting to the server
            this.print("Connected to " + this.URL + " in port " + this.port);         // client-side-only message indicating the client has connected to the specified URL at the specified port
            this.out = new ObjectOutputStream(this.requestSocket.getOutputStream());  // create the output stream used to send messages to the server
            this.out.flush();                                                         // ensure the output stream is empty
            this.in = new ObjectInputStream(this.requestSocket.getInputStream());     // create the input stream used to read messages from the server

            // Receive "Hello!" from server
            this.ReceiveMessage();

            // Create a scanner for reading lines
            s = new Scanner(System.in);

            // Begin interfacing with server
            while (true) { // start the user input loop with intent to exit only manually
                // Receive client-side input from the user
                this.message = s.nextLine();

                // Start timing process and send message
                long start = System.currentTimeMillis();  // get the current time in milliseconds
                this.SendMessage(this.message);           // send the user input to the server
                this.ReceiveMessage();                    // get any messages returned from the server
                
                if (this.message.matches("sample0[1-5].bmp")) { // check if the string matches one of the sample files
                    this.ReceiveFile(this.message, this.in);

                    long end = System.currentTimeMillis();    // get the current time in milliseconds
                    long elapsed = end - start;                 // determine the elapsed time                    

                    if (this.timeCounter < 5) {                 // if we haven't recorded five times 
                        this.times[this.timeCounter] = elapsed; // record the elapsed time in our array

                        if (++this.timeCounter == 5)            // increase the counter to note we've recorded a time, and if we have all five times
                            CalculateTimesAndPrint();           // Calculate the minimum, mean, maximum, and standard deviation of the five RTTs and print them to the console
                    }
                    this.print("Elapsed time: " + elapsed + "ms\n");                          // print the RTT of this file
                }

                if (this.message.equals("bye") && this.response.equals("disconnected")) { // if the user input is "bye" and the server's response is "disconnected"
                    this.print("exit");                                                   // client-side-only message indicating the client program is going to exit
                    break;                                                                // exit the user input loop
                }

            }
        } catch (ConnectException ce) {
            System.err.println("Connection refused. A server must be initiated at the specified destination."); // client-side-only message indicating the server is not hosted at the specified URL and port
        } catch (ClassNotFoundException cnfe) {
            System.err.println("Received object's class not found.");                                           // client-side-only message indicating the server sent an uncrecognizable object
        } catch (UnknownHostException uhe) {
            System.err.println("You are trying to connect to an unknown host.");                                // client-side-only message indicating the specified URL and port do not point to a valid network destination
        } catch (IOException ioException) {
            ioException.printStackTrace();                                                                      // on IO Exception, print the stack trace
        } finally {
            // Close the input/output streams and the socket.
            try {
                if (s != null) s.close();                                   // if the socket exists, close it
                if (this.in != null) this.in.close();                       // if the input stream exists, close it
                if (this.out != null) this.out.close();                     // if the output stream exists, close it
                if (this.requestSocket != null) this.requestSocket.close(); // if the socket used to connect to the server exists, close it
            } catch (IOException ioException) {
                ioException.printStackTrace();                              // if there is an IO exception, print the stack trace
            } finally {
                this.print("All connections closed, exiting in 3s.");       // client-side-only message indicating all connections have been closed and that the client program will exit in 3 seconds
                Thread.sleep(3000);                                         // wait 3 seconds
            }
        }
    }

    // helper function for readability, calculates the min, mean, max, and standard deviation of the five RTTs
    void CalculateTimesAndPrint() {
        long min = this.times[0];       // used for storing the minimum of the five times
        long max = this.times[0];       // used for storing the maximum of the five times
        double sum = 0.0;               // used for storing the sum of the times 
        for(int i = 0; i < 5; i++) {
            min = this.times[i] < min ? this.times[i] : min; // if the current value is less than minimum, set the minimum to the current value
            max = this.times[i] > max ? this.times[i] : max; // if the current value is greater than maximum, set the maximum to the current value
            sum += this.times[i];                            // add the current value to the sum
        }
        double mean = sum/5.0;                               // calculate the mean by dividing the sum by the number of times

        double varSum = 0.0;                                 // used for storing the vsum of variances
        for(int i = 0; i < 5; i++){                          // add the square of the difference between each value and the mean (variance) to the sum of variances
            double difference = this.times[i] - mean;        // calculate the difference
            varSum += difference * difference;               // add the variance to the variance sum
        }
        double stddev = Math.sqrt(varSum/(this.times.length - 1)); // calculate the standard deviation by taking the square root of the mean variance

        this.print("\nTIMES (ms): " + Arrays.toString(this.times)); // print all the times for easy reference

        // Print the minimum RTT, mean RTT, max RTT, and standard deviation of the RTT
        this.print("RTT stats over 5 trials (ms):");
        this.print("min: " + min);
        this.print("mean: " + mean);
        this.print("max: " + max);
        this.print("standard deviation: " + stddev);
    }


    // helper function used to receive messages from the server through the client's input stream, can throw ClassNotFoundException or IOExcetion
    void ReceiveMessage() throws ClassNotFoundException, IOException {
        this.response = (String)this.in.readObject(); // Read the message from the server out of the input stream and attempt to cast it to a String
        this.print("\n[SERVER]: " + this.response);   // client-side-only message showing the server's message to the client
    }

    // receive file from server
    void ReceiveFile(String filename, ObjectInputStream input_stream) throws ClassNotFoundException {
        try (BufferedOutputStream output_stream = new BufferedOutputStream(new FileOutputStream(client.RECEIVED_FILES_PATH + filename))) {
            long fileSize = input_stream.readLong();            // read the size of the file first

            long lastPercent = 0L;                               // track the percentage of completion of receiving the file
                        
            byte[] buffer = new byte[1000];                     // declare buffer of 1,000 bytes
            long remaining = fileSize;                          // keep track of how many bytes are left to be read
            while (remaining > 0) {                             // while there are still bytes to be read,
                int bytes = input_stream.read(buffer, 0, (int)Math.min(buffer.length, remaining)); // read as many bytes as possible
                if (bytes == -1) break;                         // if no bytes are read, exit the loop
                output_stream.write(buffer, 0, bytes);          // write the buffer's content to the output stream (to local file)
                output_stream.flush();                          // flush stream in case data left waiting in there

                remaining -= bytes;                                     // subtract the number of bytes received/written from the number of remaining bytes
                long currentPercent = 100 * (fileSize - remaining) / fileSize;  // calculate the current status expressed as a percentage
                if (currentPercent % 10 == 0 && currentPercent > lastPercent) {          // if the status has increased by 10 percent,
                    lastPercent = currentPercent;                                // update the last status
                    this.print("File downloaded " + currentPercent + "%");      // print the current status to the user
                }
            }
            output_stream.close();                                                         // close the output stream, ensuring the file is closed
            this.print("File " + RECEIVED_FILES_PATH + filename + " saved successfully."); // client-side-only message indicating the file has finished saving
        } catch (IOException ex) {                              // Catch exception for opening output stream
            this.print(ex.getMessage());                        // Send message to console
        }
    }

    // helper function used to send messages to the server through the client's output stream, can throw IOException
    void SendMessage(String msg) throws IOException {
        this.out.writeObject(msg);                     // write the String parameter to the client's output stream
        this.out.flush();                              // send the message to the server by flushing the client's output stream, emptying the output stream
    }

    public static void main(String[] args) throws InterruptedException {           // main function, used as the client program's entry point
        client c = new client();                       // create a new object of our client class
        if (args.length == 2) {                        // ensure the command-line input specified a URL and a port
            c.URL = args[0];                           // set the client's URL by getting the URL from the command line argument
            c.port = Integer.parseInt(args[1]);        // set the client's port number by parsing the port number from the command line argument
            c.run();                                   // run this client now that its operational parameters have been set
        } else {                                                                                    // the command-line arguments were not specified correctly
            c.print("Invalid client parameters. Usage: client <serverURL> <port_number>");          // client-side-only message indicating how to properly run the client program
            return;                                                                                 // if client program started incorrectly, exit
        }
    }
}