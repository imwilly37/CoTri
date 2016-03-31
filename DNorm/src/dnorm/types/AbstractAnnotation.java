package dnorm.types;
public class AbstractAnnotation {
	private String documentId;
	private String conceptId;

	public AbstractAnnotation(String documentId, String conceptId) {
		this.documentId = documentId;
		this.conceptId = conceptId;
	}

	public String getDocumentId() {
		return documentId;
	}

	public String getConceptId() {
		return conceptId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((conceptId == null) ? 0 : conceptId.hashCode());
		result = prime * result + ((documentId == null) ? 0 : documentId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractAnnotation other = (AbstractAnnotation) obj;
		if (conceptId == null) {
			if (other.conceptId != null)
				return false;
		} else if (!conceptId.equals(other.conceptId))
			return false;
		if (documentId == null) {
			if (other.documentId != null)
				return false;
		} else if (!documentId.equals(other.documentId))
			return false;
		return true;
	}
}
