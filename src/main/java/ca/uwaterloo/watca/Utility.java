package ca.uwaterloo.watca;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class Utility {
    static void runCmd(String cmd) {
        try {
            System.out.println(cmd);
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            // read the output from the command
            String s = null;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
            // read any errors from the attempted command
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void runCmd(String cmd, OutputStream os) {
        try {
            os.write("<pre>".getBytes());
            System.out.println(cmd);
	    ProcessBuilder pb = new ProcessBuilder()
		.command(cmd.split(" "))
		.redirectErrorStream(true);
            Process p = pb.start(); //Runtime.getRuntime().exec(cmd);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            //BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            // read the output from the command
            String s = null;
	    while ((s = stdInput.readLine()) != null) {
		String color = "black";
		if (s.toUpperCase().contains("ERROR")) {
		    color = "red";
		}
		s = "<span style=\"color:" + color + "; font-family: monospace\">" + s + "</span>\n";
		os.write(s.getBytes());
		os.flush();
	    }
	    /*
            while (true) {
                boolean finish = true;
                while (stdInput.ready() && (s = stdInput.readLine()) != null) {
                    s += "\n";
                    os.write(s.getBytes());
                    os.flush();
                }
                while (stdError.ready() && (s = stdError.readLine()) != null) {
                    s = "<span style=\"color:red\">" + s + "</span>\n";
                    os.write(s.getBytes());
                    os.flush();
                }
                if ((s = stdInput.readLine()) != null) {
                    finish = false;
                    s += "\n";
                    os.write(s.getBytes());
                    os.flush();
                }
                if(finish)
                    break;
            }
	    */
            os.write("</pre>".getBytes());
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
