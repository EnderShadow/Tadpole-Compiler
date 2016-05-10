package net.tadpole.compiler;

import java.util.Arrays;

import org.apache.bcel.classfile.Utility;

import org.apache.bcel.generic.BasicType;

import net.tadpole.compiler.exceptions.CompilationException;

public class Type
{
	private static final String[] primitives = {"byte", "short", "int", "long", "boolean", "char", "float", "double", "void"};
	
	public final String typeName;
	
	public Type(String typeName)
	{
		this.typeName = typeName;
	}
	
	public boolean isPrimitive()
	{
		return Arrays.stream(primitives).anyMatch(s -> s.equals(typeName));
	}
	
	public boolean isArray()
	{
		return typeName.endsWith("[]");
	}
	
	public boolean isPrimitiveArray()
	{
		return isPrimitiveArray(this);
	}
	
	public static boolean isPrimitiveArray(Type t)
	{
		if(!t.isArray())
			return false;
		while(t.isArray())
			t = t.getElementType();
		return t.isPrimitive();
	}
	
	public Type getElementType()
	{
		if(isArray())
			return new Type(typeName.substring(0, typeName.length() - 2));
		throw new IllegalStateException("Cannot get element type from non-array");
	}
	
	public boolean isAbsoluteType()
	{
		return typeName.contains(".") || typeName.contains("$");
	}
	
	public Type getAbsoluteType(String moduleIfRelative)
	{
		return isAbsoluteType() ? this : new Type(moduleIfRelative + "." + typeName);
	}
	
	public String getModuleName()
	{
		if(isPrimitive())
			return null;
		if(isAbsoluteType())
			return typeName.indexOf(".") != -1 ? typeName.substring(0, typeName.indexOf(".")) : typeName.substring(0, typeName.indexOf("$"));
		throw new CompilationException("Cannot get module from non-absolute type");
	}
	
	public String getTypeName()
	{
		if(isPrimitive())
			return typeName;
		if(isAbsoluteType())
			return typeName.indexOf(".") != -1 ? typeName.substring(typeName.indexOf(".") + 1) : typeName.substring(typeName.indexOf("$") + 1);
		return typeName;
	}
	
	public static Type fromStruct(Struct struct)
	{
		return new Type(struct.moduleName + "." + struct.name);
	}
	
	public org.apache.bcel.generic.Type toBCELType()
	{
		if(isPrimitive())
			return BasicType.getType(Utility.getSignature(typeName));
		try
		{
			return org.apache.bcel.generic.Type.getType(Class.forName(typeName));
		}
		catch(Exception e) {}
		
		return org.apache.bcel.generic.Type.getType(Utility.getSignature(typeName.replace('.', '$')));
	}
	
	@Override
	public String toString()
	{
		return typeName;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(obj != null && obj instanceof Type)
		{
			return typeName.equals(((Type) obj).typeName);
		}
		return false;
	}
}