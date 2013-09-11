
public class EssayGrades {
	String essayId;
	int s1a;
	int s1b;
	int s1c;
	int s1d;
	int s2a;
	int s2b;
	int s3a;
	double fin;
	
	public EssayGrades(String essayId,int s1a, int s1b, int s1c, int s1d, int s2a, int s2b,
			int s3a,double fin) {
		this.essayId=essayId;
		this.s1a = s1a;
		this.s1b = s1b;
		this.s1c = s1c;
		this.s1d = s1d;
		this.s2a = s2a;
		this.s2b = s2b;
		this.s3a = s3a;
		this.fin=fin;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("EssayID: "+essayId+"- ");
		sb.append("1a:"+s1a+" ");
		sb.append("1b:"+s1b+" ");
		sb.append("1c:"+s1c+" ");
		sb.append("1d:"+s1d+" ");
		sb.append("2a:"+s2a+" ");
		sb.append("2b:"+s2b+" ");
		sb.append("3a:"+s3a+" ");
		sb.append("Final:"+fin+" ");
		return sb.toString();
	}
}
