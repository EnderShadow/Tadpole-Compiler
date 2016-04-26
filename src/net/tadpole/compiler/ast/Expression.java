package net.tadpole.compiler.ast;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Utility;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.BranchHandle;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.GOTO;
import org.apache.bcel.generic.IFEQ;
import org.apache.bcel.generic.IFNE;
import org.apache.bcel.generic.IF_ACMPEQ;
import org.apache.bcel.generic.IF_ICMPEQ;
import org.apache.bcel.generic.IF_ICMPGE;
import org.apache.bcel.generic.IF_ICMPGT;
import org.apache.bcel.generic.IF_ICMPLE;
import org.apache.bcel.generic.IF_ICMPLT;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.ReferenceType;

import javafx.util.Pair;
import net.tadpole.compiler.Function;
import net.tadpole.compiler.Module;
import net.tadpole.compiler.Struct;
import net.tadpole.compiler.Type;
import net.tadpole.compiler.exceptions.CompilationException;
import net.tadpole.compiler.parser.TadpoleParser;
import net.tadpole.compiler.util.MethodUtils;
import net.tadpole.compiler.util.Triplet;
import net.tadpole.compiler.util.TypeUtils;

public abstract class Expression
{
	public static Expression convert(TadpoleParser.ExpressionContext context)
	{
		if(context.primary() != null)
			return convertPrimary(context.primary());
		if(context.unaryExpression() != null)
			return convertUnary(context.unaryExpression());
		
		return convertBinary(context.expression(0), BinaryOp.resolve(context.getChild(1).getText()), context.expression(1));
	}
	
	public static PrimaryExpression convertPrimary(TadpoleParser.PrimaryContext context)
	{
		if(context == null)
			return null;
		if(context.literal() != null)
			return PrimaryExpression.parseLiteral(context.literal());
		else if(context.dimension() != null)
			return new PrimaryExpression.ArrayAccessExpression(convertPrimary(context.primary()), context.dimension());
		else if(context.type() != null)
			return new PrimaryExpression.CastExpression(new Type(context.type().getText()), context.primary() != null ? convertPrimary(context.primary()) : convertUnary(context.unaryExpression()));
		else if(context.objType() != null)
			return new PrimaryExpression.InstantiationExpression(new Type(context.objType().getText()), context.expressionList());
		else if(context.expression() != null)
			return new PrimaryExpression.WrapExpression(Expression.convert(context.expression()));
		else if(context.fieldName() != null)
			return new PrimaryExpression.FieldAccessExpression(context.primary(), context.fieldName().getText());
		else if(context.functionCall() != null)
			return new PrimaryExpression.FunctionCallExpression(convertPrimary(context.primary()), context.functionCall());
		throw new CompilationException("Unknown primary expression: " + context.getText());
	}
	
	public static UnaryExpression convertUnary(TadpoleParser.UnaryExpressionContext context)
	{
		if(context.unaryOp() != null)
			return new UnaryExpression(UnaryOp.resolve(context.unaryOp().getText()), Expression.convert(context.expression()));
		return new UnaryExpression(UnaryOp.resolve(context.prefixPostfixOp().getText()), Expression.convert(context.expression()), context.getChild(1) instanceof TadpoleParser.PrefixPostfixOpContext);
	}
	
	public static BinaryExpression convertBinary(TadpoleParser.ExpressionContext exprContextLeft, BinaryOp op, TadpoleParser.ExpressionContext exprContextRight)
	{
		return new BinaryExpression(Expression.convert(exprContextLeft), op, Expression.convert(exprContextRight));
	}
	
	public abstract Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg);
	
	public abstract static class PrimaryExpression extends Expression
	{
		public static LiteralExpression parseLiteral(TadpoleParser.LiteralContext context)
		{
			if(context.IntegerLiteral() != null)
				return new LiteralExpression.IntLiteral(context.IntegerLiteral().getText());
			else if(context.FloatingPointLiteral() != null)
				return new LiteralExpression.FloatLiteral(context.FloatingPointLiteral().getText());
			else if(context.BooleanLiteral() != null)
				return LiteralExpression.BooleanLiteral.convert(context.BooleanLiteral().getText());
			else if(context.CharacterLiteral() != null)
				return new LiteralExpression.CharacterLiteral(context.CharacterLiteral().getText());
			else if(context.StringLiteral() != null)
				return new LiteralExpression.StringLiteral(context.StringLiteral().getText());
			else if(context.NoneLiteral() != null)
				return LiteralExpression.NoneLiteral.NONE;
			else if(context.arrayLiteral() != null)
				return new LiteralExpression.ArrayLiteral(context.arrayLiteral());
			throw new CompilationException("Unknown literal: " + context.getText());
		}
		
		public static class ArrayAccessExpression extends PrimaryExpression
		{
			public Expression expression;
			public Expression indexExpression;
			
			public ArrayAccessExpression(PrimaryExpression expr, Expression dimExpr)
			{
				expression = expr;
				indexExpression = dimExpr;
			}
			
			private ArrayAccessExpression(PrimaryExpression expr, TadpoleParser.DimensionContext context)
			{
				this(expr, convert(context.expression()));
			}
			
			@Override
			public Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg)
			{
				InstructionList il = new InstructionList();
				Pair<InstructionList, org.apache.bcel.generic.Type> exprBytecode = expression.toBytecode(cg, mg);
				Pair<InstructionList, org.apache.bcel.generic.Type> indexExprBytecode = indexExpression.toBytecode(cg, mg);
				
				if(!(exprBytecode.getValue() instanceof ArrayType))
					throw new CompilationException("Cannot access non-array as an array");
				ArrayType exprType = (ArrayType) exprBytecode.getValue();
				
				org.apache.bcel.generic.Type indexType = indexExprBytecode.getValue();
				if(!(indexType.normalizeForStackOrLocal().equals(BasicType.INT) && !indexType.equals(BasicType.BOOLEAN)))
					throw new CompilationException("Array cannot be indexed with a non-integer");
				
				il.append(exprBytecode.getKey());
				il.append(indexExprBytecode.getKey());
				il.append(InstructionFactory.createArrayLoad(exprType.getElementType()));
				
				return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, exprType.getElementType());
			}
			
			public Pair<InstructionList, org.apache.bcel.generic.Type> toStoreBytecode(ClassGen cg, MethodGen mg, Expression valueToStore)
			{
				InstructionList il = new InstructionList();
				Pair<InstructionList, org.apache.bcel.generic.Type> exprBytecode = expression.toBytecode(cg, mg);
				Pair<InstructionList, org.apache.bcel.generic.Type> indexExprBytecode = indexExpression.toBytecode(cg, mg);
				
				if(!(exprBytecode.getValue() instanceof ArrayType))
					throw new CompilationException("Cannot access non-array as an array");
				ArrayType exprType = (ArrayType) exprBytecode.getValue();
				
				org.apache.bcel.generic.Type indexType = indexExprBytecode.getValue();
				if(!(indexType.normalizeForStackOrLocal().equals(BasicType.INT) && !indexType.equals(BasicType.BOOLEAN)))
					throw new CompilationException("Array cannot be indexed with a non-integer");
				
				il.append(exprBytecode.getKey());
				il.append(indexExprBytecode.getKey());
				
				Pair<InstructionList, org.apache.bcel.generic.Type> valueToStoreBytecode = valueToStore.toBytecode(cg, mg);
				il.append(valueToStoreBytecode.getKey());
				
				if(exprType.getElementType().getSize() == 2)
					il.append(InstructionConstants.DUP2_X2);
				else // it will probably never be 0
					il.append(InstructionConstants.DUP_X2);
				
				if(!valueToStoreBytecode.getValue().equals(exprType.getElementType()))
					il.append(TypeUtils.cast(valueToStoreBytecode.getValue(), exprType.getElementType(), new InstructionFactory(cg)));
				
				il.append(InstructionFactory.createArrayStore(exprType.getElementType()));
				
				return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, valueToStoreBytecode.getValue());
			}
			
			public Pair<InstructionList, org.apache.bcel.generic.Type> toStoreBytecodeTOS(ClassGen cg, MethodGen mg, org.apache.bcel.generic.Type tosType)
			{
				InstructionList il = new InstructionList();
				Pair<InstructionList, org.apache.bcel.generic.Type> exprBytecode = expression.toBytecode(cg, mg);
				Pair<InstructionList, org.apache.bcel.generic.Type> indexExprBytecode = indexExpression.toBytecode(cg, mg);
				
				if(!(exprBytecode.getValue() instanceof ArrayType))
					throw new CompilationException("Cannot access non-array as an array");
				ArrayType exprType = (ArrayType) exprBytecode.getValue();
				
				org.apache.bcel.generic.Type indexType = indexExprBytecode.getValue();
				if(!(indexType.normalizeForStackOrLocal().equals(BasicType.INT) && !indexType.equals(BasicType.BOOLEAN)))
					throw new CompilationException("Array cannot be indexed with a non-integer");
				
				il.append(exprBytecode.getKey());
				if(tosType.getSize() == 2)
				{
					il.append(InstructionConstants.DUP_X2);
					il.append(InstructionConstants.POP);
					
					il.append(indexExprBytecode.getKey());
					if(indexExprBytecode.getValue().getSize() == 2)
					{
						il.append(InstructionConstants.DUP2_X2);
						il.append(InstructionConstants.POP2);
					}
					else
					{
						il.append(InstructionConstants.DUP_X2);
						il.append(InstructionConstants.POP);
					}
				}
				else
				{
					il.append(InstructionConstants.DUP_X1);
					il.append(InstructionConstants.POP);
					
					il.append(indexExprBytecode.getKey());
					if(indexExprBytecode.getValue().getSize() == 2)
					{
						il.append(InstructionConstants.DUP2_X1);
						il.append(InstructionConstants.POP2);
					}
					else
					{
						il.append(InstructionConstants.DUP_X1);
						il.append(InstructionConstants.POP);
					}
				}
				
				if(!tosType.equals(exprType.getElementType()))
					il.append(TypeUtils.cast(tosType, exprType.getElementType(), new InstructionFactory(cg)));
				
				il.append(InstructionFactory.createArrayStore(exprType.getElementType()));
				
				return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, BasicType.VOID);
			}
		}
		
		public static class CastExpression extends PrimaryExpression
		{
			public Type targetType;
			public Expression expression;
			
			public CastExpression(Type type, Expression expression)
			{
				targetType = type;
				this.expression = expression;
			}
			
			@Override
			public Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg)
			{
				Pair<InstructionList, org.apache.bcel.generic.Type> exprBytecode = expression.toBytecode(cg, mg);
				return new Pair<InstructionList, org.apache.bcel.generic.Type>(TypeUtils.cast(exprBytecode.getValue(), targetType.toBCELType(), new InstructionFactory(cg)), targetType.toBCELType());
					
			}
		}
		
		public static class InstantiationExpression extends PrimaryExpression
		{
			public Type structType;
			public final Expression[] parameters;
			
			public InstantiationExpression(Type type, Expression[] parameters)
			{
				structType = type;
				this.parameters = Arrays.copyOf(parameters, parameters.length);
			}
			
			public InstantiationExpression(Type type, List<Expression> parameters)
			{
				structType = type;
				this.parameters = parameters.stream().toArray(Expression[]::new);
			}
			
			private InstantiationExpression(Type type, TadpoleParser.ExpressionListContext context)
			{
				structType = type;
				parameters = context.expression() != null ? context.expression().stream().map(Expression::convert).toArray(Expression[]::new) : new Expression[0];
			}
			
			@Override
			public Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg)
			{
				InstructionList il = new InstructionList();
				InstructionFactory factory = new InstructionFactory(cg);
				
				Struct struct = Struct.structs.stream().filter(s -> s.moduleName.equals(structType.getModuleName())).filter(s -> s.name.equals(structType.getTypeName())).findFirst().get();
				org.apache.bcel.generic.Type structType = this.structType.toBCELType();
				
				if(struct.parameters.size() != parameters.length)
					throw new CompilationException("Cannot instantiate struct of type " + structType + " with " + parameters.length + " parameters. Only " + struct.parameters.size() + " parameters are allowed");
				
				org.apache.bcel.generic.Type[] parameterTypes = struct.parameters.stream().map(p -> p.getKey().toBCELType()).toArray(org.apache.bcel.generic.Type[]::new);
				@SuppressWarnings("unchecked") // damn compiler giving me a warning or an error no matter what I do
				Pair<InstructionList, org.apache.bcel.generic.Type>[] argBytecode = Arrays.stream(parameters).map(e -> e.toBytecode(cg, mg)).toArray(Pair[]::new);
				
				il.append(factory.createNew((ObjectType) structType));
				il.append(InstructionConstants.DUP);
				for(int i = 0; i < argBytecode.length; i++)
				{
					il.append(argBytecode[i].getKey());
					il.append(TypeUtils.cast(argBytecode[i].getValue(), parameterTypes[i], factory));
				}
				il.append(factory.createInvoke(structType.toString(), "<init>", org.apache.bcel.generic.Type.VOID, parameterTypes, Constants.INVOKESPECIAL));
				
				return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, structType);
			}
		}
		
		public static class WrapExpression extends PrimaryExpression
		{
			public Expression expression;
			
			public WrapExpression(Expression expression)
			{
				this.expression = expression;
			}
			
			@Override
			public Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg)
			{
				return expression.toBytecode(cg, mg);
			}
		}
		
		public static class FieldAccessExpression extends PrimaryExpression
		{
			public Expression expression;
			public final String field;
			
			public FieldAccessExpression(PrimaryExpression expression, String field)
			{
				this.expression = expression;
				this.field = field;
			}
			
			private FieldAccessExpression(TadpoleParser.PrimaryContext context, String field)
			{
				this(Expression.convertPrimary(context), field);
			}
			
			@Override
			public Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg)
			{
				InstructionList il = new InstructionList();
				InstructionFactory factory = new InstructionFactory(cg);
				
				try
				{
					return new Pair<InstructionList, org.apache.bcel.generic.Type>(null, org.apache.bcel.generic.Type.getType(Class.forName(toString())));
				}
				catch(Exception e) {}
				
				if(expression != null)
				{
					Pair<InstructionList, org.apache.bcel.generic.Type> exprBytecode = expression.toBytecode(cg, mg);
					
					if(exprBytecode.getKey() == null)
					{
						try
						{
							Field fieldd = Arrays.stream(Repository.lookupClass(exprBytecode.getValue().toString()).getFields()).filter(f -> f.getName().equals(field)).findFirst().get();
							il.append(factory.createGetStatic(exprBytecode.getValue().toString(), field, fieldd.getType()));
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, fieldd.getType());
						}
						catch(Exception e)
						{
							
						}
					}
					
					for(Struct struct : Struct.structs)
					{
						// skip wrong structs
						if(!(struct.moduleName + "$" + struct.name).equals(exprBytecode.getValue().toString()))
							continue;
						
						for(Triplet<Type, String, Expression> attribute : struct.attributes)
						{
							// skip wrong attributes
							if(!attribute.second.equals(field))
								continue;
							
							il.append(exprBytecode.getKey());
							il.append(factory.createGetField(exprBytecode.getValue().toString(), field, attribute.first.toBCELType()));
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, attribute.first.toBCELType());
						}
					}
					
					throw new CompilationException("Could not find attribute '" + field + "' in struct " + exprBytecode.getValue().toString());
				}
				else
				{
					LocalVariableGen lvg = MethodUtils.getLocalVars(mg).stream().filter(lv -> lv.getName().equals(field) && lv.getEnd() == null).findFirst().orElse(null);
					if(lvg != null)
					{
						il.append(InstructionFactory.createLoad(lvg.getType(), lvg.getIndex()));
						return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, lvg.getType());
					}
					else if(Arrays.stream(cg.getFields()).map(Field::getName).anyMatch(s -> s.equals(field)))
					{
						Field fieldd = Arrays.stream(cg.getFields()).filter(f -> f.getName().equals(field)).findFirst().get();
						il.append(InstructionFactory.createThis());
						il.append(factory.createGetField(cg.getClassName(), field, fieldd.getType()));
						return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, fieldd.getType());
					}
					else
					{
						try
						{
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(null, org.apache.bcel.generic.Type.getType(Class.forName(field)));
						}
						catch(Exception e)
						{
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(null, new Type(field).toBCELType());
						}
					}
				}
			}
			
			public Pair<InstructionList, org.apache.bcel.generic.Type> toStoreBytecode(ClassGen cg, MethodGen mg, Expression valueToStore)
			{
				InstructionList il = new InstructionList();
				InstructionFactory factory = new InstructionFactory(cg);
				
				if(expression != null)
				{
					Pair<InstructionList, org.apache.bcel.generic.Type> exprBytecode = expression.toBytecode(cg, mg);
					
					for(Struct struct : Struct.structs)
					{
						// skip wrong structs
						if(!(struct.moduleName + "$" + struct.name).equals(exprBytecode.getValue().toString()))
							continue;
						
						for(Triplet<Type, String, Expression> attribute : struct.attributes)
						{
							// skip wrong attributes
							if(!attribute.second.equals(field))
								continue;
							
							il.append(exprBytecode.getKey());
							Pair<InstructionList, org.apache.bcel.generic.Type> valueToStoreBytecode = valueToStore.toBytecode(cg, mg);
							il.append(valueToStoreBytecode.getKey());
							
							if(valueToStoreBytecode.getValue().getSize() == 2)
								il.append(InstructionConstants.DUP2_X1);
							else // it will probably never be 0
								il.append(InstructionConstants.DUP_X1);
							
							il.append(TypeUtils.cast(valueToStoreBytecode.getValue(), attribute.first.toBCELType(), factory));
							il.append(factory.createPutField(exprBytecode.getValue().toString(), field, attribute.first.toBCELType()));
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, valueToStoreBytecode.getValue());
						}
					}
					
					throw new CompilationException("Could not find attribute '" + field + "' in struct " + exprBytecode.getValue().toString());
				}
				else
				{
					LocalVariableGen lvg = MethodUtils.getLocalVars(mg).stream().filter(lv -> lv.getName().equals(field) && lv.getEnd() == null).findFirst().orElse(null);
					
					Pair<InstructionList, org.apache.bcel.generic.Type> valueToStoreBytecode = valueToStore.toBytecode(cg, mg);
					
					if(lvg == null)
					{
						if(Arrays.stream(cg.getFields()).map(Field::getName).anyMatch(s -> s.equals(field)))
						{
							Field fieldd = Arrays.stream(cg.getFields()).filter(f -> f.getName().equals(field)).findFirst().get();
							il.append(InstructionFactory.createThis());
							il.append(valueToStoreBytecode.getKey());
							if(valueToStoreBytecode.getValue().getSize() == 2)
								il.append(InstructionConstants.DUP2);
							else
								il.append(InstructionConstants.DUP);
							il.append(TypeUtils.cast(valueToStoreBytecode.getValue(), fieldd.getType(), factory));
							il.append(factory.createPutField(cg.getClassName(), field, fieldd.getType()));
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, valueToStoreBytecode.getValue());
						}
						throw new CompilationException("Could not find local variable '" + field + "' in method " + mg.getName());
					}
					
					il.append(valueToStoreBytecode.getKey());
					if(valueToStoreBytecode.getValue().getSize() == 2)
						il.append(InstructionConstants.DUP2);
					else
						il.append(InstructionConstants.DUP);
					
					il.append(TypeUtils.cast(valueToStoreBytecode.getValue(), lvg.getType(), factory));
					il.append(InstructionFactory.createStore(lvg.getType(), lvg.getIndex()));
					
					return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, valueToStoreBytecode.getValue());
				}
			}
			
			public Pair<InstructionList, org.apache.bcel.generic.Type> toStoreBytecodeTOS(ClassGen cg, MethodGen mg, org.apache.bcel.generic.Type tosType)
			{
				InstructionList il = new InstructionList();
				InstructionFactory factory = new InstructionFactory(cg);
				
				if(expression != null)
				{
					Pair<InstructionList, org.apache.bcel.generic.Type> exprBytecode = expression.toBytecode(cg, mg);
					
					for(Struct struct : Struct.structs)
					{
						// skip wrong structs
						if(!(struct.moduleName + "$" + struct.name).equals(exprBytecode.getValue().toString()))
							continue;
						
						for(Triplet<Type, String, Expression> attribute : struct.attributes)
						{
							// skip wrong attributes
							if(!attribute.second.equals(field))
								continue;
							
							il.append(exprBytecode.getKey());
							if(tosType.getSize() == 2)
							{
								il.append(InstructionConstants.DUP_X2);
								il.append(InstructionConstants.POP);
							}
							else if(tosType.getSize() == 1)
							{
								il.append(InstructionConstants.DUP_X1);
								il.append(InstructionConstants.POP);
							}
							
							il.append(TypeUtils.cast(tosType, attribute.first.toBCELType(), factory));
							il.append(factory.createPutField(exprBytecode.getValue().toString(), field, attribute.first.toBCELType()));
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, BasicType.VOID);
						}
					}
					
					throw new CompilationException("Could not find attribute '" + field + "' in struct " + exprBytecode.getValue().toString());
				}
				else
				{
					LocalVariableGen lvg = MethodUtils.getLocalVars(mg).stream().filter(lv -> lv.getName().equals(field) && lv.getEnd() == null).findFirst().orElse(null);
					if(lvg == null)
					{
						if(Arrays.stream(cg.getFields()).map(Field::getName).anyMatch(s -> s.equals(field)))
						{
							Field fieldd = Arrays.stream(cg.getFields()).filter(f -> f.getName().equals(field)).findFirst().get();
							il.append(InstructionFactory.createThis());
							if(tosType.getSize() == 2)
								il.append(InstructionConstants.DUP_X2);
							else
								il.append(InstructionConstants.DUP_X1);
							il.append(InstructionConstants.POP);
							il.append(TypeUtils.cast(tosType, fieldd.getType(), factory));
							il.append(factory.createPutField(cg.getClassName(), field, fieldd.getType()));
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, tosType);
						}
						throw new CompilationException("Could not find local variable '" + field + "' in method " + mg.getName());
					}
					
					il.append(TypeUtils.cast(tosType, lvg.getType(), factory));
					il.append(InstructionFactory.createStore(lvg.getType(), lvg.getIndex()));
					
					return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, BasicType.VOID);
				}
			}
			
			@Override
			public String toString()
			{
				return expression == null ? field : (expression.toString() + "." + field);
			}
		}
		
		public static class FunctionCallExpression extends PrimaryExpression
		{
			public PrimaryExpression callingOn;
			public final String function;
			public final Expression[] parameters;
			
			private FunctionCallExpression(PrimaryExpression callingOn, TadpoleParser.FunctionCallContext context)
			{
				this.callingOn = callingOn;
				this.function = context.functionName().getText();
				this.parameters = context.expressionList() != null ? context.expressionList().expression().stream().map(Expression::convert).toArray(Expression[]::new) : new Expression[0];
			}
			
			@Override
			public Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg)
			{
				boolean staticCall;
				InstructionList callingOnBytecode = null;
				org.apache.bcel.generic.Type type = null;
				if(callingOn != null)
				{
					Pair<InstructionList, org.apache.bcel.generic.Type> bytecode = callingOn.toBytecode(cg, mg);
					callingOnBytecode = bytecode.getKey();
					type = bytecode.getValue();
					staticCall = callingOnBytecode == null;
				}
				else
				{
					staticCall = true;
					type = org.apache.bcel.generic.Type.getType(Utility.getSignature(cg.getClassName()));
				}
				
				List<Pair<InstructionList, org.apache.bcel.generic.Type>> parameters = Arrays.stream(this.parameters).map(e -> e.toBytecode(cg, mg)).collect(Collectors.toList());
				if(callingOnBytecode == null)
				{
					InstructionList il = new InstructionList();
					try
					{
						JavaClass jc = Repository.lookupClass(type.toString());
						List<Method> methods = Arrays.stream(jc.getMethods()).filter(m -> m.getName().equals(function)).filter(m -> m.isStatic()).collect(Collectors.toList());
						methods.removeIf(m -> m.getArgumentTypes().length != parameters.size());
						Method bestMethod = TypeUtils.findClosestMatch(methods, parameters.stream().map(Pair::getValue).collect(Collectors.toList()), null);
						
						InstructionFactory factory = new InstructionFactory(cg);
						
						for(int i = 0; i < parameters.size(); i++)
						{
							Pair<InstructionList, org.apache.bcel.generic.Type> eBytecode = parameters.get(i);
							il.append(eBytecode.getKey());
							il.append(TypeUtils.cast(eBytecode.getValue(), bestMethod.getArgumentTypes()[i], factory));
						}
						il.append(factory.createInvoke(type.toString(), function, bestMethod.getReturnType(), bestMethod.getArgumentTypes(), Constants.INVOKESTATIC));
						
						return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, bestMethod.getReturnType());
					}
					catch(Exception e)
					{
						Type tempType = new Type(type.toString());
						if(!tempType.isAbsoluteType())
						{
							String module = tempType.typeName;
							List<Function> functions = Module.getModule(module).declaredFunctions;
							functions = functions.stream().filter(f -> f.name.equals(function)).collect(Collectors.toList());
							functions.removeIf(f -> !TypeUtils.areMatching(f.parameters.stream().map(p -> p.getKey().toBCELType()).collect(Collectors.toList()), parameters.stream().map(Pair::getValue).collect(Collectors.toList())));
							if(functions.size() == 0)
								throw new CompilationException("No function named " + function + " with with correct parameters was found in module " + module);
							Function f = TypeUtils.findClosestMatch(functions, parameters.stream().map(Pair::getValue).collect(Collectors.toList()));
							
							InstructionFactory factory = new InstructionFactory(cg);
							
							for(int i = 0; i < parameters.size(); i++)
							{
								Pair<InstructionList, org.apache.bcel.generic.Type> eBytecode = parameters.get(i);
								il.append(eBytecode.getKey());
								il.append(TypeUtils.cast(eBytecode.getValue(), f.parameters.get(i).getKey().toBCELType(), factory));
							}
							il.append(factory.createInvoke(module, function, f.returnType.toBCELType(), f.parameters.stream().map(p -> p.getKey().toBCELType()).toArray(org.apache.bcel.generic.Type[]::new), Constants.INVOKESTATIC));
							
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, f.returnType.toBCELType());
						}
						else
						{
							throw new CompilationException("All struct functions are instance functions. Move the function to a module or create an instance of the struct first."); 
						}
					}
				}
				else
				{
					InstructionList il = new InstructionList();
					il.append(callingOnBytecode);
					try
					{
						JavaClass jc = Repository.lookupClass(type.toString());
						List<Method> methods = Arrays.stream(jc.getMethods()).filter(m -> m.getName().equals(function)).filter(m -> !staticCall || m.isStatic()).collect(Collectors.toList());
						methods.removeIf(m -> m.getArgumentTypes().length != parameters.size());
						Method bestMethod = TypeUtils.findClosestMatch(methods, parameters.stream().map(Pair::getValue).collect(Collectors.toList()), null);
						
						InstructionFactory factory = new InstructionFactory(cg);
						
						for(int i = 0; i < parameters.size(); i++)
						{
							Pair<InstructionList, org.apache.bcel.generic.Type> eBytecode = parameters.get(i);
							il.append(eBytecode.getKey());
							il.append(TypeUtils.cast(eBytecode.getValue(), bestMethod.getArgumentTypes()[i], factory));
						}
						il.append(factory.createInvoke(type.toString(), function, bestMethod.getReturnType(), bestMethod.getArgumentTypes(), bestMethod.isStatic() ? Constants.INVOKESTATIC : Constants.INVOKEVIRTUAL));
						
						return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, bestMethod.getReturnType());
					}
					catch(Exception e)
					{
						Type tempType = new Type(type.toString());
						if(!tempType.isAbsoluteType())
						{
							String module = tempType.typeName;
							List<Function> functions = Module.getModule(module).declaredFunctions;
							functions = functions.stream().filter(f -> f.name.equals(function)).collect(Collectors.toList());
							functions.removeIf(f -> !TypeUtils.areMatching(f.parameters.stream().map(p -> p.getKey().toBCELType()).collect(Collectors.toList()), parameters.stream().map(Pair::getValue).collect(Collectors.toList())));
							if(functions.size() == 0)
								throw new CompilationException("No function named " + function + " with with correct parameters was found in module " + module);
							Function f = TypeUtils.findClosestMatch(functions, parameters.stream().map(Pair::getValue).collect(Collectors.toList()));
							
							InstructionFactory factory = new InstructionFactory(cg);
							
							for(int i = 0; i < parameters.size(); i++)
							{
								Pair<InstructionList, org.apache.bcel.generic.Type> eBytecode = parameters.get(i);
								il.append(eBytecode.getKey());
								il.append(TypeUtils.cast(eBytecode.getValue(), f.parameters.get(i).getKey().toBCELType(), factory));
							}
							il.append(factory.createInvoke(module, function, f.returnType.toBCELType(), f.parameters.stream().map(p -> p.getKey().toBCELType()).toArray(org.apache.bcel.generic.Type[]::new), Constants.INVOKESTATIC));
							
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, f.returnType.toBCELType());
						}
						else
						{
							List<Function> functions = Module.getModule(tempType.getModuleName()).declaredStructs.stream().filter(s -> s.name.equals(tempType.getTypeName())).findFirst().get().functions;
							functions = functions.stream().filter(f -> f.name.equals(function)).collect(Collectors.toList());
							functions.removeIf(f -> !TypeUtils.areMatching(f.parameters.stream().map(p -> p.getKey().toBCELType()).collect(Collectors.toList()), parameters.stream().map(Pair::getValue).collect(Collectors.toList())));
							if(functions.size() == 0)
								throw new CompilationException("No function named " + function + " with with correct parameters was found in struct " + tempType.typeName);
							Function f = TypeUtils.findClosestMatch(functions, parameters.stream().map(Pair::getValue).collect(Collectors.toList()));
							
							InstructionFactory factory = new InstructionFactory(cg);
							
							for(int i = 0; i < parameters.size(); i++)
							{
								Pair<InstructionList, org.apache.bcel.generic.Type> eBytecode = parameters.get(i);
								il.append(eBytecode.getKey());
								il.append(TypeUtils.cast(eBytecode.getValue(), f.parameters.get(i).getKey().toBCELType(), factory));
							}
							il.append(factory.createInvoke(type.toString(), function, f.returnType.toBCELType(), f.parameters.stream().map(p -> p.getKey().toBCELType()).toArray(org.apache.bcel.generic.Type[]::new), Constants.INVOKEVIRTUAL));
							
							return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, f.returnType.toBCELType());
						}
					}
				}
			}
		}
	}
	
	public static class UnaryExpression extends Expression
	{
		public Expression expr;
		public final UnaryOp op;
		public final boolean postfix;
		
		public UnaryExpression(UnaryOp op, Expression expr, boolean postfix)
		{
			this.op = op;
			this.expr = expr;
			this.postfix = postfix;
		}
		
		public UnaryExpression(UnaryOp op, Expression expr)
		{
			this(op, expr, false);
		}
		
		@Override
		public Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg)
		{
			Pair<InstructionList, org.apache.bcel.generic.Type> eBytecode = expr.toBytecode(cg, mg);
			InstructionFactory factory = new InstructionFactory(cg);
			InstructionList il = new InstructionList();
			org.apache.bcel.generic.Type retType = eBytecode.getValue();
			il.append(eBytecode.getKey());
			switch(op)
			{
			case POSITIVE:
				break;
			case NEGATIVE:
				if(eBytecode.getValue().getSize() == 1)
					if(eBytecode.getValue().equals(BasicType.FLOAT))
						il.append(InstructionConstants.FNEG);
					else
						il.append(InstructionConstants.INEG);
				else
					if(eBytecode.getValue().equals(BasicType.DOUBLE))
						il.append(InstructionConstants.DNEG);
					else
						il.append(InstructionConstants.LNEG);
				break;
			case NOT:
				BranchHandle bh = il.append(new IFEQ(null));
				il.append(InstructionConstants.ICONST_0);
				BranchHandle bh2 = il.append(new GOTO(null));
				bh.setTarget(il.append(InstructionConstants.ICONST_1));
				bh2.setTarget(il.append(InstructionConstants.NOP));
				retType = BasicType.BOOLEAN;
				break;
			case UNARY_NEGATE:
				if(eBytecode.getValue().getSize() == 1)
				{
					il.append(InstructionConstants.ICONST_M1);
					il.append(InstructionConstants.IXOR);
				}
				else
				{
					il.append(factory.createConstant(Long.valueOf(-1)));
					il.append(InstructionConstants.LXOR);
				}
				break;
			case INCREMENT:
			case DECREMENT:
				throw new CompilationException("increment/decrement should have been mapped to a binary expression");
			default:
				throw new IllegalStateException("Unknown unary operator: " + op);
			}
			
			return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, retType);
		}
	}
	
	public static class BinaryExpression extends Expression
	{
		public Expression exprLeft, exprRight;
		public final BinaryOp op;
		
		public BinaryExpression(Expression exprLeft, BinaryOp op, Expression exprRight)
		{
			this.exprLeft = exprLeft;
			this.exprRight = exprRight;
			this.op = op;
		}
		
		@Override
		public Pair<InstructionList, org.apache.bcel.generic.Type> toBytecode(ClassGen cg, MethodGen mg)
		{
			// parallel assignments are handled in Statement.ParallelStatement
			if(op.equals(BinaryOp.ASSIGN) && (exprLeft instanceof PrimaryExpression.ArrayAccessExpression || exprLeft instanceof PrimaryExpression.FieldAccessExpression))
			{
				if(exprLeft instanceof PrimaryExpression.ArrayAccessExpression)
				{
					// handle storing in an array
					return ((PrimaryExpression.ArrayAccessExpression) exprLeft).toStoreBytecode(cg, mg, exprRight);
				}
				else
				{
					// handle storing in a field
					return ((PrimaryExpression.FieldAccessExpression) exprLeft).toStoreBytecode(cg, mg, exprRight);
				}
			}
			
			InstructionList il = new InstructionList();
			InstructionFactory factory = new InstructionFactory(cg);
			Pair<InstructionList, org.apache.bcel.generic.Type> exprLeftBytecode = exprLeft.toBytecode(cg, mg);
			Pair<InstructionList, org.apache.bcel.generic.Type> exprRightBytecode = exprRight.toBytecode(cg, mg);
			
			InstructionList ilLeft = exprLeftBytecode.getKey();
			InstructionList ilRight = exprRightBytecode.getKey();
			org.apache.bcel.generic.Type tLeft = exprLeftBytecode.getValue();
			org.apache.bcel.generic.Type tRight = exprRightBytecode.getValue();
			org.apache.bcel.generic.Type retType = tLeft;
			
			switch(op)
			{
			case POWER:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					il.append(ilLeft);
					il.append(TypeUtils.cast(tLeft, BasicType.DOUBLE, factory));
					il.append(ilRight);
					il.append(TypeUtils.cast(tRight, BasicType.DOUBLE, factory));
					il.append(factory.createInvoke("java.lang.Math", "pow", BasicType.DOUBLE, new org.apache.bcel.generic.Type[]{BasicType.DOUBLE, BasicType.DOUBLE}, Constants.INVOKESTATIC));
					retType = BasicType.DOUBLE;
				}
				else
				{
					throw new CompilationException("Cannot use power operator with non-numeric types");
				}
				break;
			case MULTIPLY:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DMUL : InstructionConstants.FMUL);
						retType = wide ? BasicType.DOUBLE : BasicType.FLOAT;
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(wide ? InstructionConstants.LMUL : InstructionConstants.IMUL);
						retType = wide ? BasicType.LONG : BasicType.INT;
					}
				}
				else
				{
					// TODO add string-int multiplication
					throw new CompilationException("Cannot use multiplication operator with non-numeric types");
				}
				break;
			case DIVIDE:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DDIV : InstructionConstants.FDIV);
						retType = wide ? BasicType.DOUBLE : BasicType.FLOAT;
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(wide ? InstructionConstants.LDIV : InstructionConstants.IDIV);
						retType = wide ? BasicType.LONG : BasicType.INT;
					}
				}
				else
				{
					throw new CompilationException("Cannot use division operator with non-numeric types");
				}
				break;
			case MODULUS:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DREM : InstructionConstants.FREM);
						retType = wide ? BasicType.DOUBLE : BasicType.FLOAT;
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(wide ? InstructionConstants.LREM : InstructionConstants.IREM);
						retType = wide ? BasicType.LONG : BasicType.INT;
					}
				}
				else
				{
					throw new CompilationException("Cannot use modulus operator with non-numeric types");
				}
				break;
			case ADD:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DADD : InstructionConstants.FADD);
						retType = wide ? BasicType.DOUBLE : BasicType.FLOAT;
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(wide ? InstructionConstants.LADD : InstructionConstants.IADD);
						retType = wide ? BasicType.LONG : BasicType.INT;
					}
				}
				else if(TypeUtils.isOfType(tLeft, TypeUtils.STRING) || TypeUtils.isOfType(tRight, TypeUtils.STRING))
				{
					if(tLeft.equals(org.apache.bcel.generic.Type.STRING) && tRight.equals(org.apache.bcel.generic.Type.STRING))
					{
						il.append(ilLeft);
						il.append(ilRight);
						il.append(factory.createInvoke("java.lang.String", "concat", org.apache.bcel.generic.Type.STRING, new org.apache.bcel.generic.Type[]{org.apache.bcel.generic.Type.STRING}, Constants.INVOKEVIRTUAL));
					}
					else if(tLeft.equals(org.apache.bcel.generic.Type.STRING))
					{
						if(tRight.equals(BasicType.SHORT) || tRight.equals(BasicType.BYTE))
							tRight = tRight.normalizeForStackOrLocal();
						else if(tRight instanceof ReferenceType && !tRight.equals(new ArrayType(BasicType.CHAR, 1)))
							tRight = org.apache.bcel.generic.Type.OBJECT;
						il.append(ilLeft);
						il.append(ilRight);
						il.append(factory.createInvoke("java.lang.String", "valueOf", org.apache.bcel.generic.Type.STRING, new org.apache.bcel.generic.Type[]{tRight}, Constants.INVOKESTATIC));
						il.append(factory.createInvoke("java.lang.String", "concat", org.apache.bcel.generic.Type.STRING, new org.apache.bcel.generic.Type[]{org.apache.bcel.generic.Type.STRING}, Constants.INVOKEVIRTUAL));
					}
					else if(tRight.equals(org.apache.bcel.generic.Type.STRING))
					{
						if(tLeft.equals(BasicType.SHORT) || tLeft.equals(BasicType.BYTE))
							tLeft = tLeft.normalizeForStackOrLocal();
						else if(tLeft instanceof ReferenceType && !tLeft.equals(new ArrayType(BasicType.CHAR, 1)))
							tLeft = org.apache.bcel.generic.Type.OBJECT;
						il.append(ilLeft);
						il.append(factory.createInvoke("java.lang.String", "valueOf", org.apache.bcel.generic.Type.STRING, new org.apache.bcel.generic.Type[]{tLeft}, Constants.INVOKESTATIC));
						il.append(ilRight);
						il.append(factory.createInvoke("java.lang.String", "concat", org.apache.bcel.generic.Type.STRING, new org.apache.bcel.generic.Type[]{org.apache.bcel.generic.Type.STRING}, Constants.INVOKEVIRTUAL));
					}
					retType = org.apache.bcel.generic.Type.STRING;
				}
				else
				{
					throw new CompilationException("Cannot use addition operator with non-numeric types");
				}
				break;
			case SUBTRACT:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DSUB : InstructionConstants.FSUB);
						retType = wide ? BasicType.DOUBLE : BasicType.FLOAT;
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(wide ? InstructionConstants.LSUB : InstructionConstants.ISUB);
						retType = wide ? BasicType.LONG : BasicType.INT;
					}
				}
				else
				{
					throw new CompilationException("Cannot use subtraction operator with non-numeric types");
				}
				break;
			case RIGHT_SHIFT_PRESERVE:
				if(TypeUtils.isOfType(tLeft, TypeUtils.INT) && TypeUtils.isOfType(tRight, TypeUtils.INT))
				{
					boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
					il.append(ilLeft);
					il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(ilRight);
					il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(wide ? InstructionConstants.LSHR : InstructionConstants.ISHR);
					retType = wide ? BasicType.LONG : BasicType.INT;
				}
				else
				{
					throw new CompilationException("Cannot use signed right shift operator with non-integer types");
				}
				break;
			case RIGHT_SHIFT:
				if(TypeUtils.isOfType(tLeft, TypeUtils.INT) && TypeUtils.isOfType(tRight, TypeUtils.INT))
				{
					boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
					il.append(ilLeft);
					il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(ilRight);
					il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(wide ? InstructionConstants.LUSHR : InstructionConstants.IUSHR);
					retType = wide ? BasicType.LONG : BasicType.INT;
				}
				else
				{
					throw new CompilationException("Cannot use unsigned right shift operator with non-integer types");
				}
				break;
			case LEFT_SHIFT:
				if(TypeUtils.isOfType(tLeft, TypeUtils.INT) && TypeUtils.isOfType(tRight, TypeUtils.INT))
				{
					boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
					il.append(ilLeft);
					il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(ilRight);
					il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(wide ? InstructionConstants.LSHL : InstructionConstants.ISHL);
					retType = wide ? BasicType.LONG : BasicType.INT;
				}
				else
				{
					throw new CompilationException("Cannot use left shift operator with non-integer types");
				}
				break;
			case LESS_THAN:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DCMPG : InstructionConstants.FCMPG);
						il.append(InstructionConstants.ICONST_0);
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						
						if(wide)
						{
							il.append(InstructionConstants.LCMP);
							il.append(InstructionConstants.ICONST_0);
						}
					}
					
					BranchHandle bh = il.append(new IF_ICMPLT(null));
					il.append(InstructionConstants.ICONST_0);
					BranchHandle bh2 = il.append(new GOTO(null));
					bh.setTarget(il.append(InstructionConstants.ICONST_1));
					bh2.setTarget(il.append(InstructionConstants.NOP));
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot compare non-numeric types");
				}
				break;
			case GREATER_THAN:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DCMPL : InstructionConstants.FCMPL);
						il.append(InstructionConstants.ICONST_0);
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						
						if(wide)
						{
							il.append(InstructionConstants.LCMP);
							il.append(InstructionConstants.ICONST_0);
						}
					}
					
					BranchHandle bh = il.append(new IF_ICMPGT(null));
					il.append(InstructionConstants.ICONST_0);
					BranchHandle bh2 = il.append(new GOTO(null));
					bh.setTarget(il.append(InstructionConstants.ICONST_1));
					bh2.setTarget(il.append(InstructionConstants.NOP));
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot compare non-numeric types");
				}
				break;
			case LESS_THAN_EQUAL:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DCMPG : InstructionConstants.FCMPG);
						
						il.append(InstructionConstants.ICONST_1);
						il.append(InstructionConstants.ISUB);
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						
						if(wide)
						{
							il.append(InstructionConstants.LCMP);
							il.append(InstructionConstants.ICONST_0);
						}
						
						BranchHandle bh = il.append(new IF_ICMPLE(null));
						il.append(InstructionConstants.ICONST_0);
						BranchHandle bh2 = il.append(new GOTO(null));
						bh.setTarget(il.append(InstructionConstants.ICONST_1));
						bh2.setTarget(il.append(InstructionConstants.NOP));
					}
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot compare non-numeric types");
				}
				break;
			case GREATER_THAN_EQUAL:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(wide ? InstructionConstants.DCMPL : InstructionConstants.FCMPL);
						
						il.append(InstructionConstants.ICONST_1);
						il.append(InstructionConstants.IADD);
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						
						if(wide)
						{
							il.append(InstructionConstants.LCMP);
							il.append(InstructionConstants.ICONST_0);
						}
						
						BranchHandle bh = il.append(new IF_ICMPGE(null));
						il.append(InstructionConstants.ICONST_0);
						BranchHandle bh2 = il.append(new GOTO(null));
						bh.setTarget(il.append(InstructionConstants.ICONST_1));
						bh2.setTarget(il.append(InstructionConstants.NOP));
					}
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot compare non-numeric types");
				}
				break;
			case IS:
				// TODO implement when inheritance is implemented
				throw new IllegalStateException("'is' operator is not yet implemented");
			case EQUALS:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(InstructionConstants.DCMPL);
						
						il.append(InstructionConstants.ICONST_1);
						il.append(InstructionConstants.IAND);
						il.append(InstructionConstants.ICONST_1);
						il.append(InstructionConstants.IXOR);
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						
						BranchHandle bh = il.append(new IF_ICMPEQ(null));
						il.append(InstructionConstants.ICONST_0);
						BranchHandle bh2 = il.append(new GOTO(null));
						bh.setTarget(il.append(InstructionConstants.ICONST_1));
						bh2.setTarget(il.append(InstructionConstants.NOP));
					}
				}
				else if(tLeft.equals(BasicType.BOOLEAN) && tRight.equals(BasicType.BOOLEAN))
				{
					il.append(ilLeft);
					il.append(ilRight);
					
					BranchHandle bh = il.append(new IF_ICMPEQ(null));
					il.append(InstructionConstants.ICONST_0);
					BranchHandle bh2 = il.append(new GOTO(null));
					bh.setTarget(il.append(InstructionConstants.ICONST_1));
					bh2.setTarget(il.append(InstructionConstants.NOP));
				}
				else if(TypeUtils.isOfType(tLeft, TypeUtils.OBJECT) && TypeUtils.isOfType(tRight, TypeUtils.OBJECT))
				{
					il.append(ilLeft);
					il.append(ilRight);
					
					BranchHandle bh = il.append(new IF_ACMPEQ(null));
					il.append(InstructionConstants.ICONST_0);
					BranchHandle bh2 = il.append(new GOTO(null));
					bh.setTarget(il.append(InstructionConstants.ICONST_1));
					bh2.setTarget(il.append(InstructionConstants.NOP));
				}
				else
				{
					throw new CompilationException("Cannot check equality of incompatible objects of type " + tLeft + " and " + tRight);
				}
				retType = BasicType.BOOLEAN;
				break;
			case NOT_EQUAL:
				if(TypeUtils.isOfType(tLeft, TypeUtils.NUMBER) && TypeUtils.isOfType(tRight, TypeUtils.NUMBER))
				{
					if(TypeUtils.isOfType(tLeft, TypeUtils.DOUBLE) || TypeUtils.isOfType(tRight, TypeUtils.DOUBLE))
					{
						boolean wide = tLeft.equals(BasicType.DOUBLE) || tRight.equals(BasicType.DOUBLE);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.DOUBLE : BasicType.FLOAT, factory));
						il.append(InstructionConstants.DCMPL);
						
						il.append(InstructionConstants.ICONST_1);
						il.append(InstructionConstants.IAND);
					}
					else
					{
						boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
						il.append(ilLeft);
						il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
						il.append(ilRight);
						il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
						
						BranchHandle bh = il.append(new IF_ICMPEQ(null));
						il.append(InstructionConstants.ICONST_1);
						BranchHandle bh2 = il.append(new GOTO(null));
						bh.setTarget(il.append(InstructionConstants.ICONST_0));
						bh2.setTarget(il.append(InstructionConstants.NOP));
					}
				}
				else if(tLeft.equals(BasicType.BOOLEAN) && tRight.equals(BasicType.BOOLEAN))
				{
					il.append(ilLeft);
					il.append(ilRight);
					
					BranchHandle bh = il.append(new IF_ICMPEQ(null));
					il.append(InstructionConstants.ICONST_1);
					BranchHandle bh2 = il.append(new GOTO(null));
					bh.setTarget(il.append(InstructionConstants.ICONST_0));
					bh2.setTarget(il.append(InstructionConstants.NOP));
				}
				else if(TypeUtils.isOfType(tLeft, TypeUtils.OBJECT) && TypeUtils.isOfType(tRight, TypeUtils.OBJECT))
				{
					il.append(ilLeft);
					il.append(ilRight);
					
					BranchHandle bh = il.append(new IF_ACMPEQ(null));
					il.append(InstructionConstants.ICONST_1);
					BranchHandle bh2 = il.append(new GOTO(null));
					bh.setTarget(il.append(InstructionConstants.ICONST_0));
					bh2.setTarget(il.append(InstructionConstants.NOP));
				}
				else
				{
					throw new CompilationException("Cannot check equality of incompatible objects of type " + tLeft + " and " + tRight);
				}
				retType = BasicType.BOOLEAN;
				break;
			case BITWISE_AND:
				if(TypeUtils.isOfType(tLeft, TypeUtils.INT) && TypeUtils.isOfType(tRight, TypeUtils.INT))
				{
					boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
					il.append(ilLeft);
					il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(ilRight);
					il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(wide ? InstructionConstants.LAND : InstructionConstants.IAND);
					retType = wide ? BasicType.LONG : BasicType.INT;
				}
				else if(TypeUtils.isOfType(tLeft, TypeUtils.BOOLEAN) && TypeUtils.isOfType(tRight, TypeUtils.BOOLEAN))
				{
					il.append(ilLeft);
					il.append(ilRight);
					il.append(InstructionConstants.IAND);
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot perform bitwise operations on non-integer types");
				}
				break;
			case XOR:
				if(TypeUtils.isOfType(tLeft, TypeUtils.INT) && TypeUtils.isOfType(tRight, TypeUtils.INT))
				{
					boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
					il.append(ilLeft);
					il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(ilRight);
					il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(wide ? InstructionConstants.LXOR : InstructionConstants.IXOR);
					retType = wide ? BasicType.LONG : BasicType.INT;
				}
				else if(TypeUtils.isOfType(tLeft, TypeUtils.BOOLEAN) && TypeUtils.isOfType(tRight, TypeUtils.BOOLEAN))
				{
					il.append(ilLeft);
					il.append(ilRight);
					il.append(InstructionConstants.IXOR);
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot perform bitwise operations on non-integer types");
				}
				break;
			case BITWISE_OR:
				if(TypeUtils.isOfType(tLeft, TypeUtils.INT) && TypeUtils.isOfType(tRight, TypeUtils.INT))
				{
					boolean wide = tLeft.equals(BasicType.LONG) || tRight.equals(BasicType.LONG);
					il.append(ilLeft);
					il.append(TypeUtils.cast(tLeft, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(ilRight);
					il.append(TypeUtils.cast(tRight, wide ? BasicType.LONG : BasicType.INT, factory));
					il.append(wide ? InstructionConstants.LOR : InstructionConstants.IOR);
					retType = wide ? BasicType.LONG : BasicType.INT;
				}
				else if(TypeUtils.isOfType(tLeft, TypeUtils.BOOLEAN) && TypeUtils.isOfType(tRight, TypeUtils.BOOLEAN))
				{
					il.append(ilLeft);
					il.append(ilRight);
					il.append(InstructionConstants.IOR);
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot perform bitwise operations on non-integer types");
				}
				break;
			case AND:
				if(tLeft.equals(BasicType.BOOLEAN) && tRight.equals(BasicType.BOOLEAN))
				{
					il.append(ilLeft);
					BranchHandle bh = il.append(new IFEQ(null));
					il.append(ilRight);
					BranchHandle bh2 = il.append(new GOTO(null));
					bh.setTarget(il.append(InstructionConstants.ICONST_0));
					bh2.setTarget(il.append(InstructionConstants.NOP));
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot perform boolean operations on non-booleans");
				}
				break;
			case OR:
				if(tLeft.equals(BasicType.BOOLEAN) && tRight.equals(BasicType.BOOLEAN))
				{
					il.append(ilLeft);
					BranchHandle bh = il.append(new IFNE(null));
					il.append(ilRight);
					BranchHandle bh2 = il.append(new GOTO(null));
					bh.setTarget(il.append(InstructionConstants.ICONST_1));
					bh2.setTarget(il.append(InstructionConstants.NOP));
					retType = BasicType.BOOLEAN;
				}
				else
				{
					throw new CompilationException("Cannot perform boolean operations on non-booleans");
				}
				break;
			default:
				throw new CompilationException("This shouldn't have happened. Encountered BinaryOp: " + op);
			}
			
			return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, retType);
		}
	}
}