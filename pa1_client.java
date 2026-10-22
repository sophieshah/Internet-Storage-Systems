import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class client {
    String URL;
    int port;
    Socket requestSocket;
    ObjectOutputStream out;
    ObjectInputStream in;
    String message;
    String response;

    long[] times = new long[5];
    int timeCounter = 0;

    public client() {}

    void print(String s) { System.out.println(s); }

    void run() {
        Scanner s = null;
        try {
            // Create request socket
            this.requestSocket = new Socket(this.URL, this.port);
            this.print("Connected to " + this.URL + " in port " + this.port);
            this.out = new ObjectOutputStream(this.requestSocket.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(this.requestSocket.getInputStream());

            // Receive "Hello!" from server
            this.ReceiveMessage();

            // Create a scanner for reading lines
            s = new Scanner(System.in);

            // Begin interfacing with server
            while (true) {
                // Receive input
                this.message = s.nextLine();

                // Start timing process and send message
                long start = System.currentTimeMillis();
                this.SendMessage(this.message);
                ReceiveMessage();
                long end = System.currentTimeMillis();

                long elapsed = end - start;

                if (this.timeCounter < 5) {
                    this.times[this.timeCounter] = elapsed;
                    this.timeCounter++;

                    if (this.timeCounter == 5) {
                        long min = this.times[0];
                        long max = this.times[0];
                        double sum = 0.0;
                        for(int i = 0; i < 5; i++) {
                            if (this.times[i] < min){
                                min = this.times[i];
                            }
                            if (this.times[i] > max){
                                max = this.times[i];
                            }
                            sum += this.times[i];
                        }
                        double mean = sum/5.0;

                        double varSum = 0.0;
                        for(int i = 0; i < 5; i++){
                            double difference = this.times[i] - mean;
                            varSum += difference * difference;
                        }
                        double stddev = Math.sqrt(varSum/(this.times.length - 1));
                        this.print("RTT stats over 5 trials (ms):");
                        this.print("min: " + min);
                        this.print("mean: " + mean);
                        this.print("max: " + max);
                        this.print("standard deviation: " + stddev);
                    }
                }

                if (this.message.equals("bye") && this.response.equals("disconnected")) {
                    this.print("exit");
                    break;
                }

                this.print("Elapsed time: " + elapsed + "ms\n");
            }
        } catch (ConnectException ce) {
            System.err.println("Connection refused. A server must be initiated at the specified destination.");
        } catch (ClassNotFoundException cnfe) {
            System.err.println("Received object's class not found.");
        } catch (UnknownHostException uhe) {
            System.err.println("You are trying to connect to an unknown host.");
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } finally {
            // Close the input/output streams and the socket.
            try {
                if (s != null) {
                    s.close();
                }
                if (this.in != null){
                    this.in.close();
                }
                if (this.out != null){
                    this.out.close();
                } 
                if (this.requestSocket != null) {
                    this.requestSocket.close();
                }
                this.print("All connections closed, exiting in 3s.");
                Thread.sleep(3000);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void ReceiveMessage() throws ClassNotFoundException, IOException {
        this.response = (String)this.in.readObject();
        this.print("\n[SERVER]: " + this.response);
    }

    void SendMessage(String msg) throws IOException {
        this.out.writeObject(msg);
        this.out.flush();
    }

    public static void main(String[] args) {
        client c = new client();
        if (args.length == 2) {
            c.URL = args[0];
            c.port = Integer.parseInt(args[1]);
            c.run();
        } else {
            c.print("Invalid client parameters. Usage: client <serverURL> <port_number>");
            return;
        }
    }
}