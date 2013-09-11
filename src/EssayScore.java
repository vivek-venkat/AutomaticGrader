import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Properties;


import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.dcoref.CorefChain;
//import edu.stanford.nlp.dcoref.*;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.trees.Tree;


public class EssayScore {
	public String document = "";
	public Annotation documentAnnotation;
	public Map<Integer, CorefChain> graph;
	ArrayList<SentenceScore> sentences = new ArrayList<SentenceScore>();
	String essayId;
	String filePath;
	int sentenceFormationScore=0;
	double coherenceThirdCorrect = 0; //count of third person resolved to  correct / felicitous
	double coherenceThirdIncorect = 0; //count of third person resolved to incorrect	
	static StanfordCoreNLP pipeline;
	
	static {
		//Do this once and resuse.
		 Properties props = new Properties();
	     props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner, parse, dcoref");
	     pipeline = new StanfordCoreNLP(props);
	}
	
	public EssayScore(String essayId) {
		this.essayId=essayId;
	}
	
	public EssayScore(){
	}
	
	/**
	 * Compute overall essay scores
	 * @param filePath
	 * @throws IOException 
	 */
	public void computeEssayScore(String filePath) throws IOException{
		SentenceScore.topicWords.clear();	
		this.filePath = filePath;
		ArrayList<String> sentencesDelim = getSentencesFromEssay(true);
		for (String sentence : sentencesDelim) {
			if (!sentence.trim().equals(""))
			{
				sentence = sentence.replace(".","") + ".";  //make sentence punctuation consistent
				this.document = this.document + sentence; //build document string
				this.sentences.add(new SentenceScore(sentence));  //build sentence 
			}
		}
		this.documentAnnotation = new Annotation(document);
		pipeline.annotate(documentAnnotation);
		this.graph = documentAnnotation.get(CorefChainAnnotation.class);
		checkSentenceFormation();
		if(sentencesDelim.size()>0){
			Runner.writeResults(getScoreAsString());
//			EssayGrades eg = Runner.trainedEssayMap.get(essayId);
//			if(eg!=null){
//				System.out.println("----Essay: "+essayId);
//				System.out.println("Word Order Score: "+get1a(sentences)+" vs "+"Expected: "+eg.s1a);
//				System.out.println("Agreement Score: "+get1b(sentences)+" vs "+"Expected: "+eg.s1b);
//				System.out.println("Verb Tense Score: "+get1c(sentences)+" vs "+"Expected: "+eg.s1c);
//				System.out.println("Sentence Formation Score: "+get1d()+" vs "+"Expected: "+eg.s1d);
//				System.out.println("Reference Score: "+get2a()+" vs "+"Expected: "+eg.s2a);
//				System.out.println("Topicality Score: "+get2b()+" vs "+"Expected: "+eg.s2b);
//				System.out.println("Sentence Number Score: "+get3a()+" vs "+"Expected: "+eg.s3a);
//				System.out.println("Final Score: "+getFinal()+" vs "+"Expected: "+eg.fin);
//			}
		}
	}

	/**
	 * Extract sentences from the Essay using CC tags,clauses and CRLF delims. 
	 * Traverses parse tree and gets the indiviual phrases/clauses.
	 * @param filePath,aggressive --> This indicates whether we consider SBAR or not for our sentences. 
	 * @return
	 */
	private ArrayList<String> getSentencesFromEssay(boolean aggressive){
		ArrayList<String> delimSent = new ArrayList<String>();
		BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
		StringBuffer newEssayString = new StringBuffer();
		try {
			String essayString = readFile(filePath);
			iterator.setText(essayString);
			int start = iterator.first();
			for (int end = iterator.next(); end != BreakIterator.DONE;
					start = end, end = iterator.next()) {
//				System.out.println("String new: "+essayString.substring(start,end)+"\n");
				newEssayString.append(essayString.substring(start,end)+"\n");
			}
//			System.out.println("Base Essay from file: "+newEssayString.toString());
			String[] delimPer = newEssayString.toString().replaceAll("(\r?\n)+", "\n").split("\n");
			if(!aggressive){
				for(String s : delimPer){
					delimSent.add(s);
				}
				return delimSent;
			}
			for(String s : delimPer){
				if(!s.trim().equals("")){
					String trailing = ""; //Holds all remaining text
					Queue<Tree> queue = new LinkedList<Tree>();
					Tree parseTree = Runner.lp.parse(s.trim());
					queue.add(parseTree);
					Iterator<Tree> subChildren = parseTree.iterator();
					if(subChildren!=null)
					while(subChildren.hasNext()){
						Tree t = subChildren.next();
						//Deal with sentence frags
						if(t.value().equals("SBAR")){
							queue.add(t);
						} 
						if (t.value().equals("CC")) {
							java.util.List<Tree> trees = t.siblings(parseTree);
							for (Tree t1 : trees) {
								if (t1.value().equals("S"))
									queue.add(t1);
							}
						}
					}
					while(!queue.isEmpty()){
						StringBuffer sentString = new StringBuffer();
						boolean foundS = false;
						Iterator<Tree> t = queue.remove().iterator();
						while(t.hasNext()){
							Tree temp = t.next();
							if(temp != queue.peek()){
								if(temp.value().equals("S") || temp.value().equals("ROOT")){
									foundS = true;
								}else if(temp.isLeaf() && foundS){
									sentString.append(" "+temp.value());
								}
							}else
								break;
						}      
						trailing = sentString.toString();
						if(sentString.toString().length()>0){
							delimSent.add(sentString.toString());
						}
					}
					if(s.indexOf(trailing)+trailing.length()+1<s.length())
						delimSent.add(s.substring(s.indexOf(trailing)+trailing.length()+1));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return delimSent;
	}

	/**
	 * Return Sentence Number Score (3a.)
	 * @return 
	 */
	public int get3a(){
		return (sentences.size()==0)?1:(sentences.size()>=5)?5:sentences.size()-1;
	}

	/**
	 * Return Word Order Score (1a.)
	 * @return
	 */
	public int get1a(ArrayList<SentenceScore> sentences) {
		int count = sentences.size();
		int errCount = 0;
		for(SentenceScore s : sentences){
			errCount+=s.wordOrderErrorCount;
		}
		if(count>5){
			return 5-Math.round(5*(errCount/(float)count));
		}else
			return 5-errCount;
	}

	/**
	 * Return Subject-Verb Agreement Score (1b.)
	 * @return
	 */
	public int get1b(ArrayList<SentenceScore> sentences) {
		int count = sentences.size();
		int errCount = 0;
		for(SentenceScore s : sentences){
			errCount+=s.agreementErrorCount;
		}
		if(count>5){
			return 5-Math.round(5*(errCount/(float)count));
		}else
			return 5-errCount;
	}

	/**
	 * Return Verb Tense Score (1c.)
	 * @return
	 */
	public int get1c(ArrayList<SentenceScore> sentences) {
		int count = sentences.size();
		int errCount = 0;
		for(SentenceScore s : sentences){
			errCount+=s.verbTenseErrorCount;
		}
		if(count>5){
			return 5-Math.round(5*(errCount/(float)count));
		}else
			return 5-errCount;
	}
	
 	/**
	 * Return Sentence Formation Score (1d.)
	 * @return
	 */
	public int get1d() {
		return sentenceFormationScore;
	}

	/**
	 * Return pronoun use score (2a)
	 * 
	 * 
	 */
	public int get2a() 
	{
		computePronounResolutionScore(); //calculate pronoun resolution, this populates fields for coherence. 
		
		double secondCount = 0;
		double firstCount = 0;
		
		for(SentenceScore s : sentences)
		{
			secondCount+=s.coherenceSecondPerson;
			firstCount += s.coherenceFirstPerson;
		}	
		// +first +third_correct -second -third_incorrect
		return (int)Math.min(Math.max(Math.round(firstCount + this.coherenceThirdCorrect - secondCount - this.coherenceThirdIncorect), 1), 5); 
	}	
	
	/**
	 * Return the topicality score
	 * 
	 */	
	public int get2b() 
	{
		double retCount = 0;
		for(SentenceScore s : sentences)
		{
			retCount+=s.topicalityCount;
		}	
		return (int)Math.max(1, Math.min(5, Math.round(retCount)));		
	}	
	
	/**
	 * Returns Final Essay Score
	 * @return
	 */
	public double getFinal(){
		//Final Score = (1a + 1b + 1c + 2*1d + 2a + 3*2b + 3a)/10
		int num = get1a(sentences)+get1b(sentences)+get1c(sentences)+(2*get1d())+get2a()+(3*get2b())+get3a();
		double finalScore = (double)num/10;
		//return (Math.ceil(finalScore)+Math.floor(finalScore))/2;
		return finalScore;
	}

	/**
	 * Convert file content to text
	 * @param path
	 * @return
	 * @throws IOException
	 */
	private String readFile(String path) throws IOException {
		FileInputStream stream = new FileInputStream(new File(path));
		try {
			FileChannel fc = stream.getChannel();
			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
			return Charset.defaultCharset().decode(bb).toString();
		}
		finally {
			stream.close();
		}
	}
	
	
	/**
	 * (1d.)
	 * Checks if sentence has been formed properly
	 */
	public void checkSentenceFormation(){
		ArrayList<SentenceScore> sentences1d = new ArrayList<SentenceScore>();
		ArrayList<String> sentencesDelim = getSentencesFromEssay(false);
		int totalFragError=0;
		int totalFragUsage=0;
		for (String sentence : sentencesDelim) {
			if (!sentence.trim().equals("")){
				SentenceScore sc = new SentenceScore(sentence);
				sentences1d.add(sc);
				totalFragError+=sc.fragErrorCount;
				totalFragUsage+=sc.fragUsageCount;
			}
		}
		sentenceFormationScore = Math.round((get1a(sentences1d)+get1b(sentences1d)+get1c(sentences1d))/(float)3.0);
		//If there are no frag errors add 1 to score.
		if(totalFragError==0){
			sentenceFormationScore+=1;
		}
		else{
			//If multiple frag errors subtract 2 from score or subtract 1 if just one error.
			if(totalFragError>=2)
				sentenceFormationScore-=2;
			else
				sentenceFormationScore-=1;
		}
		if(totalFragUsage==2){
			sentenceFormationScore+=1;
		}
		if(totalFragUsage>=2){
			sentenceFormationScore+=2;
		}
		//set lower bound
		sentenceFormationScore=(sentenceFormationScore<1)?1:sentenceFormationScore;
		//set upper bound
		sentenceFormationScore=(sentenceFormationScore>5)?5:sentenceFormationScore;
	}
	
	
	/**
	 * Calculate document-level score for 2a criteria.
	 * //TODO: we can incorporate the sentence index into the scoring
	 */
	private void computePronounResolutionScore()
	{
		this.coherenceThirdIncorect = 0;
		this.coherenceThirdCorrect = 0;
        for(Map.Entry<Integer, CorefChain> entry : this.graph.entrySet()) 
        {
        	CorefChain c = entry.getValue();
        	List<CorefMention> cm = c.getMentionsInTextualOrder();
        	ArrayList<String> prev = new ArrayList<String>();
        	ArrayList<Integer> prev_idx = new ArrayList<Integer>(); 
        	for(CorefMention cItem : cm) // iterate through relations. appearance is in order of sentences.
        	{
        		//cItem.mentionSpan();
        		String st = Runner.morph.stem(cItem.mentionSpan.toLowerCase());
        		if(Runner.pronouns_3.contains(st))
        		{
        			if(prev.isEmpty())
        			{
        				this.coherenceThirdIncorect++;  // third person pronoun not resolved
        			}
        			else if(prev.size() == 1)
        			{
        				this.coherenceThirdCorrect = this.coherenceThirdCorrect + 1; //resolved to exactly one previous subject
        			}
        			else if(prev.size() == 2)
        			{
        				this.coherenceThirdIncorect = this.coherenceThirdIncorect + 0.25; //scores are rounded so this counts as a half-penalty.
        			}
        			else if(prev.size() >= 3)
        			{
        				this.coherenceThirdIncorect = this.coherenceThirdIncorect + 1; //full penalty
        			}        			  			
        		}
        		else // not a pronoun, save it for later.
        		{
            		prev_idx.add(cItem.sentNum);
            		prev.add(st);
        		}
        	}
        }
	}
	
	/**
	 * Returns essay score as String.
	 * @return
	 */
	public String getScoreAsString(){
		StringBuffer scores = new StringBuffer();
		scores.append(Integer.toString(get1a(sentences))+",");
		scores.append(Integer.toString(get1b(sentences))+",");
		scores.append(Integer.toString(get1c(sentences))+",");
		scores.append(Integer.toString(get1d())+",");
		scores.append(Integer.toString(get2a())+",");
		scores.append(Integer.toString(get2b())+",");
		scores.append(Integer.toString(get3a())+",");
		scores.append(Double.toString(getFinal()));
		return scores.toString();
	}


}
