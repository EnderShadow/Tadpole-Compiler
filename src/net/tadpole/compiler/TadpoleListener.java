package net.tadpole.compiler;

import java.util.Stack;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.TerminalNode;

import net.tadpole.compiler.ast.ASTNode;
import net.tadpole.compiler.ast.Expression;
import net.tadpole.compiler.ast.FileNode;
import net.tadpole.compiler.ast.FunctionDecNode;
import net.tadpole.compiler.ast.ImportNode;
import net.tadpole.compiler.ast.ParameterListNode;
import net.tadpole.compiler.ast.ParameterNode;
import net.tadpole.compiler.ast.StatementNode;
import net.tadpole.compiler.ast.StructNode;
import net.tadpole.compiler.ast.VariableDecNode;
import net.tadpole.compiler.parser.TadpoleBaseListener;
import net.tadpole.compiler.parser.TadpoleParser;

public class TadpoleListener extends TadpoleBaseListener
{
	private Stack<ASTNode> scope = new Stack<ASTNode>();
	private String moduleName;
	
	public TadpoleListener(String moduleName)
	{
		resetRoot(moduleName);
	}
	
	public FileNode getRoot()
	{
		if(scope.size() < 1)
			throw new IllegalStateException("Scope stack has size " + scope.size() + ", this should not happen");
		ASTNode node;
		if(!((node = scope.get(0)) instanceof FileNode))
			throw new IllegalStateException("Top of scope is not an instance of FileNode, this should not happen");
		
		return (FileNode) node;
	}
	
	public void resetRoot(String moduleName)
	{
		scope.clear();
		scope.add(new FileNode());
		this.moduleName = moduleName;
	}
	
	@Override
	public void enterStructDec(TadpoleParser.StructDecContext context)
	{
		scope.push(new StructNode(scope.peek(), moduleName, context.structName().getText()));
	}
	
	@Override
	public void exitStructDec(TadpoleParser.StructDecContext context)
	{
		scope.pop();
	}
	
	@Override
	public void enterParameterList(TadpoleParser.ParameterListContext context)
	{
		scope.push(new ParameterListNode(scope.peek()));
	}
	
	@Override
	public void exitParameterList(TadpoleParser.ParameterListContext context)
	{
		scope.pop();
	}
	
	@Override
	public void enterParameter(TadpoleParser.ParameterContext context)
	{
		new ParameterNode(scope.peek(), new Type(context.type().getText()), context.fieldName().getText());
	}
	
	@Override
	public void enterFunctionDec(TadpoleParser.FunctionDecContext context)
	{
		scope.push(new FunctionDecNode(scope.peek(), context.functionName().getText(), new Type(context.type().getText())));
	}
	
	@Override
	public void exitFunctionDec(TadpoleParser.FunctionDecContext context)
	{
		scope.pop();
	}
	
	@Override
	public void enterVariableDec(TadpoleParser.VariableDecContext context)
	{
		new VariableDecNode(scope.peek(), new Type(context.type().getText()), context.fieldName().getText(), Expression.convert(context.expression()));
	}
	
	@Override
	public void enterStatement(TadpoleParser.StatementContext context)
	{
		new StatementNode(scope.peek(), context);
		while(context.getChildCount() > 0)
			context.removeLastChild();
	}
	
	@Override
	public void enterImportDec(TadpoleParser.ImportDecContext context)
	{
		new ImportNode(scope.peek(), context.Identifier().getText());
	}
}