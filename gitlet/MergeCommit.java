package gitlet;

import java.util.TreeMap;

public class MergeCommit extends Commit {
    MergeCommit(String message, String firstParent,
                String secondParent, TreeMap<String, Blob> files) {
        super(message, firstParent, files, false);
        _secondParent = secondParent;
    }

    public String getSecondParent() {
        return _secondParent;
    }

    /** SHA1 address hash of second parent commit. */
    private String _secondParent;
}
