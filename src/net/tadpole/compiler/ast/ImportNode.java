package net.tadpole.compiler.ast;

public class ImportNode extends ASTNode
{
	public final String moduleName;
	
	public ImportNode(ASTNode parent, String moduleName)
	{
		super(parent);
		this.moduleName = moduleName;
	}
}