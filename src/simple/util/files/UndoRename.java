package simple.util.files;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import simple.io.ParseException;

public class UndoRename implements Iterable<UndoRename.Entry>{
	private final List<Entry> undoList= new ArrayList<>();

	public UndoRename(){}

	public int size(){
		return undoList.size();
	}

	public void save(Writer out) throws IOException{
		for(Entry entry: undoList){
			out.write(entry.oldFile);
			out.write('\t');
			out.write(entry.newFile);
			out.write('\n');
		}
	}
	 public void load(Reader in) throws IOException, ParseException{
		 LineNumberReader entries= new LineNumberReader(in);
		 Pattern tabSplit= Pattern.compile("\t");
		 String line;

		 while( null != (line= entries.readLine()) ){
			 String[] entry= tabSplit.split(line);

			 if(entry.length != 2){
				 if(entry.length == 0){
					 continue;
				 }
				 throw new ParseException("Expected 2 parameters. Got "+ entry.length +" on line "+ entries.getLineNumber());
			 }

			 undoList.add(new Entry(entry[0], entry[1]));
		 }
	 }

	 public void add(String src, String dest){
		 undoList.add(new Entry(src, dest));
	 }

	 public void clear(){
		 undoList.clear();
	 }

	 public void undo(){
		 for(Entry entry: undoList){
			 File src= new File(entry.oldFile);
			 File dest= new File(entry.newFile);

			 if(dest.exists()){
				 dest.renameTo(src);
			 }
		 }
	 }
	 public final static class Entry {
		 public final String oldFile, newFile;
		 public Entry(String oldFile, String newFile){
			 this.oldFile= oldFile;
			 this.newFile= newFile;
		 }
	 }
	@Override
	public Iterator<Entry> iterator(){
		return undoList.iterator();
	}
}

