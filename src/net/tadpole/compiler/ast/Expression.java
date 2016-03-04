package net.tadpole.compiler.ast;

import java.util.Arrays;
import java.util.List;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;

import javafx.util.Pair;
import net.tadpole.compiler.Struct;
import net.tadpole.compiler.Type;
import net.tadpole.compiler.exceptions.CompilationException;
import net.tadpole.compiler.parser.TadpoleParser;
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
			return new PrimaryExpression.FunctionCallExpression(context.functionCall());
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
				if(!valueToStoreBytecode.getValue().equals(exprType.getElementType()))
					il.append(TypeUtils.cast(valueToStoreBytecode.getValue(), exprType.getElementType(), new InstructionFactory(cg)));
				
				if(exprType.getElementType().getSize() == 2)
					il.append(InstructionConstants.DUP2_X2);
				else // it will probably never be 0
					il.append(InstructionConstants.DUP_X2);
				
				il.append(InstructionFactory.createArrayStore(exprType.getElementType()));
				
				return new Pair<InstructionList, org.apache.bcel.generic.Type>(il, exprType.getElementType());
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
				
			}
		}
		
		public static class FunctionCallExpression extends PrimaryExpression
		{
			public final String function;
			public final Expression[] parameters;
			
			public FunctionCallExpression(String function, Expression[] parameters)
			{
				this.function = function;
				this.parameters = Arrays.copyOf(parameters, parameters.length);
			}
			
			public FunctionCallExpression(String function, List<Expression> parameters)
			{
				this.function = function;
				this.parameters = parameters.toArray(new Expression[parameters.size()]);
			}
			
			private FunctionCallExpression(TadpoleParser.FunctionCallContext context)
			{
				this.function = context.functionName().getText();
				this.parameters = context.expressionList() != null ? context.expressionList().expression().stream().map(Expression::convert).toArray(Expression[]::new) : new Expression[0];
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
			if(op.equals(BinaryOp.ASSIGN) && exprLeft instanceof PrimaryExpression.ArrayAccessExpression)
			{
				// handle storing in an array
			}
		}
	}
}