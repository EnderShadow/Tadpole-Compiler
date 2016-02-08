package net.tadpole.compiler.ast;

import net.tadpole.compiler.exceptions.CompilationException;

public enum UnaryOp
{
	POSITIVE, NEGATIVE, NOT, UNARY_NEGATE, INCREMENT, DECREMENT;
	
	public static UnaryOp resolve(String op)
	{
		switch(op)
		{
		case "+": return POSITIVE;
		case "-": return NEGATIVE;
		case "!": return NOT;
		case "~": return UNARY_NEGATE;
		case "++": return INCREMENT;
		case "--": return DECREMENT;
		default: throw new CompilationException("Unknown unary operator: " + op);
		}
	}
}