package extractionCDRapi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;

import objectCDR.SRLObject;

public class SRL {
	private SRLObject[] srlResult;
	private String command;
	private String commandDir;
	private File tempDir;
	private long timeout;
	private int cycle;
	public SRL() throws IOException{
		this.srlResult=null;
		this.command="./senna";
		this.commandDir="./senna";
		this.tempDir=new File(".");
		this.timeout=1000;
		this.cycle=5;
		
	}
	
	public SRLObject[] srl(String senStr) throws IOException{
				
				System.out.println(senStr);
				String filenamePrefix = "000";
				File f = File.createTempFile(filenamePrefix, ".txt", tempDir);
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"));
				writer.write(senStr);
				writer.close();

				command += " -srl -offsettags";
				ProcessRunner pw = new ProcessRunner(command , commandDir,senStr+"\n");
				int ci;
				for (ci=0;ci<cycle && pw.getResult()==null;ci++)
					pw.await(timeout);

				f.delete();
				if (pw.getResult()==null){
					System.err.println("SRL no result!");
					return null;
				}
				else if (ci==cycle){
					System.err.println("SRL timeout!");
					return null;
				}
				String result = pw.getResult();
				String error = pw.getError();
				if (error != null){
					System.err.println("SRL error is: " + error);
					return null;
				}


				StringReader sr = new StringReader(result);
				BufferedReader br = new BufferedReader(sr);
				int []start=null;
				while (br.ready()){
					String line=br.readLine();
					if (line==null)
						break;
					String[] sep=line.split("\\t");
					
					for (int i=0;i<sep.length;i++){
						sep[i]=sep[i].trim();
					}
					if (start==null){
						if (sep.length>3){
							start=new int[sep.length-3];
							srlResult=new SRLObject[sep.length-3];
							for (int i=0;i<sep.length-3;i++)
								srlResult[i]=new SRLObject();
						}
						else{
							srlResult=null;
							break;
						}
					}
					for (int i=3;i<sep.length;i++){
						if (sep[i].contains("-")){
							int dashIndex=sep[i].indexOf("-");
							String pre=sep[i].substring(0,dashIndex);
							String post=sep[i].substring(dashIndex+1);
							if (post.equals("V")){
								srlResult[i-3].rel=sep[2];
							}
							else if (post.length()==2 && post.indexOf("A")==0){
								
								if (pre.equals("B")){
									int spaceIndex=sep[1].indexOf(" ");
									start[i-3]=Integer.parseInt(sep[1].substring(0, spaceIndex));
								}
								else if (pre.equals("E")){
									
									int spaceIndex=sep[1].indexOf(" ");
									int end=Integer.parseInt(sep[1].substring(spaceIndex+1));
									srlResult[i-3].args.add(senStr.substring(start[i-3], end));
								}
								else if (pre.equals("S")){
									srlResult[i-3].args.add(sep[0]);
								}
							}
						}
					}
				}
				br.close();
				sr.close();
				return srlResult;
	}
	
}
