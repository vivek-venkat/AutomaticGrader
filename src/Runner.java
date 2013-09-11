import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import edu.stanford.nlp.process.*;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;


public class Runner {
	ArrayList<EssayScore> essaysList = new ArrayList<EssayScore>();
	static HashMap<String,EssayGrades> trainedEssayMap = new HashMap<String, EssayGrades>();
	
	static Morphology morph = new Morphology();
	static ArrayList<String> pronouns_1 = new ArrayList<String>(); //1st person objective pronouns	
	static ArrayList<String> pronouns_2 = new ArrayList<String>(); //2nd person subjective pronouns
	static ArrayList<String> pronouns_3 = new ArrayList<String>(); //3rd person subjective pronouns

	static ArrayList<String> topic_lexicon = new ArrayList<String>(); //list of words pertaining to topic	
	
	static ArrayList<String> pronouns_subj_n3 = new ArrayList<String>(); //Non-3rd person subjective pronouns
	static ArrayList<String> pronouns_subj_3 = new ArrayList<String>(); //3rd person subjective pronouns
	
	static LexicalizedParser lp;
	static BufferedWriter outE; 
	static BufferedWriter outR; 
	
	Runner(){
		pronouns_1 = populateStringVector("pronouns_1.txt", pronouns_1);
		pronouns_2 = populateStringVector("pronouns_2.txt", pronouns_2);
		pronouns_3 = populateStringVector("pronouns_3.txt", pronouns_3);
		topic_lexicon = populateStringVector("topicLexicon.txt", topic_lexicon);
		pronouns_subj_3.add("he");
		pronouns_subj_3.add("she");
		pronouns_subj_3.add("it");
		pronouns_subj_3.add("they");
//		populateTrainingData();
	}
		
	public ArrayList<String> populateStringVector(String filename, ArrayList<String> ret){
		//Pronouns
		try{
			DataInputStream in = new DataInputStream(new FileInputStream(filename));
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null)
			{
				ret.add(morph.stem(strLine.replace("-", "").toLowerCase()).trim());
			}
			in.close();
		}catch (Exception e){
			e.printStackTrace();
		}
		return ret;
	}
	
	/**
	 * Gets grades from existing input file data.
	 */
	private void populateTrainingData(){
		try{
			System.out.println("Reading Training Data");
			FileInputStream fstream = new FileInputStream("grades.txt");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null){
				String[] rule = strLine.split("\t");
				if(rule.length==9){
					if(strLine.contains("Essay"))
						continue;
					EssayGrades essayGrades = new EssayGrades(
							rule[0], Integer.parseInt(rule[1]), 
							Integer.parseInt(rule[2]), Integer.parseInt(rule[3]),
							Integer.parseInt(rule[4]), Integer.parseInt(rule[5]), 
							Integer.parseInt(rule[6]), Integer.parseInt(rule[7]),Double.parseDouble(rule[8]));
					trainedEssayMap.put(essayGrades.essayId+".txt", essayGrades);
				}
			}
			in.close();
		}catch (Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * read the essay from file.
	 * @param fileName
	 */
	private void readEssay(String folderName,String fileName){
		try {
			if(!fileName.equals("")){
				File f = new File(folderName+"/"+fileName);
				if(f.exists()) {
					EssayScore es = new EssayScore(fileName); 
					es.computeEssayScore(folderName+"/"+fileName);
					essaysList.add(es);
				}else
					System.out.println("Please input a valid file.");
			}else{
				System.out.println("Please input a file.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Read a folder and process all files in it as single essays alphabetically/lexically.
	 * @param folderName
	 */
	private void gradeAllEssays(String folderName){
		try {
			if(!folderName.equals("")){
				File folder = new File(folderName);
				if(folder.exists()){
					if(folder.isDirectory() && folder.list().length>0){
						lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
						String[] essays = folder.list();
						Arrays.sort(essays);
						for(String fileName : essays){
							if(!fileName.startsWith("."))
								readEssay(folderName,fileName);
						}
						outR.close();
//						outE.close();
//						getErrorRate();
						System.out.println("\nCompleted.");
						System.out.println("Please find the Scores in output.txt.");
					}else
						System.out.println("Folder is Empty!");
				}else
					System.out.println("Please input a valid Folder.");
			}else{
				System.out.println("Please input a Folder.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Write errors to File
	 * @param text
	 */
	public static void writeOuput(String text){
		try {
			if(outE==null)
				outE = new BufferedWriter(new FileWriter("errors.txt"));
			outE.write(text+"\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Write results to file.
	 * @param text
	 */
	public static void writeResults(String text){
		try {
			if(outR==null)
				outR = new BufferedWriter(new FileWriter("output.txt"));
			outR.write(text+"\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Determines a score that defines error rate. 
	 * A score < 0.500 is good.
	 */
	private void getErrorRate(){
		double avg1a = 0;
		double avg1b = 0;
		double avg1c = 0;
		double avg1d = 0;
		double avg2a = 0;
		double avg2b = 0;
		double avg3a = 0;
		int essayNum = essaysList.size();
		for(EssayScore es : essaysList){
			if(trainedEssayMap.get(es.essayId)==null)
				continue;
			avg1a += (trainedEssayMap.get(es.essayId).s1a==0)?0:Math.abs(es.get1a(es.sentences)-trainedEssayMap.get(es.essayId).s1a);
			avg1b += (trainedEssayMap.get(es.essayId).s1b==0)?0:Math.abs(es.get1b(es.sentences)-trainedEssayMap.get(es.essayId).s1b);
			avg1c += (trainedEssayMap.get(es.essayId).s1c==0)?0:Math.abs(es.get1c(es.sentences)-trainedEssayMap.get(es.essayId).s1c);
			avg1d += (trainedEssayMap.get(es.essayId).s1d==0)?0:Math.abs(es.get1d()-trainedEssayMap.get(es.essayId).s1d);
			avg2a += (trainedEssayMap.get(es.essayId).s2a==0)?0:Math.abs(es.get2a()-trainedEssayMap.get(es.essayId).s2a);
			avg2b += (trainedEssayMap.get(es.essayId).s2b==0)?0:Math.abs(es.get2b()-trainedEssayMap.get(es.essayId).s2b);
			avg3a += (trainedEssayMap.get(es.essayId).s3a==0)?0: Math.abs(es.get3a()-trainedEssayMap.get(es.essayId).s3a);
		}
		avg1a/=essayNum;
		avg1b/=essayNum;
		avg1c/=essayNum;
		avg1d/=essayNum;
		avg2a/=essayNum;
		avg2b/=essayNum;
		avg3a/=essayNum;
		double score = avg1a+avg1b+avg1c+(2*avg1d)+avg2a+(3*avg2b)+avg3a;
		score/=10;
		System.out.println("\nFinal Overall Score: "+score);
	}

	public static void main(String argsp[]){
		new Runner().gradeAllEssays(argsp[0]);
	}
}
