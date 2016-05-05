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

	static int depthCounter = 0;		//Used for nested loop alignment
	
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
	static Pattern ACTION = Pattern.compile("move|turnL|turnR|turnAround|shieldOn|shieldOff|takeFuel|wait");
	static Pattern SENSOR = Pattern.compile("fuelLeft|oppLR|oppFB|numBarrels|barrelLR|barrelFB|wallDist");

	/**
	 * PROG ::= STMT+
	 */
	static RobotProgramNode parseProgram(Scanner s) {
		// THE PARSER GOES HERE
		
		ProgramNode main = new ProgramNode();
		
		while(s.hasNext())
			main.getStatements().add(parseStatementNode(s));

		return main;
	}

	// utility methods for the parser

	/**
	 * STMT ::= ACT ; | LOOP
	 * */
	static StatementNode parseStatementNode(Scanner s){

		StatementNode stmt = null;

		if(s.hasNext(ACTION))				stmt = parseAction(s);
		else if(checkFor("loop", s)) 		stmt = parseLoop(s);
		else if(checkFor("while", s))		stmt = parseWhile(s);
		else if(checkFor("if", s))			stmt = parseIf(s);
		else{
			fail("Expecting ACT|LOOP|WHILE|IF", s);
		}

		return stmt;
	}

	/**
	 * ACT ::= move|turnL|turnR|turnAround|shieldOn|shieldOff|takeFuel|wait
	 * */
	static ActionNode parseAction(Scanner s){

		ActionNode act = null;

		if(checkFor("move", s))					act = new MoveNode();
		else if(checkFor("turnL", s))			act = new TurnLNode();
		else if(checkFor("turnR", s))			act = new TurnRNode();
		else if(checkFor("turnAround", s))		act = new TurnAroundNode();
		else if(checkFor("shieldOn", s))		act = new ShieldOnNode();
		else if(checkFor("shieldOff", s))		act = new ShielfOffNode();
		else if(checkFor("takeFuel", s))		act = new TakeFuelNode();
		else if(checkFor("wait", s))			act = new WaitNode();
	

		require(";", "Expecting ';' ", s);
		
		return act;
	}

	/**
	 * LOOP ::= loop BLOCK
	 * */
	static LoopNode parseLoop(Scanner s){
		return new LoopNode(parseBlock(s));
	}
	
	/**
	 * IF ::= if (COND) BLOCK
	 * */
	static IFNode parseIf(Scanner s){
		return new IFNode(parseCondition(s), parseBlock(s));
	}
	
	/**
	 * WHILE ::= while (COND) BLOCK
	 * */
	static WhileNode parseWhile(Scanner s){
		return new WhileNode(parseCondition(s), parseBlock(s));
	}
	
	/**
	 * COND ::= lt (SEN, NUM) | gt(SEN, NUM) | eq(SEN, NUM)
	 * */
	static ConditionalNode parseCondition(Scanner s){
		
		ConditionalNode condition = null;
		require(OPENPAREN, "Expecting '(' ", s);
		if(checkFor("gt", s))			condition = new GreaterThanNode(parseSensor(s), parseNumber(s));
		else if(checkFor("lt", s))		condition = new LessThanNode(parseSensor(s), parseNumber(s));
		else if(checkFor("eq", s))		condition = new EqualsNode(parseSensor(s), parseNumber(s));
//		else
//			fail("Expecting gt|lt|eq", s);
		require(CLOSEPAREN, "Expecting ')' ", s);
		
		return condition;
	}
	
	/**
	 * SEN ::= fuelLeft|oppLR|oppFB|numBarrels|barrelLR|barrelFB|wallDist
	 * */
	static SensorNode parseSensor(Scanner s){
		
		SensorNode sensor = null;
		require(OPENPAREN, "Expecting '('", s);
		//TODO: EDIT CHECKS!!
		if(checkFor("fuelLeft", s))					sensor = new FuelLeftNode();
		else if(checkFor("oppLR", s))				sensor = new OppLRNode();
		else if(checkFor("oppFB", s))				sensor = new OppFBNode();
		else if(checkFor("numBarrels", s))			sensor = new NumBarrelsNode();
		else if(checkFor("barrelFB", s))			sensor = new BarrelFBNode();
		else if(checkFor("barrelLR", s))			sensor = new BarrelLRNode();
		else if(checkFor("wallDist", s))			sensor = new WallDistNode();
//		else
//			fail("Expecting fuelLeft|oppLR|oppFB|numBarrels|barrelLR|barrelFB|wallDist", s);
		require(",", "Expecting ','", s);
		
		return sensor;
	}
	
	/**
	 * NUM ::= "-?[0-9]+"
	 * */
	static NumberNode parseNumber(Scanner s){
		
		int num = Integer.parseInt(require(NUMPAT, "Expecting [0-9]", s));
		NumberNode numberNode = new NumberNode(num);
		require(CLOSEPAREN, "Expecting ')' ",s);
		
		return numberNode;
	}

	/**
	 * BLOCK :: = { STMT+ }
	 * */
	static BlockNode parseBlock(Scanner s){

		BlockNode block = new BlockNode();
		
		
		require(OPENBRACE, "Expecting '{'", s);
		while(!s.hasNext(CLOSEBRACE) && s.hasNextLine())
			block.getStatements().add(parseStatementNode(s));
		
		if(block.getStatements().size() == 0)
			fail("Empty BLOCK - Requires 1 or more Statements", s);
		
		require(CLOSEBRACE, "Expecting '}' ", s);
		

		return block;
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


//==================================================================//
//============================ACTION===============================//
//=================================================================//
class MoveNode implements ActionNode{

	int moveSteps = 0;

	@Override
	public void execute(Robot robot) {
		robot.move();
	}

	public String toString(){
		return "Move;";
	}

}

class TurnLNode implements ActionNode{

	@Override
	public void execute(Robot robot) {
		robot.turnLeft();
	}

	public String toString(){
		return "TurnL;";
	}


}

class TurnRNode implements ActionNode{


	@Override
	public void execute(Robot robot) {
		robot.turnRight();
	}

	public String toString(){
		return "TurnR;";
	}

}
class TakeFuelNode implements ActionNode{

	@Override
	public void execute(Robot robot) {
		robot.takeFuel();
	}

	public String toString(){
		return "TakeFuel;";
	}

}
class WaitNode implements ActionNode{

	int waitAmount = 0;

	@Override
	public void execute(Robot robot) {
		robot.idleWait();
	}

	public String toString(){
		return "Wait;";
	}

}

class TurnAroundNode implements ActionNode{

	@Override
	public void execute(Robot robot) {
		robot.turnAround();
	}
	
	public String toString(){
		return "TurnAround;";
	}
	
}

class ShieldOnNode implements ActionNode{

	@Override
	public void execute(Robot robot) {
		robot.setShield(true);
	}
	
	public String toString(){
		return "ShieldOn;";
	}
	
}

class ShielfOffNode implements ActionNode{

	@Override
	public void execute(Robot robot) {
		robot.setShield(false);
	}
	
	public String toString(){
		return "ShieldOff;";
	}
	
}



//==================================================================//
//============================LOOP===============================//
//=================================================================//

class LoopNode implements StatementNode{

	BlockNode block;

	public LoopNode(BlockNode b){
		this.block = b;
	}

	@Override
	public void execute(Robot robot) {
		while(true)
			block.execute(robot);
	}

	public String toString(){
		return "loop \n" + this.block;
	}

}

//==================================================================//
//=========================CONDITIONALS=============================//
//=================================================================//
class IFNode implements StatementNode{
	
	ConditionalNode condition;
	BlockNode block;
	
	public IFNode(ConditionalNode c, BlockNode b){
		this.condition = c;
		this.block = b;
	}
	
	@Override
	public void execute(Robot robot) {

		if(condition.evaluate(robot))
			block.execute(robot);
	}
	
	public String toString(){
		return "if ("+this.condition +" )"+ this.block;
	}
	
	
}

class WhileNode implements StatementNode{
	
	
	ConditionalNode condition;
	BlockNode block;
	
	public WhileNode(ConditionalNode c, BlockNode b){
		this.condition = c;
		this.block = b;
	}
	
	@Override
	public void execute(Robot robot) {
		if(condition.evaluate(robot))
			block.execute(robot);
	}
	
	public String toString(){
		return "while ( "+this.condition + " )" +this.block;
	}
	
	
}

interface ConditionalNode{
	public boolean evaluate(Robot robot);
}

class GreaterThanNode implements ConditionalNode{
	
	SensorNode sen;
	NumberNode num;
	
	public GreaterThanNode(SensorNode sen, NumberNode num){
		this.sen = sen;
		this.num = num;
	}
	
	@Override
	public boolean evaluate(Robot robot) {

		boolean bool = false;
		if(sen.evaluate(robot) > num.getNum())
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "gt( "+sen.toString()+" , "+ num.getNum() + " )";
	}
}
class LessThanNode implements ConditionalNode{
	
	SensorNode sen;
	NumberNode num;
	
	public LessThanNode(SensorNode sen, NumberNode num){
		this.sen = sen;
		this.num = num;
	}	
	
	@Override
	public boolean evaluate(Robot robot) {
		
		boolean bool = false;
		if(sen.evaluate(robot) < num.getNum())
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "lt( "+sen.toString()+" , "+ num.getNum() + " )";
	}
}
class EqualsNode implements ConditionalNode{
	
	SensorNode sen;
	NumberNode num;
	
	public EqualsNode(SensorNode sen, NumberNode num){
		this.sen = sen;
		this.num = num;
	}
	
	@Override
	public boolean evaluate(Robot robot) {
		
		boolean bool = false;
		if(sen.evaluate(robot) == num.getNum())
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "eq( "+sen.toString()+" , "+ num.getNum() + " )";
	}
}

//==================================================================//
//============================BLOCK===============================//
//=================================================================//


class BlockNode implements StatementNode{

	List<StatementNode> statements;		//STMT -- LIST OF STATEMENTS
	int tabCounter = 0;
	
	public BlockNode(){
		this.statements = new ArrayList<StatementNode>();
	}


	@Override
	public void execute(Robot robot) {
		for(StatementNode sn: statements)	
			sn.execute(robot);
	}
	
	

	public List<StatementNode> getStatements() {
		return statements;
	}


	public String toString(){
		
		StringBuilder sb = new StringBuilder();
		
		//HOW MUCH TO TAB BY BASED ON HOW NESTED THE BLOCK IS??
		// - Global Depth Counter , Start at 1 - if its a block should be tabbed by 1
		// - When new Block is created increment by 1, set its tabCounter
		// - When Block is finished reset to 0
		// - How to tab x amount of times	repeated = new String(new char[n]).replace("\0", s);
		// - n = number of times to repeat, s = string to repeat
		// - i.e. String tabRepeat = new String(new char[n]).replace("\0", "\t");
		// n = globalDepthCount;
		
		String tabRepeat = null;
		
		Parser.depthCounter++;
		sb.append( "{ \n" );

		for(StatementNode sn: statements){
			tabRepeat = new String(new char[Parser.depthCounter]).replace("\0", "\t");
			sb.append(tabRepeat+sn+"\n");
		}
		Parser.depthCounter--;
		tabRepeat = new String(new char[Parser.depthCounter]).replace("\0", "\t");
		sb.append( tabRepeat+"}" );
		
		return sb.toString();
	}



}

//==================================================================//
//=========================SENSOR + NUM=============================//
//=================================================================//
interface SensorNode{	
	public int evaluate(Robot robot);
}

class FuelLeftNode implements SensorNode{

	@Override
	public int evaluate(Robot robot) {
		return robot.getFuel();
	}
	
	public String toString(){
		return "FuelLeft";
	}
}
class OppLRNode implements SensorNode{

	@Override
	public int evaluate(Robot robot) {
		return robot.getOpponentLR();
	}
	
	public String toString(){
		return "OppLR";
	}
}
class OppFBNode implements SensorNode{

	@Override
	public int evaluate(Robot robot) {
		return robot.getOpponentFB();
	}
	
	public String toString(){
		return "OppFB";
	}
}
class NumBarrelsNode implements SensorNode{

	@Override
	public int evaluate(Robot robot) {
		return robot.numBarrels();
	}
	
	public String toString(){
		return "NumBarrels";
	}
}
class BarrelLRNode implements SensorNode{

	@Override
	public int evaluate(Robot robot) {
		int n = robot.getClosestBarrelLR();
		return robot.getBarrelLR(n);
	}
	
	public String toString(){
		return "BarrelLR";
	}
}
class BarrelFBNode implements SensorNode{

	@Override
	public int evaluate(Robot robot) {
		int n = robot.getClosestBarrelFB();
		return robot.getBarrelFB(n);
	}
	
	public String toString(){
		return "BarrelFB";
	}
}
class WallDistNode implements SensorNode{

	@Override
	public int evaluate(Robot robot) {
		return robot.getDistanceToWall();
	}
	
	public String toString(){
		return "WallDist";
	}
}
class NumberNode{
	
	int num;
	
	public NumberNode(int num){
		this.num = num;
	}
	
	public int getNum(){
		return num;
	}
}

//==================================================================//
//============================PROGRAM===============================//
//=================================================================//

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
			sb.append(sn+"\n");
		
		return sb.toString();
	}
}



//Empty Interfaces - SubTyping/Polymorphism
interface StatementNode extends RobotProgramNode{}
interface ActionNode extends StatementNode{}



