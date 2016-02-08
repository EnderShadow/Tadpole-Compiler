package net.tadpole.compiler.ast;

import net.tadpole.compiler.Type;

public class ParameterNode extends ASTNode
{
	public final Type type;
	public final String name;
	
	public ParameterNode(ASTNode parent, Type type, String name)
	{
		super(parent);
		this.type = type;
		this.name = name;
	}
}