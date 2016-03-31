package dnorm.util;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.CharacterUtils;
import org.apache.lucene.util.Version;

public final class AcronymPreservingLowerCaseFilter extends TokenFilter {

	private final int maxAcronymLength;
	private final CharacterUtils charUtils;
	private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

	public AcronymPreservingLowerCaseFilter(Version matchVersion, TokenStream in, int maxAcronymLength) {
		super(in);
		charUtils = CharacterUtils.getInstance(matchVersion);
		this.maxAcronymLength = maxAcronymLength;
	}

	@Override
	public final boolean incrementToken() throws IOException {
		if (input.incrementToken()) {
			final char[] buffer = termAtt.buffer();
			final int length = termAtt.length();
			boolean allCaps = true;
			for (int i = 0; i < length && allCaps;) {
				int codePoint = charUtils.codePointAt(buffer, i);
				int lowerCase = Character.toLowerCase(codePoint);
				allCaps &= codePoint != lowerCase;
				i += Character.charCount(codePoint);
			}
			// Tokens that are all upper case do not get lower-cased
			if (allCaps && length <= maxAcronymLength)
				return true;
			// Mixed-case tokens are converted to lower-case
			for (int i = 0; i < length;) {
				int codePoint = charUtils.codePointAt(buffer, i);
				int lowerCase = Character.toLowerCase(codePoint);
				i += Character.toChars(lowerCase, buffer, i);
			}
			return true;
		} else
			return false;
	}
}
