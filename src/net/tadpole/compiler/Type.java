package net.tadpole.compiler;

import java.util.Arrays;

public class Type
{
	private static final String[] primitives = {"byte", "short", "int", "long", "boolean", "char", "float", "double"};
	
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
	
	public boolean isAbsoluteType()
	{
		return typeName.contains(".");
	}
	
	public Type getAbsoluteType(String moduleIfRelative)
	{
		return isAbsoluteType() ? this : new Type(moduleIfRelative + "." + typeName);
	}
	
	public Type fromStruct(Struct struct)
	{
		return new Type(struct.moduleName + "." + struct.name);
	}
}