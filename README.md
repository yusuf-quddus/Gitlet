# Gitlet Design Document
**Author**: Yusuf Quddus

Gitlet is a simplified clone of [git](https://git-scm.com/). 

Run gitlet programs. Start by initializing a gitlet repository by running `java gitlet.Main init` in the project directory.

## 1. Classes

### Main.java
This class is the entry point of the program. Calls `Gitlet.java` class to execute gitlet commands.

#### Fields 
* static final File CWD: A pointer to the current working directory of the program.
* static final File gitletDir: Gitlet directory.
* Gitlet GITLET: Gitlet object holds gitlet command logic and execution.

### Gitlet.java
This class implements methods to set up persistance and support each gitlet command.

#### Fields
* private static final File CWD: Current working directory.
* private static final File GIT: Directory that persists entire program `.gitlet`.
* private static final File BRANCHES: Directory that holds branches in `.gitlet/Branches`.
* private static final File COMMITS: Directory that holds commits in `.gitlet/Commits`.
* private static final File head:  File that address to the head branch `.gitlet/HEAD`.
* private static File master: File of master branch that is the default branch `.gitlet/Branches/master`.
* private static final File STAGING: Directory of files staged for commit in `.gitlet/staging`.
* private static File addStagingFile: File contains structure of staged files to be committed `.gitlet/stagging/add`.
* private static File rmStagingFile:  File contains structure of staged files to be committed `.gitlet/stagging/rm`.
* private static TreeMap<String, Blob> addStagingMap: TreeMap of files that are staged for commit.
* private static TreeMap<String, Blob> rmStagingMap: TreeMap of files that are staged for removal.


### Commit.java
This class represents a commit.

#### Fields
* private String _message: Log associated with this commit.
* private String _timestamp: Timestamp of current commit.
* private String _parent: SHA1 address hash of parent commit.
* private TreeMap<String, Blob> _files: Treemap of files in this commit.

### MergeCommit.java
This class represents a merge commit. `MergeCommit.class` extends `Commit.class`.

#### Fields
* private String _message: Log associated with this commit.
* private String _timestamp: Timestamp of current commit.
* private String _parent: SHA1 address hash of parent commit.
* private TreeMap<String, Blob> _files: Treemap of files in this commit.
* private String _secondParent: the commit that merged into the head commit.

### Blob.java
This class represents a file.
* private String _fileName: Name of file.
* private String _fileContent: Contents in the file.

### Utils.java
Class of assorted utilities mainly for handling file and directory operations 
written by P. N. Hilfinger.

## 2. Algorithms

### Main.java
1. main(String[] args): This is the entry point of the program. 
It first checks to make sure that the input array is not empty. Depending on the input argument, different 
functions are called in Gitlet class to perform the operation. Errors of improper inputs are also handled by 
exiting the program and outputting an error message. Functions were implemented to handle various command inputs.

Possible input commands in terminal:
 * java gitlet.Main init
 * java gitlet.Main add [file name]
 * java gitlet.Main commit [message]
 * java gitlet.Main log
 * java gitlet.Main global-log
 * java gitlet.Main status
 * java gitlet.Main find [message]
 * java gitlet.Main checkout [input]
   1. java gitlet.Main checkout -- [file name]
   2. java gitlet.Main checkout [commit id] -- [file name]
   3. java gitlet.Main checkout [branch name]
 * java gitlet.Main branch [branch name]
 * java gitlet.Main rm-branch [branch name]
 * java gitlet.Main reset [commit id]
 * java gitlet.Main merge [branch name]

### Gitlet.java
1. init(): Calls `setupPersistence` to create the `/.gitlet` and `/.git/branches` for persistence. It also sets up
files for staging files to be committed. Start off with an initial commit timestamped to zero.
2. add(): Stage the file for addition. Remove it from remove staging area if it exists there. 
Do not stage it for addition if the file is the same and unmodified in the current commit.
3. rm(): Stage a file for removal. Remove it from add staging area if it exists there. Do not add if 
the file is the same in the current commit.Do not stage file for removal if it is neither staged for addition and not tracked in the current commit.
4. commit(): Saves a snapshot of tracked files in the current commit and staging area.
so they can be restored at a later time, creating a new commit. By default, a commit is the same as its parent. Files staged for addition and removal are the updates to the commit. The
staging area is cleared.
5. log():  Starting at the current head commit, display the commit history
   and information about each commit until the initial commit,
   following the first parent commit links, ignoring any second
   parents found in merge commits.
 
   ```
   ===
   commit 0c69f7be8497337c9736aa80b47828bb5306c007
   Date: Wed Dec 31 16:00:00 1969 -0800
   initial commit
   ```
6. global-log(): Like log, except displays information about all commits ever made. Order of commits is arbitrary.
7. status():    Output the current status of gitlet including current branches,
   files staged for addition, files staged for removal, modifications
   not staged for commit and untracked files. Modified files include HEAD branch it led by
   a '*'. Output is in lexical order.
   ``` 
   === Branches ===
   *master
   other-branch
   
   === Staged Files ===
   wug.txt
   wug2.txt
   
   === Removed Files ===
   goodbye.txt
   
   === Modifications Not Staged For Commit ===
   junk.txt (deleted)
   wug3.txt (modified)
   
   === Untracked Files ===
   random.stuff
   ```
8. checkout(): There are 3 possible use cases for gitlet.checkout():
   * java gitlet.Main checkout -- [file name]:\
     Takes the version of the file as it exists
     in the head commit, the front of the current
     branch, and puts it in the working directory,
     overwriting the version of the file that's already
     there if there is one. The new version of the file
     is not staged.
   * java gitlet.Main checkout [commit id] -- [file name]:\
     Takes the version of the file as it exists in the commit
     with the given id, and puts it in the working directory,
     overwriting the version of the file that's already there
     if there is one. The new version of the file is not staged.
   * java gitlet.Main checkout [branch name]:\
     Takes all files in the commit at the head of the given branch,
     and puts them in the working directory, overwriting the versions
     of the files that are already there if they exist. Also, at the
     end of this command, the given branch will now be considered the
     current branch (HEAD). Any files that are tracked in the current
     branch but aren't present in the checked-out branch are deleted.
     The staging area is cleared, unless the checked-out branch is
     the current branch

9. branch(): Creates a new branch with the given name, and points it at the current head node. A branch is simply a file
that contains the hash of a commit. 
10. rmBranch(): Creates a new branch with the given name, and points it at the current head node.
11. reset(): Checks out all the files tracked by the given commit. Removes tracked files that are not present in that commit. Also moves the current
branch's head to that commit node. The staging area is cleared. Changes the current branch head.
12. merge(): The split point (latest common ancestor) between the current and given branch is found using a breadth first traversal. Then implement the merging logic to resolve the merging of the two branches. \
    The merge logic:
       1. Any files that have been modified in the given branch
                since the split point, but not modified in the current
                branch since the split point are changed to their
                versions in the given branch. These files will then
                all be automatically staged. 
       2. Any files that have been modified in both the current and
             given branch in the same way are left unchanged by the
             merge. If a file was removed from both the current and given
             branch, but a file of the same name is present in the working
             directory, it is left alone and continues to be absent in the merge.
       3. Any files that were not present at the split point and are present
       only in the current branch remain as they are.
       4. Any files that were not present at the split point and
       are present only in the given branch are checked out and staged.
       5. Any files present at the split point, unmodified in the current branch,
       and absent in the given branch are removed (and untracked).
       6. Any files present at the split point, unmodified in the given branch,
       and absent in the current branch remain absent.
       7. Any files modified in different ways in the current and given branches
       are in conflict. In this case, contents of conflicted file replaced with:
 
```
<<<<<<< HEAD
contents of file in current branch
=======
contents of file in given branch
>>>>>>>
```

## 3. Persistence

### java gitlet.Main init
Create `.gitlet` directory. Creates directories and files to persist state of the program
including, `.gitlet/commits`, `.gitlet/branches`, `gitlet/staging/`, `.gitlet/branches/master`, `.gitlet/head`, `.gitlet/staging/add`, `.git/staging/rm`.
An initial commit object is created. It is stored in a file in `.gitlet/commit`. A branch file exists by default called 'master' with the hash of the initial commit written in the file. 
A file called HEAD holds the file address to the current branch. Empty TreeMap objects are serialized and written into the staging area files. 

### java gitlet.Main add [file name]
Files added are sent to the staging area for files that are staged to be added to the commit represented by a treemap in `TreeMap<String, Blob> addStagingMap`. 
The key of the TreeMap is a SHA1 hashcode of the blob object. The TreeMap is then serialized and written into file `.gitlet/add`. The file saves the state
of the staging area to be read and accessed in other commands. 

### java gitlet.Main rm [file name]
Files removed are sent to the staging area for files that are staged to be added to the commit represented by a treemap in `TreeMap<String, Blob> rmStagingMap`.
The key of the TreeMap is a SHA1 hashcode of the blob object returned from the Utils class. The TreeMap is then serialized and written into file `.gitlet/rm`. The file saves the state
of the staging area to be read and accessed in other commands.

### java gitlet.Main commit [message]
New commit object is created which is a copy of its parent initially but with a different timestamp and associated message. The staging files are read back into their appropriate TreeMap objects. Changes from staging area will 
be reflected in `Treemap<String, blob> _files` in the new commit object. Commit object will then be serialized and written into a 
new file in `.gitlet/commits/[hash]`. The file is named after a SHA1 hashcode of the serialized commit object. `.gitlet/commits` is a 
directory that holds files of all commits made in the repository since initialization of the `.gitlet` repo. HEAD branch will be updated to most recent commit. The staging area is cleared
and written back into the staging files as empty objects. Future commands will access commits by the hash of the commit which is the same as the file name that the commit is stored in. Commits contain the hash of their parent commit which 
can be used to traverse the graph-like organization that commit histories can form. 

### java gitlet.Main branch [branch name]
A new branch file is created, `.gitlet/branch/[branch name]` which stores the commit hash of the current commit. The branch only updated to the new commit
if it is the HEAD branch, otherwise it continued to point to the same commit. gitlet. `gitlet.checkout()` command can be used to change which branch is 
considered the HEAD. Checking out to a previous branch and making commits leads to a forking of the structure. 

### java gitlet.Main merge [branch name]
Logic in the merge command decides how current and given branch is resolved into a new commit. Files modified in different ways
are handled through writing the contents of both files into a new file of the same name with structure dividing the contents. The user 
must resolve these differences and commit the new resolution. 

## 4. Differences from git

### java gitlet.Main find [commit message]
Doesn't exist in real git. Similar effects can be achieved by grepping the output of log.

### java gitlet.Main checkout [input]
Real git does not clear the staging area and stages the file that is checked out. Also, it won't do a checkout that would overwrite or undo changes (additions or removals) that are staged.

### java gitlet.Main reset [commit ID]
This command is closest to using the `--hard` option, as in `git reset --hard [commit hash]`.

### java gitlet.Main merge [branch name]
Real Git does a more subtle job of merging files, displaying conflicts only in places where both files have changed since the split point.
Real Git has a different way to decide which of multiple possible split points to use.
Real Git will force the user to resolve the merge conflicts before committing to complete the merge. Gitlet just commits the merge, conflicts and all, so that you must use a separate commit to resolve problems.
Real Git will complain if there are unstaged changes to a file that would be changed by a merge.
