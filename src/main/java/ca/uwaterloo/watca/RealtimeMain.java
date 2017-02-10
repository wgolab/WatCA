package ca.uwaterloo.watca;

import java.io.*;
import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;

import java.net.URL;

public class RealtimeMain {
    private RealtimeAnalyzer ana;
    int portYCSB;
    String hostYCSB;
    static boolean javaControl;

    public static void main(String[] args) throws Exception {
	javaControl = (args.length >= 5 && args[4].equals("javaControl")) ||
	    (args.length >= 6 && args[5].equals("javaControl"));
	boolean saveLogs = (args.length >= 5 && args[4].equals("saveLogs")) ||
	    (args.length >= 6 && args[5].equals("saveLogs"));
        RealtimeMain m = new RealtimeMain(saveLogs);
        m.listen(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));
    }

    public RealtimeMain(boolean saveLogs) {
        ana = new RealtimeAnalyzer(saveLogs);
    }

    public void listen(int port1, int port2, String host3, int port3) throws IOException {
        portYCSB = port3;
        hostYCSB = host3;
        WebServer ws = new WebServer(port2, ana);
        ws.setOldVars(portYCSB, hostYCSB, javaControl);
        ws.start();

        ServerSocket listener = new ServerSocket(port1);
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e);
        } finally {
            listener.close();
        }
    }

    private class Handler extends Thread {

        private Socket socket;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            System.out.println("Accepted connection: " + socket);
            try {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                while (true) {
                    String line = in.readLine();
                    if (line == null) {
                        break;
                    }
                    ana.processOperation(new Operation(line));
                }
            } catch (IOException e) {
                System.err.println("Error: " + e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error: " + e);
                }
            }
            System.out.println("Done handling connection: " + socket);
        }
    }


}
