package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;
import java.util.LinkedList;


public class Gitlet {

    /**
     * Creates a new Gitlet version-control system in the current directory.
     * This system will automatically start with one commit. It also starts with
     * a default branch 'master' which is by default the HEAD branch.
     *
     * @throws IOException
     */
    public void init() throws IOException {
        setupPersistance();
        Commit initialCommit = new Commit("initial commit", "", null, true);
        String initialCommitHash = Utils.sha1(Utils.serialize(initialCommit));
        File initialCommitsFile = Utils.join(COMMITS, initialCommitHash);
        initialCommitsFile.createNewFile();
        Utils.writeObject(initialCommitsFile, initialCommit);
        Utils.writeContents(head, MASTER.getPath());
        Utils.writeContents(MASTER, initialCommitHash);
    }


    /**
     * Add FILENAME to add staging area. Remove it from remove staging area if
     * it exists there. Do not add if the file is the same in the current
     * commit.
     *
     * @param fileName name of the file to be staged.
     */
    public void add(String fileName) {
        File addFile = Utils.join(CWD, fileName);

        if (!addFile.exists()) {
            Main.exitWithError("File does not exist.");
        }

        readStaging();

        Blob addBlob = new Blob(fileName, Utils.readContentsAsString(addFile));
        String blobHash = Utils.sha1(Utils.serialize(addBlob));

        Set<Map.Entry<String, Blob>> rmStagingEntries = rmStagingMap.entrySet();
        if (!rmStagingMap.isEmpty()) {
            for (Map.Entry<String, Blob> entry : rmStagingEntries) {
                if (fileName.equals(entry.getValue().getName())) {
                    rmStagingMap.remove(entry.getKey());
                }
            }
        }

        Set<Map.Entry<String, Blob>> stagingEntries = addStagingMap.entrySet();
        if (!addStagingMap.isEmpty()) {
            for (Map.Entry<String, Blob> entry : stagingEntries) {
                if (fileName.equals(entry.getValue().getName())) {
                    addStagingMap.remove(entry.getKey());
                    addStagingMap.put(blobHash, addBlob);
                }
            }
        }

        TreeMap<String, Blob> lastCommitBlobs =
                getLastCommit().getCommittedFiles();
        if (lastCommitBlobs.isEmpty()
                || !lastCommitBlobs.containsKey(blobHash)) {
            addStagingMap.put(blobHash, addBlob);
        } else {
            if (addStagingMap.containsKey(blobHash)) {
                addStagingMap.remove(blobHash);
            }
        }

        writeStaging();
    }


    /**
     * Add FILENAME to remove staging area. Remove it from add staging area if
     * it exists there. Do not add if the file is the same in the current
     * commit.Do not stage file for removal if it is neither staged for addition
     * and not tracked in the current commit.
     *
     * @param fileName name of file to be staged for removal.
     */
    public void rm(String fileName) {
        File rmFile = Utils.join(CWD, fileName);

        readStaging();

        Blob rmBlob = null;
        String blobHash = "";
        if (rmFile.exists()) {
            rmBlob = new Blob(fileName, Utils.readContentsAsString(rmFile));
            blobHash = Utils.sha1(Utils.serialize(rmBlob));
        }

        TreeMap<String, Blob> filesInHeadCommit =
                getLastCommit().getCommittedFiles();
        Set<Map.Entry<String, Blob>> trackedFiles =
                filesInHeadCommit.entrySet();
        Set<Map.Entry<String, Blob>> stagingEntries = addStagingMap.entrySet();

        String removeKey = "";
        boolean untrackedStaging = true;
        boolean untrackedCommit = true;
        for (Map.Entry<String, Blob> entry : stagingEntries) {
            if (entry.getValue().getName().equals(fileName)) {
                removeKey = entry.getKey();
                untrackedStaging = false;
                if (rmBlob == null) {
                    rmBlob = entry.getValue();
                }
            }
        }

        addStagingMap.remove(removeKey);

        if (!filesInHeadCommit.isEmpty()) {
            for (Map.Entry<String, Blob> entry : trackedFiles) {
                if (entry.getValue().getName().equals(fileName)) {
                    untrackedCommit = false;
                    if (rmBlob == null) {
                        rmBlob = entry.getValue();
                    }
                }
            }
        }

        if (untrackedStaging && untrackedCommit) {
            Main.exitWithError("No reason to remove the file.");
        }

        if (!untrackedCommit) {
            rmStagingMap.put(blobHash, rmBlob);
            if (rmFile.exists()) {
                rmFile.delete();
            }
        }

        writeStaging();
    }


    /**
     * By default, a commit is the same as its parent. Files staged
     * for addition and removal are the updates to the commit. The
     * staging area is cleared
     *
     * @param message to be logged with the commit
     * @throws IOException
     */
    public void commit(String message) throws IOException {
        TreeMap<String, Blob> lastCommitBlobs =
                getLastCommit().getCommittedFiles();

        readStaging();

        if (addStagingMap.isEmpty() && rmStagingMap.isEmpty()) {
            Main.exitWithError("No changes added to the commit.");
        }

        Set<Map.Entry<String, Blob>> commitEntries =
                lastCommitBlobs.entrySet();
        Set<Map.Entry<String, Blob>> stagingEntries = addStagingMap.entrySet();
        Set<Map.Entry<String, Blob>> rmEntries = rmStagingMap.entrySet();

        ArrayList<String> toRemoveHash = new ArrayList<>();
        for (Map.Entry<String, Blob> staged : stagingEntries) {
            String stageName = staged.getValue().getName();
            for (Map.Entry<String, Blob> committed : commitEntries) {
                String commitName = committed.getValue().getName();
                if (stageName.equals(commitName)) {
                    toRemoveHash.add(committed.getKey());
                }
            }
        }

        for (String removeFile : toRemoveHash) {
            lastCommitBlobs.remove(removeFile);
        }

        for (Map.Entry<String, Blob> staged : stagingEntries) {
            lastCommitBlobs.put(staged.getKey(), staged.getValue());
        }

        for (Map.Entry<String, Blob> toRemove : rmEntries) {
            lastCommitBlobs.remove(toRemove.getKey());
        }

        persistCommit(new Commit(message, getLastCommitHash(),
                lastCommitBlobs, false));

        clearStaging();
        writeStaging();
    }


    /**
     * Starting at the current head commit, display the commit history
     * and information about each commit until the initial commit,
     * following the first parent commit links, ignoring any second
     * parents found in merge commits.
     *
     * Format of log:
     *      ===
     *      commit 0c69f7be8497337c9736aa80b47828bb5306c007
     *      Date: Wed Dec 31 16:00:00 1969 -0800
     *      initial commit
     */
    public void log() {
        Commit commit = getLastCommit();
        String hash = getLastCommitHash();
        while (commit.getParent() != "") {
            printLog(hash);
            hash = commit.getParent();
            commit = getCommit(hash);
        }
        printLog(hash);
    }


    /**
     * Like log, except displays information about all commits ever made.
     * Order of commits is arbitrary.
     */
    public void globalLog() {
        List<String> allCommits = Utils.plainFilenamesIn(COMMITS);
        for (String commit : allCommits) {
            printLog(commit);
        }
    }


    /**
     * Output the current status of gitlet including current branches,
     * files staged for addition, files staged for removal, modifications
     * not staged for commit and untracked files. HEAD branch it led by
     * a '*'. Outputs in lexical order.
     *
     * Sample output:
     *      === Branches ===
     *      *master
     *      other-branch
     *
     *      === Staged Files ===
     *      wug.txt
     *      wug2.txt
     *
     *      === Removed Files ===
     *      goodbye.txt
     *
     *      === Modifications Not Staged For Commit ===
     *      junk.txt (deleted)
     *      wug3.txt (modified)
     *
     *      === Untracked Files ===
     *      random.stuff
     */
    public void status() {
        System.out.println("=== Branches ===");
        printBranches();

        System.out.println("=== Staged Files ===");
        printStagedForAddition();

        System.out.println("=== Removed Files ===");
        printStagedForRemoval();

        System.out.println("=== Modifications Not Staged For Commit ===");
        printModifiedFiles();

        System.out.println("=== Untracked Files ===");
        printUntrackedFiles();
    }


    /**
     * Prints out the ids of all commits that have the given MESSAGE.
     *
     * @param message a message associated with a commit.
     */
    public void find(String message) {
        String[] allCommits = COMMITS.list();
        boolean messageExists = false;

        for (String commit : allCommits) {
            File commitFile = Utils.join(COMMITS, commit);
            Commit commitObj = Utils.readObject(commitFile, Commit.class);
            if (commitObj.getMessage().equals(message)) {
                System.out.println(commit);
                messageExists = true;
            }
        }

        if (!messageExists) {
            Main.exitWithError("Found no commit with that message.");
        }
    }


    /**
     * There are 3 possible use cases for gitlet.checkout():
     *      1. java gitlet.Main checkout -- [file name]
     *          Takes the version of the file as it exists
     *          in the head commit, the front of the current
     *          branch, and puts it in the working directory,
     *          overwriting the version of the file that's already
     *          there if there is one. The new version of the file
     *          is not staged.
     *
     *      2. java gitlet.Main checkout [commit id] -- [file name]
     *          Takes the version of the file as it exists in the commit
     *          with the given id, and puts it in the working directory,
     *          overwriting the version of the file that's already there
     *          if there is one. The new version of the file is not staged.
     *
     *      3. java gitlet.Main checkout [branch name]
     *          Takes all files in the commit at the head of the given branch,
     *          and puts them in the working directory, overwriting the versions
     *          of the files that are already there if they exist. Also, at the
     *          end of this command, the given branch will now be considered the
     *          current branch (HEAD). Any files that are tracked in the current
     *          branch but aren't present in the checked-out branch are deleted.
     *          The staging area is cleared, unless the checked-out branch is
     *          the current branch
     *
     * @param branch a branch with an associated commit.
     * @param commitID a commit ID.
     * @param fileName the name of a file.
     * @throws IOException
     */
    public void checkout(String branch, String commitID, String fileName)
            throws IOException {
        if (fileName != "" && commitID == "") {
            checkoutOneTwo(fileName,
                    getLastCommit().getCommittedFiles());
        } else if (fileName != "" && commitID != "") {
            checkoutOneTwo(fileName,
                    getAbbrevCommit(commitID).getCommittedFiles());
        } else {
            checkoutThree(branch);
        }
    }


    /**
     * Creates a new branch with the given name, and points it
     * at the current head node.
     *
     * @param branchName name of the new branch
     * @throws IOException
     */
    public void branch(String branchName) throws IOException {
        File newBranch = Utils.join(BRANCHES, branchName);
        File headBranch = new File(getHeadBranch());

        if (newBranch.exists()) {
            Main.exitWithError("A branch with that name already exists.");
        } else {
            newBranch.createNewFile();
            Utils.writeContents(newBranch,
                    Utils.readContentsAsString(headBranch));
        }
    }


    /**
     *  Removes branch with the given name if it exists and does not
     *  point to the current commit.
     *
     * @param branchName name of a branch
     * @throws IOException
     */
    public void rmBranch(String branchName) {
        File branch = Utils.join(BRANCHES, branchName);
        String headCommit = getHeadBranch();

        if (!branch.exists()) {
            Main.exitWithError("A branch with that name does not exist.");
        } else if (headCommit.equals(branch.getAbsolutePath())) {
            Main.exitWithError("Cannot remove the current branch.");
        } else {
            branch.delete();
        }
    }


    /**
     *  Checks out all the files tracked by the given commit. Removes tracked
     *  files that are not present in that commit. Also moves the current
     *  branch's head to that commit node. The staging area is cleared.
     *  Changes the current branch head.
     *
     * @param commitID identifies commit to reset to.
     * @throws IOException
     */
    public void reset(String commitID) throws IOException {
        File commitFile = Utils.join(COMMITS, commitID);

        if (!commitFile.exists()) {
            Main.exitWithError("No commit with that id exists.");
        }

        TreeMap<String, Blob> filesAtCommit =
                getAbbrevCommit(commitID).getCommittedFiles();
        Set<Map.Entry<String, Blob>> commitFileEntries =
                filesAtCommit.entrySet();
        ArrayList<String> untrackedFiles = getUntrackedFiles();

        for (Map.Entry<String, Blob> cFile : commitFileEntries) {
            for (String untrackedFile : untrackedFiles) {
                if (untrackedFile.equals(cFile.getValue().getName())) {
                    Main.exitWithError("There is an untracked file in the way;"
                            + " delete it, "
                            + "or add and commit it first.");
                }
            }
        }

        ArrayList<String> trackedFiles = getTrackedFiles();
        for (String name : trackedFiles) {
            File aWorkingFile = Utils.join(CWD, name);
            aWorkingFile.delete();
        }

        for (Map.Entry<String, Blob> file : commitFileEntries) {
            File aFile = Utils.join(CWD, file.getValue().getName());
            aFile.createNewFile();
            Utils.writeContents(aFile, file.getValue().getFileContents());
        }

        clearStaging();
        writeStaging();

        File currentBranch = new File(getHeadBranch());
        Utils.writeContents(currentBranch, commitID);
    }


    /**
     * Merges files from the given branch into the current branch.
     * Before a merge takes place, the splitpoint (latest common
     * ancestor of the commit) is found.
     * The merge logic:
     * 1. Any files that have been modified in the given branch
     *            since the split point, but not modified in the current
     *            branch since the split point are changed to their
     *            versions in the given branch. These files will then
     *            all be automatically staged.
     *            2. Any files that have been modified in both the current and
     *            given branch in the same way are left unchanged by the
     *            merge. If a file was removed from both the current and given
     *            branch, but a file of the same name is present in the working
     *            directory, it is left alone and continues to be absent in the merge.
     *            3. Any files that were not present at the split point and are present
     *            only in the current branch remain as they are.
     *            4. Any files that were not present at the split point and
     *            are present only in the given branch are checked out and staged.
     *            5. Any files present at the split point, unmodified in the current branch,
     *            and absent in the given branch are removed (and untracked).
     *            6. Any files present at the split point, unmodified in the given branch,
     *           and absent in the current branch remain absent.
     *            7. Any files modified in different ways in the current and given branches
     *            are in conflict. In this case, contents of conflicted file replaced with:
     *
     *                <<<<<<< HEAD
     *                contents of file in current branch
     *                =======
     *                contents of file in given branch
     *                >>>>>>>
     *
     * @param givenBranch branch to merge into.
     * @throws IOException
     */
    public void merge(String givenBranch) throws IOException {
        File givenBranchFile = Utils.join(BRANCHES, givenBranch);
        TreeMap<String, String> allFiles = new TreeMap<>();
        TreeMap<String, Blob> result = new TreeMap<>();
        Commit splitCommit = getSplitPoint(getLastCommit(), givenBranchFile);
        TreeMap<String, Blob> current = getLastCommit().getCommittedFiles();
        TreeMap<String, Blob> given =
                getCommit(Utils.readContentsAsString(givenBranchFile))
                        .getCommittedFiles();
        TreeMap<String, Blob> split = splitCommit.getCommittedFiles();
        Set<Map.Entry<String, Blob>> currentEntries = current.entrySet();
        Set<Map.Entry<String, Blob>> givenEntries = given.entrySet();
        Set<Map.Entry<String, Blob>> splitEntries = split.entrySet();
        Set<Map.Entry<String, String>> allEntries = allFiles.entrySet();
        handleMergeErrorCases(allEntries, givenBranch, givenBranchFile,
                splitCommit);
        combineFiles(allFiles, currentEntries, givenEntries, splitEntries);
        boolean givenModied, currentModified, sameModification,
                inCurrent, inGiven, inSplit, conflict;
        conflict = false;
        for (Map.Entry<String, String> file : allEntries) {
            String fileName = file.getKey();
            givenModied = currentModified = sameModification = inCurrent
                    = inSplit = inGiven = false;
            Blob[] cgsBlob = new Blob[3];
            String[] cgsHash = new String[] {"", "", ""};
            for (Map.Entry<String, Blob> entry : splitEntries) {
                if (fileName.equals(entry.getValue().getName())) {
                    inSplit = true;
                    cgsHash[2] = entry.getKey();
                    cgsBlob[2] = entry.getValue();
                }
            }
            for (Map.Entry<String, Blob> entry : currentEntries) {
                if (fileName.equals(entry.getValue().getName())) {
                    inCurrent = true;
                    cgsHash[0] = entry.getKey();
                    cgsBlob[0] = entry.getValue();
                }
            }
            for (Map.Entry<String, Blob> entry : givenEntries) {
                if (fileName.equals(entry.getValue().getName())) {
                    inGiven = true;
                    cgsHash[1] = entry.getKey();
                    cgsBlob[1] = entry.getValue();
                }
            }
            if (!cgsHash[1].equals(cgsHash[2])) {
                givenModied = true;
            }
            if (!cgsHash[0].equals(cgsHash[2])) {
                currentModified = true;
            }
            conflict = handleMergingLogic(result, inCurrent, inGiven,
                    inSplit, givenModied, currentModified,
                    cgsHash, cgsBlob);
        }
        commitMerge(result, givenBranch, allFiles);
    }



    /* Helper functions below */


    /**
     * Sets up initial file structure.
     *
     * @throws IOException
     */
    private void setupPersistance() throws IOException {
        GIT.mkdir();
        BRANCHES.mkdir();
        COMMITS.mkdir();
        STAGING.mkdir();
        addStagingFile.createNewFile();
        rmStagingFile.createNewFile();
        head.createNewFile();
        MASTER.createNewFile();
        writeStaging();
    }


    /**
     * Saves commit to a commit file.
     *
     * @param commit commit object to be saved to file.
     * @throws IOException
     */
    private void persistCommit(Commit commit) throws IOException {
        String commitHash = Utils.sha1(Utils.serialize(commit));
        File commitFile = Utils.join(COMMITS, commitHash);

        if (!commitFile.exists()) {
            commitFile.createNewFile();
            Utils.writeObject(commitFile, commit);
            Utils.writeContents(new File(getHeadBranch()), commitHash);
        } else {
            Main.exitWithError("Commit already exists.");
        }
    }


    /**
     * @return directory address of HEAD branch
     */
    private String getHeadBranch() {
        return Utils.readContentsAsString(head);
    }


    /**
     * @return commit object of current commit.
     */
    private Commit getLastCommit() {
        File lastCommitFile = Utils.join(COMMITS, getLastCommitHash());
        return Utils.readObject(lastCommitFile, Commit.class);
    }


    /**
     * @return current commit hash
     */
    private String getLastCommitHash() {
        return Utils.readContentsAsString(new File(getHeadBranch()));
    }


    /**
     * Get the commit of the associated hash code.
     *
     * @param hash code that identifies a commit.
     * @return commit associated with the hash.
     */
    private Commit getCommit(String hash) {
        File commitFile = Utils.join(COMMITS, hash);
        if (commitFile.exists()) {
            return Utils.readObject(Utils.join(COMMITS, hash), Commit.class);
        } else {
            Main.exitWithError("No commit with that id exists.");
            return null;
        }
    }


    /**
     * Output a log of a specific commit.
     *
     * @param hash identifier of a specific commit.
     */
    private void printLog(String hash) {
        Commit commit = getCommit(hash);
        System.out.println("===");
        System.out.println("commit " + hash);
        System.out.println("Date: " + commit.getTimestamp());
        System.out.println(commit.getMessage() + "\n");
    }


    /**
     * Get a list of files that are untracked by the current commit.
     *
     * @return Arraylist of file names in working directory untracked
     *         by the current commit
     */
    private ArrayList<String> getUntrackedFiles() {
        ArrayList<String> untrackedFiles = new ArrayList<>();
        TreeMap<String, Blob> headCommitObj =
                getLastCommit().getCommittedFiles();
        Set<Map.Entry<String, Blob>> trackedFiles =
                headCommitObj.entrySet();

        String[] cwdFiles = CWD.list();
        for (String file : cwdFiles) {
            boolean isTracked = false;
            File aFile = Utils.join(CWD, file);
            for (Map.Entry<String, Blob> tracked : trackedFiles) {
                if (file.equals(tracked.getValue().getName())) {
                    isTracked = true;
                }
            }
            if (!isTracked && aFile.isFile()) {
                untrackedFiles.add(file);
            }
        }

        return  untrackedFiles;
    }


    /**
     * Get a list of files that are tracked by the current commit.
     *
     * @return Arraylist of file names in working directory untracked
     *         by the current commit
     */
    private ArrayList<String> getTrackedFiles() {
        TreeMap<String, Blob> headCommitObj =
                getLastCommit().getCommittedFiles();
        Set<Map.Entry<String, Blob>> trackedFiles =
                headCommitObj.entrySet();

        ArrayList<String> trackedFileList = new ArrayList<>();
        for (Map.Entry<String, Blob> file : trackedFiles) {
            trackedFileList.add(file.getValue().getName());
        }
        return trackedFileList;
    }

    /**
     * Assign staging variables by reading in values persisted into files.
     */
    @SuppressWarnings("unchecked")
    private void readStaging() {
        addStagingMap = Utils.readObject(addStagingFile, TreeMap.class);
        rmStagingMap = Utils.readObject(rmStagingFile, TreeMap.class);
    }

    /**
     * Persist staging area to files.
     */
    private void writeStaging() {
        Utils.writeObject(addStagingFile, addStagingMap);
        Utils.writeObject(rmStagingFile, rmStagingMap);
    }

    /**
     * Clears the staging area.
     */
    private void clearStaging() {
        addStagingMap.clear();
        rmStagingMap.clear();
    }

    /**
     * Displays what branches currently exist in lexical order, and marks the
     * current branch with a '*'.
     */
    private void printBranches() {
        File headBranch = new File(getHeadBranch());
        String headBranchName = headBranch.getName();
        String[] branchPaths = BRANCHES.list();
        Arrays.sort(branchPaths);

        for (int i = 0; i < branchPaths.length; i++) {
            if (!branchPaths[i].equals(headBranchName)) {
                System.out.println(branchPaths[i]);
            } else {
                System.out.println("*" + branchPaths[i]);
            }
        }

        System.out.println();
    }

    /**
     * Prints files staged for addition in lexical order.
     */
    @SuppressWarnings("unchecked")
    private void printStagedForAddition() {
        addStagingMap = Utils.readObject(addStagingFile, TreeMap.class);
        ArrayList<String> sortedStagedFiles = new ArrayList<>();

        Set<Map.Entry<String, Blob>> stagingEntries = addStagingMap.entrySet();
        for (Map.Entry<String, Blob> staged : stagingEntries) {
            sortedStagedFiles.add(staged.getValue().getName());
        }

        Collections.sort(sortedStagedFiles);
        for (String file : sortedStagedFiles) {
            System.out.println(file);
        }

        System.out.println();
    }


    /**
     * Prints files staged for removal in lexical order.
     */
    @SuppressWarnings("unchecked")
    private void printStagedForRemoval() {
        ArrayList<String> sortedStagedFiles = new ArrayList<>();
        rmStagingMap = Utils.readObject(rmStagingFile, TreeMap.class);

        Set<Map.Entry<String, Blob>> rmEntries = rmStagingMap.entrySet();
        for (Map.Entry<String, Blob> staged : rmEntries) {
            sortedStagedFiles.add(staged.getValue().getName());
        }

        Collections.sort(sortedStagedFiles);
        for (String file : sortedStagedFiles) {
            System.out.println(file);
        }

        System.out.println();
    }


    /**
     * Prints files that are modified but not staged.
     * Files modified but not staged include:
     *      > Tracked in the current commit, changed in the working directory,
     *        but not staged; or
     *      > Staged for addition, but with different contents than in the
     *        working directory; or
     *      > Staged for addition, but deleted in the working directory; or
     *      > Not staged for removal, but tracked in the current commit
     *        and deleted from the working directory.
     */
    private void printModifiedFiles() {
        readStaging();
        TreeMap<String, Blob> currentCommitFiles =
                getLastCommit().getCommittedFiles();
        Set<Map.Entry<String, Blob>> commitedEntries =
                currentCommitFiles.entrySet();
        Set<Map.Entry<String, Blob>> addStagingEntries =
                addStagingMap.entrySet();
        Set<Map.Entry<String, Blob>> rmEntries = rmStagingMap.entrySet();
        ArrayList<String> printList = new ArrayList<>();
        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);
        boolean tracked, stagedAdd, stagedRm, modifiedCommit, modifiedAdd;
        tracked = stagedAdd = stagedRm = modifiedCommit = modifiedAdd
                = false;
        for (String file : cwdFiles) {
            File aFile = Utils.join(CWD, file);
            Blob fileBlob = new Blob(file, Utils.readContentsAsString(aFile));
            String blobHash = Utils.sha1(Utils.serialize(fileBlob));
            for (Map.Entry<String, Blob> staged : rmEntries) {
                if (file.equals(staged.getValue().getName())) {
                    stagedRm = true;
                }
            }
            for (Map.Entry<String, Blob> staged : addStagingEntries) {
                if (file.equals(staged.getValue().getName())) {
                    stagedAdd = true;
                    if (!blobHash.equals("")
                            && !blobHash.equals(staged.getKey())) {
                        modifiedAdd = true;
                    }
                }
            }
            for (Map.Entry<String, Blob> trackedFile : commitedEntries) {
                if (file.equals(trackedFile.getValue().getName())) {
                    tracked = true;
                    if (!blobHash.equals("")
                            && !blobHash.equals(trackedFile.getKey())) {
                        modifiedCommit = true;
                    }
                }
            }
            addModified(printList, file, tracked, stagedAdd,
                    stagedRm, modifiedCommit, modifiedAdd);
        }
        stagedRm = false;
        for (Map.Entry<String, Blob> trackedFile : commitedEntries) {
            for (Map.Entry<String, Blob> staged : rmEntries) {
                if (trackedFile.getValue().getName()
                        .equals(staged.getValue().getName())) {
                    stagedRm = true;
                }
            }
            if (!stagedRm && !Utils.join(CWD, trackedFile.getValue()
                    .getName()).exists()) {
                printList.add(trackedFile.getValue().getName() + " (deleted)");
            }
        }
        addDeleteMod(printList, addStagingEntries);
        printStringsSorted(printList);
    }

    /**
     * Print strings in lexically sorted order.
     *
     * @param printList list of strings to print
     */
    private void printStringsSorted(ArrayList<String> printList) {
        Collections.sort(printList);
        for (String modifiedFile : printList) {
            System.out.println(modifiedFile);
        }
        System.out.println();
    }

    /**
     * Add list of mofified files to list of results.
     *
     * @param printList list containing modfied files.
     * @param file the filename.
     * @param tracked true if tracked in current branch
     * @param stagedAdd true if staged for addition.
     * @param stagedRm true if staged for removal.
     * @param modifiedCommit true if modified in commit.
     * @param modifiedAdd true if modified in staged.
     */
    private void addModified(ArrayList<String> printList, String file,
                             boolean tracked, boolean stagedAdd,
                             boolean stagedRm, boolean modifiedCommit,
                             boolean modifiedAdd) {
        if ((tracked && modifiedCommit && !(stagedAdd || stagedRm))
                || (stagedAdd && modifiedAdd)) {
            printList.add(file + " (modified)");
        }
    }

    /**
     * Add files deleted before being commited.
     *
     * @param printList list of modified files.
     * @param addStagingEntries files staged for addition.
     */
    private void addDeleteMod(ArrayList<String> printList,
                              Set<Map.Entry<String, Blob>> addStagingEntries) {
        for (Map.Entry<String, Blob> staged : addStagingEntries) {
            if (!Utils.join(CWD, staged.getValue().getName()).exists()) {
                printList.add(staged.getValue().getName() + " (deleted)");
            }
        }
    }

    /**
     * Prints untracked files i.e files present in the working directory
     * but neither staged for addition nor tracked.
     */
    private void printUntrackedFiles() {
        ArrayList<String> untrackedFiles = new ArrayList<>();
        ArrayList<String> uncommittedFiles = getUntrackedFiles();
        readStaging();
        Set<Map.Entry<String, Blob>> addStagingEntries =
                addStagingMap.entrySet();
        Set<Map.Entry<String, Blob>> rmEntries = rmStagingMap.entrySet();
        String[] cwdFiles = CWD.list();

        for (Map.Entry<String, Blob> staged : rmEntries) {
            for (String file : cwdFiles) {
                if (file.equals(staged.getValue().getName())) {
                    untrackedFiles.add(file);
                }
            }
        }

        for (String file : uncommittedFiles) {
            boolean untrackedInAdd = true;
            for (Map.Entry<String, Blob> staged : addStagingEntries) {
                if (file.equals(staged.getValue().getName())) {
                    untrackedInAdd = false;
                }
            }
            if (untrackedInAdd) {
                untrackedFiles.add(file);
            }
        }

        if (!untrackedFiles.isEmpty()) {
            Collections.sort(untrackedFiles);
            for (String file : untrackedFiles) {
                System.out.println(file);
            }
        }

        System.out.println();
    }


    /**
     * Return commit based on commit id that may
     * be abbreviated.
     *
     * @param commitID possibly abbraviated ID
     * @return the associated commit to COMMITID
     */
    private Commit getAbbrevCommit(String commitID) {
        File commitFile = Utils.join(COMMITS, commitID);
        Commit commit = null;
        if (commitFile.exists()) {
            commit = Utils.readObject(commitFile, Commit.class);
            return commit;
        } else {
            List<String> allCommits = Utils.plainFilenamesIn(COMMITS);
            for (String aCommit : allCommits) {
                if (aCommit.startsWith(commitID)) {
                    File abbrevCommitFile = Utils.join(COMMITS, aCommit);
                    commit = Utils.readObject(abbrevCommitFile, Commit.class);
                    return commit;
                }
            }
            Main.exitWithError("No commit with that id exists.");
            return null;
        }
    }

    /**
     * Decide which versions of files should be committed
     * in the merge.
     * The merge logic:
     *      1. Any files that have been modified in the given branch
     *      since the split point, but not modified in the current
     *      branch since the split point are changed to their
     *      versions in the given branch. These files will then
     *      all be automatically staged.
     *      2. Any files that have been modified in both the current and
     *      given branch in the same way are left unchanged by the
     *      merge. If a file was removed from both the current and given
     *      branch, but a file of the same name is present in the working
     *      directory, it is left alone and continues to be absent in the merge.
     *      3. Any files that were not present at the split point and are present
     *      only in the current branch remain as they are.
     *      4. Any files that were not present at the split point and
     *      are present only in the given branch are checked out and staged.
     *      5. Any files present at the split point, unmodified in the current branch,
     *      and absent in the given branch are removed (and untracked).
     *      6. Any files present at the split point, unmodified in the given branch,
     *      and absent in the current branch remain absent.
     *      7. Any files modified in different ways in the current and given branches
     *      are in conflict. In this case, contents of conflicted file replaced with:
     *
     *          <<<<<<< HEAD
     *          contents of file in current branch
     *          =======
     *          contents of file in given branch
     *          >>>>>>>
     *
     * @param result files that will be merged.
     * @param inCurrent true if in current directory.
     * @param inGiven true if in given directory.
     * @param inSplit true if in splitpoint.
     * @param givenModied true if modified from split in the given branch.
     * @param currentModified true if modified from split in current branch.
     * @param cgsHash array of hash values, current, given, split
     *                commit respectively.
     * @param cgsBlob array of blobs, current, given, split
     *                branches respectively.
     * @return boolean that indicated true if a merge conflict occured.
     */
    private boolean handleMergingLogic(TreeMap<String, Blob> result,
                                       boolean inCurrent, boolean inGiven,
                                       boolean inSplit, boolean givenModied,
                                       boolean currentModified,
                                       String[] cgsHash, Blob[] cgsBlob) {
        boolean conflict = false;
        if (!inSplit && !inGiven && inCurrent) {
            result.put(cgsHash[0], cgsBlob[0]);
        } else if (!inSplit && !inCurrent && inGiven) {
            result.put(cgsHash[1], cgsBlob[1]);
            if (!cgsHash[1].equals(cgsHash[0])) {
                addStagingMap.put(cgsHash[1], cgsBlob[1]);
            }
        } else if ((!inCurrent && !inCurrent)
                || (currentModified && givenModied)) {
            if (inCurrent) {
                if (cgsHash[0].equals(cgsHash[1])) {
                    result.put(cgsHash[0], cgsBlob[0]);
                } else {
                    Blob conflictBlob =
                            mergeConflictFile(cgsBlob[0], cgsBlob[1]);
                    result.put(Utils.sha1(Utils.serialize(conflictBlob)),
                            conflictBlob);
                    conflict = true;
                }
            }
        } else if (!currentModified && !inGiven) {
            rmStagingMap.put(cgsHash[0], cgsBlob[0]);
        } else if (givenModied && !currentModified) {
            result.put(cgsHash[1], cgsBlob[1]);
            if (!cgsHash[1].equals(cgsHash[0])) {
                addStagingMap.put(cgsHash[1], cgsBlob[1]);
            }
        } else if (!givenModied && currentModified) {
            result.put(cgsHash[0], cgsBlob[0]);
        }
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
        return conflict;
    }


    /**
     * Combine all file entries from multiple sets into a single TreeMap.
     *
     * @param allFiles contains all the files.
     * @param currentEntries contains entries in head branch.
     * @param givenEntries contains entries in given branch.
     * @param splitEntries contains entries in the split branch.
     */
    private void combineFiles(TreeMap<String, String> allFiles,
                              Set<Map.Entry<String, Blob>> currentEntries,
                              Set<Map.Entry<String, Blob>> givenEntries,
                              Set<Map.Entry<String, Blob>> splitEntries) {
        for (Map.Entry<String, Blob> entry : currentEntries) {
            allFiles.put(entry.getValue().getName(), entry.getKey());
        }
        for (Map.Entry<String, Blob> entry : givenEntries) {
            allFiles.put(entry.getValue().getName(), entry.getKey());
        }
        for (Map.Entry<String, Blob> entry : splitEntries) {
            allFiles.put(entry.getValue().getName(), entry.getKey());
        }
    }

    /**
     * Handles merge error cases.
     *
     * @param allEntrie all files.
     * @param branchName name of given branch.
     * @param givenBranchFile given branch.
     * @param splitCommit the split commit.
     * @throws IOException
     */
    private void handleMergeErrorCases(Set<Map.Entry<String, String>> allEntrie,
                                       String branchName, File givenBranchFile,
                                       Commit splitCommit) throws IOException {
        readStaging();
        if (!addStagingMap.isEmpty() || !rmStagingMap.isEmpty()) {
            Main.exitWithError("You have uncommitted changes.");
        }
        if (!givenBranchFile.exists()) {
            Main.exitWithError("A branch with that name does not exist.");
        }
        if (getLastCommitHash().equals(Utils
                .sha1(Utils.serialize(splitCommit)))) {
            checkoutThree(branchName);
            Main.exitWithError("Current branch fast-forwarded.");
        }
    }


    /**
     * Checkout merge to the working directory.
     *
     * @param branch the head branch now at the merged commit.
     * @param allFiles all files that were processed in merge.
     * @throws IOException
     */
    private void checkoutMerged(String branch, TreeMap<String, String> allFiles)
            throws IOException {
        File branchFile = Utils.join(BRANCHES, branch);


        String branchCommit = Utils.readContentsAsString(branchFile);
        String branchPath = branchFile.getPath();
        String headCommit = getHeadBranch();


        TreeMap<String, Blob> filesAtBranch =
                getCommit(branchCommit).getCommittedFiles();
        Set<Map.Entry<String, Blob>> branchFileEntries =
                filesAtBranch.entrySet();
        ArrayList<String> untrackedFiles = getUntrackedFiles();


        ArrayList<String> trackedFiles = getTrackedFiles();
        Set<Map.Entry<String, String>> allFileEntries = allFiles.entrySet();
        for (Map.Entry<String, String> files : allFileEntries) {
            File aWorkingFile = Utils.join(CWD, files.getKey());
            if (aWorkingFile.exists()) {
                aWorkingFile.delete();
            }
        }

        for (Map.Entry<String, Blob> file : branchFileEntries) {
            File aFile = Utils.join(CWD, file.getValue().getName());
            aFile.createNewFile();
            Utils.writeContents(aFile, file.getValue().getFileContents());
        }

        clearStaging();
        writeStaging();

        Utils.writeContents(head, branchFile.getAbsolutePath());
        writeStaging();
    }


    /**
     * Commit the state after the merge occures.
     *
     * @param mergedFiles resulting files after merge.
     * @param branch branch name.
     * @param allFiles all files relevant in the merging.
     * @throws IOException
     */
    private void commitMerge(TreeMap<String, Blob> mergedFiles,
                             String branch,
                             TreeMap<String, String> allFiles)
            throws IOException {
        File branchFile = Utils.join(BRANCHES, branch);
        File currentBranchFile = Utils.join(BRANCHES, getHeadBranch());
        String currentCommit = getLastCommitHash();
        String branchCommit = Utils.readContentsAsString(branchFile);
        String message = "Merged " + branch + " into "
                + currentBranchFile.getName() + ".";
        MergeCommit mergeCommit = new MergeCommit(message, currentCommit,
                branchCommit, mergedFiles);
        ArrayList<String> untrackedFiles = getUntrackedFiles();
        Set<Map.Entry<String, Blob>> mergeEntries = mergedFiles.entrySet();
        for (Map.Entry<String, Blob> bFile : mergeEntries) {
            for (String untrackedFile : untrackedFiles) {
                if (untrackedFile.equals(bFile.getValue().getName())) {
                    Main.exitWithError("There is an untracked file in the"
                            + " way; delete it, "
                            + "or add and commit it first.");
                }
            }
        }
        persistCommit(mergeCommit);
        checkoutMerged((new File(getHeadBranch())).getName(), allFiles);
    }


    /**
     * Addresses merge conflicts by writing both versions of
     * conflicted files and notation into conflicted file.
     *
     *      <<<<<<< HEAD
     *      * contents of file in current branch *
     *      =======
     *      * contents of file in given branch *
     *      >>>>>>>
     *
     * @param currentFile conflicted file in current commit
     * @param givenFile conflicted file in given commit
     * @return file with addressed merge conflict
     */
    private Blob mergeConflictFile(Blob currentFile, Blob givenFile) {
        String fileName = currentFile.getName();
        String currentFileContents;
        String givenFileContents;
        if (currentFile == null) {
            currentFileContents = null;
        } else {
            currentFileContents = currentFile.getFileContents();
        }
        if (givenFile == null) {
            givenFileContents = "";
        } else {
            givenFileContents = givenFile.getFileContents();
        }
        String mergeContent = "<<<<<<< HEAD\n" + currentFileContents
                + "=======\n" + givenFileContents + ">>>>>>>\n";
        return new Blob(fileName, mergeContent);
    }

    /**
     * Get latest common ancestor of commits; the "split point".
     *
     * @param current current branch.
     * @param givenBranchFile given branch as a file.
     * @return the splitpoint commit.
     */
    private Commit getSplitPoint(Commit current, File givenBranchFile) {
        if (!givenBranchFile.exists()) {
            Main.exitWithError("A branch with that name does not exist.");
        }
        File commitFile = Utils.join(COMMITS,
                Utils.readContentsAsString(givenBranchFile));
        Commit given = Utils.readObject(commitFile, Commit.class);
        String currentHash = Utils.sha1(Utils.serialize(current));
        String givenHash = Utils.sha1(Utils.serialize(given));
        ArrayList<String> currentAncestors = bfsCommit(current);
        ArrayList<String> givenAncestors = bfsCommit(given);
        String splitHash = "";
        boolean found = false;
        for (String currentCommitAncestor : currentAncestors) {
            for (String givenCommitAncestor : givenAncestors) {
                if (currentCommitAncestor.equals(givenCommitAncestor)) {
                    splitHash = currentCommitAncestor;
                    found = true;
                }
                if (givenHash.equals(getLastCommitHash())) {
                    Main.exitWithError("Cannot merge a branch with itself.");
                } else if (currentCommitAncestor.equals(givenHash)) {
                    Main.exitWithError("Given branch is an ancestor of "
                            + "the current branch.");
                }
            }
            if (found) {
                break;
            }
        }
        if (splitHash.equals("")) {
            return null;
        } else {
            return getCommit(splitHash);
        }
    }


    /**
     * Runs breadth first search on commits.
     *
     * @param node root commit.
     * @return list of commit hashes in BFS traversal order.
     */
    private ArrayList<String> bfsCommit(Commit node) {
        ArrayList<String> orderedFiles = new ArrayList<>();
        Queue<String> q = new LinkedList<>();
        List<String> allCommits = Utils.plainFilenamesIn(COMMITS);
        TreeMap<String, Boolean> visited = new TreeMap<String, Boolean>();
        for (String commit : allCommits) {
            visited.put(commit, false);
        }

        String commitHash = Utils.sha1(Utils.serialize(node));
        q.add(commitHash);

        while (q.size() != 0) {
            commitHash = q.poll();
            node = getCommit(commitHash);
            orderedFiles.add(commitHash);
            if (!visited.get(commitHash)) {
                if (node instanceof MergeCommit) {
                    String parentHash = node.getParent();
                    if (parentHash != "") {
                        visited.put(commitHash, true);
                        q.add(parentHash);
                    }
                    String secondParentHash =
                            ((MergeCommit) node).getSecondParent();
                    if (secondParentHash != "") {
                        visited.put(commitHash, true);
                        q.add(secondParentHash);
                    }
                } else {
                    if (node.getParent() != "") {
                        visited.put(commitHash, true);
                        q.add(node.getParent());
                    }
                }
            }
        }
        return orderedFiles;
    }

    /**
     * Implements use case 1 and 2 of checkout:
     * Checkout chosen FILENAME to version of the file
     * in the COMMITFILES from the chosen commit.
     *
     * @param fileName name of a file.
     * @param commitFiles files tracked in a commit.
     * @throws IOException
     */
    private void checkoutOneTwo(String fileName,
                                TreeMap<String, Blob> commitFiles)
            throws IOException {
        Blob checkoutFile = null;

        Set<Map.Entry<String, Blob>> commitEntries = commitFiles.entrySet();
        for (Map.Entry<String, Blob> committed : commitEntries) {
            if (committed.getValue().getName().equals(fileName)) {
                checkoutFile = committed.getValue();
            }
        }

        if (checkoutFile == null) {
            Main.exitWithError("File does not exist in that commit.");
        } else {
            String fileContent = checkoutFile.getFileContents();
            File fileInDirectory = Utils.join(CWD, fileName);
            if (fileInDirectory.exists()) {
                Utils.writeContents(fileInDirectory, fileContent);
            } else {
                fileInDirectory.createNewFile();
                Utils.writeContents(fileInDirectory, fileContent);
            }
        }
    }

    /**
     * Implements use case 3 of checkout:
     * Takes all files in the commit at the head of the given branch, and
     * puts them in the working directory, overwriting the versions of
     * the files that are already there if they exist. Also, at the end of
     * this command, the given branch will now be considered the current
     * branch (HEAD). Any files that are tracked in the current branch
     * but are not present in the checked-out branch are deleted. The
     * staging area is cleared, unless the checked-out branch is the
     * current branch
     *
     *
     * @param branch name of an existing branch
     * @throws IOException
     */
    private void checkoutThree(String branch) throws IOException {
        File branchFile = Utils.join(BRANCHES, branch);

        if (!branchFile.exists()) {
            Main.exitWithError("No such branch exists.");
        }

        String branchCommit = Utils.readContentsAsString(branchFile);
        String branchPath = branchFile.getPath();
        String headCommit = getHeadBranch();

        if (branchPath.equals(headCommit)) {
            Main.exitWithError("No need to checkout the current branch.");
        }

        TreeMap<String, Blob> filesAtBranch =
                getCommit(branchCommit).getCommittedFiles();
        Set<Map.Entry<String, Blob>> branchFileEntries =
                filesAtBranch.entrySet();
        ArrayList<String> untrackedFiles = getUntrackedFiles();

        for (Map.Entry<String, Blob> bFile : branchFileEntries) {
            for (String untrackedFile : untrackedFiles) {
                if (untrackedFile.equals(bFile.getValue().getName())) {
                    Main.exitWithError("There is an untracked file "
                            + "in the way; delete it, "
                            + "or add and commit it first.");
                }
            }
        }

        ArrayList<String> trackedFiles = getTrackedFiles();

        for (String name : trackedFiles) {
            File aWorkingFile = Utils.join(CWD, name);
            aWorkingFile.delete();
        }

        for (Map.Entry<String, Blob> file : branchFileEntries) {
            File aFile = Utils.join(CWD, file.getValue().getName());
            aFile.createNewFile();
            Utils.writeContents(aFile, file.getValue().getFileContents());
        }

        clearStaging();
        writeStaging();

        Utils.writeContents(head, branchFile.getAbsolutePath());
    }



    /** Current working directory. */
    private static final File CWD = new File(System.getProperty("user.dir"));

    /** Directory that persists entire program .gitlet. */
    private static final File GIT = Utils.join(CWD, ".gitlet");

    /** Directory that holds branches in .gitlet/Branches. */
    private static final File BRANCHES = Utils.join(GIT, "Branches");

    /** Directory that holds commits in .gitlet/Commits. */
    private static final File COMMITS = Utils.join(GIT, "Commits");

    /** File that address to the head branch .gitlet/HEAD. */
    private static File head = Utils.join(GIT, "HEAD");

    /** File of master branch that is the default branch. */
    private static final File MASTER =
            Utils.join(BRANCHES, "master");

    /** Directory of files staged for commit in .gitlet/staging. */
    private static final File STAGING = Utils.join(GIT, "staging");

    /** File contains structure of staged files to be committed. */
    private static File addStagingFile = Utils.join(STAGING, "add");

    /** File contains structure of staged files to be committed. */
    private static File rmStagingFile = Utils.join(STAGING, "rm");

    /** TreeMap of files that are staged for commit. */
    private static TreeMap<String, Blob> addStagingMap = new TreeMap<>();

    /** TreeMap of files that are staged for removal. */
    private static TreeMap<String, Blob> rmStagingMap = new TreeMap<>();
}
