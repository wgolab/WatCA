package ca.uwaterloo.watca;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author Shankha Subhra Chatterjee
 */
 enum EventType {
    INVOKE,
    RESPONSE;
    
    public static EventType getType(String s) throws IllegalArgumentException {
        if (s.equals("INV"))
            return EventType.INVOKE;
        else if (s.equals("RES"))
            return EventType.RESPONSE;
        else
            throw new IllegalArgumentException();
    }    
 };
 enum OperationType {
    READ,
    WRITE;
    
    public static OperationType getType(String s) throws IllegalArgumentException {
        if (s.equals("R"))
            return OperationType.READ;
        else if (s.equals("W"))
            return OperationType.WRITE;
        else
            throw new IllegalArgumentException();
    }
};
public class LogParser {
    
    class OperationLogLine {       
        String time;
        EventType eType;
        String processID;
        OperationType oType;
        String key;
        String value;
        boolean isValid;        
        
        public OperationLogLine(String line) {            
            time = "";
            processID = "";
            key = "";
            value = "";
            isValid = true;
            String[] words = line.split("\\s+");
            int count = 0;
            if(words.length >= 5)
            {
                this.time = words[count++];
                this.eType = EventType.getType(words[count++]);
                this.processID = words[count++];
                this.oType = OperationType.getType(words[count++]);               
		this.key = words[count++];
                
                if ((this.oType == OperationType.READ && this.eType == EventType.RESPONSE) || 
                        (this.oType == OperationType.WRITE && this.eType == EventType.INVOKE)) {
                    if (words.length >= 6)
                        this.value = words[count++];
                    else 
                        this.isValid = false;
                }                              
            }
            else 
                isValid = false;
        }     
        
        public boolean isCandidateInvokingLine (OperationLogLine line) {
            return (processID.equals(line.processID) && oType == line.oType
                   && key.equals(line.key));
        }
    }

    List<OperationLogLine> bufferedLines;
    
    public LogParser() {
        bufferedLines = new ArrayList();
    }
    
    public List<Operation> parseFiles (File[] files) throws IOException {
        List<Operation> operations = new ArrayList();
        for (File f : files ){
            operations.addAll(parseFile(f));
        }
        return operations;
    }    
    
    // parameter 'filename' corresponds to the fully qualified filename, including path
    public List<Operation> parseFile(File f) throws IOException {        
        List<Operation> operations = new ArrayList();
        
        try {           
            BufferedReader br = new BufferedReader(new FileReader(f));            
            String line;
            while ((line = br.readLine()) != null) {
                OperationLogLine logLine = new OperationLogLine(line);                
                if (!logLine.isValid)
                    continue;
                
                if (logLine.eType == EventType.INVOKE) {
                    bufferedLines.add(logLine);
                }else if (logLine.eType == EventType.RESPONSE) {
                    Optional<OperationLogLine> invokingLine
			= bufferedLines.stream().filter(o -> o.isCandidateInvokingLine(logLine)).reduce((first, second) -> second); // must process entries in reverse order
                    
                    if (invokingLine.isPresent()) {
                       if (logLine.oType == OperationType.READ) {
                           if (logLine.value == null)
                           {
			       logLine.value = "";
                           }
                           operations.add(new Operation(invokingLine.get().key, logLine.value, Long.parseLong(invokingLine.get().time), Long.parseLong(logLine.time), "R"));                            
                       } else if (logLine.oType == OperationType.WRITE) {
                           if (invokingLine.get().value == null)
                           {
			       // nothing to do
                           }
                           operations.add(new Operation(invokingLine.get().key, invokingLine.get().value, Long.parseLong(invokingLine.get().time), Long.parseLong(logLine.time), "W"));
                       }
                       bufferedLines.remove(invokingLine.get());
                    }
                }
            }
            
            // Add write operations for whom response has not been found
            for (OperationLogLine l : bufferedLines) {
                if (l.oType == OperationType.WRITE && l.eType == EventType.INVOKE) {
                    operations.add(new Operation(l.key, l.value, Long.parseLong(l.time), Long.MAX_VALUE, "W"));
                }                 
            }
            return operations;
        }
        catch (IOException e)
        {
            throw e;
        }
    }  
}

