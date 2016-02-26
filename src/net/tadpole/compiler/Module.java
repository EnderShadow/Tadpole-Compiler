package net.tadpole.compiler;

import java.util.ArrayList;
import java.util.List;

public class Module
{
	private static final List<Module> modules = new ArrayList<Module>();
	
	public final String name;
	public final List<Struct> declaredStructs;
	public final List<String> imports;
	public final List<Function> declaredFunctions;
	public final List<Statement> statements;
	
	public Module(String name, List<Struct> declaredStructs, List<String> imports, List<Function> declaredFunctions, List<Statement> statements)
	{
		this.name = name;
		this.declaredStructs = declaredStructs;
		this.imports = imports;
		this.declaredFunctions = declaredFunctions;
		this.statements = statements;
		modules.add(this);
	}
}