package gitlet;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;

public class Commit implements Serializable {

    /**
     * Constructor for a commit object. Assigns variables and
     * timestamp of commit.
     *
     * @param message log associated with the commit.
     * @param parent commit hash associated with the parent commit.
     * @param files Treemap of files that will be tracked by this commit
     * @param initial boolean, identifies if this commit is the initial commit
     */
    Commit(String message, String parent, TreeMap<String, Blob> files,
           boolean initial) {
        _message = message;
        _parent = parent;
        if (files != null) {
            _files = files;
        }
        if (initial) {
            _timestamp = "Wed Dec 31 16:00:00 1969 -0800";
        } else {
            _timestamp = setTimestamp();
        }
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return _message;
    }

    /**
     * @return the parent identifier
     */
    public String getParent() {
        return _parent;
    }

    /**
     * @return the timestamp of this commit
     */
    public String getTimestamp() {
        return _timestamp;
    }

    /**
     * @return map of files this commit tracks
     */
    public TreeMap<String, Blob> getCommittedFiles() {
        return _files;
    }

    /**
     * Creates a timestamp of the date/time this function
     * is called and formats it.
     *
     * @return formatted timestamp
     */
    private String setTimestamp() {
        ZonedDateTime myDateObj = ZonedDateTime.now();
        return DateTimeFormatter.ofPattern("E MMM dd HH:mm:ss"
                + " yyyy xx").format(myDateObj);
    }

    /** Log associated with this commit. */
    private String _message;

    /** Timestamp of current commit. */
    private String _timestamp;

    /** SHA1 address hash of parent commit. */
    private String _parent;

    /** Files in this commit. */
    private TreeMap<String, Blob> _files = new TreeMap<>();
}


