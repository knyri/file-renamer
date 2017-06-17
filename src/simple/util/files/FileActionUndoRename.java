package simple.util.files;

import java.io.File;

import simple.gui.factory.SJOptionPane;

public class FileActionUndoRename extends FileAction{
	private UndoRename undoRename= null;
	public FileActionUndoRename(ProgressDialog progressWindow){
		super(progressWindow);
	}

	public void setUndoList(UndoRename undo){
		this.undoRename= undo;
	}

	@Override
	protected void doWork() throws Exception{
		if(undoRename == null){
			undoRename= new UndoRename();
		}
		setProgressMax(undoRename.size());

		for(UndoRename.Entry entry: undoRename){
			progress++;
			output
				.append(entry.newFile)
				.append(" --to-- ")
				.append(entry.oldFile)
				.append('\n');
			File from= new File(entry.newFile);
			File to= new File(entry.oldFile);
			if(!from.exists()){
				if(!to.exists()){
					errors++;
					output.append("Neither file exists. Cannot undo\n");
					continue;
				}
			}else if(to.exists()){
				// destination exists. Overwrite?
				int result= SJOptionPane.showQuestionMessage("Original file exists. Do you want to overwrite?\n"+to.getAbsolutePath(), "File Exists", SJOptionPane.moYN);
				if(result == SJOptionPane.moYES){
					if(!to.delete()){
						errors++;
						output.append("Failed to delete the file.\n");
						continue;
					}
				}
			}
			if(!from.renameTo(to)){
				errors++;
				output.append("Failed to rename the file.\n");
			}
		}
	}

}
