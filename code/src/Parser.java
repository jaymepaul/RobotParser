import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.regex.*;
import javax.swing.JFileChooser;

import sun.util.locale.ParseStatus;

/**
 * The parser and interpreter. The top level parse function, a main method for
 * testing, and several utility methods are provided. You need to implement
 * parseProgram and all the rest of the parser.
 */
public class Parser {

	/**
	 * Top level parse method, called by the World
	 */
	static RobotProgramNode parseFile(File code) {
		Scanner scan = null;
		try {
			scan = new Scanner(code);

			// the only time tokens can be next to each other is
			// when one of them is one of (){},;
			scan.useDelimiter("\\s+|(?=[{}(),;])|(?<=[{}(),;])");

			RobotProgramNode n = parseProgram(scan); // You need to implement this!!!

			scan.close();
			return n;
		} catch (FileNotFoundException e) {
			System.out.println("Robot program source file not found");
		} catch (ParserFailureException e) {
			System.out.println("Parser error:");
			System.out.println(e.getMessage());
			scan.close();
		}
		return null;
	}

	/** For testing the parser without requiring the world */

	public static void main(String[] args) {
		if (args.length > 0) {
			for (String arg : args) {
				File f = new File(arg);
				if (f.exists()) {
					System.out.println("Parsing '" + f + "'");
					RobotProgramNode prog = parseFile(f);
					System.out.println("Parsing completed ");
					if (prog != null) {
						System.out.println("================\nProgram:");
						System.out.println(prog);
					}
					System.out.println("=================");
				} else {
					System.out.println("Can't find file '" + f + "'");
				}
			}
		} else {
			while (true) {
				JFileChooser chooser = new JFileChooser(".");// System.getProperty("user.dir"));
				int res = chooser.showOpenDialog(null);
				if (res != JFileChooser.APPROVE_OPTION) {
					break;
				}
				RobotProgramNode prog = parseFile(chooser.getSelectedFile());
				System.out.println("Parsing completed");
				if (prog != null) {
					System.out.println("Program: \n" + prog);
				}
				System.out.println("=================");
			}
		}
		System.out.println("Done");
	}

	// Useful Patterns

	static Pattern NUMPAT = Pattern.compile("-?\\d+"); // ("-?(0|[1-9][0-9]*)");
	static Pattern OPENPAREN = Pattern.compile("\\(");
	static Pattern CLOSEPAREN = Pattern.compile("\\)");
	static Pattern OPENBRACE = Pattern.compile("\\{");
	static Pattern CLOSEBRACE = Pattern.compile("\\}");
	static Pattern ACTION = Pattern.compile(":? (move|turnL|turnR|takeFuel|wait)");

	/**
	 * PROG ::= STMT+
	 */
	static RobotProgramNode parseProgram(Scanner s) {
		// THE PARSER GOES HERE
		return parseStatementNode(s);
	}

	// utility methods for the parser

	/**
	 * STMT ::= ACT ; | LOOP
	 * */
	static StatementNode parseStatementNode(Scanner s){

		RobotProgramNode type = null;

		if(s.hasNext(ACTION))			type = parseAction(s);
		if(s.hasNext("loop"))			type = parseLoop(s);

		return new StatementNode(type);
	}

	/**
	 * ACT ::= move|turnL|turnR|takeFuel|move
	 * */
	static ActionNode parseAction(Scanner s){

		RobotProgramNode type = null;

		if(checkFor("move", s))			type = new MoveNode();
		if(checkFor("turnL", s))		type = new TurnLNode();
		if(checkFor("turnR", s))		type = new TurnRNode();
		if(checkFor("takeFuel", s))		type = new TakeFuelNode();
		if(checkFor("wait", s))			type = new WaitNode();

		return new ActionNode(type);
	}

	/**
	 * LOOP ::= loop BLOCK
	 * */
	static LoopNode parseLoop(Scanner s){

		require("loop", "Expecting 'loop'", s);

		return new LoopNode(parseBlock(s));
	}

	/**
	 * BLOCK :: = { STMT+ }
	 * */
	static BlockNode parseBlock(Scanner s){

		StatementNode stmt = null;

		require("{", "Expecting '{'", s);
		while(!s.hasNext("}")){
			stmt = parseStatementNode(s);
		}
		require("}", "Expecting '}' ", s);

		return new BlockNode(stmt);
	}


	/**
	 * Report a failure in the parser.
	 */
	static void fail(String message, Scanner s) {
		String msg = message + "\n   @ ...";
		for (int i = 0; i < 5 && s.hasNext(); i++) {
			msg += " " + s.next();
		}
		throw new ParserFailureException(msg + "...");
	}

	/**
	 * Requires that the next token matches a pattern if it matches, it consumes
	 * and returns the token, if not, it throws an exception with an error
	 * message
	 */
	static String require(String p, String message, Scanner s) {
		if (s.hasNext(p)) {
			return s.next();
		}
		fail(message, s);
		return null;
	}

	static String require(Pattern p, String message, Scanner s) {
		if (s.hasNext(p)) {
			return s.next();
		}
		fail(message, s);
		return null;
	}

	/**
	 * Requires that the next token matches a pattern (which should only match a
	 * number) if it matches, it consumes and returns the token as an integer if
	 * not, it throws an exception with an error message
	 */
	static int requireInt(String p, String message, Scanner s) {
		if (s.hasNext(p) && s.hasNextInt()) {
			return s.nextInt();
		}
		fail(message, s);
		return -1;
	}

	static int requireInt(Pattern p, String message, Scanner s) {
		if (s.hasNext(p) && s.hasNextInt()) {
			return s.nextInt();
		}
		fail(message, s);
		return -1;
	}

	/**
	 * Checks whether the next tokenthis.node = statementNode in the scanner matches the specified
	 * pattern, if so, consumes the token and return true. Otherwise returns
	 * false without consuming anything.
	 */
	static boolean checkFor(String p, Scanner s) {
		if (s.hasNext(p)) {
			s.next();
			return true;
		} else {
			return false;
		}
	}

	static boolean checkFor(Pattern p, Scanner s) {
		if (s.hasNext(p)) {
			s.next();
			return true;
		} else {
			return false;
		}
	}

}

// You could add the node classes here, as long as they are not declared public (or private)
enum ACTION{move,turnL,turnR,takeFuel,wait,loop}


class MoveNode implements RobotProgramNode{




	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.move();
	}

}

class TurnLNode implements RobotProgramNode{



	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.turnLeft();
	}

}

class TurnRNode implements RobotProgramNode{


	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.turnRight();
	}

}
class TakeFuelNode implements RobotProgramNode{



	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.takeFuel();
	}

}
class WaitNode implements RobotProgramNode{



	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.idleWait();
	}

}

class ActionNode implements RobotProgramNode{

	RobotProgramNode n;

	public ActionNode(RobotProgramNode n){
		this.n = n;
	}

	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		n.execute(robot);
	}

}

class LoopNode implements RobotProgramNode{

	BlockNode n;

	public LoopNode(BlockNode b){
		this.n = b;
	}

	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		n.execute(robot);
	}

}
class BlockNode implements RobotProgramNode{

	StatementNode n;			//STMT

	public BlockNode(StatementNode n){
		this.n = n;
	}


	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		n.execute(robot);
	}
}

class StatementNode implements RobotProgramNode{


	RobotProgramNode n;				//ACT/LOOP

	public StatementNode(RobotProgramNode n){
		this.n = n;
	}

	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub

	}




}

class ProgramNode implements RobotProgramNode{

	RobotProgramNode n;				//STMT

	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub

	}


}




