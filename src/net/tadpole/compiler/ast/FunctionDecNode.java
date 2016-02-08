package net.tadpole.compiler.ast;

import net.tadpole.compiler.Type;

public class FunctionDecNode extends ASTNode
{
	public final String name;
	public final Type returnType;
	
	public FunctionDecNode(ASTNode parent, String name, Type returnType)
	{
		super(parent);
		this.name = name;
		this.returnType = returnType;
	}
}