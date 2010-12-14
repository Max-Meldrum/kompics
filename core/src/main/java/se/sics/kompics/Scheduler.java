package se.sics.kompics;

public abstract class Scheduler {

	public abstract void schedule(Component c, int w);

	public abstract void proceed();

	public abstract void shutdown();
	
	protected final void executeComponent(Component component, int w) {
		((ComponentCore) component).execute(w);
	}
}
