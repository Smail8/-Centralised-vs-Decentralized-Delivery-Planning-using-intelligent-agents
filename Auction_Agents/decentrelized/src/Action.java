package template;

public class Action {
	public int task;
	public int command;
	public int indexer;
	
	public Action(int task, int command, int numTasks) {
		this.task = task;
		this.command = command;
		this.indexer = numTasks * command;
	}
	
	public Action() {
	}
	
	public Action(Action action) {
		this.task = action.task;
		this.command = action.command;
		this.indexer = action.indexer;
	}
	
	public void adabptIndexer(int numTasks) {
		this.indexer = this.command * numTasks;
	}
	
	public void print() {
		if(command == 0)
			System.out.println("action = P"+task+"");
		else
			System.out.println("action = D"+task+"");
	}
	
	@Override
	public int hashCode() {
		int code = 0;
		int factor = 31;
		
		code = factor * task + factor * ((command == 0) ? 123:127) + factor * indexer;
		return code;
	}
	
	@Override
	public boolean equals(Object obj) {
		boolean flag = false;
		if (!(obj instanceof Action)) 
		 return false;
		if (this == obj)
			return true;
		Action action = (Action) obj;
		flag = (action != null && this.task == action.task && this.command == action.command && this.indexer == action.indexer);
		return flag;
	}
}