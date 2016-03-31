package objectCDR;

/**
 * 儲存Mention位置的類別 <br>
 * Mention: 正確的疾病名稱、疾病型態、原始PMID、對應到相關疾病ID(通常Mention來源是Training或Testing Set)
 * @author iir
 *
 */
public class MentionLocation{
	/**
	 * 此mention的起始位置
	 */
	public int start;
	/**
	 * 此mention的結束位置
	 */
	public int end;
	/**
	 * 此mention的類別
	 */
	public String type;
	/**
	 * 此mention原始字串
	 */
	public String mentionStr;
	/**
	 * 此mention 對應到的ID(可能不只一個)
	 */
	public String[] mentionID;
	/**
	 * 此mention原始文章ID(PMID)
	 */
	public String docID;
	
	public MentionLocation(){
		start=0;
		end=0;
		type=null;
		mentionStr=null;
		mentionID=null;
		docID=null;
	}
	
	public MentionLocation(int s,int e,String t,String mStr,String[] mID,String dID){
		start=s;
		end=e;
		type=t;
		mentionStr=mStr;
		mentionID=mID;
		docID=dID;
	}
	public MentionLocation(MentionLocation oMen) {
		start=oMen.start;
		end=oMen.end;
		type=oMen.type;
		mentionStr=oMen.mentionStr;
		mentionID=oMen.mentionID;
		docID=oMen.docID;
	}

	/**
	 * 將MentionLocation輸出成字串，格式:"mentionStr(start,end)[mentionID,docID]" <br>
	 * 其中若有多個mentionID,則由逗號分開
	 */
	public String toString(){
		String mentionIDStr="";
		for (String e:mentionID)
			if (mentionIDStr.length()>0)
				mentionIDStr+=","+e;
			else
				mentionIDStr=e;
		return mentionStr+"("+start+","+end+")"+"["+mentionIDStr+"|"+docID+"]";
	}
}
