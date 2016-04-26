package net.tadpole.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import javafx.util.Pair;
import net.tadpole.compiler.ast.*;
import net.tadpole.compiler.ast.Expression.*;
import net.tadpole.compiler.ast.LiteralExpression.*;
import net.tadpole.compiler.exceptions.CompilationException;
import net.tadpole.compiler.parser.TadpoleLexer;
import net.tadpole.compiler.parser.TadpoleParser;
import net.tadpole.compiler.parser.TadpoleParser.FileContext;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.util.BCELifier;
import org.apache.bcel.verifier.VerificationResult;
import org.apache.bcel.verifier.Verifier;
import org.apache.bcel.verifier.VerifierFactory;

public class CompilerCore
{
	public static void main(String[] args) throws Exception
	{
		//new BCELifier(new ClassParser("out/Test$IntPair.class").parse(), System.out).start();
		
		//System.exit(0);
		
		args = new String[]{"examples/Test.tadpole"};
		for(int i = 0; i < args.length; i++)
			args[i] = args[i].replace('\\', '/');
		
		if(args.length < 1)
			throw new IllegalArgumentException("Invalid number of arguments");
		
		for(int i = 0; i < args.length; i++)
		{
			String filename = args[i];
			BufferedReader br = new BufferedReader(new FileReader(new File(filename)));
			String fileData = br.lines().collect(Collectors.joining("\n"));
			br.close();
			
			TadpoleLexer lexer = new TadpoleLexer(new ANTLRInputStream(fileData));
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			TadpoleParser parser = new TadpoleParser(tokens);
			FileContext mc = parser.file();
			
			String moduleName = filename.substring(filename.lastIndexOf("/") + 1, filename.length() - 8);
			ParseTreeWalker walker = new ParseTreeWalker();
			TadpoleListener listener = new TadpoleListener(moduleName);
			walker.walk(listener, mc);
			FileNode fileNode = listener.getRoot();
			simplifyNodes(fileNode);
			List<Struct> structs = Struct.registerStructs(fileNode);
			List<String> imports = fileNode.getChildren().stream().filter(n -> n instanceof ImportNode).map(n -> ((ImportNode) n).moduleName).collect(Collectors.toList());
			List<Function> functions = fileNode.getChildren().stream().filter(n -> n instanceof FunctionDecNode).map(n -> new Function((FunctionDecNode) n)).collect(Collectors.toList());
			List<Statement> statements = fileNode.getChildren().stream().filter(n -> n instanceof StatementNode).map(n -> Statement.convert((StatementNode) n)).collect(Collectors.toList());
			Module module = new Module(moduleName, structs, imports, functions, statements);
		}
		Module.absolutifyTypes(); // also verifies that types exist and are imported
		Module.verifySanity(); // verifies that no duplicates are declared
		Module.mergeParallelAssigns();
		
		// TODO format struct names inner$outer, generate bytecode
		
		ClassGen[] classGens = Module.generateBytecode();
		JavaClass[] classes = Arrays.stream(classGens).map(ClassGen::getJavaClass).toArray(JavaClass[]::new);
		
		Arrays.stream(classes).forEach(Repository::addClass);
		for(JavaClass jc : classes)
		{
			Verifier verifier = VerifierFactory.getVerifier(jc.getClassName());
			VerificationResult vr = verifier.doPass1();
			if(vr.getStatus() == VerificationResult.VERIFIED_REJECTED)
			{
				System.err.println("Failed to verify class: " + jc.getClassName() + "\n\n" + vr.getMessage());
				continue;
			}
			vr = verifier.doPass2();
			if(vr.getStatus() == VerificationResult.VERIFIED_REJECTED)
			{
				System.err.println("Failed to verify class: " + jc.getClassName() + "\n\n" + vr.getMessage());
				continue;
			}
			for(int i = 0; i < jc.getMethods().length; i++)
			{
				vr = verifier.doPass3a(i);
				if(vr.getStatus() == VerificationResult.VERIFIED_REJECTED)
				{
					System.err.println("Failed to verify class: " + jc.getClassName() + "\n\n" + vr.getMessage());
					break;
				}
				vr = verifier.doPass3b(i);
				if(vr.getStatus() == VerificationResult.VERIFIED_REJECTED)
				{
					System.err.println("Failed to verify class: " + jc.getClassName() + "\n\n" + vr.getMessage());
					break;
				}
			}
			
			Arrays.stream(classes).forEach(clazz -> {
				try
				{
					clazz.dump(new File("out/" + clazz.getClassName().replace('.', '$') + ".class"));
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			});
			
			//copy(new File("lib"), new File("out"));
			
			//String module = args.length > 1 ? args[args.length - 1] : roots.get(0).getKey();
			
			/*File meta = new File("out/META-INF");
			meta.mkdirs();
			meta = new File(meta, "MANIFEST.MF");
			
			try(BufferedWriter bw = new BufferedWriter(new FileWriter(new File("out/META-INF/MANIFEST.MF"))))
			{
				bw.write("Manifest-Version: 1.0\nCreated-By: 1.0.0 Chigara Compiler (Matthew Warren)\nMain-Class: " + module + "." + module + "\n\n\n");
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}*/
			
			/*try
			{
				File f = new File("out/" + module + ".jar");
				f.getParentFile().mkdirs();
				f.createNewFile();
				JarOutputStream out = new JarOutputStream(new FileOutputStream(f));
				classes.forEach(clazz -> {
					int index = clazz.getClassName().lastIndexOf(".") + 1;
					
					JarEntry je = new JarEntry(p.getKey() + "/" + clazz.getClassName().substring(index) + ".class");
					try
					{
						out.putNextEntry(je);
						clazz.dump(out);
						out.closeEntry();
					}
					catch(Exception e)
					{
						e.printStackTrace();
					}
				});
				
				File libFile = new File("lib");
				LinkedList<File> files = new LinkedList<File>();
				files.add(libFile);
				byte[] ba = new byte[8192];
				while(files.size() > 0)
				{
					File file = files.removeFirst();
					if(file.isDirectory())
					{
						Arrays.stream(file.listFiles()).forEach(files::add);
					}
					else
					{
						String relPath = libFile.toURI().relativize(file.toURI()).getPath();
						JarEntry je = new JarEntry(relPath);
						try
						{
							out.putNextEntry(je);
							FileInputStream fis = new FileInputStream(file);
							int amt;
							while((amt = fis.read(ba)) >= 0)
								out.write(ba, 0, amt);
							fis.close();
							out.closeEntry();
						}
						catch(Exception e)
						{
							e.printStackTrace();
						}
					}
				}
				
				JarEntry je = new JarEntry("META-INF/MANIFEST.MF");
				out.putNextEntry(je);
				out.write(("Manifest-Version: 1.0\nCreated-By: 1.0.0 Chigara Compiler (Matthew Warren)\nMain-Class: " + module + "." + module + "\n\n\n").getBytes("UTF-8"));
				out.closeEntry();
				
				out.close();
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}*/
		}
	}
	
	private static boolean tryRead(List<String> strList, String fileName, int retryAmt)
	{
		while(retryAmt > 0)
		{
			try(BufferedReader br = new BufferedReader(new FileReader(new File(fileName))))
			{
				strList.add(br.lines().collect(Collectors.joining("\n")));
				return true;
			}
			catch(Exception e)
			{
				retryAmt--;
			}
		}
		return false;
	}
	
	private static void simplifyNodes(ASTNode node)
	{
		if(!(node instanceof StatementNode) && !(node instanceof VariableDecNode))
		{
			node.getChildren().forEach(CompilerCore::simplifyNodes);
			return;
		}
		
		if(node instanceof StatementNode)
		{
			StatementNode sn = (StatementNode) node;
			if(sn.isExpressionStatement())
				sn.expressions.set(0, simplifyExpression(sn.expressions.get(0)));
			else if(sn.isReturnStatement() && sn.expressions != null)
				sn.expressions.set(0, simplifyExpression(sn.expressions.get(0)));
		}
		else if(node instanceof VariableDecNode)
		{
			VariableDecNode vdn = (VariableDecNode) node;
			if(vdn.expression != null)
				vdn.expression = simplifyExpression(vdn.expression);
		}
	}
	
	private static Expression simplifyExpression(Expression expression)
	{
		if(expression instanceof UnaryExpression)
		{
			UnaryExpression ue = (UnaryExpression) expression;
			Expression expr = simplifyExpression(ue.expr);
			if(expr instanceof LiteralExpression)
			{
				if(expr instanceof LiteralExpression.IntLiteral)
				{
					LiteralExpression.IntLiteral il = (LiteralExpression.IntLiteral) expr;
					switch(ue.op)
					{
					case NEGATIVE:
						return new LiteralExpression.IntLiteral(-il.value, il.wide);
					case UNARY_NEGATE:
						return new LiteralExpression.IntLiteral(~il.value, il.wide);
					case POSITIVE:
						return il;
					case DECREMENT:
						return simplifyExpression(new BinaryExpression(ue.expr, BinaryOp.SUBTRACT_ASSIGN, new IntLiteral(1, false)));
					case INCREMENT:
						return simplifyExpression(new BinaryExpression(ue.expr, BinaryOp.ADD_ASSIGN, new IntLiteral(1, false)));
					case NOT:
						throw new CompilationException("Cannot perform boolean negation on integer");
					default:
						throw new CompilationException("Unknown unary operator: " + ue.op);
					}
				}
				else if(expr instanceof LiteralExpression.FloatLiteral)
				{
					LiteralExpression.FloatLiteral fl = (LiteralExpression.FloatLiteral) expr;
					switch(ue.op)
					{
					case NEGATIVE:
						return new LiteralExpression.FloatLiteral(-fl.value, fl.wide);
					case POSITIVE:
						return fl;
					case DECREMENT:
						return simplifyExpression(new BinaryExpression(ue.expr, BinaryOp.SUBTRACT_ASSIGN, new FloatLiteral(1.0D, false)));
					case INCREMENT:
						return simplifyExpression(new BinaryExpression(ue.expr, BinaryOp.ADD_ASSIGN, new FloatLiteral(1.0D, false)));
					case UNARY_NEGATE:
						throw new CompilationException("Cannot perform unary negation on floating point number");
					case NOT:
						throw new CompilationException("Cannot perform boolean negation on integer");
					default:
						throw new CompilationException("Unknown unary operator: " + ue.op);
					}
				}
				else if(expr instanceof LiteralExpression.BooleanLiteral)
				{
					LiteralExpression.BooleanLiteral bl = (LiteralExpression.BooleanLiteral) expr;
					switch(ue.op)
					{
					case NOT:
						return bl.value ? LiteralExpression.BooleanLiteral.FALSE : LiteralExpression.BooleanLiteral.TRUE;
					case NEGATIVE:
						throw new CompilationException("Cannot perform integer negation on boolean");
					case DECREMENT:
						throw new CompilationException("Cannot decrement a boolean");
					case INCREMENT:
						throw new CompilationException("Cannot increment a boolean");
					case POSITIVE:
						throw new CompilationException("Cannot perform integer identity on boolean");
					case UNARY_NEGATE:
						throw new CompilationException("Cannot perform unary negation on boolean");
					default:
						throw new CompilationException("Unknown unary operator: " + ue.op);
					}
				}
				else if(expr instanceof LiteralExpression.CharacterLiteral)
				{
					LiteralExpression.CharacterLiteral cl = (LiteralExpression.CharacterLiteral) expr;
					switch(ue.op)
					{
					case NEGATIVE:
						return new LiteralExpression.IntLiteral(-cl.value, false);
					case UNARY_NEGATE:
						return new LiteralExpression.IntLiteral(~cl.value, false);
					case POSITIVE:
						return new LiteralExpression.IntLiteral(cl.value, false);
					case DECREMENT:
						throw new CompilationException("Cannot decrement a character");
					case INCREMENT:
						throw new CompilationException("Cannot increment a character");
					case NOT:
						throw new CompilationException("Cannot perform boolean negation on character");
					default:
						throw new CompilationException("Unknown unary operator: " + ue.op);
					}
				}
				else if(expr instanceof LiteralExpression.NoneLiteral || expr instanceof LiteralExpression.StringLiteral || expr instanceof LiteralExpression.ArrayLiteral)
				{
					throw new CompilationException("Cannot perform unary operator on null, string, or array");
				}
				throw new CompilationException("Unknown literal expression of class: " + expr.getClass());
			}
			throw new CompilationException("Cannot perform unary operator on expression of class: " + expr.getClass());
		}
		else if(expression instanceof BinaryExpression)
		{
			BinaryExpression be = (BinaryExpression) expression;
			Expression exprLeft = simplifyExpression(be.exprLeft);
			Expression exprRight = simplifyExpression(be.exprRight);
			if(be.op.toString().contains("ASSIGN"))
			{
				// uncomment if binary expressions become valid on the left side for assignment
				if(!(/*exprLeft instanceof BinaryExpression || */exprLeft instanceof PrimaryExpression.ArrayAccessExpression || exprLeft instanceof PrimaryExpression.FieldAccessExpression))
					throw new CompilationException("Cannot have expression of type " + exprLeft.getClass() + " on left side of assignment expression");
				if(be.op.toString().contains("_ASSIGN") && be.op != BinaryOp.PARALLEL_ASSIGN)
					exprRight = simplifyExpression(new BinaryExpression(exprLeft, BinaryOp.valueOf(be.op.toString().substring(0, be.op.toString().length() - 7)), exprRight));
				
				// uncomment if binary expressions become valid on the left side for assignment
				if(/*findParallelAssign(exprLeft) || */findParallelAssign(exprRight))
					throw new CompilationException("Cannot have the parallel assignment operator in a sub-expression");
				
				return new BinaryExpression(exprLeft, be.op == BinaryOp.PARALLEL_ASSIGN ? BinaryOp.PARALLEL_ASSIGN : BinaryOp.ASSIGN, exprRight);
			}
			
			switch(be.op)
			{
			case POWER:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					NumberLiteral left = (NumberLiteral) exprLeft;
					NumberLiteral right = (NumberLiteral) exprRight;
					return new LiteralExpression.FloatLiteral(Math.pow(left.asDouble(), right.asDouble()), true);
				}
				break;
			case MULTIPLY:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					if(exprLeft.getClass().equals(exprRight.getClass()))
					{
						if(exprLeft instanceof IntLiteral)
						{
							IntLiteral left = (IntLiteral) exprLeft;
							IntLiteral right = (IntLiteral) exprRight;
							return new IntLiteral(left.value * right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof FloatLiteral)
						{
							FloatLiteral left = (FloatLiteral) exprLeft;
							FloatLiteral right = (FloatLiteral) exprRight;
							return new FloatLiteral(left.value * right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof CharacterLiteral)
						{
							CharacterLiteral left = (CharacterLiteral) exprLeft;
							CharacterLiteral right = (CharacterLiteral) exprRight;
							return new IntLiteral(left.value * right.value, false);
						}
						
						throw new CompilationException("Unknown number literal of class: " + exprLeft.getClass());
					}
					else
					{
						if(exprLeft instanceof FloatLiteral || exprRight instanceof FloatLiteral)
						{
							boolean wide = exprLeft instanceof FloatLiteral ? ((FloatLiteral) exprLeft).wide : ((FloatLiteral) exprRight).wide;
							NumberLiteral left = (NumberLiteral) exprLeft;
							NumberLiteral right = (NumberLiteral) exprRight;
							return new FloatLiteral(left.asDouble() * right.asDouble(), wide);
						}
						boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : ((IntLiteral) exprRight).wide;
						NumberLiteral left = (NumberLiteral) exprLeft;
						NumberLiteral right = (NumberLiteral) exprRight;
						return new IntLiteral(left.asInt() * right.asInt(), wide);
					}
				}
				else if(exprLeft instanceof StringLiteral && exprRight instanceof NumberLiteral && ((NumberLiteral) exprRight).isInt())
				{
					String str = ((StringLiteral) exprLeft).value;
					long value = ((NumberLiteral) exprRight).asInt();
					StringBuilder sb = new StringBuilder((int) (str.length() * value));
					while(value-- > 0)
						sb.append(str);
					return new StringLiteral(sb.toString());
				}
				else if(exprLeft instanceof NumberLiteral && ((NumberLiteral) exprLeft).isInt() && exprRight instanceof StringLiteral)
				{
					String str = ((StringLiteral) exprRight).value;
					long value = ((NumberLiteral) exprLeft).asInt();
					StringBuilder sb = new StringBuilder((int) (str.length() * value));
					while(value-- > 0)
						sb.append(str);
					return new StringLiteral(sb.toString());
				}
				break;
			case DIVIDE:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					if(exprLeft.getClass().equals(exprRight.getClass()))
					{
						if(exprLeft instanceof IntLiteral)
						{
							IntLiteral left = (IntLiteral) exprLeft;
							IntLiteral right = (IntLiteral) exprRight;
							return new IntLiteral(left.value / right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof FloatLiteral)
						{
							FloatLiteral left = (FloatLiteral) exprLeft;
							FloatLiteral right = (FloatLiteral) exprRight;
							return new FloatLiteral(left.value / right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof CharacterLiteral)
						{
							CharacterLiteral left = (CharacterLiteral) exprLeft;
							CharacterLiteral right = (CharacterLiteral) exprRight;
							return new IntLiteral(left.value / right.value, false);
						}
						
						throw new CompilationException("Unknown number literal of class: " + exprLeft.getClass());
					}
					else
					{
						if(exprLeft instanceof FloatLiteral || exprRight instanceof FloatLiteral)
						{
							boolean wide = exprLeft instanceof FloatLiteral ? ((FloatLiteral) exprLeft).wide : ((FloatLiteral) exprRight).wide;
							NumberLiteral left = (NumberLiteral) exprLeft;
							NumberLiteral right = (NumberLiteral) exprRight;
							return new FloatLiteral(left.asDouble() / right.asDouble(), wide);
						}
						boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : ((IntLiteral) exprRight).wide;
						NumberLiteral left = (NumberLiteral) exprLeft;
						NumberLiteral right = (NumberLiteral) exprRight;
						return new IntLiteral(left.asInt() / right.asInt(), wide);
					}
				}
				break;
			case MODULUS:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					if(exprLeft.getClass().equals(exprRight.getClass()))
					{
						if(exprLeft instanceof IntLiteral)
						{
							IntLiteral left = (IntLiteral) exprLeft;
							IntLiteral right = (IntLiteral) exprRight;
							return new IntLiteral(left.value % right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof FloatLiteral)
						{
							FloatLiteral left = (FloatLiteral) exprLeft;
							FloatLiteral right = (FloatLiteral) exprRight;
							return new FloatLiteral(left.value % right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof CharacterLiteral)
						{
							CharacterLiteral left = (CharacterLiteral) exprLeft;
							CharacterLiteral right = (CharacterLiteral) exprRight;
							return new IntLiteral(left.value % right.value, false);
						}
						
						throw new CompilationException("Unknown number literal of class: " + exprLeft.getClass());
					}
					else
					{
						if(exprLeft instanceof FloatLiteral || exprRight instanceof FloatLiteral)
						{
							boolean wide = exprLeft instanceof FloatLiteral ? ((FloatLiteral) exprLeft).wide : ((FloatLiteral) exprRight).wide;
							NumberLiteral left = (NumberLiteral) exprLeft;
							NumberLiteral right = (NumberLiteral) exprRight;
							return new FloatLiteral(left.asDouble() % right.asDouble(), wide);
						}
						boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : ((IntLiteral) exprRight).wide;
						NumberLiteral left = (NumberLiteral) exprLeft;
						NumberLiteral right = (NumberLiteral) exprRight;
						return new IntLiteral(left.asInt() % right.asInt(), wide);
					}
				}
				break;
			case ADD:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					if(exprLeft.getClass().equals(exprRight.getClass()))
					{
						if(exprLeft instanceof IntLiteral)
						{
							IntLiteral left = (IntLiteral) exprLeft;
							IntLiteral right = (IntLiteral) exprRight;
							return new IntLiteral(left.value + right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof FloatLiteral)
						{
							FloatLiteral left = (FloatLiteral) exprLeft;
							FloatLiteral right = (FloatLiteral) exprRight;
							return new FloatLiteral(left.value + right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof CharacterLiteral)
						{
							CharacterLiteral left = (CharacterLiteral) exprLeft;
							CharacterLiteral right = (CharacterLiteral) exprRight;
							return new IntLiteral(left.value + right.value, false);
						}
						
						throw new CompilationException("Unknown number literal of class: " + exprLeft.getClass());
					}
					else
					{
						if(exprLeft instanceof FloatLiteral || exprRight instanceof FloatLiteral)
						{
							boolean wide = exprLeft instanceof FloatLiteral ? ((FloatLiteral) exprLeft).wide : ((FloatLiteral) exprRight).wide;
							NumberLiteral left = (NumberLiteral) exprLeft;
							NumberLiteral right = (NumberLiteral) exprRight;
							return new FloatLiteral(left.asDouble() + right.asDouble(), wide);
						}
						boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : ((IntLiteral) exprRight).wide;
						NumberLiteral left = (NumberLiteral) exprLeft;
						NumberLiteral right = (NumberLiteral) exprRight;
						return new IntLiteral(left.asInt() + right.asInt(), wide);
					}
				}
				else if(exprLeft instanceof StringLiteral && exprRight instanceof NumberLiteral)
				{
					String str = ((StringLiteral) exprLeft).value;
					
					if(exprRight instanceof CharacterLiteral)
						return new StringLiteral(str + ((CharacterLiteral) exprRight).value);
					
					if(exprRight instanceof IntLiteral)
						return new StringLiteral(str + ((IntLiteral) exprRight).value);
					
					// exprRight is of type FloatLiteral
					FloatLiteral fl = (FloatLiteral) exprRight;
					if(fl.wide)
						return new StringLiteral(str + fl.value);
					return new StringLiteral(str + (float) fl.value);
				}
				else if(exprRight instanceof StringLiteral && exprLeft instanceof NumberLiteral)
				{
					String str = ((StringLiteral) exprRight).value;
					
					if(exprLeft instanceof CharacterLiteral)
						return new StringLiteral(str + ((CharacterLiteral) exprLeft).value);
					
					if(exprLeft instanceof IntLiteral)
						return new StringLiteral(str + ((IntLiteral) exprLeft).value);
					
					// exprRight is of type FloatLiteral
					FloatLiteral fl = (FloatLiteral) exprLeft;
					if(fl.wide)
						return new StringLiteral(str + fl.value);
					return new StringLiteral(str + (float) fl.value);
				}
				break;
			case SUBTRACT:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					if(exprLeft.getClass().equals(exprRight.getClass()))
					{
						if(exprLeft instanceof IntLiteral)
						{
							IntLiteral left = (IntLiteral) exprLeft;
							IntLiteral right = (IntLiteral) exprRight;
							return new IntLiteral(left.value - right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof FloatLiteral)
						{
							FloatLiteral left = (FloatLiteral) exprLeft;
							FloatLiteral right = (FloatLiteral) exprRight;
							return new FloatLiteral(left.value - right.value, left.wide || right.wide);
						}
						else if(exprLeft instanceof CharacterLiteral)
						{
							CharacterLiteral left = (CharacterLiteral) exprLeft;
							CharacterLiteral right = (CharacterLiteral) exprRight;
							return new IntLiteral(left.value - right.value, false);
						}
						
						throw new CompilationException("Unknown number literal of class: " + exprLeft.getClass());
					}
					else
					{
						if(exprLeft instanceof FloatLiteral || exprRight instanceof FloatLiteral)
						{
							boolean wide = exprLeft instanceof FloatLiteral ? ((FloatLiteral) exprLeft).wide : ((FloatLiteral) exprRight).wide;
							NumberLiteral left = (NumberLiteral) exprLeft;
							NumberLiteral right = (NumberLiteral) exprRight;
							return new FloatLiteral(left.asDouble() - right.asDouble(), wide);
						}
						boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : ((IntLiteral) exprRight).wide;
						NumberLiteral left = (NumberLiteral) exprLeft;
						NumberLiteral right = (NumberLiteral) exprRight;
						return new IntLiteral(left.asInt() - right.asInt(), wide);
					}
				}
				break;
			case RIGHT_SHIFT_PRESERVE:
				if(exprLeft instanceof NumberLiteral && ((NumberLiteral) exprLeft).isInt() && exprRight instanceof NumberLiteral && ((NumberLiteral) exprRight).isInt())
				{
					boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : exprRight instanceof IntLiteral ? ((IntLiteral) exprRight).wide : false;
					return new IntLiteral(((NumberLiteral) exprLeft).asInt() >> ((NumberLiteral) exprRight).asInt(), wide);
				}
				break;
			case RIGHT_SHIFT:
				if(exprLeft instanceof NumberLiteral && ((NumberLiteral) exprLeft).isInt() && exprRight instanceof NumberLiteral && ((NumberLiteral) exprRight).isInt())
				{
					boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : exprRight instanceof IntLiteral ? ((IntLiteral) exprRight).wide : false;
					return new IntLiteral(((NumberLiteral) exprLeft).asInt() >>> ((NumberLiteral) exprRight).asInt(), wide);
				}
				break;
			case LEFT_SHIFT:
				if(exprLeft instanceof NumberLiteral && ((NumberLiteral) exprLeft).isInt() && exprRight instanceof NumberLiteral && ((NumberLiteral) exprRight).isInt())
				{
					boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : exprRight instanceof IntLiteral ? ((IntLiteral) exprRight).wide : false;
					return new IntLiteral(((NumberLiteral) exprLeft).asInt() << ((NumberLiteral) exprRight).asInt(), wide);
				}
				break;
			case LESS_THAN:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					NumberLiteral left = (NumberLiteral) exprLeft;
					NumberLiteral right = (NumberLiteral) exprRight;
					return BooleanLiteral.of(left.asDouble() < right.asDouble());
				}
				break;
			case GREATER_THAN:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					NumberLiteral left = (NumberLiteral) exprLeft;
					NumberLiteral right = (NumberLiteral) exprRight;
					return BooleanLiteral.of(left.asDouble() > right.asDouble());
				}
				break;
			case LESS_THAN_EQUAL:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					NumberLiteral left = (NumberLiteral) exprLeft;
					NumberLiteral right = (NumberLiteral) exprRight;
					return BooleanLiteral.of(left.asDouble() <= right.asDouble());
				}
				break;
			case GREATER_THAN_EQUAL:
				if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
				{
					NumberLiteral left = (NumberLiteral) exprLeft;
					NumberLiteral right = (NumberLiteral) exprRight;
					return BooleanLiteral.of(left.asDouble() >= right.asDouble());
				}
				break;
			case IS:
				// TODO implement
				throw new CompilationException("Unimplemented binary operator");
			case EQUALS:
				if(exprLeft instanceof LiteralExpression && exprRight instanceof LiteralExpression)
				{
					if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
					{
						NumberLiteral left = (NumberLiteral) exprLeft;
						NumberLiteral right = (NumberLiteral) exprRight;
						return BooleanLiteral.of(left.asDouble() == right.asDouble());
					}
					else if(exprLeft instanceof BooleanLiteral && exprRight instanceof BooleanLiteral)
					{
						return BooleanLiteral.of(exprLeft == exprRight);
					}
					else
					{
						if(exprLeft instanceof NoneLiteral && exprRight instanceof NoneLiteral)
						{
							return BooleanLiteral.TRUE;
						}
						else if(exprLeft instanceof ArrayLiteral && exprRight instanceof ArrayLiteral)
						{
							// TODO sometime in the future
						}
						else if(exprLeft instanceof StringLiteral && exprRight instanceof StringLiteral)
						{
							StringLiteral left = (StringLiteral) exprLeft;
							StringLiteral right = (StringLiteral) exprRight;
							return BooleanLiteral.of(left.value.equals(right.value));
						}
						
						return BooleanLiteral.FALSE;
					}
				}
				break;
			case NOT_EQUAL:
				if(exprLeft instanceof LiteralExpression && exprRight instanceof LiteralExpression)
				{
					if(exprLeft instanceof NumberLiteral && exprRight instanceof NumberLiteral)
					{
						NumberLiteral left = (NumberLiteral) exprLeft;
						NumberLiteral right = (NumberLiteral) exprRight;
						return BooleanLiteral.of(left.asDouble() != right.asDouble());
					}
					else if(exprLeft instanceof BooleanLiteral && exprRight instanceof BooleanLiteral)
					{
						return BooleanLiteral.of(exprLeft != exprRight);
					}
					else
					{
						if(exprLeft instanceof NoneLiteral && exprRight instanceof NoneLiteral)
						{
							return BooleanLiteral.FALSE;
						}
						else if(exprLeft instanceof ArrayLiteral && exprRight instanceof ArrayLiteral)
						{
							// TODO sometime in the future
						}
						else if(exprLeft instanceof StringLiteral && exprRight instanceof StringLiteral)
						{
							StringLiteral left = (StringLiteral) exprLeft;
							StringLiteral right = (StringLiteral) exprRight;
							return BooleanLiteral.of(!left.value.equals(right.value));
						}
						
						return BooleanLiteral.TRUE;
					}
				}
				break;
			case BITWISE_AND:
				if(exprLeft instanceof NumberLiteral && ((NumberLiteral) exprLeft).isInt() && exprRight instanceof NumberLiteral && ((NumberLiteral) exprRight).isInt())
				{
					boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : exprRight instanceof IntLiteral ? ((IntLiteral) exprRight).wide : false;
					return new IntLiteral(((NumberLiteral) exprLeft).asInt() & ((NumberLiteral) exprRight).asInt(), wide);
				}
				break;
			case XOR:
				if(exprLeft instanceof NumberLiteral && ((NumberLiteral) exprLeft).isInt() && exprRight instanceof NumberLiteral && ((NumberLiteral) exprRight).isInt())
				{
					boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : exprRight instanceof IntLiteral ? ((IntLiteral) exprRight).wide : false;
					return new IntLiteral(((NumberLiteral) exprLeft).asInt() ^ ((NumberLiteral) exprRight).asInt(), wide);
				}
				break;
			case BITWISE_OR:
				if(exprLeft instanceof NumberLiteral && ((NumberLiteral) exprLeft).isInt() && exprRight instanceof NumberLiteral && ((NumberLiteral) exprRight).isInt())
				{
					boolean wide = exprLeft instanceof IntLiteral ? ((IntLiteral) exprLeft).wide : exprRight instanceof IntLiteral ? ((IntLiteral) exprRight).wide : false;
					return new IntLiteral(((NumberLiteral) exprLeft).asInt() | ((NumberLiteral) exprRight).asInt(), wide);
				}
				break;
			case AND:
				if(exprLeft instanceof BooleanLiteral && exprRight instanceof BooleanLiteral)
				{
					BooleanLiteral left = (BooleanLiteral) exprLeft;
					BooleanLiteral right = (BooleanLiteral) exprRight;
					return BooleanLiteral.of(left.value && right.value);
				}
				break;
			case OR:
				if(exprLeft instanceof BooleanLiteral && exprRight instanceof BooleanLiteral)
				{
					BooleanLiteral left = (BooleanLiteral) exprLeft;
					BooleanLiteral right = (BooleanLiteral) exprRight;
					return BooleanLiteral.of(left.value || right.value);
				}
				break;
			}
			
			return new BinaryExpression(exprLeft, be.op, exprRight);
		}
		else if(expression instanceof PrimaryExpression)
		{
			if(expression instanceof PrimaryExpression.CastExpression)
			{
				PrimaryExpression.CastExpression ce = (PrimaryExpression.CastExpression) expression;
				if(ce.targetType.isPrimitive())
				{
					Expression expr = simplifyExpression(ce.expression);
					if(expr instanceof LiteralExpression)
					{
						if(expr instanceof LiteralExpression.CharacterLiteral)
						{
							LiteralExpression.CharacterLiteral cl = (LiteralExpression.CharacterLiteral) expr;
							switch(ce.targetType.typeName)
							{
							case "byte":
								return new LiteralExpression.IntLiteral((byte) cl.value, false);
							case "short":
								return new LiteralExpression.IntLiteral((short) cl.value, false);
							case "int":
								return new LiteralExpression.IntLiteral((int) cl.value, false);
							case "long":
								return new LiteralExpression.IntLiteral((long) cl.value, true);
							case "char":
								return cl;
							case "float":
								return new LiteralExpression.FloatLiteral((double) cl.value, false);
							case "double":
								return new LiteralExpression.FloatLiteral((double) cl.value, true);
							case "boolean":
								throw new CompilationException("Cannot cast char to boolean");
							default:
								throw new CompilationException("Cannot cast char to " + ce.targetType.typeName);
							}
						}
						else if(expr instanceof LiteralExpression.IntLiteral)
						{
							LiteralExpression.IntLiteral il = (LiteralExpression.IntLiteral) expr;
							switch(ce.targetType.typeName)
							{
							case "byte":
								return new LiteralExpression.IntLiteral((byte) il.value, false);
							case "short":
								return new LiteralExpression.IntLiteral((short) il.value, false);
							case "int":
								return new LiteralExpression.IntLiteral((int) il.value, false);
							case "long":
								return new LiteralExpression.IntLiteral(il.value, true);
							case "char":
								return new LiteralExpression.CharacterLiteral((char) il.value);
							case "float":
								return new LiteralExpression.FloatLiteral((double) il.value, false);
							case "double":
								return new LiteralExpression.FloatLiteral((double) il.value, true);
							case "boolean":
								throw new CompilationException("Cannot cast integer to boolean");
							default:
								throw new CompilationException("Cannot cast integer to " + ce.targetType.typeName);
							}
						}
						else if(expr instanceof LiteralExpression.FloatLiteral)
						{
							LiteralExpression.FloatLiteral fl = (LiteralExpression.FloatLiteral) expr;
							switch(ce.targetType.typeName)
							{
							case "byte":
								return new LiteralExpression.IntLiteral((byte) fl.value, false);
							case "short":
								return new LiteralExpression.IntLiteral((short) fl.value, false);
							case "int":
								return new LiteralExpression.IntLiteral((int) fl.value, false);
							case "long":
								return new LiteralExpression.IntLiteral((long) fl.value, true);
							case "char":
								return new LiteralExpression.CharacterLiteral((char) fl.value);
							case "float":
								return new LiteralExpression.FloatLiteral((double) fl.value, false);
							case "double":
								return new LiteralExpression.FloatLiteral(fl.value, true);
							case "boolean":
								throw new CompilationException("Cannot cast floating point number to boolean");
							default:
								throw new CompilationException("Cannot cast floating point number to " + ce.targetType.typeName);
							}
						}
						else if(expr instanceof LiteralExpression.ArrayLiteral)
						{
							LiteralExpression.ArrayLiteral al = (LiteralExpression.ArrayLiteral) expr;
							for(int i = 0; i < al.expressions.length; i++)
								al.expressions[i] = simplifyExpression(al.expressions[i]);
							return al;
						}
					}
				}
			}
			else if(expression instanceof PrimaryExpression.WrapExpression)
			{
				return ((PrimaryExpression.WrapExpression) expression).expression;
			}
			return expression;
		}
		
		throw new CompilationException("Unknown expression of class " + expression.getClass());
	}
	
	private static boolean findParallelAssign(Expression expr)
	{
		if(expr == null)
			return false;
		
		if(expr instanceof BinaryExpression)
		{
			BinaryExpression be = (BinaryExpression) expr;
			return be.op == BinaryOp.PARALLEL_ASSIGN || findParallelAssign(be.exprLeft) || findParallelAssign(be.exprRight);
		}
		
		if(expr instanceof UnaryExpression)
			return findParallelAssign(((UnaryExpression) expr).expr);
		
		if(expr instanceof PrimaryExpression)
		{
			if(expr instanceof PrimaryExpression.ArrayAccessExpression)
				return findParallelAssign(((PrimaryExpression.ArrayAccessExpression) expr).expression) || findParallelAssign(((PrimaryExpression.ArrayAccessExpression) expr).indexExpression);
			if(expr instanceof PrimaryExpression.CastExpression)
				return findParallelAssign(((PrimaryExpression.CastExpression) expr).expression);
			if(expr instanceof PrimaryExpression.FieldAccessExpression)
				return findParallelAssign(((PrimaryExpression.FieldAccessExpression) expr).expression);
			if(expr instanceof PrimaryExpression.WrapExpression)
				return findParallelAssign(((PrimaryExpression.WrapExpression) expr).expression);
			if(expr instanceof PrimaryExpression.FunctionCallExpression)
				return Arrays.stream(((PrimaryExpression.FunctionCallExpression) expr).parameters).map(CompilerCore::findParallelAssign).anyMatch(b -> b);
			if(expr instanceof PrimaryExpression.InstantiationExpression)
				return Arrays.stream(((PrimaryExpression.InstantiationExpression) expr).parameters).map(CompilerCore::findParallelAssign).anyMatch(b -> b);
			
			throw new CompilationException("Unknown primary expression of type " + expr.getClass());
		}
		
		throw new CompilationException("Unknown expression of type " + expr.getClass());
	}
	
	private static void copy(File source, File dest)
	{
		if(source.isDirectory())
		{
			if(!dest.exists())
				dest.mkdirs();
			if(dest.isDirectory())
			{
				for(File file : source.listFiles())
				{
					copy(file, new File(dest, file.getName()));
				}
			}
			else
			{
				throw new IllegalStateException("Cannot write directory to non-directory");
			}
		}
		else
		{
			if(!dest.exists())
				try{dest.createNewFile();}catch(Exception e){e.printStackTrace();}
			if(dest.isDirectory())
			{
				File file = new File(dest, source.getName());
				try(FileOutputStream fos = new FileOutputStream(file); FileInputStream fis = new FileInputStream(source))
				{
					byte[] ba = new byte[8192];
					int amt;
					while((amt = fis.read(ba)) >= 0)
						fos.write(ba, 0, amt);
					fos.close();
					fis.close();
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				try(FileOutputStream fos = new FileOutputStream(dest); FileInputStream fis = new FileInputStream(source))
				{
					byte[] ba = new byte[8192];
					int amt;
					while((amt = fis.read(ba)) >= 0)
						fos.write(ba, 0, amt);
					fos.close();
					fis.close();
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}