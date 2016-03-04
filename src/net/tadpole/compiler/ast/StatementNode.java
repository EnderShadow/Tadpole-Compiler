package net.tadpole.compiler.ast;

import java.util.List;
import java.util.stream.Collectors;

import net.tadpole.compiler.LocalVariable;
import net.tadpole.compiler.parser.TadpoleParser;

public class StatementNode extends ASTNode
{
	public static final byte T_EXPRESSION = 0;
	public static final byte T_RECALL = 1;
	public static final byte T_RETURN = 2;
	public static final byte T_LOCAL_VAR = 3;
	public static final byte T_IF = 4;
	public static final byte T_WHILE = 5;
	
	public final byte type;
	public Expression expression;
	public LocalVariable localVarDec;
	public List<StatementNode> statements;
	
	public StatementNode(ASTNode parent, TadpoleParser.StatementContext context)
	{
		super(parent);
		if(context.variableDec() != null)
			type = T_LOCAL_VAR;
		else if(context.getChild(0).getText().equals("recall"))
			type = T_RECALL;
		else if(context.getChild(0).getText().equals("return"))
			type = T_RETURN;
		else if(context.getChild(0).getText().equals("if"))
			type = T_IF;
		else if(context.getChild(0).getText().equals("while"))
			type = T_WHILE;
		else
			type = T_EXPRESSION;
		
		expression = context.expression() != null ? Expression.convert(context.expression()) : null;
		localVarDec = context.variableDec() != null ? new LocalVariable(context.variableDec()) : null;
		statements = context.functionBody().statement().stream().map(sc -> new StatementNode(this, sc)).collect(Collectors.toList());
	}
	
	public boolean isLocalVariableDecStatement()
	{
		return type == T_LOCAL_VAR;
	}
	
	public boolean isExpressionStatement()
	{
		return type == T_EXPRESSION;
	}
	
	public boolean isRecallStatement()
	{
		return type == T_RECALL;
	}
	
	public boolean isReturnStatement()
	{
		return type == T_RETURN;
	}
	
	public boolean isIfStatement()
	{
		return type == T_IF;
	}
	
	public boolean isWhileStatement()
	{
		return type == T_WHILE;
	}
}