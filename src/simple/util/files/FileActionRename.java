package simple.util.files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import javax.swing.ListModel;

import simple.io.RenameFormat;
import simple.util.Utils;

public class FileActionRename extends FileAction{
	private UndoRename undoRename= null;
	private boolean makeUndo= false;
	private RenameFormat renameFormat= null;
	private ListModel<File> fileListMod= null;
	private boolean isPreview= false;

	public FileActionRename(ProgressDialog progressWindow){
		super(progressWindow);
	}

	public void setMakeUndo(boolean makeUndo){
		this.makeUndo= makeUndo;
	}
	public void setUndoList(UndoRename undo){
		this.undoRename= undo;
	}
	public void setRenameFormat(RenameFormat format){
		this.renameFormat= format;
	}
	public void setFileList(ListModel<File> fileList){
		fileListMod= fileList;
	}
	public void setIsPreview(boolean isPreview){
		this.isPreview= isPreview;
	}

	@Override
	protected void doWork() throws Exception{
		if(undoRename == null){
			undoRename= new UndoRename();
		}
		undoRename.clear();

		setProgressMax(fileListMod.getSize());

		for(progress= 0;progress < fileListMod.getSize(); progress++) {
			renameFormat.setFile(fileListMod.getElementAt(progress));
			if (!isPreview && renameFormat.rename() > 0) {
				errors++;
			}
			output
				.append("Source:\t")
				.append(renameFormat.toString())
				.append('\n')
				.append(renameFormat.getError())
				.append(":\t")
				.append(renameFormat.toStringTarget())
				.append("\n");
			undoRename.add(renameFormat.toString(), renameFormat.toStringTarget());
		}

		if (makeUndo){
			File undoFile = new File(Utils.getTimeDate()+".undo");
			try(Writer out= new BufferedWriter(new FileWriter(undoFile))) {
				undoRename.save(out);
				output.append("Undo file created.\n");
			} catch (IOException e) {
				output.append("Error making undo file.\n"+e.toString());
				e.printStackTrace();
			}
		}

	}

}
