package net.tadpole.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.bcel.generic.ClassGen;

import javafx.util.Pair;
import net.tadpole.compiler.Statement.ExpressionStatement;
import net.tadpole.compiler.ast.BinaryOp;
import net.tadpole.compiler.ast.Expression;
import net.tadpole.compiler.ast.LiteralExpression;
import net.tadpole.compiler.exceptions.CompilationException;
import net.tadpole.compiler.util.Triplet;

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
	
	public List<ClassGen> toBytecode()
	{
		List<ClassGen> classes = new ArrayList<ClassGen>();
		declaredStructs.stream().map(Struct::toBytecode).forEach(classes::add);
	}
	
	public static void absolutifyTypes()
	{
		for(Module m : modules)
		{
			List<Module> imports = modules.stream().filter(module -> m.imports.contains(module.name)).collect(Collectors.toList());
			if(imports.contains(m))
				imports.remove(m);
			imports.add(0, m);
			
			for(Statement s : m.statements)
			{
				absolutifyTypes(s, imports);
			}
			for(Struct s : m.declaredStructs)
			{
				List<Triplet<Type, String, Expression>> attributes = s.attributes;
				for(int i = 0; i < attributes.size(); i++)
				{
					Type t = attributes.get(i).first;
					if(!t.isAbsoluteType() && !t.isPrimitive() && !t.isPrimitiveArray())
					{
						Optional<Struct> oStruct = imports.stream().flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(t.typeName)).findFirst();
						Type newT = Type.fromStruct(oStruct.orElseThrow(() -> new CompilationException("No struct with name '" + t.typeName + "' was imported")));
						attributes.set(i, new Triplet<Type, String, Expression>(newT, attributes.get(i).second, attributes.get(i).third));
					}
					else if(!t.isPrimitive() && !t.isPrimitiveArray())
					{
						modules.stream().filter(module -> module.name.equals(t.getModuleName())).flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(t.getTypeName())).findFirst().orElseThrow(() -> new CompilationException("Cannot find type in imported modules"));
					}
					if(attributes.get(i).third != null)
						absolutifyTypes(attributes.get(i).third, imports);
				}
				
				List<Pair<Type, String>> parameters = s.parameters;
				for(int i = 0; i < parameters.size(); i++)
				{
					Type t = parameters.get(i).getKey();
					if(!t.isAbsoluteType() && !t.isPrimitive() && !t.isPrimitiveArray())
					{
						Optional<Struct> oStruct = imports.stream().flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(t.typeName)).findFirst();
						Type newT = Type.fromStruct(oStruct.orElseThrow(() -> new CompilationException("No struct with name '" + t.typeName + "' was imported")));
						parameters.set(i, new Pair<Type, String>(newT, parameters.get(i).getValue()));
					}
					else if(!t.isPrimitive() && !t.isPrimitiveArray())
					{
						modules.stream().filter(module -> module.name.equals(t.getModuleName())).flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(t.getTypeName())).findFirst().orElseThrow(() -> new CompilationException("Cannot find type in imported modules"));
					}
				}
			}
			for(Function f : m.declaredFunctions)
			{
				f.statements.forEach(s -> absolutifyTypes(s, imports));
				
				List<Pair<Type, String>> parameters = f.parameters;
				for(int i = 0; i < parameters.size(); i++)
				{
					Type t = parameters.get(i).getKey();
					if(!t.isAbsoluteType() && !t.isPrimitive() && !t.isPrimitiveArray())
					{
						Optional<Struct> oStruct = imports.stream().flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(t.typeName)).findFirst();
						Type newT = Type.fromStruct(oStruct.orElseThrow(() -> new CompilationException("No struct with name '" + t.typeName + "' was imported")));
						parameters.set(i, new Pair<Type, String>(newT, parameters.get(i).getValue()));
					}
					else if(!t.isPrimitive() && !t.isPrimitiveArray())
					{
						modules.stream().filter(module -> module.name.equals(t.getModuleName())).flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(t.getTypeName())).findFirst().orElseThrow(() -> new CompilationException("Cannot find type in imported modules"));
					}
				}
				
				if(!f.returnType.isAbsoluteType() && !f.returnType.isPrimitive() && !f.returnType.isPrimitiveArray())
				{
					Optional<Struct> oStruct = imports.stream().flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(f.returnType.typeName)).findFirst();
					f.returnType = Type.fromStruct(oStruct.orElseThrow(() -> new CompilationException("No struct with name '" + f.returnType.typeName + "' was imported")));
				}
				else if(!f.returnType.isPrimitive() && !f.returnType.isPrimitiveArray())
				{
					modules.stream().filter(module -> module.name.equals(f.returnType.getModuleName())).flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(f.returnType.getTypeName())).findFirst().orElseThrow(() -> new CompilationException("Cannot find type in imported modules"));
				}
			}
		}
	}
	
	private static void absolutifyTypes(Statement s, List<Module> imports)
	{
		if(s instanceof Statement.LocalVarDecStatement)
		{
			Statement.LocalVarDecStatement lvds = (Statement.LocalVarDecStatement) s;
			if(!lvds.type.isAbsoluteType() && !lvds.type.isPrimitive() && !Type.isPrimitiveArray(lvds.type))
			{
				Optional<Struct> oStruct = imports.stream().flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(lvds.type.typeName)).findFirst();
				lvds.type = Type.fromStruct(oStruct.orElseThrow(() -> new CompilationException("No struct with name '" + lvds.type.typeName + "' was imported")));
			}
			else if(!lvds.type.isPrimitive() && !Type.isPrimitiveArray(lvds.type))
			{
				modules.stream().filter(module -> module.name.equals(lvds.type.getModuleName())).flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(lvds.type.getTypeName())).findFirst().orElseThrow(() -> new CompilationException("Cannot find type in imported modules"));
			}
			if(lvds.expression != null)
				absolutifyTypes(lvds.expression, imports);
		}
		else if(s instanceof Statement.ExpressionStatement)
		{
			absolutifyTypes(((Statement.ExpressionStatement) s).expression, imports);
		}
		else if(s instanceof Statement.ReturnStatement)
		{
			if(((Statement.ReturnStatement) s).expression != null)
				absolutifyTypes(((Statement.ReturnStatement) s).expression, imports);
		}
		else if(s instanceof Statement.IfStatement)
		{
			Statement.IfStatement is = (Statement.IfStatement) s;
			absolutifyTypes(is.expression, imports);
			is.statements.forEach(s2 -> absolutifyTypes(s2, imports));
		}
		else if(s instanceof Statement.WhileStatement)
		{
			Statement.WhileStatement is = (Statement.WhileStatement) s;
			absolutifyTypes(is.expression, imports);
			is.statements.forEach(s2 -> absolutifyTypes(s2, imports));
		}
	}
	
	private static void absolutifyTypes(Expression expr, List<Module> imports)
	{
		if(expr instanceof Expression.UnaryExpression)
		{
			absolutifyTypes(((Expression.UnaryExpression) expr).expr, imports);
		}
		else if(expr instanceof Expression.BinaryExpression)
		{
			absolutifyTypes(((Expression.BinaryExpression) expr).exprLeft, imports);
			absolutifyTypes(((Expression.BinaryExpression) expr).exprRight, imports);
		}
		else if(expr instanceof Expression.PrimaryExpression)
		{
			if(expr instanceof Expression.PrimaryExpression.ArrayAccessExpression)
			{
				absolutifyTypes(((Expression.PrimaryExpression.ArrayAccessExpression) expr).expression, imports);
				absolutifyTypes(((Expression.PrimaryExpression.ArrayAccessExpression) expr).indexExpression, imports);
			}
			else if(expr instanceof Expression.PrimaryExpression.CastExpression)
			{
				Expression.PrimaryExpression.CastExpression ce = (Expression.PrimaryExpression.CastExpression) expr;
				if(!ce.targetType.isAbsoluteType() && !ce.targetType.isPrimitive() && !Type.isPrimitiveArray(ce.targetType))
				{
					Optional<Struct> oStruct = imports.stream().flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(ce.targetType.typeName)).findFirst();
					ce.targetType = Type.fromStruct(oStruct.orElseThrow(() -> new CompilationException("No struct with name '" + ce.targetType.typeName + "' was imported")));
				}
				else if(!ce.targetType.isPrimitive() && !Type.isPrimitiveArray(ce.targetType))
				{
					modules.stream().filter(module -> module.name.equals(ce.targetType.getModuleName())).flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(ce.targetType.getTypeName())).findFirst().orElseThrow(() -> new CompilationException("Cannot find type in imported modules"));
				}
				absolutifyTypes(ce.expression, imports);
			}
			else if(expr instanceof Expression.PrimaryExpression.InstantiationExpression)
			{
				Expression.PrimaryExpression.InstantiationExpression ie = (Expression.PrimaryExpression.InstantiationExpression) expr;
				if(!ie.structType.isAbsoluteType() && !ie.structType.isPrimitive() && !Type.isPrimitiveArray(ie.structType))
				{
					Optional<Struct> oStruct = imports.stream().flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(ie.structType.typeName)).findFirst();
					ie.structType = Type.fromStruct(oStruct.orElseThrow(() -> new CompilationException("No struct with name '" + ie.structType.typeName + "' was imported")));
				}
				else if(!ie.structType.isPrimitive() && !Type.isPrimitiveArray(ie.structType))
				{
					modules.stream().filter(module -> module.name.equals(ie.structType.getModuleName())).flatMap(module -> module.declaredStructs.stream()).filter(struct -> struct.name.equals(ie.structType.getTypeName())).findFirst().orElseThrow(() -> new CompilationException("Cannot find type in imported modules"));
				}
				for(Expression e : ie.parameters)
					absolutifyTypes(e, imports);
			}
			else if(expr instanceof Expression.PrimaryExpression.WrapExpression)
			{
				absolutifyTypes(((Expression.PrimaryExpression.WrapExpression) expr).expression, imports);
			}
			else if(expr instanceof Expression.PrimaryExpression.FieldAccessExpression)
			{
				absolutifyTypes(((Expression.PrimaryExpression.FieldAccessExpression) expr).expression, imports);
			}
			else if(expr instanceof Expression.PrimaryExpression.FunctionCallExpression)
			{
				for(Expression e : ((Expression.PrimaryExpression.FunctionCallExpression) expr).parameters)
					absolutifyTypes(e, imports);
			}
			else if(expr instanceof LiteralExpression.ArrayLiteral)
			{
				for(Expression e : ((LiteralExpression.ArrayLiteral) expr).literals)
					absolutifyTypes(e, imports);
			}
			else if(!(expr instanceof LiteralExpression))
			{
				throw new IllegalStateException("Wat... how was there another type of primary expression?");
			}
		}
		else
		{
			throw new IllegalStateException("Wat... how was there another type of expression?");
		}
	}
	
	public static void verifySanity() throws CompilationException
	{
		for(Module m : modules)
		{
			if(modules.stream().filter(module -> module != m).map(module -> module.name).anyMatch(mName -> mName.equals(m.name)))
				throw new CompilationException("Cannot have multiple modules with the same name");;
			List<String> temp = m.declaredStructs.stream().map(struct -> struct.name).collect(Collectors.toList());
			for(int i = 0; i < temp.size() - 1; i++)
				if(temp.subList(i + 1, temp.size()).contains(temp.get(i)))
					throw new CompilationException("Duplicate struct of name " + temp.get(i) + " found in module " + m.name);
			for(int i = 0; i < m.imports.size() - 1; i++)
				if(m.imports.subList(i + 1, m.imports.size()).contains(m.imports.get(i)))
					throw new CompilationException("Duplicate import of " + m.imports.get(i) + " found in module " + m.name);
			for(int i = 0; i < m.declaredFunctions.size() - 1; i++)
				if(m.declaredFunctions.subList(i + 1, m.declaredFunctions.size()).contains(m.declaredFunctions.get(i)))
					throw new CompilationException("Duplicate function with name " + m.declaredFunctions.get(i).name + " found in module " + m.name);
		}
	}
	
	public static void mergeParallelAssigns()
	{
		for(Module m : modules)
		{
			int start = -1;
			int end = -1;
			for(int i = 0; i < m.statements.size(); i++)
			{
				Statement s = m.statements.get(i);
				if(s instanceof ExpressionStatement && ((ExpressionStatement) s).expression instanceof Expression.BinaryExpression && ((Expression.BinaryExpression) ((ExpressionStatement) s).expression).op.equals(BinaryOp.PARALLEL_ASSIGN))
				{
					if(start == -1)
						start = i;
					else
						end = i;
					
					if(end == m.statements.size() - 1)
					{
						List<Statement> parallelStatements = m.statements.subList(start, end + 1);
						Statement parallelStatement = Statement.createParallelStatement(parallelStatements);
						parallelStatements.clear();
						m.statements.add(start, parallelStatement);
					}
				}
				else if(end == i - 1);
				{
					List<Statement> parallelStatements = m.statements.subList(start, end + 1);
					Statement parallelStatement = Statement.createParallelStatement(parallelStatements);
					i = start + 1;
					parallelStatements.clear();
					m.statements.add(start, parallelStatement);
					start = -1;
					end = -1;
				}
			}
		}
	}
	
	public static ClassGen[] generateBytecode()
	{
		List<ClassGen> cgs = new ArrayList<ClassGen>();
		modules.forEach(module -> cgs.addAll(module.toBytecode()));
		return cgs.toArray(new ClassGen[cgs.size()]);
	}
}