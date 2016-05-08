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

	//Used for nested loop alignment
	static int depthCounter = 0;
	//A map of all variables
	static Map<VariableNode, ExpressionNode> variablesMap = new HashMap<VariableNode, ExpressionNode>();
	
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
	static Pattern OPERATORS = Pattern.compile("add|sub|mul|div");
	static Pattern VARIABLES = Pattern.compile("\\$[A-Za-z][A-Za-z0-9]*");
	
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
	 * STMT ::= ACT ; | LOOP | IF | WHILE | ASSGN ; 
	 * */
	static StatementNode parseStatementNode(Scanner s){

		StatementNode stmt = null;

		if(s.hasNext(ACTION))				stmt = parseAction(s);
		else if(checkFor("loop", s)) 		stmt = parseLoop(s);
		else if(checkFor("while", s))		stmt = parseWhile(s);
		else if(checkFor("if", s))			stmt = parseIf(s);
		else if(s.hasNext(VARIABLES))		stmt = parseAssignmentNode(s);
		else{
			fail("Expecting ACT|LOOP|WHILE|IF|VAR", s);
		}

		return stmt;
	}
	
	/**
	 * ASSGN ::= VAR = EXP
	 * */
	static AssignmentNode parseAssignmentNode(Scanner s){
		
		VariableNode var = parseVar(s);
		require("=", "Expecting '='", s);
		ExpressionNode exp = parseEXP(s);
		require(";", "Expecting ';' ", s);
		
		//Add variable to global map of variables
		variablesMap.put(var, exp);
		
		return new AssignmentNode(var, exp);
	}
	
	static VariableNode parseVar(Scanner s){
		return new VariableNode(require(VARIABLES, "Expecting \\$[A-Za-z][A-Za-z0-9]*", s));
	}

	/**
	 * ACT ::= move|turnL|turnR|turnAround|shieldOn|shieldOff|takeFuel|wait
	 * */
	static ActionNode parseAction(Scanner s){

		ActionNode act = null;

		if(checkFor("move", s)){
			
			if(checkFor(OPENPAREN, s)){
				act = new MoveNode(parseEXP(s));
				require(CLOSEPAREN, "Expecting ')' ",s);
			}
			else
				act = new MoveNode();
			
		}					
		else if(checkFor("turnL", s))			act = new TurnLNode();
		else if(checkFor("turnR", s))			act = new TurnRNode();
		else if(checkFor("turnAround", s))		act = new TurnAroundNode();
		else if(checkFor("shieldOn", s))		act = new ShieldOnNode();
		else if(checkFor("shieldOff", s))		act = new ShielfOffNode();
		else if(checkFor("takeFuel", s))		act = new TakeFuelNode();
		else if(checkFor("wait", s)){
			
			if(checkFor(OPENPAREN, s)){
				act = new WaitNode(parseEXP(s));
				require(CLOSEPAREN, "Expecting ')' ",s);
			}
			else
				act = new WaitNode();
		}			
	

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
	 * IF ::= if ( COND ) BLOCK [ else BLOCK ]
	 * */
	static IFNode parseIf(Scanner s){
		
		
		require(OPENPAREN, "Expecting '('", s);
		ConditionalNode condition = parseCondition(s);
		require(CLOSEPAREN, "Expecting ')'", s);
		BlockNode block = parseBlock(s);
		
		IFNode iN = new IFNode(condition, block);
		
		//Add elif blocks to list of elif's to process
		while(checkFor("elif", s)){
			require(OPENPAREN, "Expecting '('", s);
			ConditionalNode c = parseCondition(s);
			require(CLOSEPAREN, "Expecting ')'", s);
			BlockNode b = parseBlock(s);
			iN.elifBlocks.put(c, b);
		}	
		if(checkFor("else", s))		
			iN.setElseBlock(parseBlock(s));
		
		return iN;
	}
	
	/**
	 * WHILE ::= while (COND) BLOCK
	 * */
	static WhileNode parseWhile(Scanner s){
		
		require(OPENPAREN, "Expecting '(' ", s);
		ConditionalNode condition = parseCondition(s);
		require(CLOSEPAREN, "Expecting ')'", s);
		BlockNode block = parseBlock(s);
		
		return new WhileNode(condition, block);
	}
	
	/**
	 * COND ::= and ( COND, COND ) | or ( COND, COND ) | not ( COND ) | lt ( EXP, EXP )  |
	  gt ( EXP, EXP )  | eq ( EXP, EXP )
	 * */
	static ConditionalNode parseCondition(Scanner s){
		
		ConditionalNode condition = null;
		
		if(checkFor("and", s)){
			//NEED CHECKS:
			require(OPENPAREN, "Expecting '('", s);
			ConditionalNode c1 = parseCondition(s);
			require(",", "Expecting ','", s);
			ConditionalNode c2 = parseCondition(s);
			require(CLOSEPAREN, "Expecting ')'", s);
			condition = new AndNode(c1,c2);
		}				
		else if(checkFor("or", s)){
			require(OPENPAREN, "Expecting '('", s);
			ConditionalNode c1 = parseCondition(s);
			require(",", "Expecting ','", s);
			ConditionalNode c2 = parseCondition(s);
			require(CLOSEPAREN, "Expecting ')' ", s);
			condition = new OrNode(c1, c2);
		}			
		else if(checkFor("not", s)){
			require(OPENPAREN, "Expecting '('", s);
			ConditionalNode c = parseCondition(s);
			require(CLOSEPAREN, "Expecting ')' ", s);
			condition = new NotNode(c);
		}			
		else if(checkFor("gt", s)){
			require(OPENPAREN, "Expecting '('", s);
			ExpressionNode e1 = parseEXP(s);
			require(",", "Expecting ','", s);
			ExpressionNode e2 = parseEXP(s);
			require(CLOSEPAREN, "Expecting ')' ", s);
			condition = new GreaterThanNode(e1, e2);
		}			
		else if(checkFor("lt", s)){
			require(OPENPAREN, "Expecting '('", s);
			ExpressionNode e1 = parseEXP(s);
			require(",", "Expecting ','", s);
			ExpressionNode e2 = parseEXP(s);
			require(CLOSEPAREN, "Expecting ')' ", s);
			condition = new LessThanNode(e1, e2);
		}		
		else if(checkFor("eq", s)){
			require(OPENPAREN, "Expecting '('", s);
			ExpressionNode e1 = parseEXP(s);
			require(",", "Expecting ','", s);
			ExpressionNode e2 = parseEXP(s);
			require(CLOSEPAREN, "Expecting ')' ", s);
			condition = new EqualsNode(e1, e2);
		}
		else{
			fail("Expecting a COND ::= and|or|not|gt|lt|eq", s);
		}
		
		
		
		return condition;
	}
	
	/**
	 * SEN ::= fuelLeft|oppLR|oppFB|numBarrels|barrelLR [( EXP )] | barrelFB [ ( EXP ) ]|wallDist
	 * */
	static SensorNode parseSensor(Scanner s){
		
		SensorNode sensor = null;

		if(checkFor("fuelLeft", s))					sensor = new FuelLeftNode();
		else if(checkFor("oppLR", s))				sensor = new OppLRNode();
		else if(checkFor("oppFB", s))				sensor = new OppFBNode();
		else if(checkFor("numBarrels", s))			sensor = new NumBarrelsNode();
		else if(checkFor("barrelFB", s)){
			
			if(checkFor(OPENPAREN, s)){
				sensor = new BarrelFBNode(parseEXP(s));
				require(CLOSEPAREN, "Expecting ')' ", s);
			}else
				sensor = new BarrelFBNode();
		}	
		else if(checkFor("barrelLR", s)){
			
			if(checkFor(OPENPAREN, s)){
				sensor = new BarrelLRNode(parseEXP(s));
				require(CLOSEPAREN, "Expecting ')' ", s);
			}else
				sensor = new BarrelLRNode();
		}			
		else if(checkFor("wallDist", s))			sensor = new WallDistNode();


		return sensor;
	}
	
	/**
	 * NUM ::= "-?[0-9]+"
	 * */
	static NumberNode parseNumber(Scanner s){
		
		int num = Integer.parseInt(require(NUMPAT, "Expecting [0-9]", s));
		NumberNode numberNode = new NumberNode(num);
		
		return numberNode;
	}
	
	/**
	 * OP ::= add | sub | mul | div
	 */
	static OPNode parseOP(Scanner s){
		
		OPNode op = null;
		
		if(checkFor("add", s))				op = new AddNode();
		else if(checkFor("sub", s))			op = new SubNode();
		else if(checkFor("mul", s))			op = new MultNode();
		else if(checkFor("div", s))			op = new DivNode();
		
		return op;
	}
	
	/**
	 * EXP   ::= NUM | SEN | OP ( EXP, EXP ) | VAR
	 * */
	static ExpressionNode parseEXP(Scanner s){
		
		ExpressionNode exp = null;
		
		if(s.hasNext(SENSOR))				exp = parseSensor(s);
		else if(s.hasNext(NUMPAT))			exp = parseNumber(s);
		
		else if(s.hasNext(OPERATORS)){
			
			OPNode op = parseOP(s);
			require(OPENPAREN, "Expecting '('", s);
			ExpressionNode e1 = parseEXP(s);
			require(",", "Expecting ',' ", s);
			ExpressionNode e2 = parseEXP(s);
			exp = new OPNodeExpr(op, e1, e2);
			require(CLOSEPAREN, "Expecting ')'", s);
			
		}
		else if(s.hasNext(VARIABLES))		exp = parseVar(s);
		else
			fail("Expecting SEN|NUM|VAR|OPEXP", s);
		
		return exp;
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

	ExpressionNode exp;

	public MoveNode(){}			
	public MoveNode(ExpressionNode exp){			//Optional Argument
		this.exp = exp;
	}

	@Override
	public void execute(Robot robot) {
		
		if(exp!=null){
			int i = 0, e = exp.evaluate(robot);		//Number of Steps
			while( i < e ){
				robot.move();
				i++;
			}
		}
		else
			robot.move();
		
		
	}

	public String toString(){
		if(exp!=null)
			return "Move("+this.exp+")";
		else
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

	ExpressionNode exp;

	public WaitNode(){}
	public WaitNode(ExpressionNode exp){
		this.exp = exp;
	}
	
	@Override
	public void execute(Robot robot) {
		
		if(exp!=null){
			int i = 0, e = exp.evaluate(robot);
			while( i < e){
				robot.idleWait();
				i++;
			}
		}
		else
			robot.idleWait();
	}

	public String toString(){
		
		if(exp!=null)
			return "Wait("+this.exp+")";
		else
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
	Map<ConditionalNode, BlockNode> elifBlocks;		//List of elseIf Blocks *
	BlockNode mainBlock, elseBlock;
	
	public IFNode(ConditionalNode c, BlockNode b){
		this.condition = c;
		this.mainBlock = b;
		this.elifBlocks = new HashMap<ConditionalNode, BlockNode>();
	}
	
	@Override
	public void execute(Robot robot) {
		
		//IF
		if(elifBlocks.size() == 0 && elseBlock == null){
			if(condition.evaluate(robot))
				mainBlock.execute(robot);
		}
		//IF|ELSE
		else if(elifBlocks.size() == 0 && elseBlock != null){
			if(condition.evaluate(robot))
				mainBlock.execute(robot);
			elseBlock.execute(robot);
		}
		//IF|ELIF
		else if(elifBlocks.size() > 0 && elseBlock == null){
			if(condition.evaluate(robot))
				mainBlock.execute(robot);
			for(Map.Entry<ConditionalNode, BlockNode> e : elifBlocks.entrySet()){
				if(e.getKey().evaluate(robot)){
					e.getValue().execute(robot);
				}
			}
		}
		//IF|ELIF|ELSE
		else{
			if(condition.evaluate(robot))
				mainBlock.execute(robot);
			for(Map.Entry<ConditionalNode, BlockNode> e : elifBlocks.entrySet()){
				if(e.getKey().evaluate(robot)){
					e.getValue().execute(robot);
				}
			}
			elseBlock.execute(robot);
		}
	}
	
	public void setElseBlock(BlockNode e){
		this.elseBlock = e;
	}
	
	public String toString(){
		
		String ifString = null;
		if(elseBlock!=null){
			String tabRepeat = new String(new char[Parser.depthCounter]).replace("\0", "\t");
			ifString =  "if("+this.condition +")"+ this.mainBlock +"\n"+tabRepeat+
					"else"+ this.elseBlock;
		}
		else if(elseBlock==null)
			ifString = "if ("+this.condition +")"+ this.mainBlock;
			
		return ifString;
		
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
		return "while( "+this.condition + ")" +this.block;
	}
	
	
}

interface ConditionalNode{
	public boolean evaluate(Robot robot);
}

class GreaterThanNode implements ConditionalNode{
	
	ExpressionNode left;
	ExpressionNode right;
	
	public GreaterThanNode(ExpressionNode left, ExpressionNode right){
		this.left = left;
		this.right = right;
	}
	
	@Override
	public boolean evaluate(Robot robot) {
		
		boolean bool = false;
		if(left.evaluate(robot) > right.evaluate(robot))
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "gt( "+left+", "+right+ ")";
	}
}
class LessThanNode implements ConditionalNode{
	
	ExpressionNode left;
	ExpressionNode right;
	
	public LessThanNode(ExpressionNode left, ExpressionNode right){
		this.left = left;
		this.right = right;
	}
	
	@Override
	public boolean evaluate(Robot robot) {
		
		boolean bool = false;
		if(left.evaluate(robot) < right.evaluate(robot))
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "lt( "+left+", "+right+ ")";
	}
}
class EqualsNode implements ConditionalNode{
	
	ExpressionNode left;
	ExpressionNode right;
	
	public EqualsNode(ExpressionNode left, ExpressionNode right){
		this.left = left;
		this.right = right;
	}
	
	@Override
	public boolean evaluate(Robot robot) {
		
		boolean bool = false;
		if(left.evaluate(robot) == right.evaluate(robot))
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "eq( "+left+", "+right+ ")";
	}
}

class AndNode implements ConditionalNode{
	
	ConditionalNode left;
	ConditionalNode right;
	
	public AndNode(ConditionalNode left, ConditionalNode right){
		this.left = left;
		this.right = right;
	}
	
	@Override
	public boolean evaluate(Robot robot) {
		
		boolean bool = false;
		if(left.evaluate(robot) && right.evaluate(robot))
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "and( "+left+", "+ right + ")";
	}
}

class OrNode implements ConditionalNode{
	
	ConditionalNode left;
	ConditionalNode right;
	
	public OrNode(ConditionalNode left, ConditionalNode right){
		this.left = left;
		this.right = right;
	}
	
	@Override
	public boolean evaluate(Robot robot) {
		
		boolean bool = false;
		if(left.evaluate(robot) || right.evaluate(robot))
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "or( "+left+", "+ right + ")";
	}
}

class NotNode implements ConditionalNode{
	
	ConditionalNode cond;
	
	public NotNode(ConditionalNode cond){
		this.cond = cond;
	}
	
	@Override
	public boolean evaluate(Robot robot) {
		
		boolean bool = false;
		if(!cond.evaluate(robot))
			bool = true;
		else
			bool = false;
		
		return bool;
	}
	
	public String toString(){
		return "not( "+cond+")";
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
//========================SENSOR + NUM + OP=========================//
//=================================================================//
interface ExpressionNode{
	public int evaluate(Robot robot);
}

interface SensorNode extends ExpressionNode{	
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

	ExpressionNode exp;
	
	public BarrelLRNode(){}
	public BarrelLRNode(ExpressionNode exp){
		this.exp = exp;
	}
	
	@Override
	public int evaluate(Robot robot) {
		int n = robot.getClosestBarrelLR();
		return robot.getBarrelLR(n);
	}
	
	public String toString(){
		
		if(exp!=null)
			return "BarrelLR("+this.exp+")";
		else
			return "BarrelLR";
		
	}
}
class BarrelFBNode implements SensorNode{

	ExpressionNode exp;
	
	public BarrelFBNode(){}
	public BarrelFBNode(ExpressionNode exp){
		this.exp = exp;
	}
	
	@Override
	public int evaluate(Robot robot) {
		int n = robot.getClosestBarrelFB();
		return robot.getBarrelFB(n);
	}
	
	public String toString(){
		
		if(exp!=null)
			return "BarreFB("+this.exp+")";
		else
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
class NumberNode implements ExpressionNode{
	
	int num;
	
	public NumberNode(int num){
		this.num = num;
	}
	
	public int getNum(){
		return num;
	}

	@Override
	public int evaluate(Robot robot) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public String toString(){
		return Integer.toString(num);
	}
}

interface OPNode extends ExpressionNode{ 
	public String getOP();
}
class AddNode implements OPNode{

	@Override
	public String getOP() {
		return "add";
	}

	@Override
	public int evaluate(Robot robot) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public String toString(){
		return "add";
	}
}
class SubNode implements OPNode{

	@Override
	public String getOP() {
		return "sub";
	}

	@Override
	public int evaluate(Robot robot) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public String toString(){
		return "sub";
	}
}
class MultNode implements OPNode{

	@Override
	public String getOP() {
		return "mul";
	}

	@Override
	public int evaluate(Robot robot) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public String toString(){
		return "mult";
	}
}
class DivNode implements OPNode{

	@Override
	public String getOP() {
		return "div";
	}

	@Override
	public int evaluate(Robot robot) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public String toString(){
		return "div";
	}
}

class OPNodeExpr implements ExpressionNode{

	OPNode op;
	ExpressionNode left, right;
	
	public OPNodeExpr(OPNode op, ExpressionNode left, ExpressionNode right){
		this.op = op;
		this.left = left;
		this.right = right;
	}
	
	@Override
	public int evaluate(Robot robot) {
		
		int eval = 0;
		
		if(op.getClass() == AddNode.class)			eval = left.evaluate(robot) + right.evaluate(robot);
		else if(op.getClass() == SubNode.class)  	eval = left.evaluate(robot) - right.evaluate(robot);
		else if(op.getClass() == MultNode.class)	eval = left.evaluate(robot) * right.evaluate(robot);
		else if(op.getClass() == DivNode.class)		eval = left.evaluate(robot) / right.evaluate(robot);
		
		return eval;
	}
	
	public String toString(){
		return op.toString() + "(" + left.toString() + ", " + right.toString() + ")";
	}
	
}

class VariableNode implements ExpressionNode{
	
	String value = "0";
	boolean isInt = false;
	boolean isString = false;
	
	
	public VariableNode(String value){
		
		this.value = value;
		
		//Recognize if value is int or string?
		if(value.matches("\\d"))
			isInt = true;
		else if(value.matches("A-Z|a-z"))
			isString = true;
	}

	@Override
	public int evaluate(Robot robot) {
		// TODO Auto-generated method stub
		for(Map.Entry<VariableNode, ExpressionNode> e : Parser.variablesMap.entrySet()){
			if(value == e.getKey().value){
				return ((NumberNode)e.getValue()).num;
			}
		}
		
		return 0;
	}
	
	public String toString(){
		return value;
	}
}

class AssignmentNode implements StatementNode{

	VariableNode var;
	ExpressionNode exp;
	
	public AssignmentNode(VariableNode var, ExpressionNode exp){
		
		this.var = var;
		this.exp = exp;
		
		//Update Map - Assign VAR = EXP
		Parser.variablesMap.remove(var);
		Parser.variablesMap.put(var, exp);
				
	}
	
	@Override
	public void execute(Robot robot) {
		// TODO Auto-generated method stub
		
	}
	
	public String toString(){
		return var + " = " + exp;
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



