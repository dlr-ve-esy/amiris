// SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.ListIterator;

/** A {@link LinkedList} of {@link Comparable} elements that is sorted according to the natural ordering of its elements
 * 
 * @author Christoph Schimeczek */
public class SortedLinkedList<T extends Comparable<T>> extends LinkedList<T> {
	private static final long serialVersionUID = 1L;

	/** insert an item - the item will be placed at the correct position in the list
	 * 
	 * @return true, as required by {@link LinkedList} */
	@Override
	public boolean add(T item) {
		if (super.size() == 0) {
			super.add(item);
		} else if (super.get(0).compareTo(item) >= 0) {
			super.addFirst(item);
		} else if (getLastItem().compareTo(item) <= 0) {
			super.addLast(item);
		} else {
			ListIterator<T> iterator = super.listIterator(0);
			while (iterator.next().compareTo(item) < 0) {}
			iterator.previous();
			iterator.add(item);
		}
		return true;
	}

	/** returns last item of LinkedList */
	private T getLastItem() {
		return super.get(super.size() - 1);
	}

	/** Not supported by {@link SortedLinkedList}, use {@link #add(Comparable)} instead */
	@Override
	public void add(int i, T t) {
		throw new UnsupportedOperationException();
	}

	/** Not supported by {@link SortedLinkedList}, use {@link #add(Comparable)} instead */
	@Override
	public void addFirst(T t) {
		throw new UnsupportedOperationException();
	}

	/** Not supported by {@link SortedLinkedList}, use {@link #add(Comparable)} instead */
	@Override
	public void addLast(T t) {
		throw new UnsupportedOperationException();
	}

	/** Not supported by {@link SortedLinkedList}, use {@link #add(Comparable)} instead */
	@Override
	public boolean offer(T t) {
		throw new UnsupportedOperationException();
	}

	/** Not supported by {@link SortedLinkedList}, use {@link #add(Comparable)} instead */
	@Override
	public boolean offerFirst(T t) {
		throw new UnsupportedOperationException();
	}

	/** Not supported by {@link SortedLinkedList}, use {@link #add(Comparable)} instead */
	@Override
	public boolean offerLast(T t) {
		throw new UnsupportedOperationException();
	}

	/** Not supported by {@link SortedLinkedList}, use {@link #add(Comparable)} and {@link #remove()} instead */
	@Override
	public T set(int i, T t) {
		throw new UnsupportedOperationException();
	}

	/** Not supported by {@link SortedLinkedList}, order is fixed to natural order */
	@Override
	public void sort(Comparator<? super T> c) {
		throw new UnsupportedOperationException();
	}
}