import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class client {
    String URL;             // the URL for the network location of the server
    int port;               // the port that the server is hosting its socket on
    Socket requestSocket;   // the socket used to connect to the server
    ObjectOutputStream out; // the output stream used to send messages to the server
    ObjectInputStream in;   // the input stream used to read messages from the server
    String message;         // variable used to store the message to be sent to the server
    String response;        // variable used to store the message received from the server
    String saveDir;         // used to determine where files will be saved
    
    // default location for saving received files
    private String RECEIVED_FILES_PATH = "./saved client files/";

    long[] times = new long[5];  // array of longs used to store the communication times between the client and the server
    int timeCounter = 0;         // counter used to store how many communication times have been captured

    public client() {                                                               // constructor
        Date date = new Date(System.currentTimeMillis());                           // create a readable date from the current time in milliseconds
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HHmmss");     // create a new simple date format to make the date and time readable
        String formattedDate = formatter.format(date);                              // format the date we created
        this.RECEIVED_FILES_PATH += formattedDate + "/";                            // make the save directory for this client unique by including its creation time
    }          

    void print(String s) { System.out.println(s); }  // helper function used to eliminate verbose message logging on client-side

    void run() throws InterruptedException { // the function used to initate client loop. can throw an interrupted exception due to thread.sleep
        Scanner s = null;  // define the scanner used to read messages from the server
        try {
            try {
                Files.createDirectories(Paths.get(this.RECEIVED_FILES_PATH));                                // Create folder from client's parameters for saving files if it does not already exist
                this.print("Created folder " + this.RECEIVED_FILES_PATH + " for saving files.");             // client-side-only message indicating the folder was successfully created
            } catch (FileAlreadyExistsException faex) {                                                        // handles the case that the folder already exists
                this.print ("Folder " + this.RECEIVED_FILES_PATH + " already exists. Proceeding normally."); // client-side-only message indicating the folder already exists and that the program will proceed as normal
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
                
                if (this.message.equals("SEND"))           // if the user inputs the command to send the random list of file numbers
                {
                    this.SetUpSaveDir();                            // set up a directory for those files to be saved
                    long start = System.currentTimeMillis();        // get the current time in milliseconds
                    this.SendMessage(this.message);                 // send the "SEND" message
                    this.ReceiveMessage();                          // wait for server's ready signal
                    if (this.response.equals("sendnums"))  // if the server sends the ready signal
                        this.SendRandomImageNumberList();           // send the random numbers to the server
                    this.ReceiveBatchedImages();                   // wait to receive batched images

                    long end = System.currentTimeMillis();      // get the current time in milliseconds
                    long elapsed = end - start;                 // determine the elapsed time                    

                    if (this.timeCounter < 5) {                 // if we haven't recorded five times 
                        this.times[this.timeCounter] = elapsed; // record the elapsed time in our array

                        if (++this.timeCounter == 5)            // increase the counter to note we've recorded a time, and if we have all five times
                            CalculateTimesAndPrint();           // Calculate the minimum, mean, maximum, and standard deviation of the five RTTs and print them to the console
                    }
                    this.print("Elapsed time: " + elapsed + "ms\n");                          // print the RTT of this set of images
                } else if (this.message.equals("bye")) {                  // if the user's input is "bye"
                    this.SendMessage(this.message);                          // send the message to the server
                    this.ReceiveMessage();                                  // wait for server reply
                    if (this.response.equals("disconnected")) {    // if the server's response is "disconnected"
                        this.print("exit");                               // client-side-only message indicating the client program is going to exit
                        break;                                               // exit the user input loop
                    }
                } else {
                    this.SendMessage(this.message);                      // send the message and check reply bro
                    this.ReceiveMessage();                               // and see what happens bro
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
            } catch (IOException ioException) {                             // catch an IO exception
                ioException.printStackTrace();                              // if there is an IO exception, print the stack trace
            } finally {
                this.print("All connections closed, exiting in 3s.");       // client-side-only message indicating all connections have been closed and that the client program will exit in 3 seconds
                Thread.sleep(3000);                                         // wait 3 seconds
            }
        }
    }

    // helper function to set up a new directory to write files to
    void SetUpSaveDir() throws FileAlreadyExistsException, IOException {
        String loc = this.RECEIVED_FILES_PATH + "attempt" + String.valueOf(this.timeCounter + 1) + "/";   // get a unique value to use in the folder's name for when we have many clients
        Files.createDirectories(Paths.get(loc));                                                            // Create folder from client's parameters for saving files if it does not already exist
        this.print("Created folder " + loc + " for saving files.");                                         // client-side-only message indicating the folder was successfully created
        this.saveDir = loc;                                                                                 // tell this client where it will be saving files
    }

    // helper function for readability, calculates the min, mean, max, and standard deviation of the five RTTs
    void CalculateTimesAndPrint() {
        long min = this.times[0];       // used for storing the minimum of the five times
        long max = this.times[0];       // used for storing the maximum of the five times
        double sum = 0.0;               // used for storing the sum of the times 
        for(int i = 0; i < 5; i++) {    // iterate over the five times
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
        this.print("RTT stats over 5 trials (ms):");      // print statement indicating that RTT values are incoming
        this.print("min: " + min);                          // print min
        this.print("mean: " + mean);                        // print mean
        this.print("max: " + max);                          // print max
        this.print("standard deviation: " + stddev);        // print stddev
    }


    // helper function used to receive messages from the server through the client's input stream, can throw ClassNotFoundException or IOExcetion
    void ReceiveMessage() throws ClassNotFoundException, IOException {
        this.response = (String)this.in.readObject(); // Read the message from the server out of the input stream and attempt to cast it to a String
        this.print("\n[SERVER]: " + this.response);   // client-side-only message showing the server's message to the client
    }

    // helper function to receive batched images, throws IOException for read/write error and ClassNotFoundException for failure to recognize file type
    void ReceiveBatchedImages() throws IOException, ClassNotFoundException {
        List<ImageFileBatch> batches = new ArrayList<>();                   // create a list for storing all the batches

        ImageFileBatch firstBatch = (ImageFileBatch)this.in.readObject();   // read the input stream and attempt to convert it into an ImageFileBatch
        int numBatches = firstBatch.GetTotalBatches();                      // calculate the total number of batches that will be received
        batches.add(firstBatch);                                            // add the first batch to the list of batches

        while (batches.size() < numBatches)                                 // until all batches have been received
            batches.add((ImageFileBatch)this.in.readObject());              // read all the batches from the stream

        for (int i = 0; i < batches.size(); i++)                            // iterate over all the batches
            this.print("Received batch " + (i + 1) + " of " + numBatches + ": " + batches.get(i).GetFileNamesString()); // log batch receipt

        this.WriteFilesFromBatches(batches);                                // write the batches
    }

    // helper function to write the files from the batches, throws IOException for read/write error and FileNotFoundException if it can't write to the file for some reason
    void WriteFilesFromBatches(List<ImageFileBatch> batches) throws IOException, FileNotFoundException {
        this.print("Saving batched files...");                                  // client-side-only message indicating that files are being saved
        for (ImageFileBatch batch : batches) {                                    // iterate over all batches
            for (ImageFileBatch.ImageFile img : batch.batch) {                    // iterate over all files in each batch
                try (BufferedOutputStream output_stream = new BufferedOutputStream(new FileOutputStream(this.saveDir + img.filename))) { // try to open a new file output stream
                    output_stream.write(img.data);                                // write the byte data at the specified location
                    output_stream.flush();                                        // flush the output stream
                    output_stream.close();                                        // close the output stream
                    this.print("File " + img.filename + " saved to " + this.saveDir + " successfully."); // client-side-only message to indicate the image file has been saved
                }
            }
        }
        this.print("Finished saving all files.");  // client-side-only message to indicate all files have been saved
    }

    // helper function used to send messages to the server through the client's output stream, can throw IOException
    void SendMessage(String msg) throws IOException {
        this.out.writeObject(msg);                     // write the String parameter to the client's output stream
        this.out.flush();                              // send the message to the server by flushing the client's output stream, emptying the output stream
    }

    // send the list to the server, can throw IOException when trying to write
    void SendRandomImageNumberList() throws IOException {
        List<Integer> numbers = GetRandomImageOrderIntegers();  // get the randomly-ordered integers used for the filenames
        this.print("\nExpected image order: " + numbers.toString()); // print a client-side message indicating the expected image order based on the random generation
        for (int i : numbers)             // iterate over all the randomly-ordered image numbers
        {
            this.out.writeInt(i);      // write the image number
            this.out.flush();          // flush the output stream
        }
    }

    // helper function used to get a random ordering of the integer numbers for the images.
    List<Integer> GetRandomImageOrderIntegers() {
        ArrayList<Integer> nums = new ArrayList<>(List.of( // define the original list of all 10 imnage numbers
            1,
            2, 
            3, 
            4, 
            5, 
            6, 
            7,
            8,
            9,
            10
        ));                                 
        Collections.shuffle(nums);  // shuffle the numbers in place
        return nums;                // return the shuffled numbers
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