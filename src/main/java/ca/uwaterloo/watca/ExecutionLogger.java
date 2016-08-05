package ca.uwaterloo.watca;


import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Wojciech Golab
 */
public class ExecutionLogger {
    private String logFileName;
    private BufferedWriter out;
    private long startTime;
    private boolean started;
    private boolean stopped;

    public ExecutionLogger(String logFileName) {
	this.logFileName = logFileName;
	started = false;
    }

    public synchronized void start() {
	try {
	    if (!started) {
		startTime = System.nanoTime();
		out = new BufferedWriter(new FileWriter(logFileName, false));
		started = true;
	    }
	} catch (IOException e) { throw new RuntimeException(e); }
    }

    public synchronized void stop() {
	try {
	    if (!stopped) {
		if (out != null) {
		    out.close();
		    out = null;
		}
		stopped = true;
	    }
	} catch (IOException e) { throw new RuntimeException(e); }
    }

    public synchronized void logReadInvocation(long tid, String key) throws IOException {
	if (!started || stopped) return;
	out.write("" + (System.nanoTime() - startTime));
	out.write("\tINV");
	out.write("\t" + tid);
	out.write("\tR\t");
	out.write(key);
	out.newLine();
    }

    public synchronized void logReadResponse(long tid, String key, String value) throws IOException {
	if (!started || stopped) return;
	out.write("" + (System.nanoTime() - startTime));
	out.write("\tRES");
	out.write("\t" + tid);
	out.write("\tR\t");
	out.write(key + "\t" + value);
	out.newLine();
    }

    public synchronized void logWriteInvocation(long tid, String key, String value) throws IOException {
	if (!started || stopped) return;
	out.write("" + (System.nanoTime() - startTime));
	out.write("\tINV");
	out.write("\t" + tid);
	out.write("\tW\t");
	out.write(key);
	out.write("\t");
	out.write(value);
	out.newLine();
    }

    public synchronized void logWriteResponse(long tid, String key) throws IOException {
	if (!started || stopped) return;
	out.write("" + (System.nanoTime() - startTime));
	out.write("\tRES");
	out.write("\t" + tid);
	out.write("\tW\t");
	out.write(key);
	out.newLine();
    }

    private String sha1(String input) {
	try {
	    MessageDigest mDigest = MessageDigest.getInstance("SHA1");
	    byte[] result = mDigest.digest(input.getBytes());
	    StringBuffer sb = new StringBuffer();
	    for (int i = 0; i < result.length; i++) {
		sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
	    }
	    return sb.toString();
	} catch (NoSuchAlgorithmException e) {
	    throw new RuntimeException(e);
	}
    }
}
