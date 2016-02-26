package net.tadpole.compiler.ast;

public class StructNode extends ASTNode
{
	public final String moduleName;
	public final String name;
	
	public StructNode(ASTNode parent, String moduleName, String structName)
	{
		super(parent);
		this.moduleName = moduleName;
		name = structName;
	}
}