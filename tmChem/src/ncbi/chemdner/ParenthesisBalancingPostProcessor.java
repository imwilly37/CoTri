package ncbi.chemdner;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import banner.postprocessing.PostProcessor;
import banner.types.Mention;
import banner.types.Sentence;
import banner.types.Token;

public class ParenthesisBalancingPostProcessor implements PostProcessor {

	public ParenthesisBalancingPostProcessor() {
		// Empty
	}

	public static boolean isBalanced(String s) {
		Stack<Character> stack = new Stack<Character>();
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '(')
				stack.push('(');
			else if (s.charAt(i) == '{')
				stack.push('{');
			else if (s.charAt(i) == '[')
				stack.push('[');
			else if (s.charAt(i) == ')') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '(')
					return false;
			} else if (s.charAt(i) == '}') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '{')
					return false;
			} else if (s.charAt(i) == ']') {
				if (stack.isEmpty())
					return false;
				if (stack.pop() != '[')
					return false;
			}
			// ignore all other characters
		}
		return stack.isEmpty();
	}

	@Override
	public void postProcess(Sentence sentence) {
		List<Mention> mentions = new ArrayList<Mention>(sentence.getMentions());
		for (int i = 0; i < mentions.size(); i++) {
			Mention mention = mentions.get(i);
			if (!isBalanced(mention.getText())) {
				// Drop the mention
				sentence.removeMention(mention);
				// Check +/- 1 on left and right
				int lt = mention.getStart();
				int rt = mention.getEnd(); // Exclusive
				List<Token> tokens = sentence.getTokens();
				if ((rt - lt > 1) && isBalanced(sentence.getText(tokens.get(lt + 1).getStart(), mention.getEndChar()))) {
					// Add a new mention with those dimensions
					Mention mention2 = new Mention(sentence, lt + 1, rt, mention.getEntityType(), mention.getMentionType());
					sentence.addMention(mention2);
				} else if ((rt - lt > 1) && isBalanced(sentence.getText(mention.getStartChar(), tokens.get(rt - 1).getEnd()))) {
					// Add a new mention with those dimensions
					Mention mention2 = new Mention(sentence, lt, rt - 1, mention.getEntityType(), mention.getMentionType());
					sentence.addMention(mention2);
				} else if ((lt < 0) && isBalanced(sentence.getText(tokens.get(lt - 1).getStart(), mention.getEndChar()))) {
					// Add a new mention with those dimensions
					Mention mention2 = new Mention(sentence, lt - 1, rt, mention.getEntityType(), mention.getMentionType());
					sentence.addMention(mention2);
				} else if ((rt < (tokens.size()-1)) && isBalanced(sentence.getText(mention.getStartChar(), tokens.get(rt + 1).getEnd()))) {
					// Add a new mention with those dimensions
					Mention mention2 = new Mention(sentence, lt, rt + 1, mention.getEntityType(), mention.getMentionType());
					sentence.addMention(mention2);
				}
			}
		}
	}
}
