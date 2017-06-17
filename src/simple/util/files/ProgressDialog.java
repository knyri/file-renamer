package simple.util.files;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import simple.gui.SDialog;
import simple.gui.factory.SwingFactory;

public class ProgressDialog extends SDialog{
	private static final long serialVersionUID=1L;

	private final JTextArea progressText= new JTextArea();

	private final ProgressDialog progressFrame= this;

	private final JProgressBar progressProgress= new JProgressBar();

	public ProgressDialog(JFrame parent, String title){
		super(parent, title, true);
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setSize(parent.getWidth(), 300);

		// Clears contents when hidden
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentHidden(ComponentEvent e) {
				progressText.setText("");
			}
			@Override
			public void componentShown(ComponentEvent e) {}
		});

		addTop(progressProgress);

		progressText.setFont(new Font("Courier New", Font.PLAIN, 12));
		addCenter(new JScrollPane(progressText));

		addBottom(SwingFactory.makeJButton("Close","cp",new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent arg0) {
					progressFrame.setVisible(false);
					progressText.setText("");
				}
		}));
	}

	public void clear(){
		progressText.setText("");
		progressProgress.setValue(0);
	}
	public void append(String text){
		progressText.append(text);
	}
	public void appendln(String text){
		progressText.append(text);
		progressText.append("\n");
	}

	public void setProgressMax(int max){
		progressProgress.setMaximum(max);
	}
	public void setProgressCur(int cur){
		progressProgress.setValue(cur);
	}

	public void update(String text, int val){
		progressText.append(text);
		progressProgress.setValue(val);
	}

	public int getMaximum(){
		return progressProgress.getMaximum();
	}
}
