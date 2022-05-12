package gitlet;

import java.io.File;
import java.io.IOException;


/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Yusuf Quddus
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String[] args) throws IOException {
        checkNoInput(args);
        switch (args[0]) {
        case "init":
            checkInputInit(args);
            GITLET.init();
            break;
        case "add":
            checkInput(args.length, 2);
            GITLET.add(args[1]);
            break;
        case "rm":
            checkInput(args.length, 2);
            GITLET.rm(args[1]);
            break;
        case "commit":
            checkInputCommit(args);
            GITLET.commit(args[1]);
            break;
        case "log":
            checkInput(args.length, 1);
            GITLET.log();
            break;
        case "global-log":
            checkInput(args.length, 1);
            GITLET.globalLog();
            break;
        case "status":
            checkInput(args.length, 1);
            GITLET.status();
            break;
        case "find":
            checkInput(args.length, 2);
            GITLET.find(args[1]);
            break;
        case "checkout":
            checkInputCheckout(args);
            break;
        case "branch":
            checkInput(args.length, 2);
            GITLET.branch(args[1]);
            break;
        case "rm-branch":
            checkInput(args.length, 2);
            GITLET.rmBranch(args[1]);
            break;
        case "reset":
            checkInput(args.length, 2);
            GITLET.reset(args[1]);
            break;
        case "merge":
            checkInput(args.length, 2);
            GITLET.merge(args[1]);
            break;
        default:
            exitWithError("No command with that name exists.");
        }
        return;
    }


    /**
     * Prints out MESSAGE and exits with code 0.
     *
     * @param message error message to print
     */
    public static void exitWithError(String message) {
        if (message != null && !message.equals("")) {
            System.out.println(message);
        }
        System.exit(0);
    }


    /**
     * Checks if there was an input.
     *
     * @param args arguments into the program.
     */
    private static void checkNoInput(String[] args) {
        if (args.length == 0) {
            exitWithError("Please enter a command.");
        }
    }

    /**
     * Checks if a gitlet repository has been initialized. Checks input
     * to the program and confirms if it has the proper number of operands.
     *
     * @param inputCount count of input operand
     * @param correctInputCount correct number of operands
     */
    private static void checkInput(int inputCount, int correctInputCount) {
        if (!GITLET_DIR.exists()) {
            exitWithError("Not in an initialized Gitlet directory.");
        } else if (inputCount != correctInputCount) {
            exitWithError("Incorrect operands.");
        }
    }


    /**
     * Checks if a gitlet repository has been initialized. Checks input
     * to the program and confirms if it has the proper number of operands.
     *
     * @param args arguments into the program.
     */
    private static void checkInputInit(String[] args) {
        if (GITLET_DIR.exists()) {
            exitWithError("Gitlet version-control system already exists"
                    + " in the current directory.");
        } else if (args.length != 1) {
            exitWithError("Incorrect operands.");
        }
    }


    /**
     * Checks input for commit command.
     *
     * @param args arguments into the program.
     */
    private static void checkInputCommit(String[] args) {
        checkInput(args.length, 2);
        if (args[1].isBlank()) {
            exitWithError("Please enter a commit message.");
        }
    }


    /**
     * Checks input for checkout command.
     *
     * @param args arguments into the program.
     * @throws IOException
     */
    private static void checkInputCheckout(String[] args) throws IOException {
        if (!GITLET_DIR.exists()) {
            exitWithError("Not in an initialized Gitlet directory.");
        } else {
            if (args.length == 3 && args[1].equals("--")) {
                GITLET.checkout("", "", args[2]);
            } else if (args.length == 4 && args[2].equals("--")) {
                GITLET.checkout("", args[1], args[3]);
            } else if (args.length == 2) {
                GITLET.checkout(args[1], "", "");
            } else {
                exitWithError("Incorrect operands.");
            }
        }
    }

    /** Current Working Directory. */
    static final File CWD = new File(System.getProperty("user.dir"));

    /** Gitlet repository. */
    static final File GITLET_DIR = Utils.join(CWD, ".gitlet");

    /** Gitlet object holds gitlet command logic and execution. */
    static final Gitlet GITLET = new Gitlet();

}
