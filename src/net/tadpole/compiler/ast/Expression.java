package net.tadpole.compiler.ast;

import java.util.Arrays;
import java.util.List;

import net.tadpole.compiler.Type;
import net.tadpole.compiler.exceptions.CompilationException;
import net.tadpole.compiler.parser.TadpoleParser;

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
		else if(context.structName() != null)
			return new PrimaryExpression.InstantiationExpression(new Type(context.structName().getText()), context.expressionList());
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
				this(expr, new LiteralExpression.IntLiteral(context.IntegerLiteral().getText()));
			}
		}
		
		public static class CastExpression extends PrimaryExpression
		{
			public final Type targetType;
			public Expression expression;
			
			public CastExpression(Type type, Expression expression)
			{
				targetType = type;
				this.expression = expression;
			}
		}
		
		public static class InstantiationExpression extends PrimaryExpression
		{
			public final Type structType;
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
		}
		
		public static class WrapExpression extends PrimaryExpression
		{
			public Expression expression;
			
			public WrapExpression(Expression expression)
			{
				this.expression = expression;
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
	}
}