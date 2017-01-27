package net.imglib2.filter;

import java.util.Iterator;

import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.RealType;

public class MaskedIterableFilter<T extends RealType<T>, B extends BooleanType<B>> 
	implements Iterable<T>
{

	Iterable<T> it;
	Iterator<B> maskIt;
	MaskedIterator<T,B> fit;

	public MaskedIterableFilter( Iterator<B> maskIt )
	{
		this.maskIt = maskIt;
	}

	public void set( Iterable<T> it )
	{
		this.it = it;
		fit = new MaskedIterator<T,B>( it.iterator(), maskIt );
	}
	
	@Override
	public Iterator< T > iterator()
	{
		return fit;
	}

	public static class MaskedIterator<T extends RealType<T>, B extends BooleanType<B>>
		implements Iterator<T>
	{
		Iterator<T> baseIt;
		Iterator<B> maskIt;
		T val;
		long numTotal = 0;
		long numInvalid = 0;
		long numValid = 0;
		
		public MaskedIterator( Iterator<T> it, Iterator<B> maskIt )
		{
			this.baseIt = it;
			this.maskIt = maskIt;
			
		}

		public long getNumTotal()
		{
			return numTotal;
		}

		public long getNumValid()
		{
			return numValid;
		}

		public long getNumInvalid()
		{
			return numInvalid;
		}

		@Override
		public boolean hasNext()
		{
			if( !baseIt.hasNext() )
			{
				return false;
			}

			while( baseIt.hasNext() )
			{
				numTotal++;
				val = baseIt.next();
				boolean pass = maskIt.next().get();
				if( pass )
				{	
					numValid++;
					return true;
				}
				numInvalid++;
			}
			return false;
		}

		@Override
		public T next()
		{
			return val;
		}
	}
}
