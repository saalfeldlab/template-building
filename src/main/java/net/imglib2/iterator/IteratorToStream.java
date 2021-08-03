package net.imglib2.iterator;

import java.util.Iterator;

public class IteratorToStream<T> implements Iterator<T> {

	private net.imglib2.Cursor<T> c;

	public IteratorToStream(net.imglib2.Cursor<T> c) {
		this.c = c;
	}

	@Override
	public boolean hasNext() {
		return c.hasNext();
	}

	@Override
	public T next() {
		return c.next();
	}


}
