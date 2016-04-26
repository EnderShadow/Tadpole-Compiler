package net.tadpole.compiler.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.Type;

import net.tadpole.compiler.Function;
import net.tadpole.compiler.exceptions.CompilationException;

public class TypeUtils
{
	public static final int INT = 0;
	public static final int DOUBLE = 1;
	public static final int NUMBER = 2;
	public static final int CHAR = 3;
	public static final int STRING = 4;
	public static final int BOOLEAN = 5;
	public static final int OBJECT = 6;
	
	public static boolean isOfType(Type type, int t)
	{
		switch(t)
		{
		case INT:
			if(type instanceof BasicType && !type.equals(BasicType.BOOLEAN) && !type.equals(BasicType.DOUBLE) && !type.equals(BasicType.FLOAT))
				return true;
			return false;
		case DOUBLE:
			if(type instanceof BasicType && !type.normalizeForStackOrLocal().equals(BasicType.INT))
				return true;
			return false;
		case NUMBER:
			if(type instanceof BasicType && !type.equals(BasicType.BOOLEAN))
				return true;
			return false;
		case CHAR:
			return type.equals(BasicType.CHAR);
		case STRING:
			return type.equals(Type.STRING);
		case BOOLEAN:
			return type.equals(BasicType.BOOLEAN);
		case OBJECT:
			return type instanceof ReferenceType;
		default:
			throw new IllegalArgumentException("Unknown type flag" + t);
		}
	}
	
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
	
	public static boolean areMatching(List<Type> paramTypes, List<Type> argTypes)
	{
		if(paramTypes.size() != argTypes.size())
			return false;
		for(int i = 0; i < paramTypes.size(); i++)
		{
			Type pType = paramTypes.get(i);
			Type aType = argTypes.get(i);
			if(pType instanceof BasicType && aType instanceof BasicType)
				if(!cast(aType, pType, new InstructionFactory(null, null)).isEmpty())
					return false;
			if(!aType.equals(pType))
				return false;
		}
		return true;
	}
	
	public static Function findClosestMatch(List<Function> possibilities, List<Type> argTypes)
	{
		List<Function> bestFunctions = new ArrayList<Function>();
		int numCasts = argTypes.size() + 1;
		for(Function function : possibilities)
		{
			int casts = 0;
			List<Type> paramTypes = function.parameters.stream().map(p -> p.getKey().toBCELType()).collect(Collectors.toList());
			for(int i = 0; i < argTypes.size(); i++)
				if(!paramTypes.get(i).equals(argTypes.get(i)))
					casts++;
			if(casts < numCasts)
			{
				numCasts = casts;
				bestFunctions.clear();
				bestFunctions.add(function);
			}
			else if(casts == numCasts)
			{
				bestFunctions.add(function);
			}
		}
		possibilities = bestFunctions;
		bestFunctions = new ArrayList<Function>();
		InstructionFactory dummyFactory = new InstructionFactory(new ConstantPoolGen());
		numCasts = argTypes.size() + 1;
		for(Function function : possibilities)
		{
			int casts = 0;
			List<Type> paramTypes = function.parameters.stream().map(p -> p.getKey().toBCELType()).collect(Collectors.toList());
			for(int i = 0; i < argTypes.size(); i++)
				if(!cast(argTypes.get(i), paramTypes.get(i), dummyFactory).isEmpty())
					casts++;
			if(casts < numCasts)
			{
				numCasts = casts;
				bestFunctions.clear();
				bestFunctions.add(function);
			}
			else if(casts == numCasts)
			{
				bestFunctions.add(function);
			}
		}
		
		if(bestFunctions.size() > 1)
			throw new CompilationException("Ambiguous function call with name '" + bestFunctions.get(0).name + "' and " + argTypes.size() + " parameters");
		return bestFunctions.get(0);
	}
	
	public static Method findClosestMatch(List<Method> possibilities, List<Type> argTypes, Void notUsed)
	{
		List<Method> bestMethods = new ArrayList<Method>();
		int numCasts = argTypes.size() + 1;
		for(Method method : possibilities)
		{
			int casts = 0;
			List<Type> paramTypes = Arrays.asList(method.getArgumentTypes());
			for(int i = 0; i < argTypes.size(); i++)
				if(!paramTypes.get(i).equals(argTypes.get(i)))
					casts++;
			if(casts < numCasts)
			{
				numCasts = casts;
				bestMethods.clear();
				bestMethods.add(method);
			}
			else if(casts == numCasts)
			{
				bestMethods.add(method);
			}
		}
		possibilities = bestMethods;
		bestMethods = new ArrayList<Method>();
		InstructionFactory dummyFactory = new InstructionFactory(new ConstantPoolGen());
		numCasts = argTypes.size() + 1;
		for(Method method : possibilities)
		{
			int casts = 0;
			List<Type> paramTypes = Arrays.asList(method.getArgumentTypes());
			for(int i = 0; i < argTypes.size(); i++)
				if(!cast(argTypes.get(i), paramTypes.get(i), dummyFactory).isEmpty())
					casts++;
			if(casts < numCasts)
			{
				numCasts = casts;
				bestMethods.clear();
				bestMethods.add(method);
			}
			else if(casts == numCasts)
			{
				bestMethods.add(method);
			}
		}
		
		if(bestMethods.size() > 1)
			throw new CompilationException("Ambiguous function call with name '" + bestMethods.get(0).getName() + "' and " + argTypes.size() + " parameters");
		return bestMethods.get(0);
	}
	
	public static ArrayType getArrayType(List<Type> types)
	{
		Type t = types.stream().reduce(Type.VOID, TypeUtils::getMostGeneral);
		return new ArrayType(t, 1);
	}
	
	public static Type getMostGeneral(Type t1, Type t2)
	{
		if((t1 instanceof ReferenceType && t2 instanceof BasicType) || (t1 instanceof BasicType && t2 instanceof ReferenceType))
			throw new CompilationException("Cannot create an array with both primitives and objects");
		if(t1.equals(t2))
			return t1;
		if(t1.equals(Type.VOID))
			return t2;
		if(t2.equals(Type.VOID))
			return t1;
		if(t1 instanceof ReferenceType || t2 instanceof ReferenceType)
			return Type.OBJECT;
		if(t1.equals(Type.DOUBLE) || t2.equals(Type.DOUBLE))
			return Type.DOUBLE;
		if(t1.equals(Type.FLOAT) || t2.equals(Type.FLOAT))
			return Type.FLOAT;
		if(t1.equals(Type.LONG) || t2.equals(Type.LONG))
			return Type.LONG;
		if(t1.equals(Type.CHAR) && t2.equals(Type.CHAR))
			return Type.CHAR;
		if(t1.equals(Type.BYTE) && t2.equals(Type.BYTE))
			return Type.BYTE;
		if(t1.equals(Type.SHORT) && t2.equals(Type.SHORT))
			return Type.SHORT;
		if(t1.equals(Type.BOOLEAN) && t2.equals(Type.BOOLEAN))
			return Type.BOOLEAN;
		if(t1.normalizeForStackOrLocal().equals(Type.INT) || t2.normalizeForStackOrLocal().equals(Type.INT))
			return Type.INT;
		throw new IllegalStateException("Cannot determine most general type of " + t1 + " and " + t2);
	}
}