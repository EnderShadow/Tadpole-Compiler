package net.tadpole.compiler.ast;

public class StructNode extends ASTNode
{
	public final String name;
	
	public StructNode(ASTNode parent, String structName)
	{
		super(parent);
		name = structName;
	}
}