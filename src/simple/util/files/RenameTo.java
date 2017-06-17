package simple.util.files;

import java.awt.BorderLayout;
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
import java.io.File;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import simple.gui.AboutWindow;
import simple.gui.SDialog;
import simple.gui.SwingWorkerProgressWindow;
import simple.gui.component.MoveableJList;
import simple.gui.component.SMenuBar;
import simple.gui.factory.SJOptionPane;
import simple.gui.factory.SJPanel;
import simple.gui.factory.SwingFactory;
import simple.io.ParseException;
import simple.io.RenameFormat;
import simple.util.FileLoader;
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
	private final UndoRename undoRename= new UndoRename();
	final DefaultListModel<File> gfileListMod = new DefaultListModel<File>();
	final MoveableJList<File> gfileListDis = new MoveableJList<File>(gfileListMod);
	final JTextField gtfSyntax = new JTextField();
	final ProgressDialog gPreviewFrame;
	final JTextField
		gtfRep1 = new JTextField(),
		gtfRep2 = new JTextField();
	//final JComboBox gReplaceCombo = new JComboBox();
	final JFrame gParentFrame = SwingFactory.makeDefaultJFrame("Rename Utility");
	final JCheckBoxMenuItem
		gRecursiveAddCheck = new JCheckBoxMenuItem("Add Subdirectories"),
		gUndoFileCheck = new JCheckBoxMenuItem("Make Undo File"),
		gResolveUrlCharsCheck = new JCheckBoxMenuItem("Resolve URL Characters"),
		gUriFriendlyFile = new JCheckBoxMenuItem("URI Friendly File Name"),
		gUriFriendlyDir = new JCheckBoxMenuItem("URI Friendly Dir Name");
	final AboutWindow gHelpWin;
	final SwingWorkerProgressWindow gFileLoaderWin = new SwingWorkerProgressWindow(gParentFrame, "Adding Files");
	private int giStart = 0;
	private final SDialog gFilterWin = new SDialog(gParentFrame, "Select files", true);
	final JTextField gFilterInput = new JTextField();
	private final FileActionRename doRename;
	private final FileActionUndoRename doUndo;
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
		mTmp = new JMenuItem("Replacements (not implemented)");
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
					RenameFormat.fRESOLVEURLESCAPED= gResolveUrlCharsCheck.isSelected();
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
				if (gfileListDis.getSelectedIndex() == -1){
					return;
				}

				final String cmd = ae.getActionCommand();
				if ("u".equals(cmd)) {
				//move up one
					gfileListDis.moveSelectedUp();
				} else if ("d".equals(cmd)) {
				//move down one
					gfileListDis.moveSelectedDown();
				} else if ("t".equals(cmd)) {
				//move to top
					gfileListDis.moveSelectedToTop();
				} else if ("b".equals(cmd)) {
				//move to bottom
					gfileListDis.moveSelectedToBottom();
				} else if ("r".equals(cmd)) {
				//remove
					gfileListDis.removeSelected();
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
			gPreviewFrame = new ProgressDialog(gParentFrame, "Rename Preview");
			doRename= new FileActionRename(gPreviewFrame);
			doUndo= new FileActionUndoRename(gPreviewFrame);
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
			doRename.setFileList(gfileListMod);
			doRename.setMakeUndo(gUndoFileCheck.isSelected());
			doRename.setUndoList(undoRename);
			doRename.setIsPreview(false);
			try{
				doRename.setRenameFormat(new RenameFormat(null, gtfSyntax.getText()));
				Thread rename=new Thread(doRename);
				rename.start();
			}catch(ParseException e){
				SJOptionPane.showErrorMessage(gParentFrame, e);
				e.printStackTrace();
			}
		}else if (ac.equals("cl")) {
		//Clear list
			gfileListMod.clear();
		}else if (ac.equals("un")) {
		//Undo Renaming
			doUndo.setUndoList(undoRename);
			Thread undo=new Thread(doUndo);
			undo.start();
		}else if(ac.equals("pr")){
		//Preview renaming
			doRename.setFileList(gfileListMod);
			doRename.setMakeUndo(gUndoFileCheck.isSelected());
			doRename.setUndoList(undoRename);
			doRename.setIsPreview(true);
			try{
				doRename.setRenameFormat(new RenameFormat(null, gtfSyntax.getText()));
				Thread rename=new Thread(doRename);
				rename.start();
			}catch(ParseException e){
				SJOptionPane.showErrorMessage(gParentFrame, e);
				e.printStackTrace();
			}
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
		Transferable dl = dtde.getTransferable();
		if(!dl.isDataFlavorSupported(DataFlavor.javaFileListFlavor)){
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