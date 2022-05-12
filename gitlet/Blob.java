package gitlet;

import java.io.Serializable;

public class Blob implements Serializable {
    /**
     * Blob constructor.
     *
     * @param name name of file.
     * @param content contents of file.
     */
    Blob(String name, String content) {
        _fileName = name;
        _fileContents = content;
    }

    /**
     * @return name of this file
     */
    public String getName() {
        return _fileName;
    }

    /**
     * @return contents of this file
     */
    public String getFileContents() {
        return _fileContents;
    }

    /** Name of file. */
    private String _fileName;

    /** Contents in the file. */
    private String _fileContents;
}
