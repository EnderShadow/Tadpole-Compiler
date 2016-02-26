package net.tadpole.compiler;

import net.tadpole.compiler.ast.Expression;
import net.tadpole.compiler.parser.TadpoleParser;

public class LocalVariable
{
	public final Type type;
	public final String name;
	public final Expression expression;
	
	public LocalVariable(TadpoleParser.VariableDecContext context)
	{
		type = new Type(context.type().getText());
		name = context.fieldName().getText();
		expression = Expression.convert(context.expression());
	}
	
	public LocalVariable(Type type, String name, Expression expression)
	{
		this.type = type;
		this.name = name;
		this.expression = expression;
	}
}