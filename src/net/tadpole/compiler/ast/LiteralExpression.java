package net.tadpole.compiler.ast;

import net.tadpole.compiler.exceptions.CompilationException;
import net.tadpole.compiler.parser.TadpoleParser;

public class LiteralExpression extends Expression.PrimaryExpression
{
	public static class IntLiteral extends LiteralExpression implements NumberLiteral
	{
		public final long value;
		public final boolean wide;
		
		public IntLiteral(long value, boolean wide)
		{
			this.value = value;
			this.wide = wide;
		}
		
		public IntLiteral(String intLiteral)
		{
			intLiteral = intLiteral.replace("_", "");
			int signOffset = intLiteral.startsWith("+") || intLiteral.startsWith("-") ? 1 : 0;
			int base = 10;
			switch(Character.toLowerCase(intLiteral.charAt(signOffset + 1)))
			{
			case 'o':
				base = 8;
				intLiteral = intLiteral.substring(0, signOffset) + intLiteral.substring(signOffset + 2);
				break;
			case 'b':
				base = 2;
				intLiteral = intLiteral.substring(0, signOffset) + intLiteral.substring(signOffset + 2);
				break;
			case 'h':
				base = 16;
				intLiteral = intLiteral.substring(0, signOffset) + intLiteral.substring(signOffset + 2);
				break;
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				break;
			default:
				throw new CompilationException("Unknown integer base: " + intLiteral.charAt(signOffset + 1));
			}
			
			if(wide = intLiteral.toLowerCase().endsWith("l"))
				intLiteral = intLiteral.substring(0, intLiteral.length() - 1);
			
			value = Long.parseLong(intLiteral, base);
		}
		
		@Override
		public double asDouble()
		{
			return value;
		}
		
		@Override
		public boolean isInt()
		{
			return true;
		}
		
		@Override
		public long asInt()
		{
			return value;
		}
	}
	
	public static class FloatLiteral extends LiteralExpression implements NumberLiteral
	{
		public final double value;
		public final boolean wide;
		
		public FloatLiteral(double value, boolean wide)
		{
			this.value = value;
			this.wide = wide;
		}
		
		public FloatLiteral(String floatLiteral)
		{
			value = Double.parseDouble(floatLiteral);
			wide = floatLiteral.toLowerCase().endsWith("d");
		}
		
		@Override
		public double asDouble()
		{
			return value;
		}
		
		@Override
		public boolean isInt()
		{
			return false;
		}
		
		@Override
		public long asInt()
		{
			return (long) value;
		}
	}
	
	public static class BooleanLiteral extends LiteralExpression
	{
		public static final BooleanLiteral TRUE = new BooleanLiteral(true);
		public static final BooleanLiteral FALSE = new BooleanLiteral(false);
		
		public final boolean value;
		
		private BooleanLiteral(boolean value)
		{
			this.value = value;
		}
		
		public static BooleanLiteral convert(String booleanLiteral)
		{
			return Boolean.parseBoolean(booleanLiteral) ? TRUE : FALSE;
		}
		
		public static BooleanLiteral of(boolean value)
		{
			return value ? TRUE : FALSE;
		}
	}
	
	public static class CharacterLiteral extends LiteralExpression implements NumberLiteral
	{
		public final char value;
		
		public CharacterLiteral(char c)
		{
			value = c;
		}
		
		public CharacterLiteral(String charLiteral)
		{
			charLiteral = charLiteral.substring(1, charLiteral.length() - 1);
			if(charLiteral.length() == 1)
				value = charLiteral.charAt(0);
			else if(charLiteral.length() == 6)
				value = (char) Integer.parseInt(charLiteral.substring(2), 16);
			else if(!Character.isDigit(charLiteral.charAt(1)))
				value = escape(charLiteral.charAt(1));
			else
				value = (char) Integer.parseInt(charLiteral.substring(1), 8);
		}
		
		private char escape(char c)
		{
			switch(c)
			{
			case 'b':
				return '\b';
			case 't':
				return '\t';
			case 'n':
				return '\n';
			case 'f':
				return '\f';
			case 'r':
				return '\r';
			case '"':
			case '\'':
			case '\\':
				return c;
			default:
				throw new CompilationException("Cannot escape unknown character: " + c);
			}
		}
		
		@Override
		public double asDouble()
		{
			return value;
		}
		
		@Override
		public boolean isInt()
		{
			return true;
		}
		
		@Override
		public long asInt()
		{
			return value;
		}
	}
	
	public static class StringLiteral extends LiteralExpression
	{
		public final String value;
		
		public StringLiteral(String stringLiteral)
		{
			value = escapeCharacters(stringLiteral);
		}
		
		private String escapeCharacters(String str)
		{
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < str.length(); i++)
			{
				char c = str.charAt(i);
				if(c == '\\')
				{
					if(Character.isDigit(str.charAt(i + 1)))
					{
						int amt = 1;
						for(int j = 1; j < 3; j++)
							if(Character.isDigit(str.charAt(i + 1 + j)))
								amt++;
						sb.append((char) Integer.parseInt(str.substring(i + 1, i + 1 + amt), 8));
					}
					else if(str.charAt(i + 1) == 'u')
					{
						sb.append((char) Integer.parseInt(str.substring(i + 2, i + 6), 16));
					}
					else
					{
						switch(str.charAt(i + 1))
						{
						case 'b':
							sb.append('\b');
							break;
						case 't':
							sb.append('\t');
							break;
						case 'n':
							sb.append('\n');
							break;
						case 'f':
							sb.append('\f');
							break;
						case 'r':
							sb.append('\r');
							break;
						case '\'':
						case '\"':
						case '\\':
							sb.append(str.charAt(i + 1));
							break;
						default:
							throw new CompilationException("Cannot escape unknown character: " + str.charAt(i + 1));
						}
					}
				}
				else
				{
					sb.append(c);
				}
			}
			
			return sb.toString();
		}
	}
	
	public static class NoneLiteral extends LiteralExpression
	{
		public static final NoneLiteral NONE = new NoneLiteral();
		
		private NoneLiteral()
		{
			
		}
	}
	
	public static class ArrayLiteral extends LiteralExpression
	{
		public final Expression[] literals;
		
		public ArrayLiteral(TadpoleParser.ArrayLiteralContext context)
		{
			if(context.expressionList() != null)
				literals = context.expressionList().expression().stream().map(Expression::convert).toArray(Expression[]::new);
			else
				literals = new Expression[0];
		}
	}
	
	public static interface NumberLiteral
	{
		double asDouble();
		boolean isInt();
		long asInt();
	}
}