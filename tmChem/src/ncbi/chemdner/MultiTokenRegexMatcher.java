/* 
 Copyright (c) 2007 Arizona State University, Dept. of Computer Science and Dept. of Biomedical Informatics.
 This file is part of the BANNER Named Entity Recognition System, http://banner.sourceforge.net
 This software is provided under the terms of the Common Public License, version 1.0, as published by http://www.opensource.org.  For further information, see the file 'LICENSE.txt' included with this distribution.
 */

package ncbi.chemdner;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import banner.types.Sentence;
import banner.types.Token;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;

public class MultiTokenRegexMatcher extends Pipe {
	private static final long serialVersionUID = 1L;

	private String prefix;
	private Pattern pattern;

	public MultiTokenRegexMatcher(String prefix, Pattern pattern) {
		this.prefix = prefix;
		this.pattern = pattern;
	}

	@Override
	public Instance pipe(Instance carrier) {
		Sentence sentence = (Sentence) carrier.getSource();
		List<Token> tokens = sentence.getTokens();
		TokenSequence ts = (TokenSequence) carrier.getData();
		boolean[] values = new boolean[tokens.size()];

		String sentenceText = sentence.getText();
		Matcher textMatcher = pattern.matcher(sentenceText);
		while (textMatcher.find()) {
			int start = textMatcher.start();
			int end = textMatcher.end();
			String matchText = sentenceText.substring(start, end);
			System.out.println("\tPATTERN FOUND: " + sentence.getDocumentId() + "|" + start + "|" + end + "|" + matchText);
			int tagstart = sentence.getTokenIndexStart(start);
			int tagend = sentence.getTokenIndexEnd(end);
			if (tagstart < 0 || tagend < 0) {
				System.out.println("WARNING: Pattern ignored");
			} else {
				for (int i = tagstart; i <= tagend; i++) {
					values[i] = true;
					System.out.println("\t\tMarking token TRUE: " + tokens.get(i).getText());
				}
			}
		}

		for (int i = 0; i < ts.size(); i++) {
			cc.mallet.types.Token token = ts.get(i);
			if (values[i]) {
				token.setFeatureValue(prefix, 1);
			}
		}
		return carrier;
	}
}
