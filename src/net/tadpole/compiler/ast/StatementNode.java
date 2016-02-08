package net.tadpole.compiler.ast;

import net.tadpole.compiler.parser.TadpoleParser;

public class StatementNode extends ASTNode
{
	private final byte T_EXPRESSION = 0;
	private final byte T_RECALL = 1;
	private final byte T_RETURN = 2;
	
	private final byte type;
	public Expression expression;
	
	public StatementNode(ASTNode parent, TadpoleParser.StatementContext context)
	{
		super(parent);
		if(context.getChild(0).getText().equals("recall"))
			type = T_RECALL;
		else if(context.getChild(0).getText().equals("return"))
			type = T_RETURN;
		else
			type = T_EXPRESSION;
		
		expression = context.expression() != null ? Expression.convert(context.expression()) : null;
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
}