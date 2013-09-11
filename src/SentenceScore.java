import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;


public class SentenceScore {
	Tree parse;
	String sentence;
	
	int agreementErrorCount = 0;
	int wordOrderErrorCount = 0;
	int verbTenseErrorCount = 0;
	// Word Order variables
	int subjectIndex = -1;
	int verbIndex = -1;
	int objectIndex = -1;
	// Agreement variables
	int agreementNumber = 0; // 1-Plural, 0-Singular
	int agreementPerson = 0; // 1-3rd person, 0-Non-3rd person
	boolean isSubjPronoun = false;
	// Verb Tense variables
	int verbTense = -1; // 0-Present, 1-Past ,2-Future
	
	//coherence variables
	double coherenceFirstPerson = 0; // count of first person uses
	double coherenceSecondPerson = 0; //count of second
	int fragUsageCount=0;
	int fragErrorCount=0; //Not the number of times someone dies in an FPS game. 
	double topicalityCount = 0;
	
	
	static ArrayList<String> verbs3sg = new ArrayList<String>();
	static ArrayList<String> verbsn3sg = new ArrayList<String>();
	static ArrayList<String> subjectPronouns = new ArrayList<String>();
	static ArrayList<String> topicWords = new ArrayList<String>();

	SentenceScore(String sentence) {
		this.sentence = sentence;
//		Runner.writeOuput("\nSentence: " + sentence);
		this.parse = Runner.lp.parse(sentence);
//		Runner.writeOuput("Parse: " + parse.taggedYield().toString());
		computeWordOrderScore();//1a
		computeAgreementScore();//1b
		computeVerbTenseScore();//1c
		computeSentenceCoherence(); //2a
		computeFragScore(); //1d
		computeTopicality(); //2b		
	}
	
	/**
	 * Ensure words are in the correct order (1a.)
	 * This is done by checking dependencies and 
	 */
	public void computeWordOrderScore() {
//		Runner.writeOuput("Computing Word Order Score: ");
		int subjCount = 0;
		GrammaticalStructure gs = new PennTreebankLanguagePack()
				.grammaticalStructureFactory().newGrammaticalStructure(parse);
		Collection<TypedDependency> tdl = gs.allTypedDependencies();
		String depString = tdl.toString();
		if (!tdl.toString().contains("nsubj")) {
			wordOrderErrorCount = 1;
			// There is no subject.
			// Technically this is possible in imperative sentences with phrase
			// structure S-> VP. But I havent seen it in the corpus.
			// Oh Well.
//			Runner.writeOuput("ERROR: No Subject Found.");
			return;
		}
		for (TypedDependency td : tdl) {
			if (td.reln().getShortName().startsWith("nsubj"))
				subjCount += 1;
			// Find Nominal Subject and its corresponding obj if it exists.
			if (td.reln().getShortName().startsWith("nsubj")
					&& subjectIndex < 0) {
				subjectIndex = Integer
						.parseInt(td.dep().toString().split("-")[1]);
				verbIndex = Integer.parseInt(td.gov().toString().split("-")[1]);
				verbIndex = (isVerb(verbIndex)) ? verbIndex : -1;
			}
			// Pick copula if verb not found from subject.
			if (td.reln().getShortName().equals("cop") && verbIndex < 0) {
				verbIndex = Integer.parseInt(td.dep().toString().split("-")[1]);
				objectIndex = Integer
						.parseInt(td.gov().toString().split("-")[1]);
			}
			// Pick Aux if copula not present
			if (td.reln().getShortName().startsWith("aux") && verbIndex < 0) {
				verbIndex = Integer.parseInt(td.dep().toString().split("-")[1]);
				if (depString.contains("cop"))
					verbIndex = -1;

			}
			// Pick obj if it exists
			if (td.reln().getShortName().endsWith("obj") && objectIndex < 0) {
				objectIndex = Integer
						.parseInt(td.dep().toString().split("-")[1]);
				// Do this only if there is no direct or indirect obj. Worst
				// case pick up prep obj
				if (td.reln().getShortName().equals("pobj")) {
					if (depString.contains("dobj")
							|| depString.contains("iobj")) {
						objectIndex = -1;
					}
				}
			}
		}
		if (subjCount > 1) {
//			Runner.writeOuput("ERROR: Multiple Subjects: " + subjCount);
			wordOrderErrorCount += subjCount;
			return;
		}
//		System.out.println("verbIndex: " + verbIndex);
//		System.out.println("subjectIndex: " + subjectIndex);
//		System.out.println("objectIndex: " + objectIndex);
//		System.out.println("Given dependency: " + tdl);
		// If verb lies before subject
		if (verbIndex < subjectIndex) {
			wordOrderErrorCount = 1;
//			Runner.writeOuput("ERROR: verb is at index: " + verbIndex);
		} else if (objectIndex != -1) {
			// If the object lies before the verb or the subject its wrong.
			if (objectIndex < verbIndex || objectIndex < subjectIndex) {
//				Runner.writeOuput("ERROR: with object at index: " + objectIndex);
				wordOrderErrorCount = 1;
			}
		}
//		if(wordOrderErrorCount==0)
//			Runner.writeOuput("No Errors.");
	}

	/**
	 * (1b.)
	 * Check agreement between subject(Pronoun or Noun) and verb. Checks
	 * agreement in person and number (1b.) These rules below indicate those
	 * tags that WONT follow the tag. PRP 3P-Sing -/> VBZ PRP 3P-plural -/> VBP
	 * PRP N3P-Sing -/> VBP PRP N3P-Plural -/> VBP NNP -/> VBZ NNPS -/> VBP NN
	 * -/> VBZ NNS -/> VBP
	 */
	public void computeAgreementScore() {
//		Runner.writeOuput("Computing Agreement Score: ");
		GrammaticalStructure gs = new PennTreebankLanguagePack()
				.grammaticalStructureFactory().newGrammaticalStructure(parse);
		List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
		if (!tdl.toString().contains("nsubj")) {
			agreementErrorCount = 1;
//			Runner.writeOuput("ERROR: No Subject Found.");
			// There is no subject.
			// Technically this is possible in imperative sentences with phrase
			// structure S-> VP. But I havent seen it in the corpus.
			// Oh Well.
			return;
		}
		for (TypedDependency td : tdl) {
			// Find first subject based tag and break. //Can match nsubj or
			// nsubjpass. There haven't been cases of csubj though. Here's
			// hoping. *fingerscrossed.
			if (td.reln().getShortName().startsWith("nsubj")) {
				String tagOfWord = parse
						.taggedYield()
						.get(Integer
								.parseInt(td.dep().toString().split("-")[1]) - 1)
						.tag();
				String word = parse
						.taggedYield()
						.get(Integer
								.parseInt(td.dep().toString().split("-")[1]) - 1)
						.word();
				// Pronoun
				if (tagOfWord.startsWith("PR")) {
					isSubjPronoun = true;
					agreementPerson = Runner.pronouns_subj_3.contains(word
							.toLowerCase()) ? 1 : 0;
					agreementNumber = (word.toLowerCase().equals("they") || word
							.toLowerCase().equals("we")) ? 1 : 0;
				}
				// Noun
				if (tagOfWord.startsWith("NN")) {
					agreementNumber = (tagOfWord.endsWith("S")) ? 1 : 0;
					agreementPerson = (tagOfWord.contains("P")) ? 1 : 0;
				}
				break;
			}
		}
//		System.out.println("Given dependency: " + tdl);
		if(!containsVerb()){
			agreementErrorCount = 1;
//			Runner.writeOuput("ERROR: No Verb Found..");
			return;
		}
		for (TaggedWord word : parse.taggedYield()) {
			if (word.tag().startsWith("VB"))
				if (!isValidNumberPerson(word.tag())) {
					agreementErrorCount = 1;
//					Runner.writeOuput("ERROR: Error with tag: " + word.tag()
//							+ " for word: " + word.word());
					return;
				}
		}
//		if(agreementErrorCount==0)
//			Runner.writeOuput("No Errors.");
	}

	/**
	 * Check if given Verb tag is valid in agreement
	 * @param tag
	 * @return
	 */
	private boolean isValidNumberPerson(String verbTag) {
		if (agreementNumber == 1 && agreementPerson == 0) {
			if (verbTag.endsWith("Z"))
				return false;
		} else if (agreementNumber == 1 && agreementPerson == 1) {
			if (verbTag.endsWith("Z"))
				return false;
		} else if (agreementNumber == 0 && agreementPerson == 0) {
			if (isSubjPronoun) {
				if (verbTag.endsWith("Z"))
					return false;
			} else {
				if (verbTag.endsWith("P"))
					return false;
			}
		} else if (agreementNumber == 0 && agreementPerson == 1) {
			if (verbTag.endsWith("P"))
				return false;
		}
		return true;
	}

	/**
	 * (1c.)
	 * Check if the verb tense is ok. Handles verbs with wrong tense, missing
	 * verbs or extra verbs
	 */
	public void computeVerbTenseScore() {
//		Runner.writeOuput("Computing Verb Tense Score: ");
		int firstVerbIndex = 0;
		int verbCount=0;
		String previousTag;
		String firstVerbTag = null;
//		System.out.println("PP: " + parse.taggedLabeledYield());
		for (CoreLabel word : parse.taggedLabeledYield()) {
			// Find first verb
			if (word.tag().startsWith("VB")) {
				verbCount+=1;
				firstVerbIndex = word.index();
				firstVerbTag = word.tag();
				break;
			}
		}
		if (firstVerbTag != null) {
//			System.out.println("VIndex: " + firstVerbIndex);
			previousTag=firstVerbTag;
			// Check if "VB"
			if (firstVerbTag.length() == 2) {
				if(firstVerbIndex>0)
				if (!parse.taggedYield().get(firstVerbIndex - 1).tag()
						.equals("MD")) {
					verbTenseErrorCount += 1;
//					Runner.writeOuput("ERROR: Missing Modal.");
				}
			}
			// Starting verb cannot be gerundive or past participle.
			if (firstVerbTag.endsWith("G") || firstVerbTag.endsWith("N")) {
//				Runner.writeOuput("ERROR: Missing Verb.");
				verbTenseErrorCount += 1;
				verbTense=0;
			}
			if (firstVerbTag.endsWith("D")) {
				verbTense = 1;
			}
			// Otherwise VBP or VBZ which are ok to begin with.
			// Now check if there are any more verbs
			for (int i = firstVerbIndex+1; i < parse.taggedLabeledYield().size(); i++) {
				CoreLabel word = parse.taggedLabeledYield().get(i);
				if (word.tag().startsWith("VB")) {
					verbCount+=1;
					// Check the tense of this one...
					if(previousTag.endsWith("D")){
						if(!word.tag().endsWith("N")){
							verbTenseErrorCount += 1;
//							Runner.writeOuput("ERROR: Tense is wrong");
						}
						if(word.tag().equals("VB")){
							if (!parse.taggedYield().get(firstVerbIndex - 1).tag()
									.equals("TO")) {
								verbTenseErrorCount += 1;
//								Runner.writeOuput("ERROR: Missing TO Verb");
							}
						}
					}
					if(previousTag.endsWith("P") || previousTag.endsWith("Z")){
						if(word.tag().endsWith("P") || word.tag().endsWith("Z")){
							verbTenseErrorCount += 1;
//							Runner.writeOuput("ERROR: Too many verbs.");
						}
						if (word.tag().endsWith("D") || word.tag().endsWith("N")) {
							verbTenseErrorCount += 1;
//							Runner.writeOuput("ERROR: Tense is wrong");
						}
						if(word.tag().equals("VB")){
							if (!parse.taggedYield().get(i - 1).tag()
									.equals("TO")) {
								verbTenseErrorCount += 1;
//								Runner.writeOuput("ERROR: Missing TO Verb");
							}
						}
					}
					if(previousTag.endsWith("G")){
						if(word.tag().endsWith("P") || word.tag().endsWith("Z")){
							verbTenseErrorCount += 1;
//							Runner.writeOuput("ERROR: Tense is wrong");
						}
						if (word.tag().endsWith("D")) {
							verbTenseErrorCount += 1;
//							Runner.writeOuput("ERROR: Tense is wrong");
						}
						if(word.tag().equals("VB")){
							if (!parse.taggedYield().get(i - 1).tag()
									.equals("TO")) {
								verbTenseErrorCount += 1;
//								Runner.writeOuput("ERROR: Missing TO Verb");
							}
						}
					}
					
					previousTag=word.tag();
					if(verbCount>3){
//						Runner.writeOuput("ERROR: Too many verbs.");
						verbTenseErrorCount+=1;
						return;
					}
				}
			}
		} else {
//			Runner.writeOuput("ERROR: Missing verb.");
			verbTenseErrorCount = 1;
			return;
		}
//		if(verbTenseErrorCount==0)
//			Runner.writeOuput("No Errors.");
	}
	
	
	/**
	 * (1d.) Find cases of poorly fragmented sentences.
	 * If SBAR is present it must have an S within it and it must have a IN connecting its previous S
	 */
	public void computeFragScore(){
		ArrayList<Tree> nodes = findFrag(parse);
		 for (Tree t : nodes){
			 if(t.value().equals("FRAG"))
				 fragErrorCount+=1;
			 if(t.value().equals("SBAR")){
//				 System.out.println("Parse: "+parse.taggedYield());
				 Tree[] t1 = t.children();
				 //Check if the SBAR has two children with Tags IN and S
				 if(t1.length!=2)
					 fragErrorCount+=1;
				 else{
					 if(t1[0].value().equals("IN") && t1[0].value().equals("S"))
						 fragUsageCount+=1;
				 }
			 }
		 }
	}
	
	/**
	 * 2a. Is the essay coherent ?
	 * The sentence-level grading is done here. The document-level third-person pronoun resolution is done in computeSentenceCoherence
	 * 
	 */
	public void computeSentenceCoherence()
	{
		this.coherenceFirstPerson = 0;
		this.coherenceSecondPerson = 0;
		ArrayList<Tree> pronouns = findPronouns(parse);
		boolean first_flag = true;
		boolean second_flag = true;
		
		for (Tree t : pronouns)
		{
			CoreLabel word = t.taggedLabeledYield().get(0);
			if(Runner.pronouns_1.contains(Runner.morph.stem(word.word().toString().toLowerCase())) & first_flag)
			{
				this.coherenceFirstPerson = this.coherenceFirstPerson+0.6;
				first_flag = false;
			}
			else if(Runner.pronouns_2.contains(Runner.morph.stem(word.word().toString().toLowerCase())) & second_flag)
			{
				this.coherenceSecondPerson++;
				second_flag = false;
			}
			else if(!(Runner.pronouns_3.contains(Runner.morph.stem(word.word().toString().toLowerCase()))) & second_flag) //not a third person pronoun?
			{
				//System.out.println(Runner.morph.stem(word.word().toString().toLowerCase()));
				this.coherenceSecondPerson++; //I'm not sure what will come here. use penalty term
				second_flag = false;
			}
		}		
	}
		
	
	
	/**
	 * 2b. How on-topic is the sentence ?
	 */
	public void computeTopicality()
	{
		this.topicalityCount = 0;
		for (CoreLabel word : parse.taggedLabeledYield())
		{
			String s = Runner.morph.stem(word.word().toString().toLowerCase());
			if(Runner.topic_lexicon.contains(s) & !SentenceScore.topicWords.contains(s))
			{
				this.topicalityCount = this.topicalityCount+ 1.2;
				SentenceScore.topicWords.add(s);
			}
		}
	}
	
	
	/**
	 * Utility function checks if a word at the given index is a verb.
	 * @param wordIndex
	 * @return
	 */
	private boolean isVerb(int wordIndex) {
		String tagOfWord = parse.taggedYield().get(wordIndex - 1).tag();
		if (tagOfWord.startsWith("VB"))
			return true;
		return false;
	}
	
	/**
	 * Utility function checks if the sentence contains a verb.
	 * @return
	 */
	private boolean containsVerb(){
		for(TaggedWord word: parse.taggedYield()){
			if(word.tag().startsWith("VB"))
				return true;
		}
		return false;
	}
	
	// cite: from stackoverflow: http://stackoverflow.com/questions/10474827/get-certain-nodes-out-of-a-parse-tree
	public ArrayList<Tree> findPronouns(Tree t) 
	{
	    ArrayList<Tree> pronouns = new ArrayList<Tree>();
	    if (t.label().value().equals("PRP") || t.label().value().equals("PRP$"))
	        pronouns.add(t);
	    else
	        for (Tree child : t.children())
	            pronouns.addAll(findPronouns(child));
	    return pronouns;
	}	
	
	
	/**
	 * Gets all sentence fragments and returns all nodes of that type.
	 * @param t
	 * @return
	 */
	public ArrayList<Tree> findFrag(Tree t){
		ArrayList<Tree> frag = new ArrayList<Tree>();
	    if (t.label().value().equals("FRAG") || t.label().value().equals("SBAR"))
	    	frag.add(t);
	    else
	        for (Tree child : t.children())
	        	frag.addAll(findFrag(child));
	    return frag;
	}
}
