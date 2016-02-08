package net.tadpole.compiler.exceptions;

public class CompilationException extends RuntimeException
{
	private static final long serialVersionUID = 6771131264470388235L;
	
	public CompilationException(String message, Throwable cause)
	{
		super(message, cause);
	}
	
	public CompilationException(String message)
	{
		super(message);
	}
	
	public CompilationException(Throwable cause)
	{
		super(cause);
	}
	
	public CompilationException()
	{
		super();
	}
}