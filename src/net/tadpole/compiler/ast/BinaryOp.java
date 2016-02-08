package net.tadpole.compiler.ast;

import net.tadpole.compiler.exceptions.CompilationException;

public enum BinaryOp
{
	POWER, MULTIPLY, DIVIDE, MODULUS, ADD, SUBTRACT, RIGHT_SHIFT_PRESERVE, RIGHT_SHIFT, LEFT_SHIFT, LESS_THAN, GREATER_THAN,
	LESS_THAN_EQUAL, GREATER_THAN_EQUAL, IS, EQUALS, NOT_EQUAL, BITWISE_AND, XOR, BITWISE_OR, AND, OR, ASSIGN, POWER_ASSIGN,
	MULTIPLY_ASSIGN, DIVIDE_ASSIGN, MODULUS_ASSIGN, ADD_ASSIGN, SUBTRACT_ASSIGN, BITWISE_AND_ASSIGN, BITWISE_OR_ASSIGN,
	XOR_ASSIGN, RIGHT_SHIFT_PRESERVE_ASSIGN, RIGHT_SHIFT_ASSIGN, LEFT_SHIFT_ASSIGN, PARALLEL_ASSIGN;
	
	public static BinaryOp resolve(String op)
	{
		switch(op)
		{
		case "**": return POWER;
		case "*": return MULTIPLY;
		case "/": return DIVIDE;
		case "%": return MODULUS;
		case "+": return ADD;
		case "-": return SUBTRACT;
		case ">>": return RIGHT_SHIFT_PRESERVE;
		case ">>>": return RIGHT_SHIFT;
		case "<<": return LEFT_SHIFT;
		case "<": return LESS_THAN;
		case ">": return GREATER_THAN;
		case "<=": return LESS_THAN_EQUAL;
		case ">=": return GREATER_THAN_EQUAL;
		case "is": return IS;
		case "==": return EQUALS;
		case "!=": return NOT_EQUAL;
		case "&": return BITWISE_AND;
		case "^": return XOR;
		case "|": return BITWISE_OR;
		case "&&": return AND;
		case "||": return OR;
		case "=": return ASSIGN;
		case "**=": return POWER_ASSIGN;
		case "*=": return MULTIPLY_ASSIGN;
		case "/=": return DIVIDE_ASSIGN;
		case "%=": return MODULUS_ASSIGN;
		case "+=": return ADD_ASSIGN;
		case "-=": return SUBTRACT_ASSIGN;
		case "&=": return BITWISE_AND_ASSIGN;
		case "|=": return BITWISE_OR_ASSIGN;
		case "^=": return XOR_ASSIGN;
		case ">>=": return RIGHT_SHIFT_PRESERVE_ASSIGN;
		case ">>>=": return RIGHT_SHIFT_ASSIGN;
		case "<<=": return LEFT_SHIFT_ASSIGN;
		case "<-": return PARALLEL_ASSIGN;
		default: throw new CompilationException("Unknown binary operator: " + op);
		}
	}
}