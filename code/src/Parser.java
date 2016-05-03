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
	static Pattern ACTION = Pattern.compile("move|turnL|turnR|takeFuel|wait");

	/**
	 * PROG ::= STMT+
	 */
	static RobotProgramNode parseProgram(Scanner s) {
		// THE PARSER GOES HERE
		
		ProgramNode main = new ProgramNode();
		
		//How to insert all statements in ONE NODE???
		while(s.hasNext())
			main.getStatements().add(parseStatementNode(s));

		return main;
	}

	// utility methods for the parser

	/**
	 * STMT ::= ACT ; | LOOP
	 * */
	static StatementNode parseStatementNode(Scanner s){

		RobotProgramNode type = null;

		if(s.hasNext(ACTION)){
			type = parseAction(s);
			require(";", "Expecting ';' ", s);
		}
		else if(s.hasNext("loop")) {
			type = parseLoop(s);
		}
		else{
			throw new ParserFailureException("Statement unable to be parsed - next token is: " + s.next());
		}

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


	int moveSteps = 0;


	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.move();
	}

	public String toString(){
		return "Move";
	}

}

class TurnLNode implements RobotProgramNode{



	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.turnLeft();
	}

	public String toString(){
		return "TurnL ";
	}


}

class TurnRNode implements RobotProgramNode{


	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.turnRight();
	}

	public String toString(){
		return "TurnR";
	}

}
class TakeFuelNode implements RobotProgramNode{



	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.takeFuel();
	}

	public String toString(){
		return "TakeFuel";
	}

}
class WaitNode implements RobotProgramNode{

	int waitAmount = 0;

	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		robot.idleWait();
	}

	public String toString(){
		return "Wait";
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
		n.execute(robot);						//Call execute on ActionObject (move,turnR,L etc)
	}

	public String toString(){
		return "ACT "+this.n +"\n";
	}
}

class LoopNode implements RobotProgramNode{

	BlockNode block;

	public LoopNode(BlockNode b){
		this.block = b;
	}

	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		while(true)
			block.execute(robot);
	}

	public String toString(){
		return "LOOP " + this.block +"\n";
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

	public String toString(){
		return "BLOCK " + this.n+"\n";
	}
}


//NOTE: interface StatementNode extends RobotProgramNode
// ActionNode implements StatementNode 		i.e. STMT = ACT 
// LoopNode implements StatementNode 		i.e. STMT = LOOP
// NON-TERMINALS AS INTERFACES, never going to be making one of that class

class StatementNode implements RobotProgramNode{


	RobotProgramNode n;				//ACT/LOOP

	public StatementNode(RobotProgramNode n){

		this.n = n;

	}

	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub

	}

	public String toString(){

		return "STMT " + this.n +"\n";
	}



}

class ProgramNode implements RobotProgramNode{

	
	List<StatementNode> statements;
	
	public ProgramNode(){
		this.statements = new ArrayList<StatementNode>();
	}

	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub

	}
	
	

	public List<StatementNode> getStatements() {
		return statements;
	}

	public String toString(){
		StringBuilder sb = new StringBuilder();
		
		for(StatementNode sn: statements)
			sb.append("PROG "+sn.n+"\n");
		
		return sb.toString();
	}
}




