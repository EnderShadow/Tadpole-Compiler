package net.tadpole.compiler.util;

import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.Type;

public class TypeUtils
{
	public static InstructionList cast(Type from, Type to, InstructionFactory factory)
	{
		InstructionList il = new InstructionList();
		if(!from.equals(to))
		{
			try
			{
				il.append(factory.createCast(from, to));
			}
			catch(RuntimeException e)
			{
				if(e.getMessage().startsWith("Can not cast "))
					throw e;
			}
		}
		return il;
	}
}