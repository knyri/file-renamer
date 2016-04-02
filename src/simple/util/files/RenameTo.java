package simple.util.files;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import simple.gui.AboutWindow;
import simple.gui.SDialog;
import simple.gui.SwingWorkerProgressWindow;
import simple.gui.component.SMenuBar;
import simple.gui.factory.SJOptionPane;
import simple.gui.factory.SJPanel;
import simple.gui.factory.SwingFactory;
import simple.io.FileUtil;
import simple.io.ParseException;
import simple.io.RenameFormat;
import simple.io.StreamFactory;
import simple.util.FileLoader;
import simple.util.Utils;
import simple.util.do_str;
/**
 * Rename Tokens:
 * <dl>
 * <dt>$N</dt>
 * <dd>File name without extension</dd>
 * <dt>$N(n)</dt>
 * <dd>Characters starting from n of the file name without extension. (inclusive)
 * You can use negative numbers to start from the end of the filename.
 * E.g. 'hello.txt' with  $N(-3) will give 'llo'.</dd>
 * <dt>$N(n,m)</dt>
 * <dd>Characters n to m of the file name without extension. (inclusive, exclusive)
 * You can use negative numbers to start from the end of the filename.
 * E.g. 'hello.txt' with  $N(-2,-1) will give 'l'.</dd>
 * <dt>$F</dt>
 * <dd>File name with extension.</dd>
 * <dt>$F(n,m)</dt>
 * <dd>Characters n to m of the file name with extension. (inclusive, exclusive)
 * You can use negative numbers to start from the end of the filename.
 * E.g. 'hello.txt' with  $F(-2,-1) will give 'x'.</dd>
 * <dt>$F(n)</dt>
 * <dd>Characters starting from n of the file name with extension. (inclusive)
 * You can use negative numbers to start from the end of the filename.
 * E.g. 'hello.txt' with  $F(-3) will give 'txt'.</dd>
 * <dt>$E</dt>
 * <dd>Extension of file without '.'
 * E.g. 'hello.txt.log' with $E will give 'log'</dd>
 * <dt>$D</dt>
 * <dd>Path without the ending '\'</dd>
 * <dt>$D(n)</dt>
 * <dd>Part n of the directory name from the lowest level.
 * E.g. $D(1) of "C:\program files\games\doom.exe" = "games"</dd>
 * <dt>$M</dt>
 * <dd>Moves the file up one directory. Equivalent to $M(1).</dd>
 * <dt>$M(n)</dt>
 * <dd>Moves the file up the directory tree n times.</dd>
 * <dt>$#</dt>
 * <dd>Number starting from 0. Additional '#' can be added to pad with '0's.
 * (e.g. $## would result in a scheme like 00, 01, 02, etc.)</dd>
 * <dt>$P</dt>
 * <dd>The path with out the drive letter.</dd>
 * </dl>
 * Created: 2008
 * @author Kenneth Pierce
 *
 */
public final class RenameTo implements ActionListener, DropTargetListener {
	final DefaultListModel<File> gfileListMod = new DefaultListModel<File>();
	final JList<File> gfileListDis = new JList<File>(gfileListMod);
	final JTextField gtfSyntax = new JTextField();
	final JProgressBar previewProgress=new JProgressBar();
	final JFrame gPreviewFrame;
	final JTextArea gPreviewText = new JTextArea();
	final JTextField gtfRep1 = new JTextField();
	final JTextField gtfRep2 = new JTextField();
	//final JComboBox gReplaceCombo = new JComboBox();
	final JFrame gParentFrame = SwingFactory.makeDefaultJFrame("Rename Utility");
	final JCheckBoxMenuItem gRecursiveAddCheck = new JCheckBoxMenuItem("Add Subdirectories");
	final JCheckBoxMenuItem gUndoFileCheck = new JCheckBoxMenuItem("Make Undo File");
	final JCheckBoxMenuItem gResolveUrlCharsCheck = new JCheckBoxMenuItem("Resolve URL Characters");
	final JCheckBoxMenuItem gUriFriendlyFile = new JCheckBoxMenuItem("URI Friendly File Name");
	final JCheckBoxMenuItem gUriFriendlyDir = new JCheckBoxMenuItem("URI Friendly Dir Name");
	final AboutWindow gHelpWin;
	final SwingWorkerProgressWindow gFileLoaderWin = new SwingWorkerProgressWindow(gParentFrame, "Adding Files");
	File gLogFile;
	private int giStart = 0;
	private final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1);
	private final SDialog gFilterWin = new SDialog(gParentFrame, "Select files", true);
	final JTextField gFilterInput = new JTextField();
	private final Runnable doRename= new Runnable(){
		final StringBuffer output=new StringBuffer();
		final Runnable update=new Runnable(){
			@Override
			public void run(){
				String app=output.toString();
				output.setLength(0);
				gPreviewText.append(app);
				previewProgress.setValue(progress);
			}
		};
		private int progress=0;
		@Override
		public void run(){
			ScheduledFuture<?> task= TIMER.scheduleAtFixedRate(update,0,700,TimeUnit.MILLISECONDS);
			BufferedOutputStream out = null;
			if (gUndoFileCheck.isSelected()) {
				gLogFile = new File(Utils.getTimeDate()+".undo");
				try {
					out = StreamFactory.getBufferedOutputStream(gLogFile);
					gPreviewText.append("Undo file created.\n");
				} catch (final FileNotFoundException e) {
					gPreviewText.append("Error making undo file.\n"+e.toString());
					e.printStackTrace();
				}
			}
			RenameFormat rfTmp;
			try{
				rfTmp=new RenameFormat(null,gtfSyntax.getText());
			}catch(ParseException e1){
				output.append(e1.getMessage());
				return;
			}
			int err = 0;
			rfTmp.setNumber(giStart);
			previewProgress.setValue(0);
			previewProgress.setMaximum(gfileListMod.size());
			for(progress=0;progress<gfileListMod.size();progress++) {
				rfTmp.setFile(gfileListMod.get(progress));
				if (rfTmp.rename()>0) {
					err++;
				}
				output.append("Source:\t"+rfTmp.toString()+"\n"+
						rfTmp.getError()+":\t"+rfTmp.toStringTarget()+"\n");
				if (gUndoFileCheck.isSelected() && out != null) {
					try {
						out.write((rfTmp.toString()+"\t"+rfTmp.toStringTarget()+"\n").getBytes("UTF-8"));
					} catch (final IOException e) {
						e.printStackTrace();
					}
				}
			}
			if (out!=null)
				FileUtil.close(out);
			task.cancel(false);
			update.run();
			gPreviewText.append(err+" errors.\nDone.");
			previewProgress.setValue(previewProgress.getMaximum());
		}
	},
	doUndo=new Runnable(){
		final StringBuffer output=new StringBuffer();
		final Runnable update=new Runnable(){
			@Override
			public void run(){
				String app=output.toString();
				output.setLength(0);
				gPreviewText.append(app);
				previewProgress.setValue(progress);
			}
		};
		private int progress=0;
		@Override
		public void run(){
			ScheduledFuture<?> task= TIMER.scheduleAtFixedRate(update,0,700,TimeUnit.MILLISECONDS);
//			RenameFormat rfTmp = null;
			int err = 0;
			/*/ TODO: this needs to be redone

			for (progress =0;progress<gfileListMod.size();progress++) {
				rfTmp = gfileListMod.get(progress);
				if (rfTmp.undo()>0) {
					err++;
				}
				output.append("Source:\t"+rfTmp.toStringTarget()+"\n"+
						rfTmp.getError()+":\t"+rfTmp.toStringTarget()+"\n");
			}
			*/
			task.cancel(false);
			update.run();
			gPreviewText.append(err+" errors.\nDone.");
			previewProgress.setValue(previewProgress.getMaximum());
		}
	},
	doPreview=new Runnable(){
		final StringBuffer output=new StringBuffer();
		final TimerTask update=new TimerTask(){
			@Override
			public void run(){
				String app=output.toString();
				output.setLength(0);
				gPreviewText.append(app);
				previewProgress.setValue(progress);
			}
		};
		private int progress=0;
		@Override
		public void run(){
			ScheduledFuture<?> task= TIMER.scheduleAtFixedRate(update,0,700,TimeUnit.MILLISECONDS);
			RenameFormat rfTmp;
			try{
				rfTmp=new RenameFormat(null,gtfSyntax.getText());
			}catch(ParseException e1){
				output.append(e1.getMessage());
				return;
			}
			int err = 0;
			rfTmp.setNumber(giStart);
			for (progress =0;progress<gfileListMod.size();progress++) {
				rfTmp.setFile(gfileListMod.get(progress));
				if (rfTmp.mockRename()>0){
					err++;
				}
				output.append("Source:\t"+rfTmp.toString()+"\n"+
						rfTmp.getError()+":\t"+rfTmp.toStringTarget()+"\n");
			}
			task.cancel(false);
			update.run();

			gPreviewText.append(err+" errors.\nDone.");
			previewProgress.setValue(previewProgress.getMaximum());
		}
	};
	public RenameTo() {
		final DropTarget listDT = new DropTarget(gfileListDis, this);
		gfileListDis.setDropTarget(listDT);
		gtfSyntax.setText("$D\\$N.$E");//default format, does nothing
		//begin menu bar
		final SMenuBar mBar = new SMenuBar(SMenuBar.HELP_HELP+SMenuBar.FILE+SMenuBar.FILE_QUIT+SMenuBar.OPTION);
		gHelpWin = new AboutWindow(gParentFrame, "Help", true);
		RenameTo.createHelp(gHelpWin);
		gHelpWin.setSize(12*50, 200);
		mBar.addActionListener(SMenuBar.HELP_HELP,new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				gHelpWin.center();
				gHelpWin.setVisible(true);
			}
		});
		/* ** OPTION MENU ** */
		JMenuItem mTmp = new JMenuItem("Numbering");
		mTmp.setActionCommand("setN");
		mTmp.addActionListener(this);
		mBar.addToOptionMenu(mTmp);
		mTmp = new JMenuItem("Replacements");
		mTmp.setActionCommand("addR");
		mTmp.addActionListener(this);
		mBar.addToOptionMenu(mTmp);
		mBar.addToOptionMenu(gRecursiveAddCheck);
		mBar.addToOptionMenu(gResolveUrlCharsCheck);
		mBar.addToOptionMenu(gUriFriendlyFile);
		mBar.addToOptionMenu(gUriFriendlyDir);
		gResolveUrlCharsCheck.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e){
					RenameFormat.fRESOLVEURLESCAPED=gResolveUrlCharsCheck.isSelected();
			}
		});
		mBar.addToOptionMenu(gUndoFileCheck);
		mBar.addSeparator(SMenuBar.OPTION);
		mTmp = new JCheckBoxMenuItem("Always on top");
		mTmp.setActionCommand("aot");
		mTmp.addActionListener(this);
		mBar.addToOptionMenu(mTmp);
		gParentFrame.setJMenuBar(mBar);
		//end menu bar

		JPanel main = null;
		JPanel bottom = null;
		main = new JPanel(new BorderLayout());
		main.setOpaque(true);
		bottom = SJPanel.makeBoxLayoutPanelX();
		//create and add buttons
		bottom.add(SwingFactory.makeJButton("Rename", "rn", this));
		bottom.add(SwingFactory.makeJButton("Preview", "pr", this));
		bottom.add(SwingFactory.makeJButton("Add Files", "af", this));
		bottom.add(SwingFactory.makeJButton("Add Dir", "ad", this));
		bottom.add(SwingFactory.makeJButton("Clear List", "cl", this));
		bottom.add(SwingFactory.makeJButton("Undo Rename", "un", this));
		/* **** HANDLES SHIFTING THE ITEMS AROUND **** */
		final ActionListener moveListener = new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent ae) {
				boolean update = true;
				if (gfileListDis.getSelectedIndex() == -1) return;
				final int[] indices = gfileListDis.getSelectedIndices();
				File tmp;
				final String cmd = ae.getActionCommand();
				if ("u".equals(cmd)) {
				//move up one
					if (indices[0] == 0) return;
					tmp = gfileListMod.get(indices[0]-1);
					for (int i = 0; i<indices.length; i++) {
						gfileListMod.set(indices[i]-1, gfileListMod.get(indices[i]));
						indices[i]--;
					}
					gfileListMod.set(indices[indices.length-1]+1, tmp);
				} else if ("d".equals(cmd)) {
				//move down one
					final int insertIndex = indices[indices.length-1]+1;
					if (insertIndex == gfileListMod.size()) return;
					tmp = gfileListMod.get(insertIndex);
					for (int index = insertIndex; index > indices[0]; index--) {
						gfileListMod.set(index, gfileListMod.get(index-1));
					}
					gfileListMod.set(indices[0], tmp);
					for (int i = 0; i<indices.length; i++) {
						indices[i]++;
					}
				} else if ("t".equals(cmd)) {
				//move to top
					if (indices[0] == 0) return;
					final File[] top = new File[indices[0]];
					for (int i = 0; i < top.length; i++)
						top[i] = gfileListMod.get(i);
					for (int i = 0; i<indices.length; i++) {
						gfileListMod.set(i, gfileListMod.get(indices[i]));
						indices[i] = i;
					}
					final int offset = indices.length;
					for (int i = 0; i<top.length; i++)
						gfileListMod.set(i+offset, top[i]);
				} else if ("b".equals(cmd)) {
				//move to bottom
					if (indices[indices.length-1] == gfileListMod.size()-1) return;
					final File[] bottom = new File[gfileListMod.size()-indices[indices.length-1]-1];
					int offset = indices[indices.length-1]+1;
					for (int i = 0; i < bottom.length; i++)
						bottom[i] = gfileListMod.get(i+offset);
					offset = gfileListMod.size()-indices.length;
					for (int i = 0; i<indices.length; i++) {
						gfileListMod.set(i+offset, gfileListMod.get(indices[i]));
						indices[i] = i+offset;
					}
					offset = offset-bottom.length;
					for (int i = 0; i<bottom.length; i++)
						gfileListMod.set(i+offset, bottom[i]);
				} else if ("r".equals(cmd)) {
				//remove
					int offset=0;
					for(int i:indices){
						gfileListMod.remove(i-offset);
						offset++;
					}
					update = false;
				}
				if (update) {
				//update selection to match new positions
					gfileListDis.setSelectedIndices(indices);
					gfileListDis.scrollRectToVisible(gfileListDis.getCellBounds(indices[0], indices[indices.length-1]));
				}
			}};
			/*
			 * Right side(moving items in list)
			 */
			final JPanel right = SJPanel.makeBoxLayoutPanelY();
			right.add(SwingFactory.makeJButton("top", "t", moveListener));
			right.add(SwingFactory.makeJButton("up", "u", moveListener));
			right.add(SwingFactory.makeJButton("down", "d", moveListener));
			right.add(SwingFactory.makeJButton("bottom", "b", moveListener));
			right.add(SwingFactory.makeJButton("remove","r", moveListener));
			right.add(SwingFactory.makeJButton("filter","filter", this));

			//add it all to the main panel and the frame
			main.add(SJPanel.makeLabeledPanel(gtfSyntax, "Syntax"),BorderLayout.NORTH);
			main.add(right, BorderLayout.EAST);
			main.add(new JScrollPane(gfileListDis));
			main.add(bottom, BorderLayout.SOUTH);
			gParentFrame.setContentPane(main);

			/*
			 * Create Rename Preview Window
			 */
			gPreviewFrame = SwingFactory.makeDefaultJFrame("Preview Rename");
			gPreviewFrame.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentHidden(ComponentEvent e) {
					gPreviewText.setText("");
					System.gc();
				}
				@Override
				public void componentShown(ComponentEvent e) {}
				});
			gPreviewFrame.setSize(gParentFrame.getWidth(),300);
			gPreviewText.setFont(new Font("Courier New", Font.PLAIN, 12));
			main = new JPanel(new BorderLayout());
			main.add(new JScrollPane(gPreviewText));
			bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
			bottom.add(SwingFactory.makeJButton("Close","cp",new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent arg0) {
					gPreviewFrame.setVisible(false);
					gPreviewText.setText("");
				}
			}));
			main.add(bottom, BorderLayout.SOUTH);
			main.add(previewProgress,BorderLayout.NORTH);
			gPreviewFrame.add(main);
			//End Rename Preview Creation

			/*
			 * File list filter(selects files matching or not matching the filter)
			 */
			final ActionListener filter_listener = new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent ae) {
					final String ac = SwingFactory.getActionCommand(ae);
					final Vector<Integer> indecies = new Vector<Integer>();
					if ("s".equals(ac)) {
					//select matching
						for (int i=0;i <gfileListMod.size(); i++) {
							if (gfileListMod.get(i).toString().contains(gFilterInput.getText()))
								indecies.add(i);
						}
					} else if ("si".equals(ac)){
						//select not matching
						for (int i=0;i <gfileListMod.size(); i++) {
							if (!gfileListMod.get(i).toString().contains(gFilterInput.getText()))
								indecies.add(i);
						}
					} else {
					//hide window
						gFilterWin.setVisible(false);
						return;
					}
					final int inde[] = new int[indecies.size()];
					for(int i=0; i<indecies.size(); i++) {
						inde[i] = indecies.get(i);
					}
					gfileListDis.setSelectedIndices(inde);
				}
			};//file list filter
			//build filter window
			gFilterWin.addCenter(gFilterInput);
			gFilterWin.addBottom(SwingFactory.makeJButton("Select", "s", filter_listener));
			gFilterWin.addBottom(SwingFactory.makeJButton("Select Inverse", "si", filter_listener));
			gFilterWin.addBottom(SwingFactory.makeJButton("Cancel", "c", filter_listener));
			gFilterWin.pack();

			//Pack it up and show it off
			gParentFrame.setSize(400,100);
			gParentFrame.pack();
			gPreviewFrame.setSize(gParentFrame.getWidth(), gPreviewFrame.getHeight());
			gParentFrame.setLocationRelativeTo(null);
			gParentFrame.setVisible(true);
	}
	public static void main(final String[] args) {
		new RenameTo();
	}
	@Override
	public void actionPerformed (final ActionEvent ae) {
		RenameFormat.fURISAFEF=gUriFriendlyFile.isSelected();
		RenameFormat.fURISAFED=gUriFriendlyDir.isSelected();
		final String ac = SwingFactory.getActionCommand(ae);
		if (ac.equals("af")){
		//add files
			final File[] x = SwingFactory.getFileNames(null);
			if (x==null || x.length==0) return;
			final FileLoader floader = new FileLoader(x, gRecursiveAddCheck.isSelected());
			gFileLoaderWin.setWorker(floader);
			gFileLoaderWin.center();
			gFileLoaderWin.setVisible(true);
			floader.execute();
			try {
				addFiles(floader.get());
			} catch (final InterruptedException e) {
				e.printStackTrace();
			} catch (final ExecutionException e) {
				e.printStackTrace();
			}
		} else if (ac.equals("ad")) {
		//add directories
			final File x[] = SwingFactory.getDirNames(null);
			if (x==null || x.length==0) return;
			final FileLoader floader = new FileLoader(x, gRecursiveAddCheck.isSelected());
			gFileLoaderWin.setWorker(floader);
			gFileLoaderWin.center();
			gFileLoaderWin.setVisible(true);
			floader.execute();
			try {
				addFiles(floader.get());
			} catch (final InterruptedException e) {
				e.printStackTrace();
			} catch (final ExecutionException e) {
				e.printStackTrace();
			}
		}else if (ac.equals("rn")) {
		//Rename
			gPreviewFrame.setVisible(true);
			gPreviewFrame.setLocationRelativeTo(null);
			Thread rename=new Thread(doRename);
			rename.start();
		}else if (ac.equals("cl")) {
		//Clear list
			gfileListMod.clear();
		}else if (ac.equals("un")) {
		//Undo Renaming
			gPreviewFrame.setVisible(true);
			gPreviewFrame.setLocationRelativeTo(null);
			Thread undo=new Thread(doUndo);
			undo.start();
		}else if(ac.equals("pr")){
		//Preview renaming
			gPreviewFrame.setVisible(true);
			gPreviewFrame.setLocationRelativeTo(null);
			Thread preview=new Thread(doPreview);
			preview.start();
		}else if (ac.equals("setN")){
		//set the start number for numbered renaming
			final String tmp = SJOptionPane.prompt("Number file numbering will start at:", String.valueOf(giStart));
			if(!do_str.isNaN(tmp)){
				giStart = Integer.parseInt(tmp);
			}
		}else if (ac.equals("aot")){
		//toggle always on top for main and child windows
			boolean state= ((JCheckBoxMenuItem)ae.getSource()).getState();
			gParentFrame.setAlwaysOnTop(state);
			gHelpWin.setAlwaysOnTop(state);
			gFileLoaderWin.setAlwaysOnTop(state);
			gPreviewFrame.setAlwaysOnTop(state);
		} else if (ac.equals("filter")) {
			//show file filter window
			new Thread(new Runnable () {
				@Override
				public void run() {
					gFilterWin.center();
					gFilterWin.setVisible(true);
				}
			}).start();
		}
		//System.out.println(do_gui.getFileName(null));
	}
	/**
	 * TODO: This needs a rewrite
	 * @param flist
	 */
	private void addFiles(final java.util.List<File> flist) {
		//attempts to add files to the list using the SwingWorker method
		//far from perfect
		//Sometimes the list box doesn't refresh(shows empty)
		final SwingWorker<Object, File> fadder = new SwingWorker<Object, File>() {
			int fcount = 0;
			@Override
			protected Object doInBackground() throws Exception {
				try {
					for (final File file : flist) {
						if (isCancelled()) {break;}
						fcount++;
						gfileListMod.addElement(file);
						setProgress((fcount*100)/flist.size());
					}
				}catch (final Exception e) {
				}
				setProgress(100);
				return null;
			}
		};
		gFileLoaderWin.setWorker(fadder);
		fadder.execute();
		gFileLoaderWin.setVisible(true);
	}
	@Override
	public void dragEnter(final DropTargetDragEvent arg0) {}
	@Override
	public void dragExit(final DropTargetEvent arg0) {}
	@Override
	public void dragOver(final DropTargetDragEvent arg0) {}
	@Override
	@SuppressWarnings("unchecked")
	public void drop(final DropTargetDropEvent dtde) {
		final DataFlavor[] DFL = dtde.getCurrentDataFlavors();
		Transferable dl = null;
		for (final DataFlavor df : DFL) {
			if (df.equals(DataFlavor.javaFileListFlavor)) {
				dl = dtde.getTransferable();
			}
		}
		if (dl == null) {
			dtde.rejectDrop();
			return;
		}
		dtde.acceptDrop(DnDConstants.ACTION_LINK);
		java.util.List<File> fl = null;
		try {
			fl = (java.util.List<File>) dl.getTransferData(DataFlavor.javaFileListFlavor);
		} catch (final Exception e) {
			e.printStackTrace();
			return;
		}
		final FileLoader floader = new FileLoader(fl, gRecursiveAddCheck.isSelected());
		gFileLoaderWin.setWorker(floader);
		gFileLoaderWin.center();
		gFileLoaderWin.setVisible(true);
		floader.execute();
		try {
			addFiles(floader.get());
		} catch (final InterruptedException e) {
			e.printStackTrace();
		} catch (final ExecutionException e) {
			e.printStackTrace();
		}
		dtde.dropComplete(true);
	}
	@Override
	public void dropActionChanged(final DropTargetDragEvent arg0) {}
	protected static void createHelp(final AboutWindow help) {
		help.appendLineBold("$N");
		help.appendLine("\tFile name without extention");

		help.appendLineBold("$N(n)");
		help.appendLine("\tCharacters starting from n of the file name without extention. (inclusive)");
		help.appendLine("\tYou can use negative numbers to start from the end of the filename.");
		help.appendLine("\tE.g. 'hello.txt' with  $N(-3) will give 'llo'.");

		help.appendLineBold("$N(n,m)");
		help.appendLine("\tCharacters n to m of the file name without extention. (inclusive, exclusive)");
		help.appendLine("\tYou can use negative numbers to start from the end of the filename.");
		help.appendLine("\tE.g. 'hello.txt' with  $N(-2,-1) will give 'l'.");

		help.appendLineBold("$F");
		help.appendLine("\tFile name with extention.");

		help.appendLineBold("$F(n,m)");
		help.appendLine("\tCharacters n to m of the file name with extention. (inclusive, exclusive)");
		help.appendLine("\tYou can use negative numbers to start from the end of the filename.");
		help.appendLine("\tE.g. 'hello.txt' with  $F(-2,-1) will give 'x'.");

		help.appendLineBold("$F(n)");
		help.appendLine("\tCharacters starting from n of the file name with extention. (inclusive)");
		help.appendLine("\tYou can use negative numbers to start from the end of the filename.");
		help.appendLine("\tE.g. 'hello.txt' with  $F(-3) will give 'txt'.");

		help.appendLineBold("$E");
		help.appendLine("\tExtention of file without '.'");
		help.appendLine("\tE.g. 'hello.txt.log' with $E will give 'log'");

		help.appendLineBold("$D");
		help.appendLine("\tPath without the ending '\\'");

		help.appendLineBold("$D(n)");
		help.appendLine("\tPart n of the directory name from the lowest level.");
		help.appendLine("\tE.g. $D(1) of \"C:\\program files\\games\\doom.exe\" = \"games\"");

		help.appendLineBold("$M");
		help.appendLine("\tMoves the file up one directory. Equivalent to $M(1).");

		help.appendLineBold("$M(n)");
		help.appendLine("\tMoves the file up the directory tree n times.");

		help.appendLineBold("$#");
		help.appendLine("\tNumber starting from 0. Additional '#' can be added to pad with '0's.");
		help.appendLine("\t(e.g. $## would result in a scheme like 00, 01, 02, etc.)");

		help.appendLineBold("$P");
		help.appendLine("\tThe path with out the drive letter.");


		help.appendLine("\nAny other character is treated as a literal.");
		help.appendLine("The add sub-directories option in the file menu only applies when adding directories.");
	}
}