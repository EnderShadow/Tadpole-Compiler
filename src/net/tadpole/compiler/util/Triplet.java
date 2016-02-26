package net.tadpole.compiler.util;

import java.util.Objects;

public class Triplet<K, V, T>
{
	public final K first;
	public final V second;
	public final T third;
	
	public Triplet(K first, V second, T third)
	{
		this.first = first;
		this.second = second;
		this.third = third;
	}
	
	@Override
	public String toString()
	{
		return String.format("(%s, %s, %s)", first, second, third);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(obj != null && obj instanceof Triplet)
		{
			if(this == obj)
				return true;
			Triplet<?, ?, ?> o = (Triplet<?, ?, ?>) obj;
			return Objects.equals(first, o.first) && Objects.equals(second, o.second) && Objects.equals(third, o.third);
		}
		return false;
	}
	
	@Override
	public int hashCode()
	{
		return (first != null ? (first.hashCode() << 20) : 0) + (second != null ? (second.hashCode() << 10) : 0) + (third != null ? third.hashCode() : 0);
	}
}