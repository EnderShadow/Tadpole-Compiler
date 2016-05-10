package net.tadpole.compiler.util;

public class StringUtils
{
	public static String multiply(String str, int num)
	{
		if(str == null)
			return null;
		if(num < 0)
		{
			char[] temp = str.toCharArray();
			for(int i = 0; i < temp.length / 2; i++)
			{
				char t = temp[i];
				temp[i] = temp[temp.length - 1 - i];
				temp[temp.length - 1 - i] = t;
			}
			return multiply(new String(temp), -num);
		}
		StringBuilder res = new StringBuilder(str.length() * num);
		while(num > 0)
			res.append(str);
		return res.toString();
	}
}