package net.tadpole.compiler.util;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MethodGen;

public class MethodUtils
{
	@SuppressWarnings("unchecked")
	public static List<LocalVariableGen> getLocalVars(MethodGen mg)
	{
		try
		{
			Field f = mg.getClass().getDeclaredField("variable_vec");
			f.setAccessible(true);
			return (List<LocalVariableGen>) f.get(mg);
		}
		catch(Exception e)
		{
			throw new RuntimeException(e.getMessage(), e);
		}
	}
}