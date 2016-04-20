package net.tadpole.compiler;

import static net.tadpole.compiler.ast.StatementNode.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.BranchHandle;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.GOTO;
import org.apache.bcel.generic.IF_ICMPEQ;
import org.apache.bcel.generic.IF_ICMPNE;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MethodGen;

import javafx.util.Pair;
import net.tadpole.compiler.ast.Expression;
import net.tadpole.compiler.ast.StatementNode;
import net.tadpole.compiler.exceptions.CompilationException;
import net.tadpole.compiler.util.MethodUtils;
import net.tadpole.compiler.util.TypeUtils;

public abstract class Statement
{
	public static Statement convert(StatementNode sn)
	{
		switch(sn.type)
		{
		case T_EXPRESSION:
			return new ExpressionStatement(sn.expressions.get(0));
		case T_RECALL:
			return RecallStatement.INSTANCE;
		case T_RETURN:
			return new ReturnStatement(sn.expressions.get(0));
		case T_LOCAL_VAR:
			return new LocalVarDecStatement(sn.localVarDec);
		case T_IF:
			return new IfStatement(sn.expressions.get(0), sn.statements.stream().map(Statement::convert).collect(Collectors.toList()));
		case T_WHILE:
			return new WhileStatement(sn.expressions.get(0), sn.statements.stream().map(Statement::convert).findFirst().get());
		case T_BLOCK:
			return new BlockStatement(sn.statements.stream().map(Statement::convert).collect(Collectors.toList()));
		case T_DO_WHILE:
			if(sn.expressions.size() == 2)
				return new DoWhileStatement(sn.expressions);
			else
				return new DoWhileStatement(sn.expressions.get(0), sn.statements.stream().map(Statement::convert).findFirst().get());
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
	
	public abstract InstructionList toBytecode(ClassGen cg, MethodGen mg);
	
	public static class BlockStatement extends Statement
	{
		public final List<Statement> statements;
		
		public BlockStatement(List<Statement> statements)
		{
			this.statements = statements;
		}
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			InstructionList il = new InstructionList();
			statements.forEach(s -> il.append(s.toBytecode(cg, mg)));
			List<LocalVariableGen> lvgs = MethodUtils.getLocalVars(mg);
			for(LocalVariableGen lvg : lvgs)
				if(lvg.getStart() != null && lvg.getEnd() == null && il.contains(lvg.getStart()))
					lvg.setEnd(il.getEnd());
			
			return il;
		}
	}
	
	public static class ExpressionStatement extends Statement
	{
		public final Expression expression;
		
		public ExpressionStatement(Expression expression)
		{
			this.expression = expression;
		}
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			Pair<InstructionList, org.apache.bcel.generic.Type> exprData = expression.toBytecode(cg, mg);
			InstructionList il = exprData.getKey();
			org.apache.bcel.generic.Type stackTop = exprData.getValue();
			if(stackTop.getSize() == 1)
				il.append(InstructionConstants.POP);
			else if(stackTop.getSize() == 2)
				il.append(InstructionConstants.POP2);
			
			return il;
		}
	}
	
	public static class RecallStatement extends Statement
	{
		public static final RecallStatement INSTANCE = new RecallStatement();
		
		private RecallStatement() {}
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			InstructionList il = new InstructionList();
			il.append(new GOTO(mg.getInstructionList().getStart()));
			return il;
		}
	}
	
	public static class ReturnStatement extends Statement
	{
		public final Expression expression;
		
		public ReturnStatement(Expression expression)
		{
			this.expression = expression;
		}
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			if((mg.getReturnType().equals(BasicType.VOID) && expression != null) || (!mg.getReturnType().equals(BasicType.VOID) && expression == null))
				throw new CompilationException("Return statement error in fucntion " + mg.getName() + " in class " + cg.getClassName());
			
			InstructionList il = new InstructionList();
			if(expression != null)
			{
				Pair<InstructionList, org.apache.bcel.generic.Type> exprData = expression.toBytecode(cg, mg);
				il.append(exprData.getKey());
				il.append(TypeUtils.cast(exprData.getValue(), mg.getReturnType(), new InstructionFactory(cg)));
			}
			il.append(InstructionFactory.createReturn(mg.getReturnType()));
			
			return il;
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
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			InstructionList il = new InstructionList();
			il.append(InstructionConstants.NOP);
			LocalVariableGen lvg = mg.addLocalVariable(name, type.toBCELType(), il.getStart(), null);
			if(expression != null)
			{
				Pair<InstructionList, org.apache.bcel.generic.Type> exprData = expression.toBytecode(cg, mg);
				il.append(exprData.getKey());
				il.append(TypeUtils.cast(exprData.getValue(), type.toBCELType(), new InstructionFactory(cg)));
				il.append(InstructionFactory.createStore(type.toBCELType(), lvg.getIndex()));
			}
			
			return il;
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
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			InstructionList il = new InstructionList();
			Pair<InstructionList, org.apache.bcel.generic.Type> exprData = expression.toBytecode(cg, mg);
			if(!exprData.getValue().equals(BasicType.BOOLEAN))
				throw new CompilationException("Cannot use non-boolean as a conditional\nClass: " + cg.getClassName() + ";Method: " + mg.getName());
			
			if(statements.size() > 1)
			{
				il.append(exprData.getKey());
				BranchHandle bh = il.append(new IF_ICMPNE(null));
				il.append(statements.get(0).toBytecode(cg, mg));
				BranchHandle bh2 = il.append(new GOTO(null));
				bh.setTarget(il.append(InstructionConstants.NOP));
				il.append(statements.get(1).toBytecode(cg, mg));
				bh2.setTarget(il.append(InstructionConstants.NOP));
			}
			else
			{
				il.append(exprData.getKey());
				BranchHandle bh = il.append(new IF_ICMPNE(null));
				il.append(statements.get(0).toBytecode(cg, mg));
				bh.setTarget(il.append(InstructionConstants.NOP));
			}
			
			// shouldn't be needed TODO remove
			//List<LocalVariableGen> lvgs = MethodUtils.getLocalVars(mg);
			//for(LocalVariableGen lvg : lvgs)
			//	if(lvg.getEnd() == null && il.contains(lvg.getStart()))
			//		lvg.setEnd(il.getEnd());
			
			return il;
		}
	}
	
	public static class WhileStatement extends Statement
	{
		public final Expression expression;
		public final Statement statement;
		
		public WhileStatement(Expression expression, Statement statement)
		{
			this.expression = expression;
			this.statement = statement;
		}
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			InstructionList il = new InstructionList();
			Pair<InstructionList, org.apache.bcel.generic.Type> exprData = expression.toBytecode(cg, mg);
			if(!exprData.getValue().equals(BasicType.BOOLEAN))
				throw new CompilationException("Cannot use non-boolean as a conditional\nClass: " + cg.getClassName() + ";Method: " + mg.getName());
			
			il.append(exprData.getKey());
			BranchHandle bh = il.append(new IF_ICMPNE(null));
			il.append(statement.toBytecode(cg, mg));
			il.append(new GOTO(il.getStart()));
			bh.setTarget(il.append(InstructionConstants.NOP));
			
			// shouldn't be needed TODO remove
			//List<LocalVariableGen> lvgs = MethodUtils.getLocalVars(mg);
			//for(LocalVariableGen lvg : lvgs)
			//	if(lvg.getEnd() == null && il.contains(lvg.getStart()))
			//		lvg.setEnd(il.getEnd());
			
			return il;
		}
	}
	
	public static class DoWhileStatement extends Statement
	{
		public final Expression expression;
		public final Statement statement;
		
		public DoWhileStatement(Expression expression, Statement statement)
		{
			this.expression = expression;
			this.statement = statement;
		}
		
		public DoWhileStatement(List<Expression> expressions)
		{
			this.expression = expressions.get(1);
			this.statement = new ExpressionStatement(expressions.get(0));
		}
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			InstructionList il = new InstructionList();
			Pair<InstructionList, org.apache.bcel.generic.Type> exprData = expression.toBytecode(cg, mg);
			if(!exprData.getValue().equals(BasicType.BOOLEAN))
				throw new CompilationException("Cannot use non-boolean as a conditional\nClass: " + cg.getClassName() + ";Method: " + mg.getName());
			
			il.append(statement.toBytecode(cg, mg));
			il.append(exprData.getKey());
			il.append(new IF_ICMPEQ(il.getStart()));
			
			// shouldn't be needed TODO remove
			//List<LocalVariableGen> lvgs = MethodUtils.getLocalVars(mg);
			//for(LocalVariableGen lvg : lvgs)
			//	if(lvg.getEnd() == null && il.contains(lvg.getStart()))
			//		lvg.setEnd(il.getEnd());
			
			return il;
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
		
		@Override
		public InstructionList toBytecode(ClassGen cg, MethodGen mg)
		{
			InstructionList il = new InstructionList();
			@SuppressWarnings("unchecked")
			Pair<InstructionList, org.apache.bcel.generic.Type>[] rightExprData = rightExprs.stream().map(e -> e.toBytecode(cg, mg)).toArray(Pair[]::new);
			
			Arrays.stream(rightExprData).map(Pair::getKey).forEachOrdered(il::append);
			
			for(int i = leftExprs.size() - 1; i >= 0; i--)
			{
				Expression expr = leftExprs.get(i);
				if(expr instanceof Expression.PrimaryExpression.FieldAccessExpression)
				{
					il.append(((Expression.PrimaryExpression.FieldAccessExpression) expr).toStoreBytecodeTOS(cg, mg, rightExprData[i].getValue()).getKey());
				}
				else if(expr instanceof Expression.PrimaryExpression.ArrayAccessExpression)
				{
					il.append(((Expression.PrimaryExpression.ArrayAccessExpression) expr).toStoreBytecodeTOS(cg, mg, rightExprData[i].getValue()).getKey());
				}
				else
				{
					throw new CompilationException("Invalid expression type for parallel assignment: " + expr.getClass());
				}
			}
			
			return il;
		}
	}
}