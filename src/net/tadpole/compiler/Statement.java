package net.tadpole.compiler;

import static net.tadpole.compiler.ast.StatementNode.T_EXPRESSION;
import static net.tadpole.compiler.ast.StatementNode.T_IF;
import static net.tadpole.compiler.ast.StatementNode.T_LOCAL_VAR;
import static net.tadpole.compiler.ast.StatementNode.T_RECALL;
import static net.tadpole.compiler.ast.StatementNode.T_RETURN;
import static net.tadpole.compiler.ast.StatementNode.T_WHILE;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.tadpole.compiler.ast.Expression;
import net.tadpole.compiler.ast.StatementNode;
import net.tadpole.compiler.exceptions.CompilationException;

public abstract class Statement
{
	public static Statement convert(StatementNode sn)
	{
		switch(sn.type)
		{
		case T_EXPRESSION:
			return new ExpressionStatement(sn.expression);
		case T_RECALL:
			return RecallStatement.INSTANCE;
		case T_RETURN:
			return new ReturnStatement(sn.expression);
		case T_LOCAL_VAR:
			return new LocalVarDecStatement(sn.localVarDec);
		case T_IF:
			return new IfStatement(sn.expression, sn.statements.stream().map(Statement::convert).collect(Collectors.toList()));
		case T_WHILE:
			return new WhileStatement(sn.expression, sn.statements.stream().map(Statement::convert).collect(Collectors.toList()));
		default:
			throw new CompilationException("Unknown statement with type id: " + sn.type);
		}
	}
	
	public static ParallelStatement createParallelStatement(List<Statement> statements)
	{
		List<Expression.BinaryExpression> bes = statements.stream().map(s -> ((ExpressionStatement) s).expression).map(e -> (Expression.BinaryExpression) e).collect(Collectors.toList());
		List<Expression> left = new ArrayList<Expression>();
		List<Expression> right = new ArrayList<Expression>();
		bes.forEach(be -> {
			left.add(be.exprLeft);
			right.add(be.exprRight);
		});
		
		return new ParallelStatement(left, right);
	}
	
	public static class ExpressionStatement extends Statement
	{
		public final Expression expression;
		
		public ExpressionStatement(Expression expression)
		{
			this.expression = expression;
		}
	}
	
	public static class RecallStatement extends Statement
	{
		public static final RecallStatement INSTANCE = new RecallStatement();
		
		private RecallStatement() {}
	}
	
	public static class ReturnStatement extends Statement
	{
		public final Expression expression;
		
		public ReturnStatement(Expression expression)
		{
			this.expression = expression;
		}
	}
	
	public static class LocalVarDecStatement extends Statement
	{
		public Type type;
		public final String name;
		public final Expression expression;
		
		public LocalVarDecStatement(LocalVariable lv)
		{
			type = lv.type;
			name = lv.name;
			expression = lv.expression;
		}
	}
	
	public static class IfStatement extends Statement
	{
		public final Expression expression;
		public final List<Statement> statements;
		
		public IfStatement(Expression expression, List<Statement> statements)
		{
			this.expression = expression;
			this.statements = statements;
		}
	}
	
	public static class WhileStatement extends Statement
	{
		public final Expression expression;
		public final List<Statement> statements;
		
		public WhileStatement(Expression expression, List<Statement> statements)
		{
			this.expression = expression;
			this.statements = statements;
		}
	}
	
	public static class ParallelStatement extends Statement
	{
		public final List<Expression> leftExprs;
		public final List<Expression> rightExprs;
		
		public ParallelStatement(List<Expression> leftExprs, List<Expression> rightExprs)
		{
			if(leftExprs.size() != rightExprs.size())
				throw new IllegalStateException("Parrallel statement requires equal list lengths: " + leftExprs.size() + " != " + rightExprs.size());
			this.leftExprs = leftExprs;
			this.rightExprs = rightExprs;
		}
	}
}