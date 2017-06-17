package simple.util.files;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class FileAction implements Runnable{
	private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1);
	private final ProgressDialog progressWindow;

	public FileAction(ProgressDialog progressWindow){
		this.progressWindow= progressWindow;
	}

	final StringBuffer output=new StringBuffer();
	int progress, errors;
	final Runnable update=new Runnable(){
		@Override
		public void run(){
			String app=output.toString();
			output.setLength(0);
			progressWindow.append(app);
			progressWindow.setProgressCur(progress);
		}
	};

	@Override
	public final void run(){
		progress= 0;
		errors= 0;
		progressWindow.clear();
		progressWindow.setVisible(true);
		progressWindow.setLocationRelativeTo(null);

		ScheduledFuture<?> task= TIMER.scheduleAtFixedRate(update, 0, 700, TimeUnit.MILLISECONDS);
		try{
			doWork();
		}catch(Exception e){

		}
		task.cancel(false);
		update.run();
		progressWindow.append(errors+" errors.\nDone.");
		progressWindow.setProgressCur(progressWindow.getMaximum());
	}

	protected void setProgressMax(int max){
		progressWindow.setProgressMax(max);
	}
	protected void setProgress(int cur){
		progress= cur;
	}
	protected abstract void doWork() throws Exception;

}
