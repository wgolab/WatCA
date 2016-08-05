package ca.uwaterloo.watca;


import java.util.Collection;

/**
 *
 * @author Wojciech Golab
 */
public class Operation implements Comparable<Operation> {

    private String key;
    private String value;
    private long start;
    private long finish;

    enum Type { READ, WRITE };

    private Type type;

    public Operation(String line) {
        String key, val, type;
        long start = 0, finish = 0;

        int i = line.lastIndexOf("\t");
        type = line.substring(i + 1);
        line = line.substring(0, i);
        i = line.lastIndexOf("\t");
        finish = Long.parseLong(line.substring(i + 1)); // + (long) Math.ceil(clockSkew);
        line = line.substring(0, i);
        i = line.lastIndexOf("\t");
        start = Long.parseLong(line.substring(i + 1)); // + (long) Math.floor(clockSkew);
        line = line.substring(0, i);
        i = line.indexOf("\t");
        key = line.substring(0, i);
        val = line.substring(i, line.length());

        // copied from other constructor
        initialize(key, val, start, finish, type);
    }

    public Operation(String key, String value, long start, long finish, String type) {
        initialize(key, value, start, finish, type);
    }

    public void initialize(String key, String value, long start, long finish, String type) {
        this.key = key;
        this.value = value;
        this.start = start;
        this.finish = finish;

        if (type.equals("R") || type.equals("0")) {
            this.type = Type.READ;
        } else {
            this.type = Type.WRITE;
        }

        if (finish < start) {
            throw new RuntimeException("Operation finishes before it starts by " + (start - finish) + ": " + start + "/" + finish);
        }
    }

    public void adjustForClockSkew(double clockSkew) {
        finish += (long) Math.ceil(clockSkew);
        start += (long) Math.floor(clockSkew);
    }

    public double getMidpoint() {
        return (start + finish) / 2.0d;
    }

    public boolean overlaps(Operation op) {
        return !(finish < op.start || op.finish < start);
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public long getStart() {
        return start;
    }

    public long getFinish() {
        return finish;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public void setFinish(long finish) {
        this.finish = finish;
    }

    public boolean isRead() {
        return type == Type.READ;

    }

    public boolean isWrite() {
        return type == Type.WRITE;
    }

    public int compareTo(Operation op) {
        if (start == op.start) {
            if (finish == op.finish) {
                if (type != op.type) {
                    return type == Type.READ ? -1 : +1;
                }
                int cmp = key.compareTo(op.key);
                if (cmp != 0) {
                    return cmp;
                }
                cmp = value.compareTo(op.value);
                return cmp;
            } else {
                return Long.compare(finish, op.finish);
            }
        } else {
            return Long.compare(start, op.start);
        }
    }

    public boolean equals(Object o) {
        Operation op = (Operation) o;
        return compareTo(op) == 0;
    }
}
