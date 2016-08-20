package ca.uwaterloo.watca;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * Created by Hua
 */
class WebServer {

    private int port;
    private RealtimeAnalyzer ana;
    private Properties prop;

    // deprecated, these variable is related with py based code base, will be removed
    private int portYCSB;
    private String hostYCSB;
    private boolean javaControl;

    WebServer(int port, RealtimeAnalyzer ana) {
        this.port = port;
        this.ana = ana;
        this.prop = new Properties();

        InputStream input = null;
        try {
            input = new FileInputStream("../scripts/config.properties");
            prop.load(input);
            // set up initial value for file "config.update"
            saveConfUpdate();
            String cmd = "bash ../scripts/control.sh " + "storage_type";
            Utility.runCmd(cmd);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // deprecated, when the py based code is removed, this function also should be removed
    void setOldVars(int portYCSB, String hostYCSB, boolean javaControl) {
        this.portYCSB = portYCSB;
        this.hostYCSB = hostYCSB;
        this.javaControl = javaControl;
    }

    void saveConfUpdate() {
        File configUpdateFile = new File("../scripts/gen_file/config.update");
        FileWriter writer = null;
        try {
            writer = new FileWriter(configUpdateFile);
            prop.store(writer, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        if (javaControl) {
            server.createContext("/controls", new HandlerControl());
        } else {
            server.createContext("/controls", new HandlerYCSB());
        }

        server.createContext("/staleProp", new HandlerStaleProp());
        server.createContext("/staleQuart", new HandlerStaleQuart());
        server.createContext("/throughput", new HandlerThroughput());
        server.createContext("/latency", new HandlerLatency());
        server.createContext("/latencyStaleProp", new HandlerLatencyStaleProp());
	server.createContext("/consistencySpectrum", new HandlerConsistencySpectrum());
        server.createContext("/setScoreType", new HandlerSetScoreType());
        server.createContext("/snapshot", new HandlerSnapshot());
        server.createContext("/clearData", new HandlerClearData());
        server.createContext("/", new HandlerDefault());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    class HandlerControl implements HttpHandler {

        public HandlerControl() {}

        public void handle(HttpExchange t) throws IOException {
            URI requestedUri = t.getRequestURI();
            String ret = "";
            byte[] response = ret.getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            String query = requestedUri.getRawQuery();
            ret =  "<html> <head>" +
                    "<style>" +
                    "* { font-family: sans-serif }" +
                    "pre { font-family: monospace }" +
                    "</style>" +
                    "<title> WatCA Experiment Controller </title> " +
                    "</head>";
            os.write(ret.getBytes());
            if (query == null || !query.contains("action")) {
                ret =  "<html> <head><title> WatCA Experiment Controller </title> </head> <body>" +
                        "<h1>WatCA Main Menu</h1>" +
                        "<div style=\"float: left;\"><form method=\"get\"><input type=\"hidden\" name=\"action\" value=\"startDB\">" +
                        "<button type=\"submit\">Start NoSQL</button></form></div>&nbsp;" +
                        "<div style=\"float: left;\"><form method=\"get\"><input type=\"hidden\" name=\"action\" value=\"stopDB\">" +
                        "<button type=\"submit\">Stop NoSQL</button></form></div><br><br><br>" +
                        "<div style=\"float: left;\"><form method=\"get\"><input type=\"hidden\" name=\"action\" value=\"loadYCSB\">" +
                        "<button type=\"submit\">YCSB Load Phase</button></form></div>&nbsp;" +
                        "<div style=\"float: left;\"><form method=\"get\"><input type=\"hidden\" name=\"action\" value=\"workYCSB\">" +
                        "<button type=\"submit\">YCSB Work Phase</button></form></div><br><br><br>";
                ret += dumpVars();
                ret += "</body>";
                os.write(ret.getBytes());
            } else if (query.contains("action=updateVars")) {
                ret = "<html><body>" + "<h1>Update Settings</h1>";
                updateVars(query);
                ret += "<form method=\"get\" id=\"emptyForm\"></form>" +
                        "<script type=\"text/javascript\">" +
                        "setTimeout(func, 1000);\n" +
                        "function func() { document.getElementById(\"emptyForm\").submit(); }" +
                        "</script>";
                ret += "<pre>Done.</pre> </body></html>";
                os.write(ret.getBytes());
            } else if (query.contains("action=startDB")
                    || query.contains("action=stopDB")
                    || query.contains("action=workYCSB")
                    || query.contains("action=loadYCSB")) {
                doAction(query, os);
            } else {
                System.err.println("Control query has no valid action: " + query);
            }
            ret = "<form method=\"get\"><button type=\"submit\">Main Menu</button></form>";
            ret += "</body></html>";
            os.write(ret.getBytes());
            os.close();
        }

        private void doAction(String query, OutputStream os) {
            String db = prop.getProperty("storage_type");
            if (!db.equals("Cassandra2_0") &&
                    //!db.equals("Riak") &&
                    !db.equals("Cassandra2_2")) {
                System.err.println(db + " is not supported.");
                return;
            }
            String ret = "";
            String cmd = "";
            if (query.contains("action=startDB")) {
                ret = "<h1>Start NoSQL DB</h1>" + "<pre>Stopping service ...</pre>";
                ret += "<pre>Starting service and waiting for confirmation\n" +
                        "(this may take more than one minute) ...</pre>";
                cmd = "bash ../scripts/control.sh " + "start_db" ;
            } else if (query.contains("action=stopDB")) {
                ret = "<h1>Stop NoSQL DB</h1>" + "<pre>Stoppping service ...</pre>";
                cmd = "bash ../scripts/control.sh " + "kill_db" ;
            } else if (query.contains("action=workYCSB")) {
                ret = "<h1>YCSB Work</h1>" + "<pre>Running work phase ...</pre>";
                cmd = "bash ../scripts/control.sh " + "work_ycsb";
            } else if (query.contains("action=loadYCSB")) {
                ret = "<h1>YCSB Load</h1>" + "<pre>Running load phase ...</pre>";
                cmd = "bash ../scripts/control.sh " + "load_ycsb";
            } else {
                System.err.println("query has no valid action");
                return;
            }
            try {
                os.write(ret.getBytes());
                os.flush();
                Utility.runCmd(cmd, os);
                ret = "<pre>Done.</pre>";
                os.write(ret.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String dumpVars() {
            String ret = "<hr><form method=\"get\">";
            ret += "<input type=\"hidden\" name=\"action\" value=\"updateVars\">" +
                    "<h3>YCSB Workload Settings</h3>" +
                    "<p>Target throughput (ops/s): <input type=\"text\" size=\"6\" name=\"target_thr_per_host\" value=\"" +
                    prop.getProperty("target_thr_per_host") + "\"></p>" +
                    "<p>Work phase duration (s): <input type=\"text\" size=\"6\" name=\"num_seconds_to_run\" value=\""
                    + prop.getProperty("num_seconds_to_run") + "\"></p>" +
                    "<p>Keyspace size: <input type=\"text\" size=\"6\" name=\"keyspace\" value=\"" +
                    prop.getProperty("keyspace") + "\"></p>" +
                    "<p>Key distribution: <select name=\"dist\">";
            if (prop.getProperty("dist").equals("uniform"))
                ret += "<option value=\"uniform\" selected>uniform</option>";
            else
                ret += "<option value=\"uniform\">uniform</option>";
            if (prop.getProperty("dist").equals("latest"))
                ret += "<option value=\"latest\" selected>latest</option>";
            else
                ret += "<option value=\"latest\">latest</option>";
            if (prop.getProperty("dist").equals("zipfian"))
                ret += "<option value=\"zipfian\" selected>zipfian</option>";
            else
                ret += "<option value=\"zipfian\">zipfian</option>";
            if (prop.getProperty("dist").equals("hotspot"))
                ret += "<option value=\"hotspot\" selected>zipfian</option>";
            else
                ret += "<option value=\"hotspot\">hotspot</option>";
            ret += "</select> </p>";

            ret += "<p>Read-write proportion: <input type=\"text\" size=\"6\" name=\"read_prop\" value=\""
                    + prop.getProperty("read_prop") + "\"></p>" +
                    "<p>Hotspot hotset fraction: <input type=\"text\" size=\"6\" name=\"hotspotdatafraction\" value=\""
                    + prop.getProperty("hotspotdatafraction") + "\"></p>" +
                    "<h3>YCSB Client Settings</h3>" +
                    "<p>Num threads work phase: <input type=\"text\" size=\"6\" name=\"YCSB_threads\" value=\""
                    + prop.getProperty("YCSB_threads") + "\"></p>";

            ret += "<p>Num threads load phase: <input type=\"text\" size=\"6\" name=\"YCSB_threads_for_load\" value=\""
                    + prop.getProperty("YCSB_threads_for_load") + "\"></p>";
            ret += "<p>Read consistency: <select name=\"read_consistency\">" ;
            if (prop.getProperty("read_consistency").equals("ONE"))
                ret += "<option value=\"ONE\" selected>ONE</option>";
            else
                ret += "<option value=\"ONE\">ONE</option>";
            if (prop.getProperty("read_consistency").equals("QUORUM"))
                ret += "<option value=\"QUORUM\" selected>QUORUM</option>";
            else
                ret += "<option value=\"QUORUM\">QUORUM</option>";
            if (prop.getProperty("read_consistency").equals("ALL"))
                ret += "<option value=\"ALL\" selected>ALL</option>";
            else
                ret += "<option value=\"ALL\">ALL</option>";
            ret += "</select> </p>" + "<p>Write consistency: <select name=\"write_consistency\">";

            if (prop.getProperty("write_consistency").equals("ONE"))
                ret += "<option value=\"ONE\" selected>ONE</option>";
            else
                ret += "<option value=\"ONE\">ONE</option>";
            if (prop.getProperty("write_consistency").equals("QUORUM"))
                ret += "<option value=\"QUORUM\" selected>QUORUM</option>";
            else
                ret += "<option value=\"QUORUM\">QUORUM</option>";
            if (prop.getProperty("write_consistency").equals("ALL"))
                ret += "<option value=\"ALL\" selected>ALL</option>";
            else
                ret += "<option value=\"ALL\">ALL</option>";
            ret += "</select> </p>";

            ret += "<p>Read delay (ms): <input type=\"text\" size=\"6\" name=\"read_delay\" value=\""
                    + prop.getProperty("read_delay") + "\"></p>";

            ret += "<p>Write delay (ms): <input type=\"text\" size=\"6\" name=\"write_delay\" value=\""
                    + prop.getProperty("write_delay") + "\"></p>";
            ret += "<p>CPQ parameter: <input type=\"text\" size=\"6\" name=\"con_prob\" value=\""
                    + prop.getProperty("con_prob") + "\"></p>";

            ret += "<h3>NoSQL Settings</h3>";
            ret += "<p>Storage Type: <select name=\"storage_type\">";
            if (prop.getProperty("storage_type").equals("Cassandra2_0"))
                ret += "<option value=\"Cassandra2_0\" selected>Cassandra2.0-CassandraClient10</option>";
            else
                ret += "<option value=\"Cassandra2_0\">Cassandra2.0-CassandraClient10</option>";
            if (prop.getProperty("storage_type").equals("Cassandra2_2"))
                ret += "<option value=\"Cassandra2_2\" selected>Cassandra2.2-CassandraCQLClient</option>";
            else
                ret += "<option value=\"Cassandra2_2\">Cassandra2.2-CassandraCQLClient</option>";
            // TODO add Riak
            //if (prop.getProperty("storage_type").equals("Riak"))
            //    ret += "<option value=\"Riak\" selected>Riak[in dev]</option>";
            //else
            //    ret += "<option value=\"Riak\">Riak[in dev]</option>";
            ret += "</select> </p>";
            ret += "<p>Replication factor: <input type=\"text\" size=\"6\" name=\"replication_factor\" value=\""
                    + prop.getProperty("replication_factor") + "\"></p>";

            ret += "<h3>Linux Kernel Settings</h3>";

            ret += "<p>Simulated network delay (ms): <input type=\"text\" size=\"6\" name=\"kernel_net_delay\" value=\""
                    + prop.getProperty("kernel_net_delay") + "\"></p>";
            ret += "<button type=\"submit\">Update</button></form>";
            ret += "<hr>";
            return ret;
        }

        private String updateVars(String query) {
            String ret = "";
            if (query == null)
                return ret;
            String param[] = query.split("[&]");
            String netDelay = prop.getProperty("kernel_net_delay");
            String current_db = prop.getProperty("storage_type");
            for (String s : param) {
                String pair[] = s.split("[=]");
                prop.setProperty(pair[0], pair[1]);
            }
            saveConfUpdate();
            if (!prop.getProperty("kernel_net_delay").equals(netDelay)) {
                ret += "<pre>Configuring simulated network delay ...</pre>";
                updateNetworkDelay();
            }
            if (!prop.getProperty("storage_type").equals(current_db)) {
                ret += "<pre>Update storage type setting ...</pre>";
                updateStorageType();
            }
            return ret;
        }

        private void updateNetworkDelay() {
            String cmd = "bash ../scripts/control.sh " + "kernel_net_delay";
            Utility.runCmd(cmd);
        }

        private void updateStorageType() {
            String cmd = "bash ../scripts/control.sh " + "storage_type";
            Utility.runCmd(cmd);
        }

    }

    class HandlerYCSB implements HttpHandler {

        public HandlerYCSB() {
        }

        public void handle(HttpExchange t) throws IOException {
            URI requestedUri = t.getRequestURI();
            String query = requestedUri.getRawQuery();
            URL ycsb = new URL("http://" + hostYCSB + ":" + portYCSB + "?" + query);
            try {
                InputStream in = ycsb.openStream();
                Headers h = t.getResponseHeaders();
                h.add("Content-Type", "text/html");
                t.sendResponseHeaders(200, 0);
                OutputStream os = t.getResponseBody();
                int i;
                while ((i = in.read()) != -1) {
                    os.write(i);
                    os.flush();
                }
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class HandlerDefault implements HttpHandler {

        String root;
        public HandlerDefault() {
            root = ".." + File.separator + "www";
        }

        public void handle(HttpExchange t) throws IOException {
            File path = new File(root, t.getRequestURI().getPath());
            if (path.equals(new File(root, File.separator))) {
                path = new File(root + File.separator + "index.html");
            }
            System.out.println("Received request for B " + path);
            if (!sanitize(new File(root), path) || !path.exists()) {
                byte[] data = "<h1>404 (Not Found)</h1>\n".getBytes();
                t.sendResponseHeaders(404, data.length);
                OutputStream os = t.getResponseBody();
                os.write(data);
                t.close();
            } else {
                RandomAccessFile f = new RandomAccessFile(path, "r");
                byte[] data = new byte[(int) f.length()];
                t.sendResponseHeaders(200, data.length);
                f.read(data);
                f.close();
                OutputStream os = t.getResponseBody();
                os.write(data);
                t.close();
            }
        }

        boolean sanitize(File parent, File child) {
            if (child.toString().matches("^.*[^a-zA-Z0-9._/\\\\-].*$")) {
                return false;
            } else {
                return child.getAbsolutePath().startsWith(parent.getAbsolutePath());
            }
        }
    }

    class HandlerStaleProp implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            byte[] response = ana.getOutputStaleProp().getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class HandlerStaleQuart implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            byte[] response = ana.getOutputStaleQuart().getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class HandlerThroughput implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            byte[] response = ana.getOutputThroughput().getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class HandlerLatency implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            byte[] response = ana.getOutputLatency().getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class HandlerLatencyStaleProp implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            byte[] response = ana.getOutputLatencyStaleProp().getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class HandlerConsistencySpectrum implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            byte[] response = ana.getOutputSpectrum().getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    class HandlerSetScoreType implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            try {
                URI requestedUri = t.getRequestURI();
                String query = requestedUri.getRawQuery();
                // TODO current there is one pairs
                String type = "regular";
                String ret = "<html><head><style>* { font-family: sans-serif }</style></head> <body >";
                ret += "<div style=\"float: left;\"><b>Consistency metric selection:&nbsp;&nbsp;</b></div>";
                if (query != null) {
                    String param[] = query.split("[=]");
                    type = param[1];
                }
                try {
                        ana.setScoreType(type);
                } catch (IllegalArgumentException e) {
                }
                ret = ret + "<div style=\"float: left;\"><form name=\"scoreTypeForm\" action=\"setScoreType\" method=\"get\"> "
                        + "<select name=\"scoreType\" onchange=\"this.form.submit()\">"
                        + "<option value=\"regular\">Metric: Regular"
                        + "<option value=\"gamma\">Metric: Gamma"
                        + "<option value=\"gk\">Metric: GK"
                        + "</select></form></div>";
                String regex = "value=\"" + type + "\"";
                String replacement = regex + " selected";
                ret = ret.replaceAll(regex, replacement);

                ret += "</body></html>";
                byte[] response = ret.getBytes();
                t.sendResponseHeaders(200, response.length);
                OutputStream os = t.getResponseBody();
                os.write(response);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class HandlerSnapshot implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            try {
                URI requestedUri = t.getRequestURI();
                String query = requestedUri.getRawQuery();

		if (query != null)
		    ana.snapshot();

                String ret = "<html><head><style>* { font-family: sans-serif }</style></head> <body >";
                ret = ret + "<div style=\"float: left;\"><form name=\"snapshotForm\" action=\"snapshot\" method=\"get\"> "
     		    + "<input type=\"submit\" value=\"Take snapshot of data\">"
		    + "</form></div>"
		    + "</body></html>";
                byte[] response = ret.getBytes();
                t.sendResponseHeaders(200, response.length);
                OutputStream os = t.getResponseBody();
                os.write(response);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
	}
    }

    class HandlerClearData implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            try {
                URI requestedUri = t.getRequestURI();
                String query = requestedUri.getRawQuery();

		if (query != null)
		    ana.clearData();

                String ret = "<html><head><style>* { font-family: sans-serif }</style></head> <body >";
                ret = ret + "<div style=\"float: left;\"><form name=\"clearDataForm\" action=\"clearData\" method=\"get\"> "
     		    + "<input type=\"submit\" value=\"Clear data\">"
		    + "</form></div>"
		    + "</body></html>";
                byte[] response = ret.getBytes();
                t.sendResponseHeaders(200, response.length);
                OutputStream os = t.getResponseBody();
                os.write(response);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
	}
    }
}
