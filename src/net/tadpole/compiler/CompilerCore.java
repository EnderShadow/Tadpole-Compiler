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
import net.tadpole.compiler.exceptions.CompilationException;
import net.tadpole.compiler.parser.TadpoleLexer;
import net.tadpole.compiler.parser.TadpoleParser;
import net.tadpole.compiler.parser.TadpoleParser.FileContext;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.verifier.VerificationResult;
import org.apache.bcel.verifier.Verifier;
import org.apache.bcel.verifier.VerifierFactory;

public class CompilerCore
{
	public static void main(String[] args) throws Exception
	{
		//new BCELifier(new ClassParser("bin/net/chigara/compiler/TestClass.class").parse(), System.out).start();
		
		//System.exit(0);
		
		args = new String[]{"examples/Test.tadpole"};
		
		if(args.length < 1)
			throw new IllegalArgumentException("Invalid number of arguments");
		
		String filename = args[0];
		String fileData = new BufferedReader(new FileReader(new File(filename))).lines().collect(Collectors.joining("\n"));
		
		TadpoleLexer lexer = new TadpoleLexer(new ANTLRInputStream(fileData));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		TadpoleParser parser = new TadpoleParser(tokens);
		FileContext mc = parser.file();
		
		ParseTreeWalker walker = new ParseTreeWalker();
		TadpoleListener listener = new TadpoleListener();
		walker.walk(listener, mc);
		FileNode fileNode = listener.getRoot();
		simplifyNodes(fileNode);
		
		List<ModuleNode> modules = roots.stream().map(Pair::getValue).collect(Collectors.toList());
		LibLoader.loadLibraries();
		Library.treeify();
		modules.forEach(m -> applyToExpression(m, CompilerCore::simplifyConstantExpressions));
		List<Module> modules2 = modules.stream().map(Module::new).collect(Collectors.toList());
		modules2.forEach(Library::addModule);
		modules2.forEach(Module::finalizeImports);
		ResolutionFactory.Future.resolve();
		Library.compileable.forEach(c -> {
			c.parent.children.add(c);
			c.interfaces.forEach(i -> i.children.add(c));
		});
		// is this one even needed?
		//FutureResolutions.resolve();
		if(!ResolutionFactory.Future.isResolved())
			throw new IllegalStateException("Could not resolve all resolutions in stage 1 of the compilation process");
		Statement.resolveStage2();
		Field.resolveStage2();
		ResolutionFactory.Future.resolve();
		if(!ResolutionFactory.Future.isResolved())
			throw new IllegalStateException("Could not resolve all resolutions in stage 2 of the compilation process");
		List<ClassGen> classes = Library.compileable.stream().map(Class::toBytecode).collect(Collectors.toList());
		// TODO convert to bytecode
		
/*		for(Pair<String, ClassNode> p : roots)
		{
			if(p == null)
				continue;
			
			ClassNode root = p.getValue();
			List<String> importNames = root.getChildren().stream().filter(n -> n instanceof ImportNode).map(n -> ((ImportNode) n).module).collect(Collectors.toList());
			if(!importNames.contains("Lang"))
				importNames.add("Lang");
			List<Inheritable> imports = modules.stream().filter(n -> importNames.stream().anyMatch(i -> n.getName().contains(i))).collect(Collectors.toList());
			
			//validate(root);
			applyToExpression(root, CompilerCore::simplifyConstantExpressions);
			//root.getChildren().forEach(node -> printNodes(node, 0));
			
			LibLoader.delete(new File("out"), false);
			
			BCELConverter converter = new BCELConverter(p.getKey(), imports);
			List<JavaClass> classes = converter.convertToBCEL(root, modules);
			classes.stream().map(JavaClass::getMethods).mapToInt(ma -> ma.length).forEach(System.out::println);
			classes.forEach(Repository::addClass);
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
			}
			classes.forEach(clazz -> {
				int index = clazz.getClassName().lastIndexOf(".") + 1;
				try
				{
					clazz.dump(new File("out/" + p.getKey() + "/" + clazz.getClassName().substring(index) + ".class"));
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			});
			
			copy(new File("lib"), new File("out"));
			
			String module = args.length > 1 ? args[args.length - 1] : roots.get(0).getKey();
			
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
			}
		}*/
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
				sn.expression = simplifyExpression(sn.expression);
			else if(sn.isReturnStatement() && sn.expression != null)
				sn.expression = simplifyExpression(sn.expression);
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
					case INCREMENT:
						break;
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
					case INCREMENT:
						break;
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
					}
				}
			}
			else if(expression instanceof PrimaryExpression.WrapExpression)
			{
				return ((PrimaryExpression.WrapExpression) expression).expression;
			}
			return expression;
		}
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