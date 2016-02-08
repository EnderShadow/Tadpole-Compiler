package net.tadpole.compiler.ast;

import net.tadpole.compiler.Type;

public class VariableDecNode extends ASTNode
{
	public final Type type;
	public final String name;
	public Expression expression;
	
	public VariableDecNode(ASTNode parent, Type type, String name, Expression expression)
	{
		super(parent);
		this.type = type;
		this.name = name;
		this.expression = expression;
	}
}