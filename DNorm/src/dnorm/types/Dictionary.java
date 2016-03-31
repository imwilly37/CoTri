package dnorm.types;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Dictionary implements Serializable {

	private static final long serialVersionUID = -4625314897161279504L;

	boolean frozen;
	private List<String> indexToElement;
	private TObjectIntMap<String> elementToIndex;

	public Dictionary() {
		frozen = false;
		indexToElement = new ArrayList<String>();
		elementToIndex = new TObjectIntHashMap<String>();
	}

	public int addToken(String token) {
		if (frozen)
			throw new IllegalStateException("Cannot add to a frozen Dictionary");
		if (elementToIndex.containsKey(token)) {
			return elementToIndex.get(token);
		}
		int next = indexToElement.size();
		elementToIndex.put(token, next);
		indexToElement.add(token);
		return next;
	}

	public int getIndex(String token) {
		if (!elementToIndex.containsKey(token)) {
			return Integer.MIN_VALUE;
		}
		return elementToIndex.get(token);
	}

	public String getToken(int index) {
		return indexToElement.get(index);
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void freeze() {
		frozen = true;
	}

	public int size() {
		return indexToElement.size();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Dictionary other = (Dictionary) obj;
		if (!indexToElement.equals(other.indexToElement))
			return false;
		return true;
	}
}
